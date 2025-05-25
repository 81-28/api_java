package test;

import org.junit.Before;
import org.junit.Test;
import org.junit.After;
import static org.junit.Assert.*;
import src.MergeManager;
import src.MergeResult;
import src.DatabaseManager;
import src.CommitManager;
import java.io.File;

/**
 * MergeManagerのテストクラス
 */
public class MergeManagerTest {
    private MergeManager mergeManager;
    private DatabaseManager dbManager;
    private CommitManager commitManager;
    private static final String TEST_DB_PATH = "database/test_database.db";

    @Before
    public void setUp() {
        // テスト用データベースファイルを削除
        File testDb = new File(TEST_DB_PATH);
        if (testDb.exists()) {
            testDb.delete();
        }
        
        dbManager = DatabaseManager.getInstance();
        dbManager.initializeDatabase();
        commitManager = new CommitManager();
        mergeManager = new MergeManager();
        
        // テスト用データを準備
        setupTestData();
    }

    @After
    public void tearDown() {
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
        dbManager.createBranch("feature", 1);
    }

    @Test
    public void testStrictMergeSuccess() {
        // 両ブランチに同じ内容をコミット
        CommitManager.CreateCommitCommand commit1 = commitManager.new CreateCommitCommand(
            1, "Main commit", 1, "Same content");
        CommitManager.CreateCommitCommand commit2 = commitManager.new CreateCommitCommand(
            2, "Feature commit", 1, "Same content");
        
        commitManager.executeCommitCommand(commit1);
        commitManager.executeCommitCommand(commit2);

        // 厳密マージを実行
        MergeResult result = mergeManager.performStrictMerge(1, 2);
        
        // 結果確認
        assertTrue("マージが成功する", result instanceof MergeResult.Success);
        MergeResult.Success success = (MergeResult.Success) result;
        assertEquals("成功メッセージが正しい", "マージが完了しました", success.message());
    }

    @Test
    public void testStrictMergeConflict() {
        // 両ブランチに異なる内容をコミット
        CommitManager.CreateCommitCommand commit1 = commitManager.new CreateCommitCommand(
            1, "Main commit", 1, "Main content");
        CommitManager.CreateCommitCommand commit2 = commitManager.new CreateCommitCommand(
            2, "Feature commit", 1, "Feature content");
        
        commitManager.executeCommitCommand(commit1);
        commitManager.executeCommitCommand(commit2);

        // 厳密マージを実行
        MergeResult result = mergeManager.performStrictMerge(1, 2);
        
        // 結果確認
        assertTrue("コンフリクトが発生する", result instanceof MergeResult.Conflict);
        MergeResult.Conflict conflict = (MergeResult.Conflict) result;
        assertEquals("ブランチ1のIDが正しい", 1, conflict.branchId1());
        assertEquals("ブランチ1の内容が正しい", "Main content", conflict.content1());
        assertEquals("ブランチ2のIDが正しい", 2, conflict.branchId2());
        assertEquals("ブランチ2の内容が正しい", "Feature content", conflict.content2());
    }

    @Test
    public void testForceMergeSuccess() {
        // 両ブランチに異なる内容をコミット
        CommitManager.CreateCommitCommand commit1 = commitManager.new CreateCommitCommand(
            1, "Main commit", 1, "Main content");
        CommitManager.CreateCommitCommand commit2 = commitManager.new CreateCommitCommand(
            2, "Feature commit", 1, "Feature content");
        
        commitManager.executeCommitCommand(commit1);
        commitManager.executeCommitCommand(commit2);

        // 強制マージを実行
        MergeResult result = mergeManager.performForceMerge(1, 2, "Merged content");
        
        // 結果確認
        assertTrue("強制マージが成功する", result instanceof MergeResult.Success);
        MergeResult.Success success = (MergeResult.Success) result;
        assertEquals("成功メッセージが正しい", "強制マージが完了しました", success.message());
    }

    @Test
    public void testMergeWithEmptyBranch() {
        // 片方のブランチのみにコミット
        CommitManager.CreateCommitCommand commit1 = commitManager.new CreateCommitCommand(
            1, "Main commit", 1, "Main content");
        
        commitManager.executeCommitCommand(commit1);

        // 空のブランチとのマージを試行
        MergeResult result = mergeManager.performStrictMerge(1, 2);
        
        // 結果確認（空ブランチとのマージはコンフリクトになる）
        assertTrue("空ブランチとのマージはコンフリクト", result instanceof MergeResult.Conflict);
    }

    @Test
    public void testMergeNonExistentBranch() {
        // 存在しないブランチとのマージを試行
        MergeResult result = mergeManager.performStrictMerge(999, 1);
        
        // 結果確認
        assertTrue("存在しないブランチとのマージはコンフリクト", result instanceof MergeResult.Conflict);
    }

    @Test
    public void testMergeStrategyPattern() {
        // Strategy パターンの動作確認
        // 両ブランチに異なる内容をコミット
        CommitManager.CreateCommitCommand commit1 = commitManager.new CreateCommitCommand(
            1, "Main commit", 1, "Main content");
        CommitManager.CreateCommitCommand commit2 = commitManager.new CreateCommitCommand(
            2, "Feature commit", 1, "Feature content");
        
        commitManager.executeCommitCommand(commit1);
        commitManager.executeCommitCommand(commit2);

        // 厳密マージ戦略
        MergeManager.MergeStrategy strictStrategy = mergeManager.new StrictMergeStrategy();
        MergeResult strictResult = mergeManager.executeMerge(strictStrategy, 1, 2);
        assertTrue("厳密マージでコンフリクト", strictResult instanceof MergeResult.Conflict);

        // 強制マージ戦略
        MergeManager.MergeStrategy forceStrategy = mergeManager.new ForceMergeStrategy("Force merged");
        MergeResult forceResult = mergeManager.executeMerge(forceStrategy, 1, 2);
        assertTrue("強制マージで成功", forceResult instanceof MergeResult.Success);
    }
}