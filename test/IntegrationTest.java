package test;

import org.junit.Before;
import org.junit.Test;
import org.junit.After;
import static org.junit.Assert.*;
import src.*;
import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * 統合テストクラス
 * 複数のコンポーネントが連携して動作することを確認
 */
public class IntegrationTest {
    private DatabaseManager dbManager;
    private CommitManager commitManager;
    private MergeManager mergeManager;
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
    }

    @After
    public void tearDown() {
        File testDb = new File(TEST_DB_PATH);
        if (testDb.exists()) {
            testDb.delete();
        }
    }

    @Test
    public void testCompleteGitWorkflow() {
        // 完全なGitワークフローをテスト
        
        // 1. ユーザー作成
        assertTrue("ユーザー作成成功", dbManager.createUser("developer"));
        
        // 2. リポジトリ作成
        assertTrue("リポジトリ作成成功", dbManager.createRepository("myproject", 1));
        
        // 3. メインブランチ作成
        assertTrue("メインブランチ作成成功", dbManager.createBranch("main", 1));
        
        // 4. 初期コミット
        CommitManager.CreateCommitCommand initialCommit = commitManager.new CreateCommitCommand(
            1, "Initial commit", 1, "# My Project\nInitial version");
        assertTrue("初期コミット成功", commitManager.executeCommitCommand(initialCommit));
        
        // 5. フィーチャーブランチ作成
        assertTrue("フィーチャーブランチ作成成功", dbManager.createBranch("feature", 1));
        
        // 6. フィーチャーブランチでの開発
        CommitManager.CreateCommitCommand featureCommit1 = commitManager.new CreateCommitCommand(
            2, "Add feature A", 1, "# My Project\nInitial version\n\n## Feature A\nNew feature");
        assertTrue("フィーチャーコミット1成功", commitManager.executeCommitCommand(featureCommit1));
        
        CommitManager.CreateCommitCommand featureCommit2 = commitManager.new CreateCommitCommand(
            2, "Improve feature A", 1, "# My Project\nInitial version\n\n## Feature A\nImproved feature");
        assertTrue("フィーチャーコミット2成功", commitManager.executeCommitCommand(featureCommit2));
        
        // 7. メインブランチでの並行開発
        CommitManager.CreateCommitCommand mainCommit = commitManager.new CreateCommitCommand(
            1, "Update README", 1, "# My Project\nUpdated initial version\n\nThis is the main branch.");
        assertTrue("メインコミット成功", commitManager.executeCommitCommand(mainCommit));
        
        // 8. マージ試行（コンフリクト発生）
        MergeResult conflictResult = mergeManager.performStrictMerge(2, 1);
        assertTrue("コンフリクトが発生", conflictResult instanceof MergeResult.Conflict);
        
        // 9. 強制マージで解決
        String mergedContent = "# My Project\nMerged version\n\n## Feature A\nImproved feature\n\nThis includes both changes.";
        MergeResult mergeResult = mergeManager.performForceMerge(2, 1, mergedContent);
        assertTrue("強制マージ成功", mergeResult instanceof MergeResult.Success);
        
        // 10. 結果確認
        List<Map<String, Object>> commits = commitManager.getCommits(1);
        assertTrue("コミット履歴が存在", commits.size() >= 4);
        
        // マージコミットが作成されていることを確認
        boolean mergeCommitFound = false;
        for (Map<String, Object> commit : commits) {
            if ("Merge commit".equals(commit.get("message"))) {
                mergeCommitFound = true;
                assertNotNull("親コミット1が設定", commit.get("parent_commit_id"));
                assertNotNull("親コミット2が設定", commit.get("parent_commit_id_2"));
                break;
            }
        }
        assertTrue("マージコミットが作成されている", mergeCommitFound);
        
        // 11. 最終的なファイル内容確認
        List<Map<String, Object>> files = commitManager.getFilesByBranch(1);
        assertFalse("ファイルが存在", files.isEmpty());
        assertEquals("マージされた内容が保存されている", mergedContent, files.get(0).get("text"));
    }

    @Test
    public void testMultipleBranchWorkflow() {
        // 複数ブランチでの並行開発ワークフロー
        
        // 準備
        dbManager.createUser("dev1");
        dbManager.createUser("dev2");
        dbManager.createRepository("project", 1);
        dbManager.createBranch("main", 1);
        dbManager.createBranch("feature1", 1);
        dbManager.createBranch("feature2", 1);
        
        // 各ブランチで開発
        CommitManager.CreateCommitCommand mainCommit = commitManager.new CreateCommitCommand(
            1, "Main work", 1, "main content");
        commitManager.executeCommitCommand(mainCommit);
        
        CommitManager.CreateCommitCommand feature1Commit = commitManager.new CreateCommitCommand(
            2, "Feature 1 work", 1, "feature1 content");
        commitManager.executeCommitCommand(feature1Commit);
        
        CommitManager.CreateCommitCommand feature2Commit = commitManager.new CreateCommitCommand(
            3, "Feature 2 work", 2, "feature2 content");
        commitManager.executeCommitCommand(feature2Commit);
        
        // 各ブランチの内容確認
        List<Map<String, Object>> mainFiles = commitManager.getFilesByBranch(1);
        List<Map<String, Object>> feature1Files = commitManager.getFilesByBranch(2);
        List<Map<String, Object>> feature2Files = commitManager.getFilesByBranch(3);
        
        assertEquals("メインブランチの内容", "main content", mainFiles.get(0).get("text"));
        assertEquals("フィーチャー1の内容", "feature1 content", feature1Files.get(0).get("text"));
        assertEquals("フィーチャー2の内容", "feature2 content", feature2Files.get(0).get("text"));
        
        // 全リポジトリのコミット確認
        List<Map<String, Object>> allCommits = commitManager.getCommits(1);
        assertEquals("3つのコミットが存在", 3, allCommits.size());
        
        // 各開発者のコミット確認
        int dev1Commits = 0, dev2Commits = 0;
        for (Map<String, Object> commit : allCommits) {
            int authorId = (Integer) commit.get("author_id");
            if (authorId == 1) dev1Commits++;
            if (authorId == 2) dev2Commits++;
        }
        assertEquals("開発者1のコミット数", 2, dev1Commits);
        assertEquals("開発者2のコミット数", 1, dev2Commits);
    }

    @Test
    public void testDatabaseConsistency() {
        // データベースの整合性をテスト
        
        // データ作成
        dbManager.createUser("user1");
        dbManager.createUser("user2");
        dbManager.createRepository("repo1", 1);
        dbManager.createRepository("repo2", 2);
        dbManager.createBranch("main1", 1);
        dbManager.createBranch("main2", 2);
        
        // コミット作成
        CommitManager.CreateCommitCommand commit1 = commitManager.new CreateCommitCommand(
            1, "Commit 1", 1, "content 1");
        CommitManager.CreateCommitCommand commit2 = commitManager.new CreateCommitCommand(
            2, "Commit 2", 2, "content 2");
        
        commitManager.executeCommitCommand(commit1);
        commitManager.executeCommitCommand(commit2);
        
        // データ整合性確認
        List<Map<String, Object>> users = dbManager.getAllUsers();
        List<Map<String, Object>> repos = dbManager.getRepositories(null);
        List<Map<String, Object>> branches = dbManager.getBranches(null);
        List<Map<String, Object>> commits = commitManager.getCommits(null);
        
        assertEquals("ユーザー数が正しい", 2, users.size());
        assertEquals("リポジトリ数が正しい", 2, repos.size());
        assertEquals("ブランチ数が正しい", 2, branches.size());
        assertEquals("コミット数が正しい", 2, commits.size());
        
        // 外部キー関係の確認
        for (Map<String, Object> repo : repos) {
            int ownerId = (Integer) repo.get("owner_id");
            assertTrue("所有者が存在する", ownerId == 1 || ownerId == 2);
        }
        
        for (Map<String, Object> branch : branches) {
            int repoId = (Integer) branch.get("repository_id");
            assertTrue("リポジトリが存在する", repoId == 1 || repoId == 2);
        }
        
        for (Map<String, Object> commit : commits) {
            int authorId = (Integer) commit.get("author_id");
            int repoId = (Integer) commit.get("repository_id");
            assertTrue("作成者が存在する", authorId == 1 || authorId == 2);
            assertTrue("リポジトリが存在する", repoId == 1 || repoId == 2);
        }
    }

    @Test
    public void testErrorHandling() {
        // エラーハンドリングのテスト
        
        // 存在しないユーザーでリポジトリ作成を試行
        boolean result1 = dbManager.createRepository("test", 999);
        // SQLiteの制約により失敗するが、falseが返される
        
        // 存在しないリポジトリでブランチ作成を試行
        boolean result2 = dbManager.createBranch("test", 999);
        // SQLiteの制約により失敗するが、falseが返される
        
        // 存在しないブランチでコミット作成を試行
        CommitManager.CreateCommitCommand invalidCommit = commitManager.new CreateCommitCommand(
            999, "Invalid commit", 1, "content");
        boolean result3 = commitManager.executeCommitCommand(invalidCommit);
        assertFalse("存在しないブランチでのコミットは失敗", result3);
        
        // 存在しないブランチでのマージ試行
        MergeResult result4 = mergeManager.performStrictMerge(999, 1);
        assertTrue("存在しないブランチでのマージはコンフリクト", result4 instanceof MergeResult.Conflict);
    }
}