// package src;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ApiServer {
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/api/user", new UserHandler());
        server.createContext("/api/repository", new RepositoryHandler());
        server.createContext("/api/branch", new BranchHandler());
        server.createContext("/api/commit", new CommitHandler());
        server.createContext("/api/file", new FileHandler());
        server.createContext("/api/merge", new MergeHandler());
        server.createContext("/api/force-merge", new ForceMergeHandler()); // 追加
        server.createContext("/api/graph", new GraphHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("Server is running on http://localhost:8080/api");
    }

    // --- ユーザーAPI ---
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
                String jsonResponse = success ? "{\"success\":true}" : "{\"success\":false}";
                sendResponse(exchange, 200, jsonResponse);
            } else if ("GET".equals(method)) {
                List<String[]> users = fetchAllUsers();
                StringBuilder json = new StringBuilder("{\"users\":[");
                for (int i = 0; i < users.size(); i++) {
                    String[] u = users.get(i);
                    json.append(String.format("{\"id\":%s,\"username\":\"%s\"}", u[0], u[1]));
                    if (i < users.size() - 1) json.append(",");
                }
                json.append("]}");
                sendResponse(exchange, 200, json.toString());
            } else if ("DELETE".equals(method)) {
                String requestBody = readRequestBody(exchange);
                String idStr = parseFieldFromJson(requestBody, "id");
                boolean success = deleteUserById(idStr);
                String jsonResponse = success ? "{\"success\":true}" : "{\"success\":false}";
                sendResponse(exchange, 200, jsonResponse);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // --- リポジトリAPI ---
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
                String jsonResponse = success ? "{\"success\":true}" : "{\"success\":false}";
                sendResponse(exchange, 200, jsonResponse);
            } else if ("GET".equals(method)) {
                String query = exchange.getRequestURI().getQuery();
                String ownerId = null;
                if (query != null && query.contains("owner_id=")) {
                    for (String param : query.split("&")) {
                        if (param.startsWith("owner_id=")) ownerId = param.substring("owner_id=".length());
                    }
                }
                List<String[]> repos = fetchAllRepositories(ownerId);
                StringBuilder json = new StringBuilder("{\"repositories\":[");
                for (int i = 0; i < repos.size(); i++) {
                    String[] r = repos.get(i);
                    json.append(String.format("{\"id\":%s,\"name\":\"%s\",\"owner_id\":%s}", r[0], r[1], r[2]));
                    if (i < repos.size() - 1) json.append(",");
                }
                json.append("]}");
                sendResponse(exchange, 200, json.toString());
            } else if ("DELETE".equals(method)) {
                String requestBody = readRequestBody(exchange);
                String idStr = parseFieldFromJson(requestBody, "id");
                boolean success = deleteRepositoryById(idStr);
                String jsonResponse = success ? "{\"success\":true}" : "{\"success\":false}";
                sendResponse(exchange, 200, jsonResponse);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // --- ブランチAPI ---
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
                String jsonResponse = success ? "{\"success\":true}" : "{\"success\":false}";
                sendResponse(exchange, 200, jsonResponse);
            } else if ("GET".equals(method)) {
                String query = exchange.getRequestURI().getQuery();
                String repositoryId = null;
                if (query != null && query.contains("repository_id=")) {
                    for (String param : query.split("&")) {
                        if (param.startsWith("repository_id=")) repositoryId = param.substring("repository_id=".length());
                    }
                }
                List<String[]> branches = fetchAllBranches(repositoryId);
                StringBuilder json = new StringBuilder("{\"branches\":[");
                for (int i = 0; i < branches.size(); i++) {
                    String[] b = branches.get(i);
                    json.append(String.format("{\"id\":%s,\"name\":\"%s\",\"repository_id\":%s,\"head_commit_id\":%s}", b[0], b[1], b[2], b[3]));
                    if (i < branches.size() - 1) json.append(",");
                }
                json.append("]}");
                sendResponse(exchange, 200, json.toString());
            } else if ("DELETE".equals(method)) {
                String requestBody = readRequestBody(exchange);
                String idStr = parseFieldFromJson(requestBody, "id");
                boolean success = deleteBranchById(idStr);
                String jsonResponse = success ? "{\"success\":true}" : "{\"success\":false}";
                sendResponse(exchange, 200, jsonResponse);
            } else if ("PUT".equals(method)) {
                String requestBody = readRequestBody(exchange);
                String branchId = parseFieldFromJson(requestBody, "id");
                String commitId = parseFieldFromJson(requestBody, "commit_id");
                boolean success = updateBranchHead(branchId, commitId);
                String jsonResponse = success ? "{\"success\":true}" : "{\"success\":false}";
                sendResponse(exchange, 200, jsonResponse);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // --- コミットAPI ---
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
                String jsonResponse = success ? "{\"success\":true}" : "{\"success\":false}";
                sendResponse(exchange, 200, jsonResponse);
            } else if ("GET".equals(method)) {
                String query = exchange.getRequestURI().getQuery();
                String branchId = null;
                if (query != null && query.contains("branch_id=")) {
                    for (String param : query.split("&")) {
                        if (param.startsWith("branch_id=")) branchId = param.substring("branch_id=".length());
                    }
                }
                List<Map<String, String>> commits = fetchAllCommits(branchId);
                StringBuilder json = new StringBuilder("{\"commits\":[");
                for (int i = 0; i < commits.size(); i++) {
                    Map<String, String> c = commits.get(i);
                    json.append("{");
                    int j = 0;
                    for (String k : c.keySet()) {
                        json.append(String.format("\"%s\":\"%s\"", k, c.get(k)));
                        if (j < c.size() - 1) json.append(",");
                        j++;
                    }
                    json.append("}");
                    if (i < commits.size() - 1) json.append(",");
                }
                json.append("]}");
                sendResponse(exchange, 200, json.toString());
            } else if ("DELETE".equals(method)) {
                String requestBody = readRequestBody(exchange);
                String idStr = parseFieldFromJson(requestBody, "id");
                boolean success = deleteCommitById(idStr);
                String jsonResponse = success ? "{\"success\":true}" : "{\"success\":false}";
                sendResponse(exchange, 200, jsonResponse);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // --- ファイル表示API ---
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
                // ブランチのhead_commit_id取得
                Integer headCommitId = null;
                String url = "jdbc:sqlite:database/database.db";
                try (Connection conn = DriverManager.getConnection(url)) {
                    String sql = "SELECT head_commit_id FROM branch WHERE id = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setInt(1, Integer.parseInt(branchId));
                        try (ResultSet rs = pstmt.executeQuery()) {
                            if (rs.next()) {
                                headCommitId = rs.getInt("head_commit_id");
                                if (rs.wasNull()) headCommitId = null;
                            }
                        }
                    }
                } catch (SQLException | NumberFormatException e) { headCommitId = null; }
                List<Map<String, String>> files = new ArrayList<>();
                if (headCommitId != null) {
                    try (Connection conn = DriverManager.getConnection(url)) {
                        String fileSql = "SELECT id, commit_id, content FROM file WHERE commit_id = ?";
                        try (PreparedStatement pstmt = conn.prepareStatement(fileSql)) {
                            pstmt.setInt(1, headCommitId);
                            try (ResultSet rs = pstmt.executeQuery()) {
                                while (rs.next()) {
                                    Map<String, String> f = new LinkedHashMap<>();
                                    f.put("commit_id", String.valueOf(rs.getInt("commit_id")));
                                    f.put("file_id", String.valueOf(rs.getInt("id")));
                                    f.put("text", rs.getString("content"));
                                    files.add(f);
                                }
                            }
                        }
                    } catch (SQLException e) { /* ignore */ }
                }
                StringBuilder json = new StringBuilder("{\"files\":[");
                for (int i = 0; i < files.size(); i++) {
                    Map<String, String> f = files.get(i);
                    json.append("{");
                    json.append(String.format("\"commit_id\":%s,\"file_id\":%s,\"text\":%s",
                        f.get("commit_id"), f.get("file_id"),
                        f.get("text") == null ? "null" : ("\"" + f.get("text").replace("\"", "\\\"") + "\"")));
                    json.append("}");
                    if (i < files.size() - 1) json.append(",");
                }
                json.append("]}");
                sendResponse(exchange, 200, json.toString());
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // --- マージAPI ---
    static class MergeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod();
            if ("OPTIONS".equals(method)) { exchange.sendResponseHeaders(204, -1); return; }
            if ("POST".equals(method)) {
                String requestBody = readRequestBody(exchange);
                // 新仕様: branch_id_1, branch_id_2
                String branchId1 = parseFieldFromJson(requestBody, "branch_id_1");
                String branchId2 = parseFieldFromJson(requestBody, "branch_id_2");
                Map<String, Object> result = mergeBranchesStrict(branchId1, branchId2);
                StringBuilder json = new StringBuilder("{");
                int i = 0;
                for (String k : result.keySet()) {
                    Object v = result.get(k);
                    if (v instanceof String) {
                        json.append(String.format("\"%s\":\"%s\"", k, v.toString().replace("\"", "\\\"")));
                    } else if (v instanceof Boolean) {
                        json.append(String.format("\"%s\":%s", k, v.toString()));
                    } else {
                        json.append(String.format("\"%s\":%s", k, v));
                    }
                    if (i < result.size() - 1) json.append(",");
                    i++;
                }
                json.append("}");
                sendResponse(exchange, 200, json.toString());
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // --- 強制マージAPI ---
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
                boolean success = forceMerge(branchId1, branchId2, text);
                String json = String.format("{\"success\":%s}", success ? "true" : "false");
                sendResponse(exchange, 200, json);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // --- グラフAPI ---
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

    // --- 共通ユーティリティ ---
    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }
    private static void sendResponse(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }
    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void initializeDatabase() {
        String url = "jdbc:sqlite:database/database.db";
        String[] sqls = new String[] {
            // ユーザーテーブル
            "CREATE TABLE IF NOT EXISTS name (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE)",
            // リポジトリ
            "CREATE TABLE IF NOT EXISTS repository (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, owner_id INTEGER)",
            // ブランチ
            "CREATE TABLE IF NOT EXISTS branch (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, repository_id INTEGER, head_commit_id INTEGER)",
            // コミット（repository_idで管理）
            "CREATE TABLE IF NOT EXISTS git_commit (id INTEGER PRIMARY KEY AUTOINCREMENT, repository_id INTEGER, author_id INTEGER, message TEXT, parent_commit_id INTEGER, parent_commit_id_2 INTEGER, created_at DATETIME)",
            // ファイル
            "CREATE TABLE IF NOT EXISTS \"file\" (id INTEGER PRIMARY KEY AUTOINCREMENT, commit_id INTEGER, filename TEXT, content TEXT)"
        };
        try (Connection conn = DriverManager.getConnection(url)) {
            for (String sql : sqls) {
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.err.println("Database initialization error: " + e.getMessage());
        }
    }

    // ユーザー追加
    private static boolean addUser(String username) {
        String url = "jdbc:sqlite:database/database.db";
        String sql = "INSERT INTO name(username) VALUES(?)";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
    // ユーザー一覧
    private static List<String[]> fetchAllUsers() {
        List<String[]> users = new ArrayList<>();
        String url = "jdbc:sqlite:database/database.db";
        String sql = "SELECT id, username FROM name";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                users.add(new String[]{String.valueOf(rs.getInt("id")), rs.getString("username")});
            }
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }
        return users;
    }
    // ユーザー削除
    private static boolean deleteUserById(String idStr) {
        String url = "jdbc:sqlite:database/database.db";
        String sql = "DELETE FROM name WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(idStr));
            int affected = pstmt.executeUpdate();
            return affected > 0;
        } catch (SQLException | NumberFormatException e) {
            return false;
        }
    }

    // リポジトリ追加
    private static boolean addRepository(String name, String ownerIdStr) {
        String url = "jdbc:sqlite:database/database.db";
        String sql = "INSERT INTO repository(name, owner_id) VALUES(?, ?)";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setInt(2, Integer.parseInt(ownerIdStr));
            pstmt.executeUpdate();
            return true;
        } catch (SQLException | NumberFormatException e) {
            return false;
        }
    }
    // リポジトリ一覧
    private static List<String[]> fetchAllRepositories(String ownerId) {
        List<String[]> repos = new ArrayList<>();
        String url = "jdbc:sqlite:database/database.db";
        String sql = (ownerId != null) ? "SELECT id, name, owner_id FROM repository WHERE owner_id = ?" : "SELECT id, name, owner_id FROM repository";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (ownerId != null) pstmt.setInt(1, Integer.parseInt(ownerId));
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    repos.add(new String[]{String.valueOf(rs.getInt("id")), rs.getString("name"), String.valueOf(rs.getInt("owner_id"))});
                }
            }
        } catch (SQLException | NumberFormatException e) {
            System.err.println("Database error: " + e.getMessage());
        }
        return repos;
    }
    // リポジトリ削除
    private static boolean deleteRepositoryById(String idStr) {
        String url = "jdbc:sqlite:database/database.db";
        String sql = "DELETE FROM repository WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(idStr));
            int affected = pstmt.executeUpdate();
            return affected > 0;
        } catch (SQLException | NumberFormatException e) {
            return false;
        }
    }

    // ブランチ
    private static boolean addBranch(String name, String repositoryIdStr) {
        String url = "jdbc:sqlite:database/database.db";
        String sql = "INSERT INTO branch(name, repository_id) VALUES(?, ?)";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setInt(2, Integer.parseInt(repositoryIdStr));
            pstmt.executeUpdate();
            return true;
        } catch (SQLException | NumberFormatException e) { return false; }
    }
    private static List<String[]> fetchAllBranches(String repositoryId) {
        List<String[]> branches = new ArrayList<>();
        String url = "jdbc:sqlite:database/database.db";
        String sql = (repositoryId != null) ? "SELECT id, name, repository_id, head_commit_id FROM branch WHERE repository_id = ?" : "SELECT id, name, repository_id, head_commit_id FROM branch";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (repositoryId != null) pstmt.setInt(1, Integer.parseInt(repositoryId));
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    branches.add(new String[]{String.valueOf(rs.getInt("id")), rs.getString("name"), String.valueOf(rs.getInt("repository_id")), String.valueOf(rs.getInt("head_commit_id"))});
                }
            }
        } catch (SQLException | NumberFormatException e) { System.err.println("Database error: " + e.getMessage()); }
        return branches;
    }
    private static boolean deleteBranchById(String idStr) {
        String url = "jdbc:sqlite:database/database.db";
        String sql = "DELETE FROM branch WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(idStr));
            int affected = pstmt.executeUpdate();
            return affected > 0;
        } catch (SQLException | NumberFormatException e) { return false; }
    }
    // コミット
    private static boolean addCommit(String branchIdStr, String message, String authorIdStr) {
        String url = "jdbc:sqlite:database/database.db";
        String sql = "INSERT INTO git_commit(branch_id, author_id, message, created_at) VALUES(?, ?, ?, datetime('now'))";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(branchIdStr));
            pstmt.setInt(2, Integer.parseInt(authorIdStr));
            pstmt.setString(3, message);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException | NumberFormatException e) { return false; }
    }
    // コミット＋内容＋head更新（repository_idで管理）
    private static boolean createCommitSnapshot(String branchIdStr, String message, String authorIdStr, String content) {
        String url = "jdbc:sqlite:database/database.db";
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url);
            conn.setAutoCommit(false);
            // 1. parent_commit_id取得 & repository_id取得
            Integer parentCommitId = null;
            Integer repositoryId = null;
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
            // 2. commit挿入（parent_commit_id_2はnull）
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
            // 3. fileスナップショット保存（main.txt固定）
            String fileSql = "INSERT INTO file(commit_id, filename, content) VALUES(?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(fileSql)) {
                pstmt.setInt(1, newCommitId);
                pstmt.setString(2, "main.txt");
                pstmt.setString(3, content);
                pstmt.executeUpdate();
            }
            // 4. branchのhead_commit_id更新
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

    // コミット一覧取得（repository_idで取得）
    private static List<Map<String, String>> fetchAllCommits(String repositoryId) {
        List<Map<String, String>> commits = new ArrayList<>();
        String url = "jdbc:sqlite:database/database.db";
        String sql = (repositoryId != null) ? "SELECT id, repository_id, author_id, message, parent_commit_id, parent_commit_id_2, created_at FROM git_commit WHERE repository_id = ?" : "SELECT id, repository_id, author_id, message, parent_commit_id, parent_commit_id_2, created_at FROM git_commit";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (repositoryId != null) pstmt.setInt(1, Integer.parseInt(repositoryId));
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> c = new LinkedHashMap<>();
                    c.put("id", String.valueOf(rs.getInt("id")));
                    c.put("repository_id", String.valueOf(rs.getInt("repository_id")));
                    c.put("author_id", String.valueOf(rs.getInt("author_id")));
                    c.put("message", rs.getString("message"));
                    c.put("parent_commit_id", String.valueOf(rs.getInt("parent_commit_id")));
                    c.put("parent_commit_id_2", String.valueOf(rs.getInt("parent_commit_id_2")));
                    c.put("created_at", rs.getString("created_at"));
                    commits.add(c);
                }
            }
        } catch (SQLException | NumberFormatException e) { System.err.println("Database error: " + e.getMessage()); }
        return commits;
    }

    // ファイル: HEADコミットのファイル一覧を返す（main.txtのみ）
    private static List<Map<String, String>> fetchFilesByRepoAndBranch(String repositoryId, String branchId) {
        List<Map<String, String>> files = new ArrayList<>();
        if (repositoryId == null && branchId == null) return files;
        String url = "jdbc:sqlite:database/database.db";
        Integer headCommitId = null;
        try (Connection conn = DriverManager.getConnection(url)) {
            if (branchId != null) {
                String sql = "SELECT head_commit_id FROM branch WHERE id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setInt(1, Integer.parseInt(branchId));
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            headCommitId = rs.getInt("head_commit_id");
                            if (rs.wasNull()) headCommitId = null;
                        }
                    }
                }
            } else if (repositoryId != null) {
                String sql = "SELECT head_commit_id FROM branch WHERE repository_id = ? ORDER BY id LIMIT 1";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setInt(1, Integer.parseInt(repositoryId));
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            headCommitId = rs.getInt("head_commit_id");
                            if (rs.wasNull()) headCommitId = null;
                        }
                    }
                }
            }
            if (headCommitId == null) return files;
            String fileSql = "SELECT id, commit_id, filename, content FROM file WHERE commit_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(fileSql)) {
                pstmt.setInt(1, headCommitId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, String> f = new LinkedHashMap<>();
                        f.put("id", String.valueOf(rs.getInt("id")));
                        f.put("commit_id", String.valueOf(rs.getInt("commit_id")));
                        f.put("filename", rs.getString("filename"));
                        f.put("content", rs.getString("content"));
                        files.add(f);
                    }
                }
            }
        } catch (SQLException | NumberFormatException e) { return files; }
        return files;
    }
    private static String parseFieldFromJson(String json, String field) {
        if (json.contains("\"" + field + "\":")) {
            int startIndex = json.indexOf("\"" + field + "\":") + field.length() + 3;
            int endIndex = json.indexOf('"', startIndex + 1);
            return json.substring(startIndex + 1, endIndex);
        }
        return "";
    }

    // git風マージ本体
    private static Map<String, Object> mergeBranches(String branchIdStr, String targetBranchIdStr) {
        String url = "jdbc:sqlite:database/database.db";
        Connection conn = null;
        Map<String, Object> result = new HashMap<>();
        try {
            conn = DriverManager.getConnection(url);
            conn.setAutoCommit(false);
            // 1. 各ブランチのHEADコミット取得
            Integer sourceHead = null, targetHead = null;
            String sql = "SELECT id, head_commit_id FROM branch WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, Integer.parseInt(branchIdStr));
                try (ResultSet rs = pstmt.executeQuery()) { if (rs.next()) sourceHead = rs.getInt("head_commit_id"); }
                pstmt.setInt(1, Integer.parseInt(targetBranchIdStr));
                try (ResultSet rs = pstmt.executeQuery()) { if (rs.next()) targetHead = rs.getInt("head_commit_id"); }
            }
            if (sourceHead == null || targetHead == null || sourceHead == 0 || targetHead == 0) {
                result.put("conflict", true);
                result.put("message", "No HEAD commit");
                return result;
            }
            // 2. 各HEADコミットの内容取得
            String getContentSql = "SELECT content FROM file WHERE commit_id = ? ORDER BY id LIMIT 1";
            String sourceContent = "", targetContent = "";
            try (PreparedStatement pstmt = conn.prepareStatement(getContentSql)) {
                pstmt.setInt(1, sourceHead);
                try (ResultSet rs = pstmt.executeQuery()) { if (rs.next()) sourceContent = rs.getString("content"); }
                pstmt.setInt(1, targetHead);
                try (ResultSet rs = pstmt.executeQuery()) { if (rs.next()) targetContent = rs.getString("content"); }
            }
            // 3. 内容が同じなら何もしない
            if (sourceContent.equals(targetContent)) {
                result.put("conflict", false);
                result.put("message", "Already up-to-date");
                return result;
            }
            // 4. fast-forward判定（targetがsourceの祖先ならfast-forward）
            if (isAncestor(conn, targetHead, sourceHead)) {
                // targetブランチのhead_commit_idをsourceHeadに更新
                String updateSql = "UPDATE branch SET head_commit_id = ? WHERE id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                    pstmt.setInt(1, sourceHead);
                    pstmt.setInt(2, Integer.parseInt(targetBranchIdStr));
                    pstmt.executeUpdate();
                }
                conn.commit();
                result.put("conflict", false);
                result.put("message", "Fast-forward");
                return result;
            }
            // 5. マージコミット作成（parent_commit_id, parent_commit_id_2両方セット）
            String mergedContent = sourceContent + "\n" + targetContent;
            String commitSql = "INSERT INTO git_commit(branch_id, author_id, message, parent_commit_id, parent_commit_id_2, created_at) VALUES(?, ?, ?, ?, ?, datetime('now'))";
            int mergeCommitId = -1;
            try (PreparedStatement pstmt = conn.prepareStatement(commitSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                pstmt.setInt(1, Integer.parseInt(targetBranchIdStr));
                pstmt.setInt(2, 1); // author_id=1固定（本来はAPIで指定）
                pstmt.setString(3, "Merge branch " + branchIdStr);
                pstmt.setInt(4, targetHead);
                pstmt.setInt(5, sourceHead);
                pstmt.executeUpdate();
                try (ResultSet rs = pstmt.getGeneratedKeys()) { if (rs.next()) mergeCommitId = rs.getInt(1); }
            }
            if (mergeCommitId == -1) { conn.rollback(); result.put("conflict", true); result.put("message", "Merge commit failed"); return result; }
            // ファイル保存
            String fileSql = "INSERT INTO file(commit_id, filename, content) VALUES(?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(fileSql)) {
                pstmt.setInt(1, mergeCommitId);
                pstmt.setString(2, "main.txt");
                pstmt.setString(3, mergedContent);
                pstmt.executeUpdate();
            }
            // head_commit_id更新
            String updateSql = "UPDATE branch SET head_commit_id = ? WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                pstmt.setInt(1, mergeCommitId);
                pstmt.setInt(2, Integer.parseInt(targetBranchIdStr));
                pstmt.executeUpdate();
            }
            conn.commit();
            result.put("conflict", false);
            result.put("message", "Merge success");
        } catch (SQLException | NumberFormatException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ignore) {}
            result.put("conflict", true);
            result.put("message", "Merge error: " + e.getMessage());
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignore) {}
        }
        return result;
    }
    // 祖先判定（targetがsourceの祖先か）
    private static boolean isAncestor(Connection conn, int ancestorId, int descendantId) throws SQLException {
        if (ancestorId == descendantId) return true;
        String sql = "SELECT parent_commit_id FROM git_commit WHERE id = ?";
        int cur = descendantId;
        while (cur != 0) {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, cur);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        int parent = rs.getInt("parent_commit_id");
                        if (parent == ancestorId) return true;
                        if (parent == 0) break;
                        cur = parent;
                    } else {
                        break;
                    }
                }
            }
        }
        return false;
    }
    // グラフAPI本体
    private static String getGraphJson(String repositoryId) {
        String url = "jdbc:sqlite:database/database.db";
        List<String> nodes = new ArrayList<>();
        List<String> edges = new ArrayList<>();
        List<String> branchPointers = new ArrayList<>(); // 追加: ブランチ→コミットの矢印
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
    // ブランチのhead_commit_id付け替え
    private static boolean updateBranchHead(String branchIdStr, String commitIdStr) {
        String url = "jdbc:sqlite:database/database.db";
        String sql = "UPDATE branch SET head_commit_id = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(commitIdStr));
            pstmt.setInt(2, Integer.parseInt(branchIdStr));
            int affected = pstmt.executeUpdate();
            return affected > 0;
        } catch (SQLException | NumberFormatException e) { return false; }
    }
    // コミット削除
    private static boolean deleteCommitById(String idStr) {
        String url = "jdbc:sqlite:database/database.db";
        String sql = "DELETE FROM git_commit WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(idStr));
            int affected = pstmt.executeUpdate();
            return affected > 0;
        } catch (SQLException | NumberFormatException e) { return false; }
    }

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

    // 2ブランチのファイル内容が完全一致ならマージ、違えば両方の内容返す
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

    // 強制マージ: 指定テキストで新コミット作成、両ブランチhead更新
    private static boolean forceMerge(String branchId1, String branchId2, String text) {
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
