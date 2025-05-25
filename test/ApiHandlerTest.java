package test;

import org.junit.Before;
import org.junit.Test;
import org.junit.After;
import static org.junit.Assert.*;
import src.*;
import java.io.*;
import java.net.URI;
import java.util.List;
import java.util.Map;
import com.sun.net.httpserver.HttpExchange;

/**
 * APIハンドラーのテストクラス
 * 主にBaseApiHandlerのユーティリティメソッドをテスト
 */
public class ApiHandlerTest {
    private TestApiHandler handler;
    private DatabaseManager dbManager;
    private static final String TEST_DB_PATH = "database/test_database.db";

    /**
     * テスト用のAPIハンドラー実装
     */
    private static class TestApiHandler extends BaseApiHandler {
        @Override
        protected void handleRequest(HttpExchange exchange, String method) throws IOException {
            // テスト用の空実装
        }

        // テスト用にprotectedメソッドを公開
        public String testExtractJsonField(String json, String field) {
            return extractJsonField(json, field);
        }

        public String testListToJson(List<? extends Map<String, ?>> list, String arrayName) {
            return listToJson(list, arrayName);
        }

        public String testMapToJson(Map<String, ?> map) {
            return mapToJson(map);
        }

        public String testValueToJson(Object value) {
            return valueToJson(value);
        }
    }

    @Before
    public void setUp() {
        // テスト用データベースファイルを削除
        File testDb = new File(TEST_DB_PATH);
        if (testDb.exists()) {
            testDb.delete();
        }
        
        dbManager = DatabaseManager.getInstance();
        dbManager.initializeDatabase();
        handler = new TestApiHandler();
    }

    @After
    public void tearDown() {
        File testDb = new File(TEST_DB_PATH);
        if (testDb.exists()) {
            testDb.delete();
        }
    }

    @Test
    public void testExtractJsonFieldString() {
        String json = "{\"username\":\"testuser\",\"id\":123}";
        
        String username = handler.testExtractJsonField(json, "username");
        assertEquals("文字列フィールドが正しく抽出される", "testuser", username);
    }

    @Test
    public void testExtractJsonFieldNumber() {
        String json = "{\"username\":\"testuser\",\"id\":123,\"count\":456}";
        
        String id = handler.testExtractJsonField(json, "id");
        assertEquals("数値フィールドが正しく抽出される", "123", id);
        
        String count = handler.testExtractJsonField(json, "count");
        assertEquals("数値フィールドが正しく抽出される", "456", count);
    }

    @Test
    public void testExtractJsonFieldNotFound() {
        String json = "{\"username\":\"testuser\",\"id\":123}";
        
        String nonExistent = handler.testExtractJsonField(json, "nonexistent");
        assertEquals("存在しないフィールドは空文字", "", nonExistent);
    }

    @Test
    public void testValueToJsonString() {
        String result = handler.testValueToJson("test string");
        assertEquals("文字列が正しくJSON化される", "\"test string\"", result);
    }

    @Test
    public void testValueToJsonNumber() {
        String result1 = handler.testValueToJson(123);
        assertEquals("整数が正しくJSON化される", "123", result1);
        
        String result2 = handler.testValueToJson(123.45);
        assertEquals("小数が正しくJSON化される", "123.45", result2);
    }

    @Test
    public void testValueToJsonBoolean() {
        String result1 = handler.testValueToJson(true);
        assertEquals("trueが正しくJSON化される", "true", result1);
        
        String result2 = handler.testValueToJson(false);
        assertEquals("falseが正しくJSON化される", "false", result2);
    }

    @Test
    public void testValueToJsonNull() {
        String result = handler.testValueToJson(null);
        assertEquals("nullが正しくJSON化される", "null", result);
    }

    @Test
    public void testValueToJsonWithEscaping() {
        String result = handler.testValueToJson("test \"quoted\" string");
        assertEquals("エスケープが正しく処理される", "\"test \\\"quoted\\\" string\"", result);
    }

    @Test
    public void testMapToJson() {
        Map<String, Object> map = Map.of(
            "id", 123,
            "name", "test",
            "active", true,
            "score", 95.5
        );
        
        String result = handler.testMapToJson(map);
        
        // JSONの各フィールドが含まれているかチェック
        assertTrue("idフィールドが含まれる", result.contains("\"id\":123"));
        assertTrue("nameフィールドが含まれる", result.contains("\"name\":\"test\""));
        assertTrue("activeフィールドが含まれる", result.contains("\"active\":true"));
        assertTrue("scoreフィールドが含まれる", result.contains("\"score\":95.5"));
        assertTrue("JSONオブジェクト形式", result.startsWith("{") && result.endsWith("}"));
    }

    @Test
    public void testListToJson() {
        List<Map<String, Object>> list = List.of(
            Map.of("id", 1, "name", "user1"),
            Map.of("id", 2, "name", "user2")
        );
        
        String result = handler.testListToJson(list, "users");
        
        assertTrue("配列名が含まれる", result.contains("\"users\""));
        assertTrue("最初の要素が含まれる", result.contains("\"name\":\"user1\""));
        assertTrue("2番目の要素が含まれる", result.contains("\"name\":\"user2\""));
        assertTrue("JSON配列形式", result.contains("[") && result.contains("]"));
    }

    @Test
    public void testEmptyListToJson() {
        List<Map<String, Object>> emptyList = List.of();
        
        String result = handler.testListToJson(emptyList, "items");
        
        assertEquals("空配列が正しく生成される", "{\"items\": []}", result);
    }

    @Test
    public void testRealUserHandler() {
        // 実際のUserHandlerの動作テスト
        dbManager.createUser("testuser1");
        dbManager.createUser("testuser2");
        
        List<Map<String, Object>> users = dbManager.getAllUsers();
        String json = handler.testListToJson(users, "users");
        
        assertTrue("ユーザーリストがJSON化される", json.contains("\"users\""));
        assertTrue("ユーザー1が含まれる", json.contains("testuser1"));
        assertTrue("ユーザー2が含まれる", json.contains("testuser2"));
    }

    @Test
    public void testRealRepositoryHandler() {
        // 実際のRepositoryHandlerの動作テスト
        dbManager.createUser("owner");
        dbManager.createRepository("repo1", 1);
        dbManager.createRepository("repo2", 1);
        
        List<Map<String, Object>> repos = dbManager.getRepositories(1);
        String json = handler.testListToJson(repos, "repositories");
        
        assertTrue("リポジトリリストがJSON化される", json.contains("\"repositories\""));
        assertTrue("リポジトリ1が含まれる", json.contains("repo1"));
        assertTrue("リポジトリ2が含まれる", json.contains("repo2"));
        assertTrue("所有者IDが含まれる", json.contains("\"owner_id\":1"));
    }
}