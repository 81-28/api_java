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
        server.createContext("/api", new HelloHandler());
        server.setExecutor(null); // デフォルトのエグゼキュータを使用
        server.start();
        System.out.println("Server is running on http://localhost:8080/api");
    }

    static class HelloHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod();

            if ("OPTIONS".equals(method)) {
                // Preflight request handling
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equals(method)) {
                // データの追加
                String requestBody;
                try (Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A")) {
                    requestBody = scanner.hasNext() ? scanner.next() : "";
                }
                String title = parseFieldFromJson(requestBody, "title");
                String content = parseFieldFromJson(requestBody, "content");
                saveToDatabase(title, content);

                String jsonResponse = "{\"message\":\"Data added successfully\",\"status\":\"success\"}";
                sendResponse(exchange, 200, jsonResponse);

            } else if ("GET".equals(method)) {
                // データの一覧を返す
                List<String[]> data = fetchAllData();
                StringBuilder jsonResponse = new StringBuilder("{\"data\":[");
                for (int i = 0; i < data.size(); i++) {
                    String[] entry = data.get(i);
                    jsonResponse.append(String.format("{\"id\":%s,\"title\":\"%s\",\"content\":\"%s\"}",
                            entry[0], entry[1], entry[2]));
                    if (i < data.size() - 1) jsonResponse.append(",");
                }
                jsonResponse.append("]}");
                sendResponse(exchange, 200, jsonResponse.toString());

            } else if ("DELETE".equals(method)) {
                // データの削除
                String requestBody;
                try (Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A")) {
                    requestBody = scanner.hasNext() ? scanner.next() : "";
                }
                String title = parseFieldFromJson(requestBody, "title");
                deleteFromDatabase(title);

                String jsonResponse = "{\"message\":\"Data deleted successfully\",\"status\":\"success\"}";
                sendResponse(exchange, 200, jsonResponse);

            } else if ("PUT".equals(method)) {
                // データの上書き（更新）
                String requestBody;
                try (Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A")) {
                    requestBody = scanner.hasNext() ? scanner.next() : "";
                }
                String title = parseFieldFromJson(requestBody, "title");
                String newContent = parseFieldFromJson(requestBody, "content");
                String oldContent = getContentByTitle(title);
                boolean updated = updateContentByTitle(title, newContent);
                String jsonResponse;
                if (updated) {
                    // diffの代わりにoldContentとnewContentを返す
                    String oldEscaped = oldContent == null ? "" : toJsonStringRaw(oldContent);
                    String newEscaped = newContent == null ? "" : toJsonStringRaw(newContent);
                    jsonResponse = String.format("{\"message\":\"Updated successfully\",\"oldContent\":%s,\"newContent\":%s,\"status\":\"success\"}", oldEscaped, newEscaped);
                } else {
                    jsonResponse = "{\"message\":\"Title not found\",\"status\":\"error\"}";
                }
                sendResponse(exchange, 200, jsonResponse);

            } else {
                // サポートされていないメソッド
                exchange.sendResponseHeaders(405, -1);
            }
        }

        private void addCorsHeaders(HttpExchange exchange) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        }

        private List<String[]> fetchAllData() {
            List<String[]> data = new ArrayList<>();
            String url = "jdbc:sqlite:database/database.db";
            String sql = "SELECT id, title, content FROM posts";

            try (Connection conn = DriverManager.getConnection(url);
                 PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {
                    data.add(new String[]{
                        String.valueOf(rs.getInt("id")),
                        rs.getString("title"),
                        rs.getString("content")
                    });
                }

            } catch (SQLException e) {
                System.err.println("Database error: " + e.getMessage());
            }

            return data;
        }

        private void deleteFromDatabase(String title) {
            String url = "jdbc:sqlite:database/database.db";
            String sql = "DELETE FROM posts WHERE title = ?";

            try (Connection conn = DriverManager.getConnection(url);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, title);
                pstmt.executeUpdate();

            } catch (SQLException e) {
                System.err.println("Database error: " + e.getMessage());
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }

        private String parseFieldFromJson(String json, String field) {
            if (json.contains("\"" + field + "\":")) {
                int startIndex = json.indexOf("\"" + field + "\":") + field.length() + 3;
                int endIndex = json.indexOf('"', startIndex + 1);
                return json.substring(startIndex + 1, endIndex);
            }
            return "";
        }

        private void saveToDatabase(String title, String content) {
            String url = "jdbc:sqlite:database/database.db";
            String sql = "INSERT INTO posts(title, content) VALUES(?, ?)";

            try (Connection conn = DriverManager.getConnection(url);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, title);
                pstmt.setString(2, content);
                pstmt.executeUpdate();

            } catch (SQLException e) {
                System.err.println("Database error: " + e.getMessage());
            }
        }

        private String getContentByTitle(String title) {
            String url = "jdbc:sqlite:database/database.db";
            String sql = "SELECT content FROM posts WHERE title = ?";
            try (Connection conn = DriverManager.getConnection(url);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, title);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("content");
                    }
                }
            } catch (SQLException e) {
                System.err.println("Database error: " + e.getMessage());
            }
            return null;
        }

        private boolean updateContentByTitle(String title, String newContent) {
            String url = "jdbc:sqlite:database/database.db";
            String sql = "UPDATE posts SET content = ? WHERE title = ?";
            try (Connection conn = DriverManager.getConnection(url);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, newContent);
                pstmt.setString(2, title);
                int affected = pstmt.executeUpdate();
                return affected > 0;
            } catch (SQLException e) {
                System.err.println("Database error: " + e.getMessage());
            }
            return false;
        }

        private String generateDiff(String oldContent, String newContent) {
            if (oldContent == null) return "(新規作成またはタイトル未検出)";
            String[] oldLines = oldContent.split("\\r?\\n");
            String[] newLines = newContent.split("\\r?\\n");
            StringBuilder diff = new StringBuilder();
            diff.append("--- old\n");
            diff.append("+++ new\n");
            int oldLen = oldLines.length, newLen = newLines.length;
            int i = 0, j = 0;
            boolean inHunk = false;
            while (i < oldLen || j < newLen) {
                if (i < oldLen && j < newLen && oldLines[i].equals(newLines[j])) {
                    if (inHunk) diff.append(" ").append(oldLines[i]).append("\n");
                    i++; j++;
                } else {
                    // hunk header
                    if (!inHunk) {
                        diff.append(String.format("@@ -%d +%d @@\n", i+1, j+1));
                        inHunk = true;
                    }
                    if (i < oldLen && (j >= newLen || !oldLines[i].equals(newLines[j]))) {
                        diff.append("-").append(oldLines[i]).append("\n");
                        i++;
                    }
                    if (j < newLen && (i > oldLen || !oldLines[i-1].equals(newLines[j]))) {
                        diff.append("+").append(newLines[j]).append("\n");
                        j++;
                    }
                }
                // hunk終端: 直後が一致行ならhunkを閉じる
                if (inHunk && i < oldLen && j < newLen && oldLines[i].equals(newLines[j])) {
                    inHunk = false;
                }
            }
            // 最後の\nを除去
            if (diff.length() > 0 && diff.charAt(diff.length() - 1) == '\n') {
                diff.deleteCharAt(diff.length() - 1);
            }
            return diff.toString();
        }

        // diff文字列をJSONの文字列リテラルとして正しく埋め込む
        private String toJsonStringRaw(String s) {
            if (s == null) return "null";
            StringBuilder sb = new StringBuilder();
            sb.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '\\': sb.append("\\\\"); break;
                    case '"': sb.append("\\\""); break;
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append("\\r"); break;
                    case '\t': sb.append("\\t"); break;
                    default:
                        if (c < 0x20) {
                            sb.append(String.format("\\u%04x", (int)c));
                        } else {
                            sb.append(c);
                        }
                }
            }
            sb.append('"');
            return sb.toString();
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
        String sql = "CREATE TABLE IF NOT EXISTS posts (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, content TEXT)";

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Database initialization error: " + e.getMessage());
        }
    }
}
