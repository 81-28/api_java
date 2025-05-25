package src;

import java.sql.*;

/**
 * マージ結果の種類（Java 17のSealed Classes使用）
 */
sealed interface MergeResult
        permits MergeResult.Success, MergeResult.Conflict {

    /**
     * マージ成功結果
     * 
     * @param message 成功メッセージ
     */
    record Success(String message) implements MergeResult {
    }

    /**
     * マージコンフリクト結果
     * 
     * @param branchId1 ブランチ1のID
     * @param content1  ブランチ1の内容
     * @param branchId2 ブランチ2のID
     * @param content2  ブランチ2の内容
     */
    record Conflict(int branchId1, String content1, int branchId2, String content2) implements MergeResult {
    }
}

/**
 * マージ管理クラス（Strategy パターンを使用）
 */
public class MergeManager {
    private final DatabaseManager dbManager;
    private final CommitManager commitManager;

    public MergeManager() {
        this.dbManager = DatabaseManager.getInstance();
        this.commitManager = new CommitManager();
    }

    /**
     * マージ戦略インターフェース
     */
    public interface MergeStrategy {
        /**
         * マージを実行
         * 
         * @param branchId1 ブランチ1のID
         * @param branchId2 ブランチ2のID
         * @return マージ結果
         */
        MergeResult merge(int branchId1, int branchId2);
    }

    /**
     * 厳密マージ戦略（内容が一致する場合のみマージ）
     */
    public class StrictMergeStrategy implements MergeStrategy {
        @Override
        public MergeResult merge(int branchId1, int branchId2) {
            try (Connection conn = dbManager.getConnection()) {
                // 各ブランチのHEADコミット取得
                BranchInfo branch1 = getBranchInfo(conn, branchId1);
                BranchInfo branch2 = getBranchInfo(conn, branchId2);

                if (branch1 == null || branch2 == null) {
                    return new MergeResult.Conflict(branchId1, "", branchId2, "");
                }

                // ファイル内容取得
                String content1 = getFileContent(conn, branch1.headCommitId());
                String content2 = getFileContent(conn, branch2.headCommitId());

                if (content1 == null)
                    content1 = "";
                if (content2 == null)
                    content2 = "";

                // 内容比較
                if (content1.equals(content2)) {
                    // マージコミット作成
                    CommitManager.CreateMergeCommitCommand mergeCommand = commitManager.new CreateMergeCommitCommand(
                            branch1.repositoryId(),
                            branch1.headCommitId(),
                            branch2.headCommitId(),
                            content1);

                    if (commitManager.executeCommitCommand(mergeCommand)) {
                        // 両ブランチのHEAD更新
                        updateBranchHeads(conn, branchId1, branchId2, getLatestCommitId(conn, branch1.repositoryId()));
                        return new MergeResult.Success("マージが完了しました");
                    }
                }

                return new MergeResult.Conflict(branchId1, content1, branchId2, content2);

            } catch (SQLException e) {
                System.err.println("Merge error: " + e.getMessage());
                return new MergeResult.Conflict(branchId1, "", branchId2, "");
            }
        }
    }

    /**
     * 強制マージ戦略（指定された内容でマージ）
     */
    public class ForceMergeStrategy implements MergeStrategy {
        private final String forcedContent;

        /**
         * コンストラクタ
         * 
         * @param forcedContent 強制的に設定する内容
         */
        public ForceMergeStrategy(String forcedContent) {
            this.forcedContent = forcedContent;
        }

        @Override
        public MergeResult merge(int branchId1, int branchId2) {
            try (Connection conn = dbManager.getConnection()) {
                BranchInfo branch1 = getBranchInfo(conn, branchId1);
                BranchInfo branch2 = getBranchInfo(conn, branchId2);

                if (branch1 == null || branch2 == null) {
                    return new MergeResult.Conflict(branchId1, "", branchId2, "");
                }

                // 強制マージコミット作成
                CommitManager.CreateMergeCommitCommand mergeCommand = commitManager.new CreateMergeCommitCommand(
                        branch1.repositoryId(),
                        branch1.headCommitId(),
                        branch2.headCommitId(),
                        forcedContent);

                if (commitManager.executeCommitCommand(mergeCommand)) {
                    // 両ブランチのHEAD更新
                    updateBranchHeads(conn, branchId1, branchId2, getLatestCommitId(conn, branch1.repositoryId()));
                    return new MergeResult.Success("強制マージが完了しました");
                }

                return new MergeResult.Conflict(branchId1, "", branchId2, "");

            } catch (SQLException e) {
                System.err.println("Force merge error: " + e.getMessage());
                return new MergeResult.Conflict(branchId1, "", branchId2, "");
            }
        }
    }

    /**
     * ブランチ情報レコード
     * 
     * @param repositoryId リポジトリID
     * @param headCommitId HEADコミットID
     */
    private record BranchInfo(int repositoryId, int headCommitId) {
    }

    /**
     * ブランチ情報を取得
     * 
     * @param conn     データベース接続
     * @param branchId ブランチID
     * @return ブランチ情報
     * @throws SQLException SQL例外
     */
    private BranchInfo getBranchInfo(Connection conn, int branchId) throws SQLException {
        String sql = "SELECT repository_id, head_commit_id FROM branch WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, branchId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int repositoryId = rs.getInt("repository_id");
                    int headCommitId = rs.getInt("head_commit_id");
                    if (headCommitId == 0)
                        return null;
                    return new BranchInfo(repositoryId, headCommitId);
                }
            }
        }
        return null;
    }

    /**
     * ファイル内容を取得
     * 
     * @param conn     データベース接続
     * @param commitId コミットID
     * @return ファイル内容
     * @throws SQLException SQL例外
     */
    private String getFileContent(Connection conn, int commitId) throws SQLException {
        String sql = "SELECT content FROM file WHERE commit_id = ? ORDER BY id LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, commitId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("content");
                }
            }
        }
        return null;
    }

    /**
     * 最新のコミットIDを取得
     * 
     * @param conn         データベース接続
     * @param repositoryId リポジトリID
     * @return 最新のコミットID
     * @throws SQLException SQL例外
     */
    private int getLatestCommitId(Connection conn, int repositoryId) throws SQLException {
        String sql = "SELECT id FROM git_commit WHERE repository_id = ? ORDER BY id DESC LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, repositoryId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        return -1;
    }

    /**
     * 両ブランチのHEADを更新
     * 
     * @param conn        データベース接続
     * @param branchId1   ブランチ1のID
     * @param branchId2   ブランチ2のID
     * @param newCommitId 新しいコミットID
     * @throws SQLException SQL例外
     */
    private void updateBranchHeads(Connection conn, int branchId1, int branchId2, int newCommitId) throws SQLException {
        String sql = "UPDATE branch SET head_commit_id = ? WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, newCommitId);
            stmt.setInt(2, branchId1);
            stmt.executeUpdate();

            stmt.setInt(2, branchId2);
            stmt.executeUpdate();
        }
    }

    /**
     * マージを実行
     * 
     * @param strategy  マージ戦略
     * @param branchId1 ブランチ1のID
     * @param branchId2 ブランチ2のID
     * @return マージ結果
     */
    public MergeResult executeMerge(MergeStrategy strategy, int branchId1, int branchId2) {
        return strategy.merge(branchId1, branchId2);
    }

    /**
     * 厳密マージを実行
     * 
     * @param branchId1 ブランチ1のID
     * @param branchId2 ブランチ2のID
     * @return マージ結果
     */
    public MergeResult performStrictMerge(int branchId1, int branchId2) {
        return executeMerge(new StrictMergeStrategy(), branchId1, branchId2);
    }

    /**
     * 強制マージを実行
     * 
     * @param branchId1 ブランチ1のID
     * @param branchId2 ブランチ2のID
     * @param content   マージ後の内容
     * @return マージ結果
     */
    public MergeResult performForceMerge(int branchId1, int branchId2, String content) {
        return executeMerge(new ForceMergeStrategy(content), branchId1, branchId2);
    }
}