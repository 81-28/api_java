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
                String name = parseFieldFromJson(requestBody, "name");
                String description = parseFieldFromJson(requestBody, "description");
                saveToDatabase(name, description);

                String jsonResponse = "{\"message\":\"Data added successfully\",\"status\":\"success\"}";
                sendResponse(exchange, 200, jsonResponse);

            } else if ("GET".equals(method)) {
                // データの一覧を返す
                List<String[]> data = fetchAllData();
                StringBuilder jsonResponse = new StringBuilder("{\"data\":[");
                for (int i = 0; i < data.size(); i++) {
                    String[] entry = data.get(i);
                    jsonResponse.append(String.format("{\"id\":%s,\"name\":\"%s\",\"description\":\"%s\"}",
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
                String name = parseFieldFromJson(requestBody, "name");
                deleteFromDatabase(name);

                String jsonResponse = "{\"message\":\"Data deleted successfully\",\"status\":\"success\"}";
                sendResponse(exchange, 200, jsonResponse);

            } else {
                // サポートされていないメソッド
                exchange.sendResponseHeaders(405, -1);
            }
        }

        private void addCorsHeaders(HttpExchange exchange) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        }

        private List<String[]> fetchAllData() {
            List<String[]> data = new ArrayList<>();
            String url = "jdbc:sqlite:database/database.db";
            String sql = "SELECT id, name, description FROM users";

            try (Connection conn = DriverManager.getConnection(url);
                 PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {
                    data.add(new String[]{
                        String.valueOf(rs.getInt("id")),
                        rs.getString("name"),
                        rs.getString("description")
                    });
                }

            } catch (SQLException e) {
                System.err.println("Database error: " + e.getMessage());
            }

            return data;
        }

        private void deleteFromDatabase(String name) {
            String url = "jdbc:sqlite:database/database.db";
            String sql = "DELETE FROM users WHERE name = ?";

            try (Connection conn = DriverManager.getConnection(url);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, name);
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

        private void saveToDatabase(String name, String description) {
            String url = "jdbc:sqlite:database/database.db";
            String sql = "INSERT INTO users(name, description) VALUES(?, ?)";

            try (Connection conn = DriverManager.getConnection(url);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, name);
                pstmt.setString(2, description);
                pstmt.executeUpdate();

            } catch (SQLException e) {
                System.err.println("Database error: " + e.getMessage());
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
        String sql = "CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, description TEXT)";

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Database initialization error: " + e.getMessage());
        }
    }
}
