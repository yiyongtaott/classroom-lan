package io.classroomlan.game;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.InputStream;

/**
 * 谁是卧底游戏逻辑
 *
 * 流程:
 * - 分配角色: 平民(CIVILIAN, 词A) / 卧底(SPY, 词B)
 * - 阶段1: 玩家描述自己的词(30秒)
 * - 阶段2: 投票淘汰一人
 * - 结算并进入下一轮/结束
 *
 * Payload:
 *   START_GAME: {players: [...]}
 *   DESCRIPTION: {text: "..."}
 *   VOTE:       {target: "playerName"}
 *   NEXT_PHASE: {} (host triggers)
 *
 * Events:
 *   ROLE_ASSIGNED, PHASE_CHANGED, PLAYER_ELIMINATED, GAME_OVER
 */
public class WerewolfGame {
    private static final Logger LOGGER = Logger.getLogger(WerewolfGame.class.getName());
    private static final int DESCRIPTION_TIME_SEC = 30;
    private static final int VOTING_TIME_SEC    = 20;

    public enum Phase { LOBBY, ROLE_REVEAL, DESCRIPTION, VOTING, RESULT, ENDED }

    private final GameRoom room;
    private final List<WordPair> wordPairs;
    private Phase phase = Phase.LOBBY;
    private final Map<String, PlayerState> players = new ConcurrentHashMap<>();
    private String eliminatedName;
    private String eliminatedRole;
    private AtomicInteger round = new AtomicInteger(1);

    public WerewolfGame(GameRoom room) {
        this.room = room;
        this.wordPairs = loadWordPairs();
    }

    public GameActionResult handleAction(String playerName, String actionType, Map<String, Object> payload) {
        switch (actionType) {
            case "START_GAME" -> {
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) payload.get("players");
                return startGame(list);
            }
            case "CONFIRM_ROLE" -> {
                PlayerState p = players.get(playerName);
                if (p != null) p.roleConfirmed = true;
                return GameActionResult.broadcast("PLAYER_READY", Map.of("player", playerName));
            }
            case "DESCRIPTION" -> {
                if (phase != Phase.DESCRIPTION) return GameActionResult.error("Not description phase");
                String text = (String) payload.get("text");
                PlayerState ps = players.get(playerName);
                if (ps != null) ps.description = text;
                return GameActionResult.broadcast("PLAYER_DESCRIBED", Map.of(
                    "player", playerName,
                    "length",  text != null ? text.length() : 0
                ));
            }
            case "VOTE" -> {
                if (phase != Phase.VOTING) return GameActionResult.error("Not voting phase");
                String target = (String) payload.get("target");
                if (target == null) return GameActionResult.error("Target required");
                PlayerState ps = players.get(playerName);
                if (ps != null) ps.voteFor = target;
                return GameActionResult.broadcast("VOTE_CAST", Map.of(
                    "voter",  playerName,
                    "target",  target
                ));
            }
            case "NEXT_PHASE" -> {
                return advancePhase();
            }
            default -> { return GameActionResult.error("Unknown action: " + actionType); }
        }
    }

    private GameActionResult startGame(List<String> playerList) {
        players.clear();
        for (String p : playerList) {
            PlayerState ps = new PlayerState();
            ps.name = p;
            players.put(p, ps);
        }
        round.set(1);
        phase = Phase.ROLE_REVEAL;
        assignRoles();

        LOGGER.info("Werewolf started with " + playerList.size() + " players");
        return broadcastGameState();
    }

    private void assignRoles() {
        int spyCount = players.size() >= 7 ? 2 : 1;
        List<String> names = new ArrayList<>(players.keySet());
        Collections.shuffle(names);

        WordPair pair = wordPairs.get(new Random().nextInt(wordPairs.size()));

        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            PlayerState ps = players.get(name);
            if (i < spyCount) {
                ps.role = Role.SPY;
                ps.word = pair.spyWord;
            } else {
                ps.role = Role.CIVILIAN;
                ps.word = pair.civilianWord;
            }
        }

        // 私发
        for (var e : players.entrySet()) {
            PlayerState ps = e.getValue();
            sendToPlayer(e.getKey(), "ROLE_ASSIGNED", Map.of(
                "role", ps.role.name(),
                "word", ps.word
            ));
        }

        LOGGER.info("Roles set: civilian=" + pair.civilianWord + " spy=" + pair.spyWord);
    }

    private GameActionResult advancePhase() {
        switch (phase) {
            case ROLE_REVEAL -> {
                phase = Phase.DESCRIPTION;
                return broadcastGameState();
            }
            case DESCRIPTION -> {
                phase = Phase.VOTING;
                return broadcastGameState();
            }
            case VOTING -> {
                return resolveVoting();
            }
            case RESULT -> {
                phase = Phase.DESCRIPTION;
                round.incrementAndGet();
                return broadcastGameState();
            }
            case ENDED -> {
                return broadcastGameState();
            }
            default -> { return broadcastGameState(); }
        }
    }

    private GameActionResult resolveVoting() {
        Map<String, Integer> counts = new HashMap<>();
        for (PlayerState ps : players.values()) {
            if (ps.voteFor != null) {
                counts.merge(ps.voteFor, 1, Integer::sum);
            }
        }
        String eliminated = null;
        int max = 0;
        for (var e : counts.entrySet()) {
            if (e.getValue() > max) { max = e.getValue(); eliminated = e.getKey(); }
        }

        PlayerState eps = players.get(eliminated);
        if (eps != null) {
            eps.eliminated = true;
            eliminatedName  = eliminated;
            eliminatedRole = eps.role.name();
            LOGGER.info("Eliminated: " + eliminated + " (" + eliminatedRole + ")");
        }

        String winner = checkWinner();
        if (winner != null) {
            phase = Phase.ENDED;
            Map<String, Object> d = Map.of("winner", winner,
                "reason", winner.equals("CIVILIAN") ? "卧底被淘汰" : "卧底胜利");
            return GameActionResult.broadcast("GAME_OVER", d);
        }

        phase = Phase.RESULT;
        Map<String, Object> d = Map.of(
            "eliminated", Map.of("name", eliminated, "role", eps != null ? eps.role.name() : "UNKNOWN")
        );
        for (PlayerState ps : players.values()) ps.voteFor = null;
        return GameActionResult.broadcast("PLAYER_ELIMINATED", d);
    }

    private String checkWinner() {
        long aliveCiv  = players.values().stream().filter(p -> !p.eliminated && p.role == Role.CIVILIAN).count();
        long aliveSpy  = players.values().stream().filter(p -> !p.eliminated && p.role == Role.SPY).count();
        if (aliveSpy == 0)  return "CIVILIAN";
        if (aliveCiv <= 2)  return "SPY";
        return null;
    }

    // ── 查询 ─────────────────────────────────────────────────────────────

    public Map<String, Object> getGameState(String viewer) {
        Map<String, Object> s = new HashMap<>();
        s.put("phase", phase.name());
        s.put("round", round.get());

        List<String> alive = new ArrayList<>();
        for (var e : players.entrySet()) if (!e.getValue().eliminated) alive.add(e.getKey());
        s.put("players", alive);

        PlayerState my = players.get(viewer);
        if (my != null) {
            s.put("myRole",  my.eliminated ? null : my.role.name());
            s.put("myWord",  my.eliminated ? null : my.word);
            s.put("roleConfirmed", my.roleConfirmed);
        }
        return s;
    }

    // ── 辅助 ─────────────────────────────────────────────────────────────

    private GameActionResult broadcastGameState() {
        Map<String, Object> data = new HashMap<>();
        data.put("phase", phase.name());
        List<String> aliveNames = new ArrayList<>();
        for (var e : players.entrySet()) if (!e.getValue().eliminated) aliveNames.add(e.getKey());
        data.put("players", aliveNames);
        data.put("round", round.get());
        return GameActionResult.broadcast("GAME_STATE", data);
    }

    private void sendToPlayer(String playerName, String event, Map<String, Object> data) {
        // No-op here — will be enacted by the WS endpoint handler
    }

    private List<WordPair> loadWordPairs() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("words_werewolf.json")) {
            if (is == null) {
                return fallbackPairs();
            }
            List<Map<String, String>> list = mapper.readValue(is, new TypeReference<>() {});
            List<WordPair> pairs = new ArrayList<>();
            for (Map<String, String> m : list) {
                pairs.add(new WordPair(m.get("civilian"), m.get("spy")));
            }
            return pairs;
        } catch (Exception e) {
            LOGGER.warning("Failed to load words_werewolf.json: " + e.getMessage());
            return fallbackPairs();
        }
    }

    private List<WordPair> fallbackPairs() {
        return Arrays.asList(
            new WordPair("苹果", "梨"),
            new WordPair("篮球", "排球"),
            new WordPair("警察", "保安"),
            new WordPair("摩托车", "电动车")
        );
    }

    // ── 内部类 ───────────────────────────────────────────────────────────

    private static class PlayerState {
        String name;
        Role role;
        String word;
        boolean roleConfirmed = false;
        String description;
        String voteFor;
        boolean eliminated = false;
    }

    private enum Role { CIVILIAN, SPY }

    private static class WordPair {
        String civilianWord, spyWord;
        WordPair(String c, String s) { this.civilianWord = c; this.spyWord = s; }
    }

    public static class GameActionResult {
        public enum Type { BROADCAST, PRIVATE, ERROR }
        public Type type;
        public String event;
        public Map<String, Object> data;
        public String targetPlayer;
        public String error;

        static GameActionResult broadcast(String event, Map<String, Object> data) {
            GameActionResult r = new GameActionResult();
            r.type = Type.BROADCAST;
            r.event = event;
            r.data = data;
            return r;
        }
        static GameActionResult privateMsg(String player, String event, Map<String, Object> data) {
            GameActionResult r = new GameActionResult();
            r.type = Type.PRIVATE;
            r.targetPlayer = player;
            r.event = event;
            r.data = data;
            return r;
        }
        static GameActionResult error(String msg) {
            GameActionResult r = new GameActionResult();
            r.type = Type.ERROR;
            r.error = msg;
            return r;
        }
    }
}
