package io.classroomlan.game;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.InputStream;

/**
 * 快问快答游戏逻辑
 *
 * 流程:
 * 1. Host 选择题目数量 (默认 5 题) → 开始
 * 2. 服务器向房间所有人广播题目 (题目 + 4 选项)
 * 3. 玩家在 15s 内点击答案
 * 4. 第一个答对 +10 分，后续答对 +5 分，答错 -2 分
 * 5. 题目结束广播排行榜
 * 6. 全部题目结束后显示最终排名
 *
 * Payload 类型:
 * - START_QUIZ     {questionCount: 5}
 * - ANSWER         {answer: 0..3}
 * - NEXT_QUESTION  (host)
 * - END_QUIZ       (host)
 *
 * Server events:
 * - NEW_QUESTION   {question: {...}}
 * - ANSWER_RESULT  {correct: bool, points: int, total: int}
 * - LEADERBOARD    {scores: {name: int}}
 * - GAME_END       {rankings: [...]}
 */
public class QuizGame {
    private static final Logger LOGGER = Logger.getLogger(QuizGame.class.getName());
    private static final int QUESTION_TIME_SEC = 15;
    private static final int POINTS_FIRST = 10;
    private static final int POINTS_LATER = 5;
    private static final int POINTS_WRONG  = -2;

    private final GameRoom room;
    private final List<Question> questionBank;
    private final Map<String, Integer> scores = new ConcurrentHashMap<>();

    private List<Question> selectedQuestions = new ArrayList<>();
    private int currentQuestionIndex = -1;
    private Question currentQuestion;
    private long questionStartTime;
    private boolean questionActive = false;
    private Set<String> answeredThisQuestion = ConcurrentHashMap.newKeySet();
    private boolean firstAnswererLogged = false;

    public QuizGame(GameRoom room) {
        this.room = room;
        this.questionBank = loadQuestionBank();
    }

    // ── 游戏控制 ─────────────────────────────────────────────────────

    public GameActionResult handleAction(String playerName, String actionType, Map<String, Object> payload) {
        switch (actionType) {
            case "START_QUIZ" -> {
                return startQuiz((Integer) payload.get("questionCount"));
            }
            case "NEXT_QUESTION" -> {
                return nextQuestion();
            }
            case "ANSWER" -> {
                Integer answerIdx = (Integer) payload.get("answer");
                if (answerIdx == null) return GameActionResult.error("Answer index required");
                return handleAnswer(playerName, answerIdx);
            }
            case "END_QUIZ" -> {
                return endQuiz();
            }
            default -> {
                return GameActionResult.error("Unknown action: " + actionType);
            }
        }
    }

    private GameActionResult startQuiz(Integer count) {
        if (count == null || count <= 0) count = 5;
        selectedQuestions = new ArrayList<>(questionBank);
        Collections.shuffle(selectedQuestions);
        selectedQuestions = selectedQuestions.subList(0, Math.min(count, selectedQuestions.size()));

        scores.clear();  // reset
        currentQuestionIndex = -1;
        LOGGER.info("Quiz started with " + selectedQuestions.size() + " questions");

        return nextQuestion(); // 自动出第一题
    }

    private GameActionResult nextQuestion() {
        currentQuestionIndex++;
        if (currentQuestionIndex >= selectedQuestions.size()) {
            return endQuiz();
        }

        currentQuestion = selectedQuestions.get(currentQuestionIndex);
        questionActive = true;
        questionStartTime = System.currentTimeMillis();
        answeredThisQuestion.clear();
        firstAnswererLogged = false;

        Map<String, Object> data = new HashMap<>();
        data.put("question",   currentQuestion);
        data.put("index",      currentQuestionIndex + 1);
        data.put("total",      selectedQuestions.size());
        data.put("timeLimit",  QUESTION_TIME_SEC);

        LOGGER.info("Question " + (currentQuestionIndex + 1) + "/" + selectedQuestions.size() + ": " + currentQuestion.q);
        return GameActionResult.broadcast("NEW_QUESTION", data);
    }

    private GameActionResult handleAnswer(String playerName, int answerIdx) {
        if (!questionActive) return GameActionResult.error("No active question");

        long elapsed = (System.currentTimeMillis() - questionStartTime) / 1000;
        if (elapsed > QUESTION_TIME_SEC) {
            questionActive = false;
            return GameActionResult.error("Time's up!");
        }

        if (answeredThisQuestion.contains(playerName)) {
            return GameActionResult.error("Already answered");
        }
        answeredThisQuestion.add(playerName);

        boolean correct = (answerIdx == currentQuestion.answer);
        int points = 0;

        if (correct) {
            if (!firstAnswererLogged) {
                points = POINTS_FIRST;
                firstAnswererLogged = true;
            } else {
                points = POINTS_LATER;
            }
            scores.merge(playerName, points, Integer::sum);
        } else {
            points = POINTS_WRONG;
            scores.merge(playerName, points, Integer::sum);
        }

        // 广播答题结果（所有人可见）
        Map<String, Object> result = new HashMap<>();
        result.put("answer",    answerIdx);
        result.put("correct",   correct);
        result.put("points",    points);
        result.put("total",     scores.get(playerName));
        result.put("player",    playerName);

        LOGGER.info(playerName + " answered " + answerIdx + (correct ? " (+" + points + ")" : " (" + points + ")"));
        return GameActionResult.broadcast("ANSWER_RESULT", result);
    }

    private GameActionResult endQuiz() {
        questionActive = false;

        // 排序排行榜
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        List<String> rankings = new ArrayList<>();
        for (var e : sorted) {
            rankings.add(e.getKey() + ":" + e.getValue());
        }

        Map<String, Object> data = new HashMap<>();
        data.put("rankings", rankings);
        LOGGER.info("Quiz ended. Rankings: " + rankings);
        return GameActionResult.broadcast("GAME_END", data);
    }

    // ── 定时器 tick ────────────────────────────────────────────────────

    /** Called by GameWsEndpoint once per second */
    public void tick() {
        if (questionActive) {
            long elapsed = (System.currentTimeMillis() - questionStartTime) / 1000;
            if (elapsed > QUESTION_TIME_SEC) {
                questionActive = false;
                LOGGER.info("Question time out. Moving to next...");
                // Auto-advance
                // (handler will call nextQuestion on next tick via server loop)
            }
        }
    }

    // ── 查询 ───────────────────────────────────────────────────────────

    public Map<String, Integer> getScores() { return new HashMap<>(scores); }
    public Question getCurrentQuestion() { return currentQuestion; }
    public boolean isQuestionActive()       { return questionActive; }

    // ── 题库 ───────────────────────────────────────────────────────────

    private List<Question> loadQuestionBank() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("questions_quiz.json")) {
            if (is == null) {
                LOGGER.warning("questions_quiz.json not found, using fallback questions");
                return fallbackQuestions();
            }
            Map<String, Object> root = mapper.readValue(is, new TypeReference<>() {});
            List<Map<String, Object>> qList = (List<Map<String, Object>>) root.get("questions");
            List<Question> list = new ArrayList<>();
            for (Map<String, Object> q : qList) {
                String questionText = (String) q.get("q");
                List<String> opts = (List<String>) q.get("options");
                Integer ans = (Integer) q.get("answer");
                list.add(new Question(questionText, opts.toArray(new String[0]), ans));
            }
            return list;
        } catch (Exception e) {
            LOGGER.warning("Failed to load questions_quiz.json: " + e.getMessage());
            return fallbackQuestions();
        }
    }

    private List<Question> fallbackQuestions() {
        return Arrays.asList(
            new Question("Java 中哪个关键字用于定义常量？", new String[]{"final", "static", "const", "readonly"}, 0),
            new Question("HTML 是什么的缩写？", new String[]{"Hyper Text Markup Language","High Tech Modern Language","Hyper Transfer Markup Language","Home Tool Markup Language"}, 0),
            new Question("地球是第几大行星？", new String[]{"第三大", "第四大", "第五大", "第六大"}, 2),
            new Question("水的化学式是？", new String[]{"CO2", "H2O", "O2", "H2O2"}, 1),
            new Question("《红楼梦》的作者是？", new String[]{"施耐庵", "罗贯中", "曹雪芹", "吴承恩"}, 2)
        );
    }

    // ── 静态内部类 ─────────────────────────────────────────────────────

    public static class Question {
        public String q;
        public String[] options;
        public int answer;  // 0..3

        public Question(String q, String[] options, int answer) {
            this.q = q;
            this.options = options;
            this.answer = answer;
        }
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
