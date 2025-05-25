package src;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.sql.*;
import java.util.*;

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
                        ownerId = Integer.valueOf(ownerIdStr);
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
                        repositoryId = Integer.valueOf(repoIdStr);
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
                        repositoryId = Integer.valueOf(repoIdStr);
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

            if (result instanceof MergeResult.Success success) {
                sendJsonResponse(exchange, String.format("{\"success\":true,\"message\":\"%s\"}", success.message()));
            } else if (result instanceof MergeResult.Conflict conflict) {
                String json = String.format(
                        "{\"success\":false,\"branch_id_1\":%d,\"text_1\":\"%s\",\"branch_id_2\":%d,\"text_2\":\"%s\"}",
                        conflict.branchId1(), escapeJson(conflict.content1()), conflict.branchId2(), escapeJson(conflict.content2()));
                sendJsonResponse(exchange, json);
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

            if (result instanceof MergeResult.Success success) {
                sendJsonResponse(exchange, String.format("{\"success\":true,\"message\":\"%s\"}", success.message()));
            } else if (result instanceof MergeResult.Conflict) {
                sendJsonResponse(exchange, "{\"success\":false,\"message\":\"強制マージに失敗しました\"}");
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