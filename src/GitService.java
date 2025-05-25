package src;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Observer Pattern - Git操作の変更を監視するサービスクラス
 * ビジネスロジックを分離し、観察者パターンを実装
 */
public class GitService {

    private final GitApiServer.DatabaseManager dbManager;
    private final List<GitEventListener> listeners;

    public GitService() {
        this.dbManager = GitApiServer.DatabaseManager.getInstance();
        this.listeners = new ArrayList<>();
    }

    /**
     * イベントリスナーを追加
     * 
     * @param listener GitEventListener実装
     */
    public void addListener(GitEventListener listener) {
        listeners.add(listener);
    }

    /**
     * イベントリスナーを削除
     * 
     * @param listener GitEventListener実装
     */
    public void removeListener(GitEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * すべてのリスナーにイベントを通知
     * 
     * @param event Gitイベント
     */
    private void notifyListeners(GitEvent event) {
        for (GitEventListener listener : listeners) {
            listener.onGitEvent(event);
        }
    }

    /**
     * リポジトリの統計情報を取得
     * 
     * @param repositoryId リポジトリID
     * @return リポジトリ統計
     */
    public RepositoryStats getRepositoryStats(int repositoryId) {
        try (Connection conn = dbManager.getConnection()) {
            int commitCount = getCommitCount(conn, repositoryId);
            int branchCount = getBranchCount(conn, repositoryId);
            int fileCount = getFileCount(conn, repositoryId);
            String lastCommitDate = getLastCommitDate(conn, repositoryId);

            return new RepositoryStats(repositoryId, commitCount, branchCount, fileCount, lastCommitDate);

        } catch (SQLException e) {
            System.err.println("Failed to get repository stats: " + e.getMessage());
            return new RepositoryStats(repositoryId, 0, 0, 0, null);
        }
    }

    /**
     * コミット数を取得
     * 
     * @param conn         データベース接続
     * @param repositoryId リポジトリID
     * @return コミット数
     * @throws SQLException データベースエラー
     */
    private int getCommitCount(Connection conn, int repositoryId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM commits WHERE repository_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, repositoryId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * ブランチ数を取得
     * 
     * @param conn         データベース接続
     * @param repositoryId リポジトリID
     * @return ブランチ数
     * @throws SQLException データベースエラー
     */
    private int getBranchCount(Connection conn, int repositoryId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM branches WHERE repository_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, repositoryId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * ファイル数を取得
     * 
     * @param conn         データベース接続
     * @param repositoryId リポジトリID
     * @return ファイル数
     * @throws SQLException データベースエラー
     */
    private int getFileCount(Connection conn, int repositoryId) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT f.filename) FROM files f JOIN commits c ON f.commit_id = c.id WHERE c.repository_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, repositoryId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * 最新コミット日時を取得
     * 
     * @param conn         データベース接続
     * @param repositoryId リポジトリID
     * @return 最新コミット日時
     * @throws SQLException データベースエラー
     */
    private String getLastCommitDate(Connection conn, int repositoryId) throws SQLException {
        String sql = "SELECT created_at FROM commits WHERE repository_id = ? ORDER BY created_at DESC LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, repositoryId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString("created_at") : null;
            }
        }
    }

    /**
     * ブランチ間の差分を計算
     * 
     * @param branchId1 ブランチ1ID
     * @param branchId2 ブランチ2ID
     * @return ブランチ差分情報
     */
    public Optional<BranchDiff> calculateBranchDiff(int branchId1, int branchId2) {
        try (Connection conn = dbManager.getConnection()) {
            // 各ブランチのHEADコミット取得
            Integer head1 = getBranchHead(conn, branchId1);
            Integer head2 = getBranchHead(conn, branchId2);

            if (head1 == null || head2 == null) {
                return Optional.empty();
            }

            String content1 = getFileContent(conn, head1);
            String content2 = getFileContent(conn, head2);

            int additions = calculateAdditions(content1, content2);
            int deletions = calculateDeletions(content1, content2);
            boolean hasConflicts = !content1.equals(content2);

            return Optional.of(new BranchDiff(branchId1, branchId2, additions, deletions, hasConflicts));

        } catch (SQLException e) {
            System.err.println("Failed to calculate branch diff: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * ブランチのHEADコミットIDを取得
     * 
     * @param conn     データベース接続
     * @param branchId ブランチID
     * @return HEADコミットID
     * @throws SQLException データベースエラー
     */
    private Integer getBranchHead(Connection conn, int branchId) throws SQLException {
        String sql = "SELECT head_commit_id FROM branches WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, branchId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int headId = rs.getInt("head_commit_id");
                    return rs.wasNull() ? null : headId;
                }
                return null;
            }
        }
    }

    /**
     * ファイル内容を取得
     * 
     * @param conn     データベース接続
     * @param commitId コミットID
     * @return ファイル内容
     * @throws SQLException データベースエラー
     */
    private String getFileContent(Connection conn, int commitId) throws SQLException {
        String sql = "SELECT content FROM files WHERE commit_id = ? LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, commitId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? (rs.getString("content") != null ? rs.getString("content") : "") : "";
            }
        }
    }

    /**
     * 追加行数を計算（簡易実装）
     * 
     * @param content1 内容1
     * @param content2 内容2
     * @return 追加行数
     */
    private int calculateAdditions(String content1, String content2) {
        String[] lines1 = content1.split("\n");
        String[] lines2 = content2.split("\n");
        return Math.max(0, lines2.length - lines1.length);
    }

    /**
     * 削除行数を計算（簡易実装）
     * 
     * @param content1 内容1
     * @param content2 内容2
     * @return 削除行数
     */
    private int calculateDeletions(String content1, String content2) {
        String[] lines1 = content1.split("\n");
        String[] lines2 = content2.split("\n");
        return Math.max(0, lines1.length - lines2.length);
    }

    /**
     * コミット作成後のイベント通知
     * 
     * @param repositoryId リポジトリID
     * @param commitId     コミットID
     * @param branchId     ブランチID
     */
    public void notifyCommitCreated(int repositoryId, int commitId, int branchId) {
        GitEvent event = new CommitCreatedEvent(repositoryId, commitId, branchId);
        notifyListeners(event);
    }

    /**
     * ブランチ作成後のイベント通知
     * 
     * @param repositoryId リポジトリID
     * @param branchId     ブランチID
     * @param branchName   ブランチ名
     */
    public void notifyBranchCreated(int repositoryId, int branchId, String branchName) {
        GitEvent event = new BranchCreatedEvent(repositoryId, branchId, branchName);
        notifyListeners(event);
    }

    /**
     * マージ完了後のイベント通知
     * 
     * @param repositoryId   リポジトリID
     * @param sourceBranchId ソースブランチID
     * @param targetBranchId ターゲットブランチID
     * @param mergeCommitId  マージコミットID
     */
    public void notifyMergeCompleted(int repositoryId, int sourceBranchId, int targetBranchId, int mergeCommitId) {
        GitEvent event = new MergeCompletedEvent(repositoryId, sourceBranchId, targetBranchId, mergeCommitId);
        notifyListeners(event);
    }
}

/**
 * Git操作イベントのリスナーインターフェース
 */
@FunctionalInterface
interface GitEventListener {
    /**
     * Gitイベント発生時の処理
     * 
     * @param event Gitイベント
     */
    void onGitEvent(GitEvent event);
}

/**
 * Gitイベントの基底クラス
 */
abstract class GitEvent {
    private final int repositoryId;
    private final long timestamp;

    protected GitEvent(int repositoryId) {
        this.repositoryId = repositoryId;
        this.timestamp = System.currentTimeMillis();
    }

    public int getRepositoryId() {
        return repositoryId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * イベントの種類を返す
     * 
     * @return イベント種類
     */
    public abstract String getEventType();
}

/**
 * コミット作成イベント
 */
class CommitCreatedEvent extends GitEvent {
    private final int commitId;
    private final int branchId;

    public CommitCreatedEvent(int repositoryId, int commitId, int branchId) {
        super(repositoryId);
        this.commitId = commitId;
        this.branchId = branchId;
    }

    @Override
    public String getEventType() {
        return "COMMIT_CREATED";
    }

    public int getCommitId() {
        return commitId;
    }

    public int getBranchId() {
        return branchId;
    }
}

/**
 * ブランチ作成イベント
 */
class BranchCreatedEvent extends GitEvent {
    private final int branchId;
    private final String branchName;

    public BranchCreatedEvent(int repositoryId, int branchId, String branchName) {
        super(repositoryId);
        this.branchId = branchId;
        this.branchName = branchName;
    }

    @Override
    public String getEventType() {
        return "BRANCH_CREATED";
    }

    public int getBranchId() {
        return branchId;
    }

    public String getBranchName() {
        return branchName;
    }
}

/**
 * マージ完了イベント
 */
class MergeCompletedEvent extends GitEvent {
    private final int sourceBranchId;
    private final int targetBranchId;
    private final int mergeCommitId;

    public MergeCompletedEvent(int repositoryId, int sourceBranchId, int targetBranchId, int mergeCommitId) {
        super(repositoryId);
        this.sourceBranchId = sourceBranchId;
        this.targetBranchId = targetBranchId;
        this.mergeCommitId = mergeCommitId;
    }

    @Override
    public String getEventType() {
        return "MERGE_COMPLETED";
    }

    public int getSourceBranchId() {
        return sourceBranchId;
    }

    public int getTargetBranchId() {
        return targetBranchId;
    }

    public int getMergeCommitId() {
        return mergeCommitId;
    }
}

/**
 * リポジトリ統計情報
 */
class RepositoryStats {
    private final int repositoryId;
    private final int commitCount;
    private final int branchCount;
    private final int fileCount;
    private final String lastCommitDate;

    public RepositoryStats(int repositoryId, int commitCount, int branchCount, int fileCount, String lastCommitDate) {
        this.repositoryId = repositoryId;
        this.commitCount = commitCount;
        this.branchCount = branchCount;
        this.fileCount = fileCount;
        this.lastCommitDate = lastCommitDate;
    }

    // Getters
    public int getRepositoryId() {
        return repositoryId;
    }

    public int getCommitCount() {
        return commitCount;
    }

    public int getBranchCount() {
        return branchCount;
    }

    public int getFileCount() {
        return fileCount;
    }

    public String getLastCommitDate() {
        return lastCommitDate;
    }

    /**
     * JSON形式に変換
     * 
     * @return JSON文字列
     */
    public String toJson() {
        return String.format(
                "{\"repository_id\":%d,\"commit_count\":%d,\"branch_count\":%d,\"file_count\":%d,\"last_commit_date\":\"%s\"}",
                repositoryId, commitCount, branchCount, fileCount,
                lastCommitDate != null ? lastCommitDate : "");
    }
}

/**
 * ブランチ差分情報
 */
class BranchDiff {
    private final int branchId1;
    private final int branchId2;
    private final int additions;
    private final int deletions;
    private final boolean hasConflicts;

    public BranchDiff(int branchId1, int branchId2, int additions, int deletions, boolean hasConflicts) {
        this.branchId1 = branchId1;
        this.branchId2 = branchId2;
        this.additions = additions;
        this.deletions = deletions;
        this.hasConflicts = hasConflicts;
    }

    // Getters
    public int getBranchId1() {
        return branchId1;
    }

    public int getBranchId2() {
        return branchId2;
    }

    public int getAdditions() {
        return additions;
    }

    public int getDeletions() {
        return deletions;
    }

    public boolean hasConflicts() {
        return hasConflicts;
    }

    /**
     * JSON形式に変換
     * 
     * @return JSON文字列
     */
    public String toJson() {
        return String.format(
                "{\"branch_id_1\":%d,\"branch_id_2\":%d,\"additions\":%d,\"deletions\":%d,\"has_conflicts\":%s}",
                branchId1, branchId2, additions, deletions, hasConflicts);
    }
}
