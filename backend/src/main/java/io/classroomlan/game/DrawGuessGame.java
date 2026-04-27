package io.classroomlan.game;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.InputStream;

/**
 * 你画我猜游戏逻辑
 *
 * 流程:
 * 1. 主持人/系统选取一名 Drawer
 * 2. Drawer 从 3 个词中选择 → 开始作画
 * 3. 其他玩家发送猜测 (GUESS)
 * 4. 猜对 → 该玩家 + Drawer 各 +10 分，本轮结束，下一轮换 Drawer
 * 5. 80 秒超时则切换 Drawer
 *
 * 消息类型 (payload.type):
 * - JOIN/DRAWER_SELECT: 主持人选词
 * - STROKE: {points:[{x,y},...], color, width}
 * - HINT: Drawer 发送文字提示
 * - GUESS: {word: "..."} → 验证失败/成功
 * - CLEAR: 清空画布
 */
public class DrawGuessGame {
    private static final Logger LOGGER = Logger.getLogger(DrawGuessGame.class.getName());
    private static final int ROUND_TIME_SEC = 80;
    private static final int MAX_HISTORY = 50;

    private final GameRoom room;
    private final List<String> wordPool;
    private final Map<String, Integer> scores = new ConcurrentHashMap<>();

    private String currentDrawer; // player name
    private String currentWord;
    private final List<Stroke> strokes = new ArrayList<>();
    private long roundStartTime;
    private final AtomicInteger roundNumber = new AtomicInteger(1);
    private boolean roundActive;

    public DrawGuessGame(GameRoom room) {
        this.room = room;
        this.wordPool = loadWordPool();
        this.roundActive = false;
    }

    // ── 动作处理 ─────────────────────────────────────────────────────────

    public GameActionResult handleAction(String playerName, String actionType, Map<String, Object> payload) {
        // 仅 Drawer 允许发送绘画相关
        if (actionType.equals("STROKE") || actionType.equals("CLEAR")) {
            if (!playerName.equals(currentDrawer)) {
                return GameActionResult.error("Only the drawer can draw");
            }
            if (actionType.equals("STROKE")) {
                Stroke stroke = parseStroke(payload);
                if (stroke != null) strokes.add(stroke);
                return GameActionResult.broadcast("STROKE", Map.of(
                    "stroke", stroke,
                    "drawer", currentDrawer
                ));
            } else if (actionType.equals("CLEAR")) {
                strokes.clear();
                return GameActionResult.broadcast("CLEAR", Map.of());
            }
        }

        // Drawer 发送提示
        if (actionType.equals("HINT")) {
            if (!playerName.equals(currentDrawer)) {
                return GameActionResult.error("Only the drawer can send hints");
            }
            String text = (String) payload.get("text");
            return GameActionResult.broadcast("HINT", Map.of("text", text));
        }

        // 猜测
        if (actionType.equals("GUESS")) {
            return handleGuess(playerName, (String) payload.get("word"));
        }

        // Drawer 选词 (仅第一轮或换人时)
        if (actionType.equals("SELECT_WORD")) {
            if (!playerName.equals(currentDrawer)) {
                return GameActionResult.error("Only the drawer can select word");
            }
            String word = (String) payload.get("word");
            if (word != null && wordPool.contains(word)) {
                currentWord = word;
                startRound();
                return GameActionResult.broadcast("WORD_SELECTED", Map.of("word", "***"));
            }
            return GameActionResult.error("Invalid word");
        }

        // 下一轮（主持人触发）
        if (actionType.equals("NEXT_ROUND")) {
            return nextRound();
        }

        return GameActionResult.error("Unknown action: " + actionType);
    }

    // ── 猜词验证 ─────────────────────────────────────────────────────────

    private GameActionResult handleGuess(String playerName, String guess) {
        if (guess == null) return GameActionResult.error("Empty guess");
        if (!roundActive) return GameActionResult.error("Round not active");

        String normalizedGuess = guess.trim().toLowerCase();
        String normalizedWord  = currentWord.toLowerCase();

        if (normalizedGuess.equals(normalizedWord)) {
            // 猜对了！
            roundActive = false;
            int drawerPoints = 10;
            int guesserPoints = 10;
            scores.merge(currentDrawer, drawerPoints, Integer::sum);
            scores.merge(playerName,   guesserPoints, Integer::sum);

            LOGGER.info("Correct guess by " + playerName + "! Word: " + currentWord);
            return GameActionResult.broadcast("GUESS_RESULT", Map.of(
                "guesser", playerName,
                "word",    currentWord,
                "correct", true,
                "drawerPoints",  drawerPoints,
                "guesserPoints", guesserPoints
            ));
        } else {
            // 错 — 只通知该玩家（节约流量），或广播“不对”
            return GameActionResult.privateMsg(playerName, "GUESS_WRONG", Map.of(
                "yourGuess", guess
            ));
        }
    }

    // ── 回合控制 ─────────────────────────────────────────────────────────

    private void startRound() {
        roundActive = true;
        strokes.clear();
        roundStartTime = System.currentTimeMillis();
        LOGGER.info("Round " + roundNumber.get() + " started. Drawer: " + currentDrawer + " Word: " + currentWord);
    }

    private GameActionResult nextRound() {
        int count = room.getPlayers().size();
        if (count == 0) {
            return GameActionResult.error("No players");
        }

        // Rotate drawer
        List<String> players = new ArrayList<>(room.getPlayers());
        int currentIdx = players.indexOf(currentDrawer);
        if (currentIdx == -1) currentIdx = 0;
        String nextDrawer = players.get((currentIdx + 1) % players.size());
        currentDrawer = nextDrawer;

        // Pick 3 random words
        List<String> options = pickThreeWords();
        roundNumber.incrementAndGet();
        roundActive = false; // wait for selection

        return GameActionResult.broadcast("NEW_ROUND", Map.of(
            "drawer",  currentDrawer,
            "options", options,
            "round",   roundNumber.get()
        ));
    }

    private List<String> pickThreeWords() {
        List<String> copy = new ArrayList<>(wordPool);
        Collections.shuffle(copy);
        return copy.subList(0, Math.min(3, copy.size()));
    }

    private Stroke parseStroke(Map<String, Object> payload) {
        Stroke s = new Stroke();
        s.color  = (String) payload.get("color");
        s.width  = ((Number) payload.get("width")).intValue();
        Object pts = payload.get("points");
        if (pts instanceof List<?> list) {
            for (Object p : list) {
                if (p instanceof Map<?,?> m) {
                    Number x = (Number) m.get("x");
                    Number y = (Number) m.get("y");
                    if (x != null && y != null) {
                        s.points.add(new Point(x.doubleValue(), y.doubleValue()));
                    }
                }
            }
        }
        return s;
    }

    // ── 定时检查（由 GameWsEndpoint 每 1s 调用） ─────────────────────────

    public void tick() {
        if (roundActive && System.currentTimeMillis() - roundStartTime > ROUND_TIME_SEC * 1000L) {
            LOGGER.info("Round timed out — advancing drawer");
            roundActive = false;
            // Auto-advance to next drawer (host trigger simulation)
            // Reuse nextRound logic directly; GameWsEndpoint will broadcast result
            nextRound();
        }
    }

    // ── 查询 ─────────────────────────────────────────────────────────────

    public Map<String, Integer> getScores() { return new HashMap<>(scores); }
    public String getCurrentDrawer()  { return currentDrawer; }
    public String getCurrentWord()    { return currentWord; }  // Host-only
    public boolean isRoundActive()    { return roundActive; }

    // ── 手动控制（测试/主持人用）─────────────────────────────────────────

    /**
     * 仅用于测试: 手动设置当前画词并启动回合
     */
    public void setCurrentWord(String word) {
        this.currentWord = word;
    }

    public void setCurrentDrawer(String name) {
        this.currentDrawer = name;
        // Auto-start round with word options
        List<String> options = pickThreeWords();
        // Server will send options to drawer ONLY
    }

    public void assignDrawerRound() {
        // Called after room join to start first round
        if (currentDrawer == null && !room.getPlayers().isEmpty()) {
            setCurrentDrawer(room.getPlayers().get(0));
        }
    }

    // ── 内部数据结构 ─────────────────────────────────────────────────────

    private static class Stroke {
        List<Point> points = new ArrayList<>();
        String color = "#000000";
        int width = 3;
        // JSON serialization simplified for manual handling
    }

    private static class Point {
        double x, y;
        Point(double x, double y) { this.x = x; this.y = y; }
    }

    private List<String> loadWordPool() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("words_draw.json")) {
            if (is == null) {
                LOGGER.warning("words_draw.json not found, using fallback word list");
                return fallbackWords();
            }
            Map<String, Object> root = mapper.readValue(is, new TypeReference<>() {});
            List<Map<String, Object>> categories = (List<Map<String, Object>>) root.get("categories");
            List<String> allWords = new ArrayList<>();
            for (Map<String, Object> cat : categories) {
                List<String> words = (List<String>) cat.get("words");
                if (words != null) allWords.addAll(words);
            }
            if (allWords.isEmpty()) return fallbackWords();
            return allWords;
        } catch (Exception e) {
            LOGGER.warning("Failed to load words_draw.json: " + e.getMessage());
            return fallbackWords();
        }
    }

    private List<String> fallbackWords() {
        return Arrays.asList(
            "苹果", "香蕉", "猫", "狗", "太阳", "月亮", "汽车", "飞机",
            "电脑", "手机", "书", "笔", "杯子", "雨伞", "帽子", "鞋子"
        );
    }

    // ── 结果封装 ─────────────────────────────────────────────────────────

    public static class GameActionResult {
        public enum Type { BROADCAST, PRIVATE, ERROR }
        public Type type;
        public String event;
        public Map<String, Object> data;
        public String targetPlayer;   // for PRIVATE
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
