
package src;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.util.*;

/**
 * API ハンドラー基底クラス（Template Method パターン使用）
 */
public abstract class BaseApiHandler implements HttpHandler {
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
        } catch (IOException e) {
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