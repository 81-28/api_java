package test;

import org.junit.Before;
import org.junit.Test;
import org.junit.After;
import static org.junit.Assert.*;
import src.CommitManager;
import src.DatabaseManager;
import java.util.List;
import java.util.Map;
import java.io.File;

/**
 * CommitManagerのテストクラス
 */
public class CommitManagerTest {
    private CommitManager commitManager;
    private DatabaseManager dbManager;
    private static final String TEST_DB_PATH = "database/test_database.db";

    @Before
    public void setUp() {
        // テスト用データベースファイルを削除（クリーンな状態でテスト開始）
        File testDb = new File(TEST_DB_PATH);
        if (testDb.exists()) {
            testDb.delete();
        }

        dbManager = DatabaseManager.getInstance();
        dbManager.initializeDatabase();
        commitManager = new CommitManager();

        // テスト用データを準備
        setupTestData();
    }

    @After
    public void tearDown() {
        // テスト後のクリーンアップ
        File testDb = new File(TEST_DB_PATH);
        if (testDb.exists()) {
            testDb.delete();
        }
    }

    private void setupTestData() {
        // テスト用のユーザー、リポジトリ、ブランチを作成
        dbManager.createUser("testuser");
        dbManager.createRepository("testrepo", 1);
        dbManager.createBranch("main", 1);
    }

    @Test
    public void testCreateCommitCommand() {
        // コミット作成コマンドをテスト
        CommitManager.CreateCommitCommand command = commitManager.new CreateCommitCommand(
                1, "Initial commit", 1, "Hello World");

        boolean result = commitManager.executeCommitCommand(command);
        assertTrue("コミットコマンドが正常に実行される", result);

        // 作成されたコミットを確認
        List<Map<String, Object>> commits = commitManager.getCommits(1);
        assertFalse("コミットリストが空でない", commits.isEmpty());

        Map<String, Object> commit = commits.get(0);
        assertEquals("コミットメッセージが正しく保存される", "Initial commit", commit.get("message"));
        assertEquals("作成者IDが正しく保存される", 1, commit.get("author_id"));
    }

    @Test
    public void testCreateMergeCommitCommand() {
        // まず2つの通常コミットを作成
        CommitManager.CreateCommitCommand commit1 = commitManager.new CreateCommitCommand(
                1, "First commit", 1, "Content 1");
        commitManager.executeCommitCommand(commit1);

        // 2番目のブランチを作成してコミット
        dbManager.createBranch("feature", 1);
        CommitManager.CreateCommitCommand commit2 = commitManager.new CreateCommitCommand(
                2, "Second commit", 1, "Content 2");
        commitManager.executeCommitCommand(commit2);

        // マージコミット作成
        CommitManager.CreateMergeCommitCommand mergeCommand = commitManager.new CreateMergeCommitCommand(
                1, 1, 2, "Merged content");

        boolean result = commitManager.executeCommitCommand(mergeCommand);
        assertTrue("マージコミットが正常に作成される", result);

        // マージコミットを確認
        List<Map<String, Object>> commits = commitManager.getCommits(1);

        // マージコミットを探す
        Map<String, Object> mergeCommit = null;
        for (Map<String, Object> commit : commits) {
            if ("Merge commit".equals(commit.get("message"))) {
                mergeCommit = commit;
                break;
            }
        }

        assertNotNull("マージコミットが作成されている", mergeCommit);
        assertEquals("親コミット1が正しく設定される", 1, mergeCommit.get("parent_commit_id"));
        assertEquals("親コミット2が正しく設定される", 2, mergeCommit.get("parent_commit_id_2"));
    }

    @Test
    public void testGetCommits() {
        // 複数のコミットを作成
        CommitManager.CreateCommitCommand commit1 = commitManager.new CreateCommitCommand(
                1, "First commit", 1, "Content 1");
        CommitManager.CreateCommitCommand commit2 = commitManager.new CreateCommitCommand(
                1, "Second commit", 1, "Content 2");
        CommitManager.CreateCommitCommand commit3 = commitManager.new CreateCommitCommand(
                1, "Third commit", 1, "Content 3");

        commitManager.executeCommitCommand(commit1);
        commitManager.executeCommitCommand(commit2);
        commitManager.executeCommitCommand(commit3);

        // コミット一覧取得
        List<Map<String, Object>> commits = commitManager.getCommits(1);
        assertEquals("作成したコミット数と一致する", 3, commits.size());

        // 降順でソートされていることを確認（最新が最初）
        assertEquals("最新のコミットが最初", "Third commit", commits.get(0).get("message"));
        assertEquals("最初のコミットが最後", "First commit", commits.get(2).get("message"));
    }

    @Test
    public void testGetFilesByBranch() {
        // コミットを作成
        CommitManager.CreateCommitCommand command = commitManager.new CreateCommitCommand(
                1, "Test commit", 1, "Test content");

        boolean result = commitManager.executeCommitCommand(command);
        assertTrue("コミットが正常に作成される", result);

        // ブランチのファイル内容取得
        List<Map<String, Object>> files = commitManager.getFilesByBranch(1);
        assertFalse("ファイルリストが空でない", files.isEmpty());

        Map<String, Object> file = files.get(0);
        assertEquals("ファイル内容が正しく取得される", "Test content", file.get("text"));
        assertNotNull("コミットIDが設定されている", file.get("commit_id"));
    }

    @Test
    public void testGetFilesByBranchWithoutCommit() {
        // コミットが存在しないブランチのファイル取得
        List<Map<String, Object>> files = commitManager.getFilesByBranch(1);
        assertTrue("コミットが存在しない場合は空リスト", files.isEmpty());
    }

    @Test
    public void testMultipleCommitsOnSameBranch() {
        // 同じブランチに複数のコミットを作成
        CommitManager.CreateCommitCommand commit1 = commitManager.new CreateCommitCommand(
                1, "First commit", 1, "Version 1");
        CommitManager.CreateCommitCommand commit2 = commitManager.new CreateCommitCommand(
                1, "Second commit", 1, "Version 2");

        commitManager.executeCommitCommand(commit1);
        commitManager.executeCommitCommand(commit2);

        // 最新のファイル内容を取得
        List<Map<String, Object>> files = commitManager.getFilesByBranch(1);
        assertFalse("ファイルリストが空でない", files.isEmpty());

        Map<String, Object> file = files.get(0);
        assertEquals("最新のコミットの内容が取得される", "Version 2", file.get("text"));
    }
}