package test;

import org.junit.Before;
import org.junit.Test;
import org.junit.After;
import static org.junit.Assert.*;
import src.DatabaseManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.io.File;
import java.lang.reflect.Field;

/**
 * DatabaseManagerのテストクラス
 */
public class DatabaseManagerTest {
    private DatabaseManager dbManager;
    private static final String TEST_DB_PATH = "database/test_database.db";

    @Before
    public void setUp() {
        // テスト用データベースファイルを削除（クリーンな状態でテスト開始）
        File testDb = new File(TEST_DB_PATH);
        if (testDb.exists()) {
            testDb.delete();
        }

        // データベースディレクトリが存在しない場合は作成
        File dbDir = new File("database");
        if (!dbDir.exists()) {
            dbDir.mkdirs();
        }

        // 既存のインスタンスをクリア
        try {
            Field instance = DatabaseManager.class.getDeclaredField("instance");
            instance.setAccessible(true);
            instance.set(null, null);
        } catch (Exception e) {
            System.err.println("Failed to reset singleton instance: " + e.getMessage());
        }

        dbManager = DatabaseManager.getInstance();
        dbManager.initializeDatabase();
    }

    @After
    public void tearDown() {
        // テスト後のクリーンアップ
        File testDb = new File(TEST_DB_PATH);
        if (testDb.exists()) {
            testDb.delete();
        }

        // データベース接続を閉じる
        try {
            dbManager.closeConnection();
        } catch (Exception e) {
            // エラーは無視
        }
    }

    @Test
    public void testSingletonInstance() {
        DatabaseManager instance1 = DatabaseManager.getInstance();
        DatabaseManager instance2 = DatabaseManager.getInstance();

        assertSame("Singletonパターンが正しく実装されている", instance1, instance2);
    }

    @Test
    public void testDatabaseConnection() {
        try (Connection conn = dbManager.getConnection()) {
            assertNotNull("データベース接続が取得できる", conn);
            assertFalse("接続が有効である", conn.isClosed());
        } catch (SQLException e) {
            fail("データベース接続でエラーが発生: " + e.getMessage());
        }
    }

    @Test
    public void testCreateUser() {
        // ユーザー作成テスト
        boolean result = dbManager.createUser("testuser1");
        assertTrue("ユーザーが正常に作成される", result);

        // 作成されたユーザーを確認
        List<Map<String, Object>> users = dbManager.getAllUsers();
        assertFalse("ユーザーリストが空でない", users.isEmpty());

        Map<String, Object> user = users.get(0);
        assertEquals("ユーザー名が正しく保存される", "testuser1", user.get("username"));
    }

    @Test
    public void testCreateRepository() {
        // まずユーザーを作成
        assertTrue("ユーザー作成に成功", dbManager.createUser("owner1"));

        // リポジトリ作成テスト
        boolean result = dbManager.createRepository("testrepo1", 1);
        assertTrue("リポジトリが正常に作成される", result);

        // 作成されたリポジトリを確認
        List<Map<String, Object>> repos = dbManager.getRepositories(1);
        assertFalse("リポジトリリストが空でない", repos.isEmpty());

        Map<String, Object> repo = repos.get(0);
        assertEquals("リポジトリ名が正しく保存される", "testrepo1", repo.get("name"));
        assertEquals("所有者IDが正しく保存される", 1, repo.get("owner_id"));
    }

    @Test
    public void testCreateBranch() {
        // 前提条件を設定
        dbManager.createUser("owner2");
        dbManager.createRepository("testrepo2", 1);

        // ブランチ作成テスト
        boolean result = dbManager.createBranch("main", 1);
        assertTrue("ブランチが正常に作成される", result);

        // 作成されたブランチを確認
        List<Map<String, Object>> branches = dbManager.getBranches(1);
        assertFalse("ブランチリストが空でない", branches.isEmpty());

        Map<String, Object> branch = branches.get(0);
        assertEquals("ブランチ名が正しく保存される", "main", branch.get("name"));
        assertEquals("リポジトリIDが正しく保存される", 1, branch.get("repository_id"));
    }

    @Test
    public void testGetAllUsers() {
        // 複数ユーザーを作成
        dbManager.createUser("user1");
        dbManager.createUser("user2");
        dbManager.createUser("user3");

        List<Map<String, Object>> users = dbManager.getAllUsers();
        assertEquals("作成したユーザー数と一致する", 3, users.size());

        // IDでソートされていることを確認
        for (int i = 0; i < users.size() - 1; i++) {
            int currentId = (Integer) users.get(i).get("id");
            int nextId = (Integer) users.get(i + 1).get("id");
            assertTrue("IDの昇順でソートされている", currentId < nextId);
        }
    }

    @Test
    public void testGetRepositoriesWithFilter() {
        // 複数ユーザーとリポジトリを作成
        dbManager.createUser("owner3");
        dbManager.createUser("owner4");
        dbManager.createRepository("repo1", 1);
        dbManager.createRepository("repo2", 1);
        dbManager.createRepository("repo3", 2);

        // 特定ユーザーのリポジトリのみ取得
        List<Map<String, Object>> user1Repos = dbManager.getRepositories(1);
        assertEquals("ユーザー1のリポジトリ数が正しい", 2, user1Repos.size());

        List<Map<String, Object>> user2Repos = dbManager.getRepositories(2);
        assertEquals("ユーザー2のリポジトリ数が正しい", 1, user2Repos.size());

        // 全リポジトリ取得
        List<Map<String, Object>> allRepos = dbManager.getRepositories(null);
        assertEquals("全リポジトリ数が正しい", 3, allRepos.size());
    }
}