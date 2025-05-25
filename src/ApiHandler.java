package src;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.util.*;
import java.sql.*;

/**
 * API ハンドラー基底クラス（Template Method パターン使用）
 */
abstract class BaseApiHandler implements HttpHandler {
    protected final DatabaseManager dbManager;

    public BaseApiHandler() {
        this.dbManager = DatabaseManager.getInstance();
    }

    @Override
    public final void handle(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);

        String method = exchange.getRequestMethod();
        if ("OPTIONS".equals(method)) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        try {
            handleRequest(exchange, method);
        } catch (Exception e) {
            sendErrorResponse(exchange, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * リクエストを処理（サブクラスで実装）
     * 
     * @param exchange HTTPエクスチェンジ
     * @param method   HTTPメソッド
     * @throws IOException IO例外
     */
    protected abstract void handleRequest(HttpExchange exchange, String method) throws IOException;

    /**
     * CORSヘッダーを追加
     * 
     * @param exchange HTTPエクスチェンジ
     */
    protected void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    /**
     * リクエストボディを読み込み
     * 
     * @param exchange HTTPエクスチェンジ
     * @return リクエストボディ
     * @throws IOException IO例外
     */
    protected String readRequestBody(HttpExchange exchange) throws IOException {
        try (Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    /**
     * JSONレスポンスを送信
     * 
     * @param exchange     HTTPエクスチェンジ
     * @param jsonResponse JSONレスポンス
     * @throws IOException IO例外
     */
    protected void sendJsonResponse(HttpExchange exchange, String jsonResponse) throws IOException {
        byte[] bytes = jsonResponse.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * エラーレスポンスを送信
     * 
     * @param exchange     HTTPエクスチェンジ
     * @param errorMessage エラーメッセージ
     * @throws IOException IO例外
     */
    protected void sendErrorResponse(HttpExchange exchange, String errorMessage) throws IOException {
        String json = String.format("{\"success\":false,\"error\":\"%s\"}", errorMessage);
        sendJsonResponse(exchange, json);
    }

    /**
     * JSONから値を抽出（簡易実装）
     * 
     * @param json  JSON文字列
     * @param field フィールド名
     * @return 抽出された値
     */
    protected String extractJsonField(String json, String field) {
        String pattern = "\"" + field + "\":\"";
        int startIndex = json.indexOf(pattern);
        if (startIndex == -1) {
            // 数値の場合
            pattern = "\"" + field + "\":";
            startIndex = json.indexOf(pattern);
            if (startIndex != -1) {
                startIndex += pattern.length();
                int endIndex = json.indexOf(',', startIndex);
                if (endIndex == -1)
                    endIndex = json.indexOf('}', startIndex);
                return json.substring(startIndex, endIndex).trim();
            }
            return "";
        }

        startIndex += pattern.length();
        int endIndex = json.indexOf('"', startIndex);
        return json.substring(startIndex, endIndex);
    }

    /**
     * リストをJSON配列に変換
     * 
     * @param list      変換するリスト
     * @param arrayName 配列名
     * @return JSON文字列
     */
    protected String listToJson(List<? extends Map<String, ?>> list, String arrayName) {
        StringBuilder json = new StringBuilder();
        json.append("{\"").append(arrayName).append("\": [");

        for (int i = 0; i < list.size(); i++) {
            json.append(mapToJson(list.get(i)));
            if (i < list.size() - 1)
                json.append(",");
        }

        json.append("]}");
        return json.toString();
    }

    /**
     * MapをJSONオブジェクトに変換
     * 
     * @param map 変換するMap
     * @return JSON文字列
     */
    protected String mapToJson(Map<String, ?> map) {
        StringBuilder json = new StringBuilder();
        json.append("{");

        List<String> entries = new ArrayList<>();
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String jsonValue = valueToJson(value);
            entries.add(String.format("\"%s\":%s", key, jsonValue));
        }

        json.append(String.join(",", entries));
        json.append("}");
        return json.toString();
    }

    /**
     * 値をJSON形式に変換
     * 
     * @param value 変換する値
     * @return JSON形式の値
     */
    protected String valueToJson(Object value) {
        if (value == null)
            return "null";
        if (value instanceof Number || value instanceof Boolean)
            return value.toString();
        return String.format("\"%s\"", value.toString().replace("\"", "\\\""));
    }
}

/**
 * ユーザー管理ハンドラー
 */
class UserHandler extends BaseApiHandler {
    @Override
    protected void handleRequest(HttpExchange exchange, String method) throws IOException {
        switch (method) {
            case "GET" -> {
                List<Map<String, Object>> users = dbManager.getAllUsers();
                sendJsonResponse(exchange, listToJson(users, "users"));
            }
            case "POST" -> {
                String requestBody = readRequestBody(exchange);
                String username = extractJsonField(requestBody, "username");
                boolean success = dbManager.createUser(username);
                sendJsonResponse(exchange, String.format("{\"success\":%s}", success));
            }
            default -> exchange.sendResponseHeaders(405, -1);
        }
    }
}

/**
 * リポジトリ管理ハンドラー
 */
class RepositoryHandler extends BaseApiHandler {
    @Override
    protected void handleRequest(HttpExchange exchange, String method) throws IOException {
        switch (method) {
            case "GET" -> {
                String query = exchange.getRequestURI().getQuery();
                Integer ownerId = null;
                if (query != null && query.contains("owner_id=")) {
                    String ownerIdStr = extractQueryParam(query, "owner_id");
                    if (!ownerIdStr.isEmpty()) {
                        ownerId = Integer.parseInt(ownerIdStr);
                    }
                }
                List<Map<String, Object>> repos = dbManager.getRepositories(ownerId);
                sendJsonResponse(exchange, listToJson(repos, "repositories"));
            }
            case "POST" -> {
                String requestBody = readRequestBody(exchange);
                String name = extractJsonField(requestBody, "name");
                String ownerIdStr = extractJsonField(requestBody, "owner_id");
                boolean success = dbManager.createRepository(name, Integer.parseInt(ownerIdStr));
                sendJsonResponse(exchange, String.format("{\"success\":%s}", success));
            }
            default -> exchange.sendResponseHeaders(405, -1);
        }
    }

    /**
     * クエリパラメータを抽出
     * 
     * @param query クエリ文字列
     * @param param パラメータ名
     * @return パラメータ値
     */
    private String extractQueryParam(String query, String param) {
        for (String p : query.split("&")) {
            if (p.startsWith(param + "=")) {
                return p.substring(param.length() + 1);
            }
        }
        return "";
    }
}

/**
 * ブランチ管理ハンドラー
 */
class BranchHandler extends BaseApiHandler {
    @Override
    protected void handleRequest(HttpExchange exchange, String method) throws IOException {
        switch (method) {
            case "GET" -> {
                String query = exchange.getRequestURI().getQuery();
                Integer repositoryId = null;
                if (query != null && query.contains("repository_id=")) {
                    String repoIdStr = extractQueryParam(query, "repository_id");
                    if (!repoIdStr.isEmpty()) {
                        repositoryId = Integer.parseInt(repoIdStr);
                    }
                }
                List<Map<String, Object>> branches = dbManager.getBranches(repositoryId);
                sendJsonResponse(exchange, listToJson(branches, "branches"));
            }
            case "POST" -> {
                String requestBody = readRequestBody(exchange);
                String name = extractJsonField(requestBody, "name");
                String repoIdStr = extractJsonField(requestBody, "repository_id");
                boolean success = dbManager.createBranch(name, Integer.parseInt(repoIdStr));
                sendJsonResponse(exchange, String.format("{\"success\":%s}", success));
            }
            default -> exchange.sendResponseHeaders(405, -1);
        }
    }

    /**
     * クエリパラメータを抽出
     * 
     * @param query クエリ文字列
     * @param param パラメータ名
     * @return パラメータ値
     */
    private String extractQueryParam(String query, String param) {
        for (String p : query.split("&")) {
            if (p.startsWith(param + "=")) {
                return p.substring(param.length() + 1);
            }
        }
        return "";
    }
}

/**
 * コミット管理ハンドラー
 */
class CommitHandler extends BaseApiHandler {
    private final CommitManager commitManager;

    public CommitHandler() {
        super();
        this.commitManager = new CommitManager();
    }

    @Override
    protected void handleRequest(HttpExchange exchange, String method) throws IOException {
        switch (method) {
            case "GET" -> {
                String query = exchange.getRequestURI().getQuery();
                Integer repositoryId = null;
                if (query != null && query.contains("repository_id=")) {
                    String repoIdStr = extractQueryParam(query, "repository_id");
                    if (!repoIdStr.isEmpty()) {
                        repositoryId = Integer.parseInt(repoIdStr);
                    }
                }
                List<Map<String, Object>> commits = commitManager.getCommits(repositoryId);
                sendJsonResponse(exchange, listToJson(commits, "commits"));
            }
            case "POST" -> {
                String requestBody = readRequestBody(exchange);
                String branchIdStr = extractJsonField(requestBody, "branch_id");
                String message = extractJsonField(requestBody, "message");
                String authorIdStr = extractJsonField(requestBody, "author_id");
                String content = extractJsonField(requestBody, "content");

                CommitManager.CreateCommitCommand command = commitManager.new CreateCommitCommand(
                        Integer.parseInt(branchIdStr), message, Integer.parseInt(authorIdStr), content);
                boolean success = commitManager.executeCommitCommand(command);
                sendJsonResponse(exchange, String.format("{\"success\":%s}", success));
            }
            default -> exchange.sendResponseHeaders(405, -1);
        }
    }

    /**
     * クエリパラメータを抽出
     * 
     * @param query クエリ文字列
     * @param param パラメータ名
     * @return パラメータ値
     */
    private String extractQueryParam(String query, String param) {
        for (String p : query.split("&")) {
            if (p.startsWith(param + "=")) {
                return p.substring(param.length() + 1);
            }
        }
        return "";
    }
}

/**
 * ファイル管理ハンドラー
 */
class FileHandler extends BaseApiHandler {
    private final CommitManager commitManager;

    public FileHandler() {
        super();
        this.commitManager = new CommitManager();
    }

    @Override
    protected void handleRequest(HttpExchange exchange, String method) throws IOException {
        if ("GET".equals(method)) {
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.contains("branch_id=")) {
                String branchIdStr = extractQueryParam(query, "branch_id");
                if (!branchIdStr.isEmpty()) {
                    int branchId = Integer.parseInt(branchIdStr);
                    List<Map<String, Object>> files = commitManager.getFilesByBranch(branchId);
                    sendJsonResponse(exchange, listToJson(files, "files"));
                    return;
                }
            }
            sendJsonResponse(exchange, "{\"files\":[]}");
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    /**
     * クエリパラメータを抽出
     * 
     * @param query クエリ文字列
     * @param param パラメータ名
     * @return パラメータ値
     */
    private String extractQueryParam(String query, String param) {
        for (String p : query.split("&")) {
            if (p.startsWith(param + "=")) {
                return p.substring(param.length() + 1);
            }
        }
        return "";
    }
}

/**
 * マージハンドラー
 */
class MergeHandler extends BaseApiHandler {
    private final MergeManager mergeManager;

    public MergeHandler() {
        super();
        this.mergeManager = new MergeManager();
    }

    @Override
    protected void handleRequest(HttpExchange exchange, String method) throws IOException {
        if ("POST".equals(method)) {
            String requestBody = readRequestBody(exchange);
            String branchId1Str = extractJsonField(requestBody, "branch_id_1");
            String branchId2Str = extractJsonField(requestBody, "branch_id_2");

            int branchId1 = Integer.parseInt(branchId1Str);
            int branchId2 = Integer.parseInt(branchId2Str);

            MergeResult result = mergeManager.performStrictMerge(branchId1, branchId2);

            switch (result) {
                case MergeResult.Success(String message) -> {
                    sendJsonResponse(exchange, String.format("{\"success\":true,\"message\":\"%s\"}", message));
                }
                case MergeResult.Conflict(int bid1, String content1, int bid2, String content2) -> {
                    String json = String.format(
                            "{\"success\":false,\"branch_id_1\":%d,\"text_1\":\"%s\",\"branch_id_2\":%d,\"text_2\":\"%s\"}",
                            bid1, escapeJson(content1), bid2, escapeJson(content2));
                    sendJsonResponse(exchange, json);
                }
            }
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    /**
     * JSON用文字列エスケープ
     * 
     * @param str エスケープする文字列
     * @return エスケープされた文字列
     */
    private String escapeJson(String str) {
        if (str == null)
            return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

/**
 * 強制マージハンドラー
 */
class ForceMergeHandler extends BaseApiHandler {
    private final MergeManager mergeManager;

    public ForceMergeHandler() {
        super();
        this.mergeManager = new MergeManager();
    }

    @Override
    protected void handleRequest(HttpExchange exchange, String method) throws IOException {
        if ("POST".equals(method)) {
            String requestBody = readRequestBody(exchange);
            String branchId1Str = extractJsonField(requestBody, "branch_id_1");
            String branchId2Str = extractJsonField(requestBody, "branch_id_2");
            String text = extractJsonField(requestBody, "text");

            int branchId1 = Integer.parseInt(branchId1Str);
            int branchId2 = Integer.parseInt(branchId2Str);

            MergeResult result = mergeManager.performForceMerge(branchId1, branchId2, text);

            switch (result) {
                case MergeResult.Success(String message) -> {
                    sendJsonResponse(exchange, String.format("{\"success\":true,\"message\":\"%s\"}", message));
                }
                case MergeResult.Conflict(int bid1, String content1, int bid2, String content2) -> {
                    sendJsonResponse(exchange, "{\"success\":false,\"message\":\"強制マージに失敗しました\"}");
                }
            }
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }
}

/**
 * グラフハンドラー
 */
class GraphHandler extends BaseApiHandler {
    @Override
    protected void handleRequest(HttpExchange exchange, String method) throws IOException {
        if ("GET".equals(method)) {
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.contains("repository_id=")) {
                String repoIdStr = extractQueryParam(query, "repository_id");
                if (!repoIdStr.isEmpty()) {
                    int repositoryId = Integer.parseInt(repoIdStr);
                    String graphJson = generateGraphJson(repositoryId);
                    sendJsonResponse(exchange, graphJson);
                    return;
                }
            }
            sendJsonResponse(exchange, "{\"nodes\":[],\"edges\":[]}");
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    /**
     * グラフJSONを生成
     * 
     * @param repositoryId リポジトリID
     * @return グラフJSON
     */
    private String generateGraphJson(int repositoryId) {
        List<String> nodes = new ArrayList<>();
        List<String> edges = new ArrayList<>();

        try (Connection conn = dbManager.getConnection()) {
            // コミットノード作成
            String commitSql = "SELECT id, message, parent_commit_id, parent_commit_id_2 FROM git_commit WHERE repository_id = ? ORDER BY id";
            try (PreparedStatement stmt = conn.prepareStatement(commitSql)) {
                stmt.setInt(1, repositoryId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int commitId = rs.getInt("id");
                        String message = rs.getString("message");
                        int parentId = rs.getInt("parent_commit_id");
                        int parentId2 = rs.getInt("parent_commit_id_2");

                        // コミットノード追加
                        nodes.add(String.format(
                                "{\"id\":%d,\"label\":\"%s\",\"shape\":\"box\"}",
                                commitId, escapeJson(message)));

                        // 親コミットへのエッジ
                        if (parentId != 0) {
                            edges.add(String.format("{\"from\":%d,\"to\":%d}", parentId, commitId));
                        }

                        // 2番目の親コミットへのエッジ（マージコミット）
                        if (parentId2 != 0) {
                            edges.add(String.format(
                                    "{\"from\":%d,\"to\":%d,\"dashes\":true,\"color\":\"#28a745\"}",
                                    parentId2, commitId));
                        }
                    }
                }
            }

            // ブランチノード作成
            String branchSql = "SELECT id, name, head_commit_id FROM branch WHERE repository_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(branchSql)) {
                stmt.setInt(1, repositoryId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int branchId = rs.getInt("id");
                        String branchName = rs.getString("name");
                        int headCommitId = rs.getInt("head_commit_id");

                        if (headCommitId != 0) {
                            // ブランチノード追加
                            nodes.add(String.format(
                                    "{\"id\":\"branch-%d\",\"label\":\"%s\",\"shape\":\"ellipse\",\"color\":\"#d73a49\"}",
                                    branchId, escapeJson(branchName)));

                            // ブランチからコミットへのエッジ
                            edges.add(String.format(
                                    "{\"from\":\"branch-%d\",\"to\":%d,\"color\":\"#d73a49\",\"label\":\"%s\"}",
                                    branchId, headCommitId, escapeJson(branchName)));
                        }
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println("Graph generation error: " + e.getMessage());
            return "{\"nodes\":[],\"edges\":[],\"error\":\"" + e.getMessage() + "\"}";
        }

        return String.format("{\"nodes\":[%s],\"edges\":[%s]}",
                String.join(",", nodes), String.join(",", edges));
    }

    /**
     * クエリパラメータを抽出
     * 
     * @param query クエリ文字列
     * @param param パラメータ名
     * @return パラメータ値
     */
    private String extractQueryParam(String query, String param) {
        for (String p : query.split("&")) {
            if (p.startsWith(param + "=")) {
                return p.substring(param.length() + 1);
            }
        }
        return "";
    }

    /**
     * JSON用文字列エスケープ
     * 
     * @param str エスケープする文字列
     * @return エスケープされた文字列
     */
    private String escapeJson(String str) {
        if (str == null)
            return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}