// ===============================
// ApiServer.java
// SQLiteを用いた簡易Git風APIサーバ
// REST APIでユーザー・リポジトリ・ブランチ・コミット・ファイル管理、マージ・グラフ表示も対応
// ===============================

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * メインAPIサーバクラス
 * - SQLiteを利用した簡易バージョン管理API
 * - ユーザー/リポジトリ/ブランチ/コミット/ファイル/マージ/グラフAPIを提供
 */
public class ApiServer {
    // --- DB接続設定 ---
    private static final String DB_URL = "jdbc:sqlite:database/database.db";
    private static final String CONTENT_TYPE_JSON = "application/json; charset=UTF-8";

    /**
     * サーバ起動・各APIエンドポイント登録
     */
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        // 各APIエンドポイントを登録
        server.createContext("/api/user", new UserHandler());           // ユーザー管理
        server.createContext("/api/repository", new RepositoryHandler()); // リポジトリ管理
        server.createContext("/api/branch", new BranchHandler());         // ブランチ管理
        server.createContext("/api/commit", new CommitHandler());         // コミット管理
        server.createContext("/api/file", new FileHandler());             // ファイル取得
        server.createContext("/api/merge", new MergeHandler());           // 厳密マージ
        server.createContext("/api/force-merge", new ForceMergeHandler());// 強制マージ
        server.createContext("/api/graph", new GraphHandler());           // グラフ表示
        server.setExecutor(null);
        server.start();
        System.out.println("Server is running on http://localhost:8080/api");
    }

    // --- 共通ユーティリティ ---
    /**
     * リクエストボディを文字列で取得
     */
    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }
    /**
     * レスポンス送信（JSON/UTF-8固定）
     */
    private static void sendResponse(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE_JSON);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }
    /**
     * CORSヘッダ付与
     */
    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }
    /**
     * DBコネクション取得
     */
    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }
    /**
     * JSON文字列から指定フィールド値を抽出（簡易実装）
     */
    private static String parseFieldFromJson(String json, String field) {
        if (json.contains("\"" + field + "\":")) {
            int startIndex = json.indexOf("\"" + field + "\":") + field.length() + 3;
            int endIndex = json.indexOf('"', startIndex + 1);
            return json.substring(startIndex + 1, endIndex);
        }
        return "";
    }
    // --- JSONユーティリティ ---
    /**
     * List<Map>をJSON配列形式に変換
     */
    private static String toJsonArray(List<? extends Map<String, ?>> list, String arrayName) {
        StringBuilder json = new StringBuilder();
        json.append("{\"").append(arrayName).append("\": [");
        for (int i = 0; i < list.size(); i++) {
            json.append(toJsonObject(list.get(i)));
            if (i < list.size() - 1) json.append(",");
        }
        json.append("]}");
        return json.toString();
    }
    /**
     * MapをJSONオブジェクト形式に変換
     */
    private static String toJsonObject(Map<String, ?> map) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        int j = 0;
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            json.append(String.format("\"%s\":%s", entry.getKey(), toJsonValue(entry.getValue())));
            if (j < map.size() - 1) json.append(",");
            j++;
        }
        json.append("}");
        return json.toString();
    }
    /**
     * 値をJSON値としてエスケープ
     */
    private static String toJsonValue(Object v) {
        if (v == null) return "null";
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        return String.format("\"%s\"", v.toString().replace("\"", "\\\""));
    }

    // ===============================
    // 各APIハンドラ（User/Repository/Branch/Commit/File/Merge/ForceMerge/Graph）
    // ===============================

    // --- ユーザーAPI ---
    /**
     * /api/user
     * - POST: ユーザー追加
     * - GET: ユーザー一覧取得
     * - DELETE: ユーザー削除
     */
    static class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod();
            if ("OPTIONS".equals(method)) { exchange.sendResponseHeaders(204, -1); return; }
            if ("POST".equals(method)) {
                String requestBody = readRequestBody(exchange);
                String username = parseFieldFromJson(requestBody, "username");
                boolean success = addUser(username);
                sendResponse(exchange, 200, String.format("{\"success\":%s}", success));
            } else if ("GET".equals(method)) {
                List<Map<String, Object>> users = fetchAllUsers();
                sendResponse(exchange, 200, toJsonArray(users, "users"));
            } else if ("DELETE".equals(method)) {
                String requestBody = readRequestBody(exchange);
                String idStr = parseFieldFromJson(requestBody, "id");
                boolean success = deleteUserById(idStr);
                sendResponse(exchange, 200, String.format("{\"success\":%s}", success));
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // --- リポジトリAPI ---
    /**
     * /api/repository
     * - POST: リポジトリ追加
     * - GET: リポジトリ一覧取得（owner_id指定可）
     * - DELETE: リポジトリ削除
     */
    static class RepositoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod();
            if ("OPTIONS".equals(method)) { exchange.sendResponseHeaders(204, -1); return; }
            if ("POST".equals(method)) {
                String requestBody = readRequestBody(exchange);
                String name = parseFieldFromJson(requestBody, "name");
                String ownerId = parseFieldFromJson(requestBody, "owner_id");
                boolean success = addRepository(name, ownerId);
                sendResponse(exchange, 200, String.format("{\"success\":%s}", success));
            } else if ("GET".equals(method)) {
                String query = exchange.getRequestURI().getQuery();
                String ownerId = null;
                if (query != null && query.contains("owner_id=")) {
                    for (String param : query.split("&")) {
                        if (param.startsWith("owner_id=")) ownerId = param.substring("owner_id=".length());
                    }
                }
                List<Map<String, Object>> repos = fetchAllRepositories(ownerId);
                sendResponse(exchange, 200, toJsonArray(repos, "repositories"));
            } else if ("DELETE".equals(method)) {
                String requestBody = readRequestBody(exchange);
                String idStr = parseFieldFromJson(requestBody, "id");
                boolean success = deleteRepositoryById(idStr);
                sendResponse(exchange, 200, String.format("{\"success\":%s}", success));
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // --- ブランチAPI ---
    /**
     * /api/branch
     * - POST: ブランチ追加
     * - GET: ブランチ一覧取得（repository_id指定可）
     * - DELETE: ブランチ削除
     * - PUT: ブランチのHEADコミット更新
     */
    static class BranchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod();
            if ("OPTIONS".equals(method)) { exchange.sendResponseHeaders(204, -1); return; }
            if ("POST".equals(method)) {
                String requestBody = readRequestBody(exchange);
                String name = parseFieldFromJson(requestBody, "name");
                String repositoryId = parseFieldFromJson(requestBody, "repository_id");
                boolean success = addBranch(name, repositoryId);
                sendResponse(exchange, 200, String.format("{\"success\":%s}", success));
            } else if ("GET".equals(method)) {
                String query = exchange.getRequestURI().getQuery();
                String repositoryId = null;
                if (query != null && query.contains("repository_id=")) {
                    for (String param : query.split("&")) {
                        if (param.startsWith("repository_id=")) repositoryId = param.substring("repository_id=".length());
                    }
                }
                List<Map<String, Object>> branches = fetchAllBranches(repositoryId);
                sendResponse(exchange, 200, toJsonArray(branches, "branches"));
            } else if ("DELETE".equals(method)) {
                String requestBody = readRequestBody(exchange);
                String idStr = parseFieldFromJson(requestBody, "id");
                boolean success = deleteBranchById(idStr);
                sendResponse(exchange, 200, String.format("{\"success\":%s}", success));
            } else if ("PUT".equals(method)) {
                String requestBody = readRequestBody(exchange);
                String branchId = parseFieldFromJson(requestBody, "id");
                String commitId = parseFieldFromJson(requestBody, "commit_id");
                boolean success = updateBranchHead(branchId, commitId);
                sendResponse(exchange, 200, String.format("{\"success\":%s}", success));
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // --- コミットAPI ---
    /**
     * /api/commit
     * - POST: コミット作成（スナップショット）
     * - GET: コミット一覧取得（repository_id指定可）
     * - DELETE: コミット削除
     */
    static class CommitHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod();
            if ("OPTIONS".equals(method)) { exchange.sendResponseHeaders(204, -1); return; }
            if ("POST".equals(method)) {
                String requestBody = readRequestBody(exchange);
                String branchId = parseFieldFromJson(requestBody, "branch_id");
                String message = parseFieldFromJson(requestBody, "message");
                String authorId = parseFieldFromJson(requestBody, "author_id");
                String content = parseFieldFromJson(requestBody, "content");
                boolean success = createCommitSnapshot(branchId, message, authorId, content);
                sendResponse(exchange, 200, String.format("{\"success\":%s}", success));
            } else if ("GET".equals(method)) {
                String query = exchange.getRequestURI().getQuery();
                String repositoryId = null;
                if (query != null && query.contains("repository_id=")) {
                    for (String param : query.split("&")) {
                        if (param.startsWith("repository_id=")) repositoryId = param.substring("repository_id=".length());
                    }
                }
                List<Map<String, Object>> commits = fetchAllCommits(repositoryId);
                sendResponse(exchange, 200, toJsonArray(commits, "commits"));
            } else if ("DELETE".equals(method)) {
                String requestBody = readRequestBody(exchange);
                String idStr = parseFieldFromJson(requestBody, "id");
                boolean success = deleteCommitById(idStr);
                sendResponse(exchange, 200, String.format("{\"success\":%s}", success));
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // --- ファイル表示API ---
    /**
     * /api/file
     * - GET: 指定ブランチの最新ファイル内容取得
     */
    static class FileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod();
            if ("OPTIONS".equals(method)) { exchange.sendResponseHeaders(204, -1); return; }
            if ("GET".equals(method)) {
                String query = exchange.getRequestURI().getQuery();
                String branchId = null;
                if (query != null && query.contains("branch_id=")) {
                    for (String param : query.split("&")) {
                        if (param.startsWith("branch_id=")) branchId = param.substring("branch_id=".length());
                    }
                }
                List<Map<String, Object>> files = fetchFilesByBranch(branchId);
                sendResponse(exchange, 200, toJsonArray(files, "files"));
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // --- マージAPI（厳密: 内容一致時のみマージ） ---
    /**
     * /api/merge
     * - POST: 2ブランチの内容が一致する場合のみマージコミット作成
     *   不一致時は両方の内容を返す
     */
    static class MergeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod();
            if ("OPTIONS".equals(method)) { exchange.sendResponseHeaders(204, -1); return; }
            if ("POST".equals(method)) {
                String requestBody = readRequestBody(exchange);
                String branchId1 = parseFieldFromJson(requestBody, "branch_id_1");
                String branchId2 = parseFieldFromJson(requestBody, "branch_id_2");
                Map<String, Object> result = mergeBranchesStrict(branchId1, branchId2);
                sendResponse(exchange, 200, toJsonObject(result));
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // --- 強制マージAPI ---
    /**
     * /api/force-merge
     * - POST: 指定テキストで強制的にマージコミット作成
     *   両ブランチのHEADを新コミットに更新
     */
    static class ForceMergeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod();
            if ("OPTIONS".equals(method)) { exchange.sendResponseHeaders(204, -1); return; }
            if ("POST".equals(method)) {
                String requestBody = readRequestBody(exchange);
                String branchId1 = parseFieldFromJson(requestBody, "branch_id_1");
                String branchId2 = parseFieldFromJson(requestBody, "branch_id_2");
                String text = parseFieldFromJson(requestBody, "text");
                boolean success = ApiServer.forceMerge(branchId1, branchId2, text);
                sendResponse(exchange, 200, String.format("{\"success\":%s}", success));
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // --- グラフAPI ---
    /**
     * /api/graph
     * - GET: リポジトリのコミット・ブランチ構造をグラフ形式（JSON）で返す
     */
    static class GraphHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod();
            if ("OPTIONS".equals(method)) { exchange.sendResponseHeaders(204, -1); return; }
            if ("GET".equals(method)) {
                String query = exchange.getRequestURI().getQuery();
                String repositoryId = null;
                if (query != null && query.contains("repository_id=")) {
                    for (String param : query.split("&")) {
                        if (param.startsWith("repository_id=")) repositoryId = param.substring("repository_id=".length());
                    }
                }
                String jsonResponse = getGraphJson(repositoryId);
                sendResponse(exchange, 200, jsonResponse);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // ===============================
    // DB操作・マージ・グラフ・強制マージの実装
    // ===============================

    // --- ユーザー追加 ---
    private static boolean addUser(String username) {
        String sql = "INSERT INTO name(username) VALUES(?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
    // --- ユーザー一覧取得 ---
    private static List<Map<String, Object>> fetchAllUsers() {
        List<Map<String, Object>> users = new ArrayList<>();
        String sql = "SELECT id, username FROM name";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> u = new LinkedHashMap<>();
                u.put("id", rs.getInt("id"));
                u.put("username", rs.getString("username"));
                users.add(u);
            }
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }
        return users;
    }
    // --- ユーザー削除 ---
    private static boolean deleteUserById(String idStr) {
        String sql = "DELETE FROM name WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(idStr));
            int affected = pstmt.executeUpdate();
            return affected > 0;
        } catch (SQLException | NumberFormatException e) {
            return false;
        }
    }
    // --- リポジトリ追加 ---
    private static boolean addRepository(String name, String ownerIdStr) {
        String sql = "INSERT INTO repository(name, owner_id) VALUES(?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setInt(2, Integer.parseInt(ownerIdStr));
            pstmt.executeUpdate();
            return true;
        } catch (SQLException | NumberFormatException e) {
            return false;
        }
    }
    // --- リポジトリ一覧取得 ---
    private static List<Map<String, Object>> fetchAllRepositories(String ownerId) {
        List<Map<String, Object>> repos = new ArrayList<>();
        String sql = (ownerId != null) ? "SELECT id, name, owner_id FROM repository WHERE owner_id = ?" : "SELECT id, name, owner_id FROM repository";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (ownerId != null) pstmt.setInt(1, Integer.parseInt(ownerId));
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("id", rs.getInt("id"));
                    r.put("name", rs.getString("name"));
                    r.put("owner_id", rs.getInt("owner_id"));
                    repos.add(r);
                }
            }
        } catch (SQLException | NumberFormatException e) {
            System.err.println("Database error: " + e.getMessage());
        }
        return repos;
    }
    // --- リポジトリ削除 ---
    private static boolean deleteRepositoryById(String idStr) {
        String sql = "DELETE FROM repository WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(idStr));
            int affected = pstmt.executeUpdate();
            return affected > 0;
        } catch (SQLException | NumberFormatException e) {
            return false;
        }
    }
    // --- ブランチ追加 ---
    private static boolean addBranch(String name, String repositoryIdStr) {
        String sql = "INSERT INTO branch(name, repository_id) VALUES(?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setInt(2, Integer.parseInt(repositoryIdStr));
            pstmt.executeUpdate();
            return true;
        } catch (SQLException | NumberFormatException e) { return false; }
    }
    // --- ブランチ一覧取得 ---
    private static List<Map<String, Object>> fetchAllBranches(String repositoryId) {
        List<Map<String, Object>> branches = new ArrayList<>();
        String sql = (repositoryId != null) ? "SELECT id, name, repository_id, head_commit_id FROM branch WHERE repository_id = ?" : "SELECT id, name, repository_id, head_commit_id FROM branch";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (repositoryId != null) pstmt.setInt(1, Integer.parseInt(repositoryId));
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> b = new LinkedHashMap<>();
                    b.put("id", rs.getInt("id"));
                    b.put("name", rs.getString("name"));
                    b.put("repository_id", rs.getInt("repository_id"));
                    b.put("head_commit_id", rs.getInt("head_commit_id"));
                    branches.add(b);
                }
            }
        } catch (SQLException | NumberFormatException e) { System.err.println("Database error: " + e.getMessage()); }
        return branches;
    }
    // --- ブランチ削除 ---
    private static boolean deleteBranchById(String idStr) {
        String sql = "DELETE FROM branch WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(idStr));
            int affected = pstmt.executeUpdate();
            return affected > 0;
        } catch (SQLException | NumberFormatException e) { return false; }
    }
    // --- ブランチのHEADコミット更新 ---
    private static boolean updateBranchHead(String branchIdStr, String commitIdStr) {
        String sql = "UPDATE branch SET head_commit_id = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(commitIdStr));
            pstmt.setInt(2, Integer.parseInt(branchIdStr));
            int affected = pstmt.executeUpdate();
            return affected > 0;
        } catch (SQLException | NumberFormatException e) { return false; }
    }
    // --- コミット作成（スナップショット） ---
    private static boolean createCommitSnapshot(String branchIdStr, String message, String authorIdStr, String content) {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            Integer parentCommitId = null;
            Integer repositoryId = null;
            // 対象ブランチのHEADコミット・リポジトリID取得
            String parentSql = "SELECT head_commit_id, repository_id FROM branch WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(parentSql)) {
                pstmt.setInt(1, Integer.parseInt(branchIdStr));
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        parentCommitId = rs.getInt("head_commit_id");
                        if (rs.wasNull()) parentCommitId = null;
                        repositoryId = rs.getInt("repository_id");
                    }
                }
            }
            if (repositoryId == null) { conn.rollback(); return false; }
            // 新規コミット作成
            String commitSql = "INSERT INTO git_commit(repository_id, author_id, message, parent_commit_id, parent_commit_id_2, created_at) VALUES(?, ?, ?, ?, NULL, datetime('now'))";
            int newCommitId = -1;
            try (PreparedStatement pstmt = conn.prepareStatement(commitSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                pstmt.setInt(1, repositoryId);
                pstmt.setInt(2, Integer.parseInt(authorIdStr));
                pstmt.setString(3, message);
                if (parentCommitId != null) pstmt.setInt(4, parentCommitId); else pstmt.setNull(4, java.sql.Types.INTEGER);
                pstmt.executeUpdate();
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) newCommitId = rs.getInt(1);
                }
            }
            if (newCommitId == -1) { conn.rollback(); return false; }
            // ファイル保存
            String fileSql = "INSERT INTO file(commit_id, filename, content) VALUES(?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(fileSql)) {
                pstmt.setInt(1, newCommitId);
                pstmt.setString(2, "main.txt");
                pstmt.setString(3, content);
                pstmt.executeUpdate();
            }
            // ブランチのHEAD更新
            String updateSql = "UPDATE branch SET head_commit_id = ? WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                pstmt.setInt(1, newCommitId);
                pstmt.setInt(2, Integer.parseInt(branchIdStr));
                pstmt.executeUpdate();
            }
            conn.commit();
            return true;
        } catch (SQLException | NumberFormatException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ignore) {}
            return false;
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignore) {}
        }
    }
    // --- コミット一覧取得 ---
    private static List<Map<String, Object>> fetchAllCommits(String repositoryId) {
        List<Map<String, Object>> commits = new ArrayList<>();
        String sql = (repositoryId != null) ? "SELECT id, repository_id, author_id, message, parent_commit_id, parent_commit_id_2, created_at FROM git_commit WHERE repository_id = ?" : "SELECT id, repository_id, author_id, message, parent_commit_id, parent_commit_id_2, created_at FROM git_commit";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (repositoryId != null) pstmt.setInt(1, Integer.parseInt(repositoryId));
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("id", rs.getInt("id"));
                    c.put("repository_id", rs.getInt("repository_id"));
                    c.put("author_id", rs.getInt("author_id"));
                    c.put("message", rs.getString("message"));
                    c.put("parent_commit_id", rs.getInt("parent_commit_id"));
                    c.put("parent_commit_id_2", rs.getInt("parent_commit_id_2"));
                    c.put("created_at", rs.getString("created_at"));
                    commits.add(c);
                }
            }
        } catch (SQLException | NumberFormatException e) { System.err.println("Database error: " + e.getMessage()); }
        return commits;
    }
    // --- コミット削除 ---
    private static boolean deleteCommitById(String idStr) {
        String sql = "DELETE FROM git_commit WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(idStr));
            int affected = pstmt.executeUpdate();
            return affected > 0;
        } catch (SQLException | NumberFormatException e) { return false; }
    }
    // --- 指定ブランチの最新ファイル取得 ---
    private static List<Map<String, Object>> fetchFilesByBranch(String branchId) {
        List<Map<String, Object>> files = new ArrayList<>();
        if (branchId == null) return files;
        Integer headCommitId = null;
        String sql = "SELECT head_commit_id FROM branch WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(branchId));
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    headCommitId = rs.getInt("head_commit_id");
                    if (rs.wasNull()) headCommitId = null;
                }
            }
            if (headCommitId != null) {
                String fileSql = "SELECT id, commit_id, content FROM file WHERE commit_id = ?";
                try (PreparedStatement fp = conn.prepareStatement(fileSql)) {
                    fp.setInt(1, headCommitId);
                    try (ResultSet frs = fp.executeQuery()) {
                        while (frs.next()) {
                            Map<String, Object> f = new LinkedHashMap<>();
                            f.put("commit_id", frs.getInt("commit_id"));
                            f.put("file_id", frs.getInt("id"));
                            f.put("text", frs.getString("content"));
                            files.add(f);
                        }
                    }
                }
            }
        } catch (SQLException | NumberFormatException e) { /* ignore */ }
        return files;
    }
    // --- グラフAPI本体 ---
    /**
     * 指定リポジトリのコミット・ブランチ構造をグラフ形式（JSON）で返す
     * nodes: コミット・ブランチノード, edges: コミット間・ブランチ→コミットの矢印
     */
    private static String getGraphJson(String repositoryId) {
        String url = "jdbc:sqlite:database/database.db";
        List<String> nodes = new ArrayList<>();
        List<String> edges = new ArrayList<>();
        List<String> branchPointers = new ArrayList<>(); // ブランチ→コミットの矢印
        try (Connection conn = DriverManager.getConnection(url)) {
            // 全コミット取得（リポジトリ単位）
            String commitSql = "SELECT id, message, parent_commit_id, parent_commit_id_2 FROM git_commit WHERE repository_id = ?";
            try (PreparedStatement cp = conn.prepareStatement(commitSql)) {
                cp.setInt(1, Integer.parseInt(repositoryId));
                try (ResultSet crs = cp.executeQuery()) {
                    while (crs.next()) {
                        int commitId = crs.getInt("id");
                        String msg = crs.getString("message");
                        int parent = crs.getInt("parent_commit_id");
                        int parent2 = crs.getInt("parent_commit_id_2");
                        nodes.add(String.format("{\"id\":%d,\"label\":\"%s\"}", commitId, msg));
                        if (parent != 0) {
                            edges.add(String.format("{\"from\":%d,\"to\":%d}", parent, commitId));
                        }
                        // 2親目があればエッジ追加（マージコミット）
                        if (parent2 != 0) {
                            edges.add(String.format("{\"from\":%d,\"to\":%d,\"dashes\":true,\"color\":\"#0af\"}", parent2, commitId));
                        }
                    }
                }
            }
            // 全ブランチ取得
            String branchSql = "SELECT id, name, head_commit_id FROM branch WHERE repository_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(branchSql)) {
                pstmt.setInt(1, Integer.parseInt(repositoryId));
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        int branchId = rs.getInt("id");
                        String branchName = rs.getString("name");
                        int headCommitId = rs.getInt("head_commit_id");
                        // ブランチ→コミットの矢印（head_commit_idが0でなければ）
                        if (headCommitId != 0) {
                            branchPointers.add(String.format("{\"from\":\"branch-%d\",\"to\":%d,\"label\":\"%s\",\"color\":\"#f00\"}", branchId, headCommitId, branchName));
                            // ブランチノードも追加
                            nodes.add(String.format("{\"id\":\"branch-%d\",\"label\":\"%s\",\"shape\":\"ellipse\",\"color\":\"#f00\"}", branchId, branchName));
                        }
                    }
                }
            }
        } catch (SQLException | NumberFormatException e) {
            return "{\"nodes\":[],\"edges\":[],\"error\":\"" + e.getMessage() + "\"}";
        }
        // edges + branchPointers
        List<String> allEdges = new ArrayList<>();
        allEdges.addAll(edges);
        allEdges.addAll(branchPointers);
        return String.format("{\"nodes\":[%s],\"edges\":[%s]}", String.join(",", nodes), String.join(",", allEdges));
    }

    // ===============================
    // DB初期化・マージ・強制マージの実装
    // ===============================

    static {
        // JDBCドライバをロード
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("Failed to load SQLite JDBC driver: " + e.getMessage());
        }
        // データベース初期化（サーバ起動時に必ず実行）
        initializeDatabase();
    }

    /**
     * DB初期化: 必要なテーブルを全て作成
     */
    private static void initializeDatabase() {
        String[] sqls = new String[] {
            "CREATE TABLE IF NOT EXISTS name (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE)",
            "CREATE TABLE IF NOT EXISTS repository (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, owner_id INTEGER)",
            "CREATE TABLE IF NOT EXISTS branch (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, repository_id INTEGER, head_commit_id INTEGER)",
            "CREATE TABLE IF NOT EXISTS git_commit (id INTEGER PRIMARY KEY AUTOINCREMENT, repository_id INTEGER, author_id INTEGER, message TEXT, parent_commit_id INTEGER, parent_commit_id_2 INTEGER, created_at DATETIME)",
            "CREATE TABLE IF NOT EXISTS \"file\" (id INTEGER PRIMARY KEY AUTOINCREMENT, commit_id INTEGER, filename TEXT, content TEXT)"
        };
        try (Connection conn = getConnection()) {
            for (String sql : sqls) {
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.err.println("Database initialization error: " + e.getMessage());
        }
    }

    // --- 厳密マージ: 2ブランチのファイル内容が完全一致ならマージ、違えば両方の内容返す ---
    private static Map<String, Object> mergeBranchesStrict(String branchId1, String branchId2) {
        Map<String, Object> result = new HashMap<>();
        String url = "jdbc:sqlite:database/database.db";
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url);
            conn.setAutoCommit(false);
            // 各ブランチのhead_commit_id取得
            Integer head1 = null, head2 = null;
            String sql = "SELECT head_commit_id FROM branch WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, Integer.parseInt(branchId1));
                try (ResultSet rs = pstmt.executeQuery()) { if (rs.next()) head1 = rs.getInt("head_commit_id"); }
                pstmt.setInt(1, Integer.parseInt(branchId2));
                try (ResultSet rs = pstmt.executeQuery()) { if (rs.next()) head2 = rs.getInt("head_commit_id"); }
            }
            if (head1 == null || head2 == null || head1 == 0 || head2 == 0) {
                result.put("success", false);
                result.put("message", "No HEAD commit");
                return result;
            }
            // 各HEADコミットのファイル内容取得
            String getContentSql = "SELECT id, content FROM file WHERE commit_id = ? ORDER BY id LIMIT 1";
            int fileId1 = -1, fileId2 = -1;
            String content1 = "", content2 = "";
            try (PreparedStatement pstmt = conn.prepareStatement(getContentSql)) {
                pstmt.setInt(1, head1);
                try (ResultSet rs = pstmt.executeQuery()) { if (rs.next()) { fileId1 = rs.getInt("id"); content1 = rs.getString("content"); } }
                pstmt.setInt(1, head2);
                try (ResultSet rs = pstmt.executeQuery()) { if (rs.next()) { fileId2 = rs.getInt("id"); content2 = rs.getString("content"); } }
            }
            if (content1 == null) content1 = "";
            if (content2 == null) content2 = "";
            if (content1.equals(content2)) {
                // 新しいマージコミット作成（親2つ）
                // repository_id取得
                int repositoryId = -1;
                String repoSql = "SELECT repository_id FROM branch WHERE id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(repoSql)) {
                    pstmt.setInt(1, Integer.parseInt(branchId1));
                    try (ResultSet rs = pstmt.executeQuery()) { if (rs.next()) repositoryId = rs.getInt("repository_id"); }
                }
                if (repositoryId == -1) { result.put("success", false); result.put("message", "No repository"); return result; }
                // コミット作成
                String commitSql = "INSERT INTO git_commit(repository_id, author_id, message, parent_commit_id, parent_commit_id_2, created_at) VALUES(?, 1, ?, ?, ?, ?, datetime('now'))";
                int newCommitId = -1;
                try (PreparedStatement pstmt = conn.prepareStatement(commitSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                    pstmt.setInt(1, repositoryId);
                    pstmt.setString(2, "Merge");
                    pstmt.setInt(3, head1);
                    pstmt.setInt(4, head2);
                    pstmt.executeUpdate();
                    try (ResultSet rs = pstmt.getGeneratedKeys()) { if (rs.next()) newCommitId = rs.getInt(1); }
                }
                if (newCommitId == -1) { conn.rollback(); result.put("success", false); result.put("message", "Commit failed"); return result; }
                // ファイル作成
                String fileSql = "INSERT INTO file(commit_id, filename, content) VALUES(?, 'main.txt', ?)";
                int newFileId = -1;
                try (PreparedStatement pstmt = conn.prepareStatement(fileSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                    pstmt.setInt(1, newCommitId);
                    pstmt.setString(2, content1);
                    pstmt.executeUpdate();
                    try (ResultSet rs = pstmt.getGeneratedKeys()) { if (rs.next()) newFileId = rs.getInt(1); }
                }
                // 両ブランチのhead_commit_id更新
                String updateSql = "UPDATE branch SET head_commit_id = ? WHERE id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                    pstmt.setInt(1, newCommitId);
                    pstmt.setInt(2, Integer.parseInt(branchId1));
                    pstmt.executeUpdate();
                    pstmt.setInt(1, newCommitId);
                    pstmt.setInt(2, Integer.parseInt(branchId2));
                    pstmt.executeUpdate();
                }
                conn.commit();
                result.put("success", true);
                result.put("branch_id_1", Integer.parseInt(branchId1));
                result.put("file_id_1", String.valueOf(fileId1));
                result.put("branch_id_2", Integer.parseInt(branchId2));
                result.put("file_id_2", String.valueOf(fileId2));
                return result;
            } else {
                // 不一致: 失敗＋両方の内容返す
                result.put("success", false);
                result.put("branch_id_1", Integer.parseInt(branchId1));
                result.put("file_id_1", String.valueOf(fileId1));
                result.put("text_1", content1);
                result.put("branch_id_2", Integer.parseInt(branchId2));
                result.put("file_id_2", String.valueOf(fileId2));
                result.put("text_2", content2);
                return result;
            }
        } catch (SQLException | NumberFormatException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ignore) {}
            result.put("success", false);
            result.put("message", "Merge error: " + e.getMessage());
            return result;
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignore) {}
        }
    }

    // --- 強制マージ: 指定テキストで新コミット作成、両ブランチhead更新 ---
    /**
     * 2ブランチを強制的にマージし、指定テキストで新コミットを作成
     * 両ブランチのHEADを新コミットに更新
     */
    public static boolean forceMerge(String branchId1, String branchId2, String text) {
        String url = "jdbc:sqlite:database/database.db";
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url);
            conn.setAutoCommit(false);
            // 各ブランチのhead_commit_id, repository_id取得
            Integer head1 = null, head2 = null, repositoryId = null;
            String sql = "SELECT head_commit_id, repository_id FROM branch WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, Integer.parseInt(branchId1));
                try (ResultSet rs = pstmt.executeQuery()) { if (rs.next()) { head1 = rs.getInt("head_commit_id"); repositoryId = rs.getInt("repository_id"); } }
                pstmt.setInt(1, Integer.parseInt(branchId2));
                try (ResultSet rs = pstmt.executeQuery()) { if (rs.next()) head2 = rs.getInt("head_commit_id"); }
            }
            if (head1 == null || head2 == null || repositoryId == null) return false;
            // コミット作成
            String commitSql = "INSERT INTO git_commit(repository_id, author_id, message, parent_commit_id, parent_commit_id_2, created_at) VALUES(?, 1, ?, ?, ?, datetime('now'))";
            int newCommitId = -1;
            try (PreparedStatement pstmt = conn.prepareStatement(commitSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                pstmt.setInt(1, repositoryId);
                pstmt.setString(2, "Force Merge");
                pstmt.setInt(3, head1);
                pstmt.setInt(4, head2);
                pstmt.executeUpdate();
                try (ResultSet rs = pstmt.getGeneratedKeys()) { if (rs.next()) newCommitId = rs.getInt(1); }
            }
            if (newCommitId == -1) { conn.rollback(); return false; }
            // ファイル作成
            String fileSql = "INSERT INTO file(commit_id, filename, content) VALUES(?, 'main.txt', ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(fileSql)) {
                pstmt.setInt(1, newCommitId);
                pstmt.setString(2, text);
                pstmt.executeUpdate();
            }
            // 両ブランチのhead_commit_id更新
            String updateSql = "UPDATE branch SET head_commit_id = ? WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                pstmt.setInt(1, newCommitId);
                pstmt.setInt(2, Integer.parseInt(branchId1));
                pstmt.executeUpdate();
                pstmt.setInt(1, newCommitId);
                pstmt.setInt(2, Integer.parseInt(branchId2));
                pstmt.executeUpdate();
            }
            conn.commit();
            return true;
        } catch (SQLException | NumberFormatException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ignore) {}
            return false;
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignore) {}
        }
    }
}
