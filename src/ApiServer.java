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

public class ApiServer {

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/api/user", new UserHandler());
        server.createContext("/api/repository", new RepositoryHandler());
        server.createContext("/api/branch", new BranchHandler());
        server.createContext("/api/commit", new CommitHandler());
        server.createContext("/api/file", new FileHandler());
        server.createContext("/api/merge", new MergeHandler());
        server.createContext("/api/graph", new GraphHandler());
        // TODO: 他のエンドポイントも追加
        server.setExecutor(null);
        server.start();
        System.out.println("Server is running on http://localhost:8080/api");
    }

    // ユーザーAPI
    static class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod();
            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if ("POST".equals(method)) {
                // ユーザー作成
                String requestBody;
                try (Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A")) {
                    requestBody = scanner.hasNext() ? scanner.next() : "";
                }
                String username = parseFieldFromJson(requestBody, "username");
                boolean success = addUser(username);
                String jsonResponse = success ? "{\"message\":\"User created\",\"status\":\"success\"}" : "{\"message\":\"User already exists\",\"status\":\"error\"}";
                sendResponse(exchange, 200, jsonResponse);
            } else if ("GET".equals(method)) {
                // ユーザー一覧
                List<String[]> users = fetchAllUsers();
                StringBuilder jsonResponse = new StringBuilder("{\"users\":[");
                for (int i = 0; i < users.size(); i++) {
                    String[] entry = users.get(i);
                    jsonResponse.append(String.format("{\"id\":%s,\"username\":\"%s\"}", entry[0], entry[1]));
                    if (i < users.size() - 1) jsonResponse.append(",");
                }
                jsonResponse.append("]}");
                sendResponse(exchange, 200, jsonResponse.toString());
            } else if ("DELETE".equals(method)) {
                // ユーザー削除
                String requestBody;
                try (Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A")) {
                    requestBody = scanner.hasNext() ? scanner.next() : "";
                }
                String idStr = parseFieldFromJson(requestBody, "id");
                boolean success = deleteUserById(idStr);
                String jsonResponse = success ? "{\"message\":\"User deleted\",\"status\":\"success\"}" : "{\"message\":\"User not found\",\"status\":\"error\"}";
                sendResponse(exchange, 200, jsonResponse);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // リポジトリAPI
    static class RepositoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod();
            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if ("POST".equals(method)) {
                // リポジトリ作成
                String requestBody;
                try (Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A")) {
                    requestBody = scanner.hasNext() ? scanner.next() : "";
                }
                String name = parseFieldFromJson(requestBody, "name");
                String ownerIdStr = parseFieldFromJson(requestBody, "owner_id");
                boolean success = addRepository(name, ownerIdStr);
                String jsonResponse = success ? "{\"message\":\"Repository created\",\"status\":\"success\"}" : "{\"message\":\"Repository creation failed\",\"status\":\"error\"}";
                sendResponse(exchange, 200, jsonResponse);
            } else if ("GET".equals(method)) {
                // リポジトリ一覧（owner_id指定時はそのユーザーのリポジトリのみ返す）
                String query = exchange.getRequestURI().getQuery();
                String ownerId = null;
                if (query != null && query.contains("owner_id=")) {
                    for (String param : query.split("&")) {
                        if (param.startsWith("owner_id=")) {
                            ownerId = param.substring("owner_id=".length());
                        }
                    }
                }
                List<String[]> repos = fetchAllRepositories(ownerId);
                StringBuilder jsonResponse = new StringBuilder("{\"repositories\":[");
                for (int i = 0; i < repos.size(); i++) {
                    String[] entry = repos.get(i);
                    jsonResponse.append(String.format("{\"id\":%s,\"name\":\"%s\",\"owner_id\":%s}", entry[0], entry[1], entry[2]));
                    if (i < repos.size() - 1) jsonResponse.append(",");
                }
                jsonResponse.append("]}");
                sendResponse(exchange, 200, jsonResponse.toString());
            } else if ("DELETE".equals(method)) {
                // リポジトリ削除
                String requestBody;
                try (Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A")) {
                    requestBody = scanner.hasNext() ? scanner.next() : "";
                }
                String idStr = parseFieldFromJson(requestBody, "id");
                boolean success = deleteRepositoryById(idStr);
                String jsonResponse = success ? "{\"message\":\"Repository deleted\",\"status\":\"success\"}" : "{\"message\":\"Repository not found\",\"status\":\"error\"}";
                sendResponse(exchange, 200, jsonResponse);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // ブランチAPI
    static class BranchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod();
            if ("OPTIONS".equals(method)) { exchange.sendResponseHeaders(204, -1); return; }
            if ("POST".equals(method)) {
                // ブランチ作成
                String requestBody;
                try (Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A")) { requestBody = scanner.hasNext() ? scanner.next() : ""; }
                String name = parseFieldFromJson(requestBody, "name");
                String repositoryIdStr = parseFieldFromJson(requestBody, "repository_id");
                boolean success = addBranch(name, repositoryIdStr);
                String jsonResponse = success ? "{\"message\":\"Branch created\",\"status\":\"success\"}" : "{\"message\":\"Branch creation failed\",\"status\":\"error\"}";
                sendResponse(exchange, 200, jsonResponse);
            } else if ("GET".equals(method)) {
                // ブランチ一覧（repository_id指定）
                String query = exchange.getRequestURI().getQuery();
                String repositoryId = null;
                if (query != null && query.contains("repository_id=")) {
                    for (String param : query.split("&")) {
                        if (param.startsWith("repository_id=")) { repositoryId = param.substring("repository_id=".length()); }
                    }
                }
                List<String[]> branches = fetchAllBranches(repositoryId);
                StringBuilder jsonResponse = new StringBuilder("{\"branches\":[");
                for (int i = 0; i < branches.size(); i++) {
                    String[] entry = branches.get(i);
                    jsonResponse.append(String.format("{\"id\":%s,\"name\":\"%s\",\"repository_id\":%s,\"head_commit_id\":%s}", entry[0], entry[1], entry[2], entry[3]));
                    if (i < branches.size() - 1) jsonResponse.append(",");
                }
                jsonResponse.append("]}");
                sendResponse(exchange, 200, jsonResponse.toString());
            } else if ("DELETE".equals(method)) {
                // ブランチ削除
                String requestBody;
                try (Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A")) { requestBody = scanner.hasNext() ? scanner.next() : ""; }
                String idStr = parseFieldFromJson(requestBody, "id");
                boolean success = deleteBranchById(idStr);
                String jsonResponse = success ? "{\"message\":\"Branch deleted\",\"status\":\"success\"}" : "{\"message\":\"Branch not found\",\"status\":\"error\"}";
                sendResponse(exchange, 200, jsonResponse);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }
    // コミットAPI
    static class CommitHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod();
            if ("OPTIONS".equals(method)) { exchange.sendResponseHeaders(204, -1); return; }
            if ("POST".equals(method)) {
                // コミット作成
                String requestBody;
                try (Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A")) { requestBody = scanner.hasNext() ? scanner.next() : ""; }
                String branchIdStr = parseFieldFromJson(requestBody, "branch_id");
                String message = parseFieldFromJson(requestBody, "message");
                String authorIdStr = parseFieldFromJson(requestBody, "author_id");
                String content = parseFieldFromJson(requestBody, "content");
                boolean success = addCommitWithContent(branchIdStr, message, authorIdStr, content);
                String jsonResponse = success ? "{\"message\":\"Commit created\",\"status\":\"success\"}" : "{\"message\":\"Commit creation failed\",\"status\":\"error\"}";
                sendResponse(exchange, 200, jsonResponse);
            } else if ("GET".equals(method)) {
                // コミット一覧（branch_id指定）
                String query = exchange.getRequestURI().getQuery();
                String branchId = null;
                if (query != null && query.contains("branch_id=")) {
                    for (String param : query.split("&")) {
                        if (param.startsWith("branch_id=")) { branchId = param.substring("branch_id=".length()); }
                    }
                }
                List<String[]> commits = fetchAllCommits(branchId);
                StringBuilder jsonResponse = new StringBuilder("{\"commits\":[");
                for (int i = 0; i < commits.size(); i++) {
                    String[] entry = commits.get(i);
                    jsonResponse.append(String.format("{\"id\":%s,\"branch_id\":%s,\"author_id\":%s,\"message\":\"%s\",\"parent_commit_id\":%s,\"created_at\":\"%s\"}", entry[0], entry[1], entry[2], entry[3], entry[4], entry[5]));
                    if (i < commits.size() - 1) jsonResponse.append(",");
                }
                jsonResponse.append("]}");
                sendResponse(exchange, 200, jsonResponse.toString());
            } else if ("DELETE".equals(method)) {
                // コミット削除
                String requestBody;
                try (Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A")) { requestBody = scanner.hasNext() ? scanner.next() : ""; }
                String idStr = parseFieldFromJson(requestBody, "id");
                boolean success = deleteCommitById(idStr);
                String jsonResponse = success ? "{\"message\":\"Commit deleted\",\"status\":\"success\"}" : "{\"message\":\"Commit not found\",\"status\":\"error\"}";
                sendResponse(exchange, 200, jsonResponse);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }
    // ファイルAPI
    static class FileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod();
            if ("OPTIONS".equals(method)) { exchange.sendResponseHeaders(204, -1); return; }
            if ("GET".equals(method)) {
                // ファイル表示（repository_id指定）
                String query = exchange.getRequestURI().getQuery();
                String repositoryId = null;
                if (query != null && query.contains("repository_id=")) {
                    for (String param : query.split("&")) {
                        if (param.startsWith("repository_id=")) { repositoryId = param.substring("repository_id=".length()); }
                    }
                }
                List<String[]> files = fetchFilesByRepositoryId(repositoryId);
                StringBuilder jsonResponse = new StringBuilder("{\"files\":[");
                for (int i = 0; i < files.size(); i++) {
                    String[] entry = files.get(i);
                    jsonResponse.append(String.format("{\"id\":%s,\"commit_id\":%s,\"filename\":\"%s\",\"content\":\"%s\"}", entry[0], entry[1], entry[2], entry[3]));
                    if (i < files.size() - 1) jsonResponse.append(",");
                }
                jsonResponse.append("]}");
                sendResponse(exchange, 200, jsonResponse.toString());
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }
    // マージAPI（git風実装）
    static class MergeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod();
            if ("OPTIONS".equals(method)) { exchange.sendResponseHeaders(204, -1); return; }
            if ("POST".equals(method)) {
                String requestBody;
                try (Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A")) { requestBody = scanner.hasNext() ? scanner.next() : ""; }
                String branchIdStr = parseFieldFromJson(requestBody, "branch_id"); // マージ元
                String targetBranchIdStr = parseFieldFromJson(requestBody, "target_branch_id"); // マージ先
                String jsonResponse = mergeBranches(branchIdStr, targetBranchIdStr);
                sendResponse(exchange, 200, jsonResponse);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // グラフAPI（git風実装）
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
                        if (param.startsWith("repository_id=")) { repositoryId = param.substring("repository_id=".length()); }
                    }
                }
                String jsonResponse = getGraphJson(repositoryId);
                sendResponse(exchange, 200, jsonResponse);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    static {
        // JDBCドライバをロード
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("Failed to load SQLite JDBC driver: " + e.getMessage());
        }

        // データベース初期化
        initializeDatabase();
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
            // コミット（予約語なので"git_commit"に変更）
            "CREATE TABLE IF NOT EXISTS git_commit (id INTEGER PRIMARY KEY AUTOINCREMENT, branch_id INTEGER, author_id INTEGER, message TEXT, parent_commit_id INTEGER, created_at DATETIME)",
            // ファイル（予約語なので"file"で囲む）
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
    // コミット＋内容＋head更新
    private static boolean addCommitWithContent(String branchIdStr, String message, String authorIdStr, String content) {
        String url = "jdbc:sqlite:database/database.db";
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url);
            conn.setAutoCommit(false);
            // 1. parent_commit_id取得
            Integer parentCommitId = null;
            String parentSql = "SELECT head_commit_id FROM branch WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(parentSql)) {
                pstmt.setInt(1, Integer.parseInt(branchIdStr));
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        parentCommitId = rs.getInt("head_commit_id");
                        if (rs.wasNull()) parentCommitId = null;
                    }
                }
            }
            // 2. commit挿入
            String commitSql = "INSERT INTO git_commit(branch_id, author_id, message, parent_commit_id, created_at) VALUES(?, ?, ?, ?, datetime('now'))";
            int newCommitId = -1;
            try (PreparedStatement pstmt = conn.prepareStatement(commitSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                pstmt.setInt(1, Integer.parseInt(branchIdStr));
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
    private static List<String[]> fetchAllCommits(String branchId) {
        List<String[]> commits = new ArrayList<>();
        String url = "jdbc:sqlite:database/database.db";
        String sql = (branchId != null) ? "SELECT id, branch_id, author_id, message, parent_commit_id, created_at FROM git_commit WHERE branch_id = ?" : "SELECT id, branch_id, author_id, message, parent_commit_id, created_at FROM git_commit";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (branchId != null) pstmt.setInt(1, Integer.parseInt(branchId));
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    commits.add(new String[]{String.valueOf(rs.getInt("id")), String.valueOf(rs.getInt("branch_id")), String.valueOf(rs.getInt("author_id")), rs.getString("message"), String.valueOf(rs.getInt("parent_commit_id")), rs.getString("created_at")});
                }
            }
        } catch (SQLException | NumberFormatException e) { System.err.println("Database error: " + e.getMessage()); }
        return commits;
    }
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
    // ファイル: HEADコミットのファイル一覧を返す
    private static List<String[]> fetchFilesByRepositoryId(String repositoryId) {
        List<String[]> files = new ArrayList<>();
        if (repositoryId == null) return files;
        String url = "jdbc:sqlite:database/database.db";
        // HEADコミットID取得
        String headCommitSql = "SELECT head_commit_id FROM branch WHERE repository_id = ? ORDER BY id LIMIT 1";
        Integer headCommitId = null;
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(headCommitSql)) {
            pstmt.setInt(1, Integer.parseInt(repositoryId));
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    headCommitId = rs.getInt("head_commit_id");
                    if (rs.wasNull()) headCommitId = null;
                }
            }
        } catch (SQLException | NumberFormatException e) { return files; }
        if (headCommitId == null) return files;
        // ファイル一覧取得
        String fileSql = "SELECT id, commit_id, filename, content FROM file WHERE commit_id = ?";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(fileSql)) {
            pstmt.setInt(1, headCommitId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    files.add(new String[]{String.valueOf(rs.getInt("id")), String.valueOf(rs.getInt("commit_id")), rs.getString("filename"), rs.getString("content")});
                }
            }
        } catch (SQLException e) { return files; }
        return files;
    }

    // static化
    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
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
    private static String mergeBranches(String branchIdStr, String targetBranchIdStr) {
        String url = "jdbc:sqlite:database/database.db";
        Connection conn = null;
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
                return "{\"conflict\":true,\"message\":\"No HEAD commit\"}";
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
                return "{\"conflict\":false,\"message\":\"Already up-to-date\"}";
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
                return "{\"conflict\":false,\"message\":\"Fast-forward\"}";
            }
            // 5. コンフリクト判定（内容が異なり、どちらも進んでいる場合）
            // ここでは単純に内容が異なればconflictとする
            // 本来は共通祖先を探して3-way mergeするが、ここでは簡易実装
            // 自動マージ例: 両方の内容を結合
            String mergedContent = sourceContent + "\n" + targetContent;
            // 6. マージコミット作成（parent_commit_idはtargetHead、fileはmergedContent）
            String commitSql = "INSERT INTO git_commit(branch_id, author_id, message, parent_commit_id, created_at) VALUES(?, ?, ?, ?, datetime('now'))";
            int mergeCommitId = -1;
            try (PreparedStatement pstmt = conn.prepareStatement(commitSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                pstmt.setInt(1, Integer.parseInt(targetBranchIdStr));
                pstmt.setInt(2, 1); // author_id=1固定（本来はAPIで指定）
                pstmt.setString(3, "Merge branch " + branchIdStr);
                pstmt.setInt(4, targetHead);
                pstmt.executeUpdate();
                try (ResultSet rs = pstmt.getGeneratedKeys()) { if (rs.next()) mergeCommitId = rs.getInt(1); }
            }
            if (mergeCommitId == -1) { conn.rollback(); return "{\"conflict\":true,\"message\":\"Merge commit failed\"}"; }
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
            return "{\"conflict\":false,\"message\":\"Merge success\"}";
        } catch (SQLException | NumberFormatException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ignore) {}
            return "{\"conflict\":true,\"message\":\"Merge error: " + e.getMessage() + "\"}";
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignore) {}
        }
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
        try (Connection conn = DriverManager.getConnection(url)) {
            // 全ブランチ取得
            String branchSql = "SELECT id, name FROM branch WHERE repository_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(branchSql)) {
                pstmt.setInt(1, Integer.parseInt(repositoryId));
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        int branchId = rs.getInt("id");
                        String branchName = rs.getString("name");
                        // 各ブランチのコミットを取得
                        String commitSql = "SELECT id, message, parent_commit_id FROM git_commit WHERE branch_id = ?";
                        try (PreparedStatement cp = conn.prepareStatement(commitSql)) {
                            cp.setInt(1, branchId);
                            try (ResultSet crs = cp.executeQuery()) {
                                while (crs.next()) {
                                    int commitId = crs.getInt("id");
                                    String msg = crs.getString("message");
                                    int parent = crs.getInt("parent_commit_id");
                                    nodes.add(String.format("{\"id\":%d,\"label\":\"%s\",\"branch\":\"%s\"}", commitId, msg, branchName));
                                    if (parent != 0) {
                                        edges.add(String.format("{\"from\":%d,\"to\":%d}", parent, commitId));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (SQLException | NumberFormatException e) {
            return "{\"nodes\":[],\"edges\":[],\"error\":\"" + e.getMessage() + "\"}";
        }
        return String.format("{\"nodes\":[%s],\"edges\":[%s]}", String.join(",", nodes), String.join(",", edges));
    }
}
