package src;

import java.sql.*;
import java.util.*;

/**
 * コミット管理クラス（Command パターンを使用）
 */
public class CommitManager {
    private final DatabaseManager dbManager;

    public CommitManager() {
        this.dbManager = DatabaseManager.getInstance();
    }

    /**
     * コミットを作成するコマンドインターフェース
     */
    public interface CommitCommand {
        /**
         * コミットを実行
         * 
         * @return 実行結果
         */
        boolean execute();
    }

    /**
     * 通常のコミット作成コマンド
     */
    public class CreateCommitCommand implements CommitCommand {
        private final int branchId;
        private final String message;
        private final int authorId;
        private final String content;

        /**
         * コンストラクタ
         * 
         * @param branchId ブランチID
         * @param message  コミットメッセージ
         * @param authorId 作成者ID
         * @param content  ファイル内容
         */
        public CreateCommitCommand(int branchId, String message, int authorId, String content) {
            this.branchId = branchId;
            this.message = message;
            this.authorId = authorId;
            this.content = content;
        }

        @Override
        public boolean execute() {
            return createCommitSnapshot(branchId, message, authorId, content);
        }
    }

    /**
     * マージコミット作成コマンド
     */
    public class CreateMergeCommitCommand implements CommitCommand {
        private final int repositoryId;
        private final int parentCommitId1;
        private final int parentCommitId2;
        private final String content;

        /**
         * コンストラクタ
         * 
         * @param repositoryId    リポジトリID
         * @param parentCommitId1 親コミットID1
         * @param parentCommitId2 親コミットID2
         * @param content         ファイル内容
         */
        public CreateMergeCommitCommand(int repositoryId, int parentCommitId1, int parentCommitId2, String content) {
            this.repositoryId = repositoryId;
            this.parentCommitId1 = parentCommitId1;
            this.parentCommitId2 = parentCommitId2;
            this.content = content;
        }

        @Override
        public boolean execute() {
            return createMergeCommit(repositoryId, parentCommitId1, parentCommitId2, content);
        }
    }

    /**
     * コミットコマンドを実行
     * 
     * @param command 実行するコマンド
     * @return 実行結果
     */
    public boolean executeCommitCommand(CommitCommand command) {
        return command.execute();
    }

    /**
     * 通常のコミットを作成
     * 
     * @param branchId ブランチID
     * @param message  コミットメッセージ
     * @param authorId 作成者ID
     * @param content  ファイル内容
     * @return 作成成功フラグ
     */
    private boolean createCommitSnapshot(int branchId, String message, int authorId, String content) {
        Connection conn = null;
        try {
            conn = dbManager.getConnection();
            conn.setAutoCommit(false);

            // ブランチ情報取得
            Integer parentCommitId = null;
            Integer repositoryId = null;

            String branchSql = "SELECT head_commit_id, repository_id FROM branch WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(branchSql)) {
                stmt.setInt(1, branchId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        parentCommitId = rs.getInt("head_commit_id");
                        if (rs.wasNull())
                            parentCommitId = null;
                        repositoryId = rs.getInt("repository_id");
                    }
                }
            }

            if (repositoryId == null) {
                conn.rollback();
                return false;
            }

            // コミット作成
            String commitSql = "INSERT INTO git_commit(repository_id, author_id, message, parent_commit_id, parent_commit_id_2, created_at) VALUES(?, ?, ?, ?, NULL, datetime('now'))";
            int newCommitId = -1;

            try (PreparedStatement stmt = conn.prepareStatement(commitSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, repositoryId);
                stmt.setInt(2, authorId);
                stmt.setString(3, message);
                if (parentCommitId != null && parentCommitId != 0) {
                    stmt.setInt(4, parentCommitId);
                } else {
                    stmt.setNull(4, Types.INTEGER);
                }
                stmt.executeUpdate();

                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        newCommitId = rs.getInt(1);
                    }
                }
            }

            if (newCommitId == -1) {
                conn.rollback();
                return false;
            }

            // ファイル保存
            String fileSql = "INSERT INTO file(commit_id, filename, content) VALUES(?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(fileSql)) {
                stmt.setInt(1, newCommitId);
                stmt.setString(2, "main.txt");
                stmt.setString(3, content);
                stmt.executeUpdate();
            }

            // ブランチのHEAD更新
            String updateBranchSql = "UPDATE branch SET head_commit_id = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateBranchSql)) {
                stmt.setInt(1, newCommitId);
                stmt.setInt(2, branchId);
                stmt.executeUpdate();
            }

            conn.commit();
            return true;

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
            }
            System.err.println("Create commit error: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    /**
     * マージコミットを作成
     * 
     * @param repositoryId    リポジトリID
     * @param parentCommitId1 親コミットID1
     * @param parentCommitId2 親コミットID2
     * @param content         ファイル内容
     * @return 作成成功フラグ
     */
    private boolean createMergeCommit(int repositoryId, int parentCommitId1, int parentCommitId2, String content) {
        Connection conn = null;
        try {
            conn = dbManager.getConnection();
            conn.setAutoCommit(false);

            // マージコミット作成
            String commitSql = "INSERT INTO git_commit(repository_id, author_id, message, parent_commit_id, parent_commit_id_2, created_at) VALUES(?, 1, 'Merge commit', ?, ?, datetime('now'))";
            int newCommitId = -1;

            try (PreparedStatement stmt = conn.prepareStatement(commitSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, repositoryId);
                stmt.setInt(2, parentCommitId1);
                stmt.setInt(3, parentCommitId2);
                stmt.executeUpdate();

                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        newCommitId = rs.getInt(1);
                    }
                }
            }

            if (newCommitId == -1) {
                conn.rollback();
                return false;
            }

            // ファイル保存
            String fileSql = "INSERT INTO file(commit_id, filename, content) VALUES(?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(fileSql)) {
                stmt.setInt(1, newCommitId);
                stmt.setString(2, "main.txt");
                stmt.setString(3, content);
                stmt.executeUpdate();
            }

            conn.commit();
            return true;

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
            }
            System.err.println("Create merge commit error: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    /**
     * コミット一覧を取得
     * 
     * @param repositoryId リポジトリID（nullの場合は全て）
     * @return コミットリスト
     */
    public List<Map<String, Object>> getCommits(Integer repositoryId) {
        List<Map<String, Object>> commits = new ArrayList<>();
        String sql = repositoryId != null
                ? "SELECT id, repository_id, author_id, message, parent_commit_id, parent_commit_id_2, created_at FROM git_commit WHERE repository_id = ? ORDER BY id DESC"
                : "SELECT id, repository_id, author_id, message, parent_commit_id, parent_commit_id_2, created_at FROM git_commit ORDER BY id DESC";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (repositoryId != null) {
                stmt.setInt(1, repositoryId);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> commit = new LinkedHashMap<>();
                    commit.put("id", rs.getInt("id"));
                    commit.put("repository_id", rs.getInt("repository_id"));
                    commit.put("author_id", rs.getInt("author_id"));
                    commit.put("message", rs.getString("message"));
                    commit.put("parent_commit_id", rs.getInt("parent_commit_id"));
                    commit.put("parent_commit_id_2", rs.getInt("parent_commit_id_2"));
                    commit.put("created_at", rs.getString("created_at"));
                    commits.add(commit);
                }
            }
        } catch (SQLException e) {
            System.err.println("Get commits error: " + e.getMessage());
        }

        return commits;
    }

    /**
     * 指定ブランチのファイル内容を取得
     * 
     * @param branchId ブランチID
     * @return ファイル情報リスト
     */
    public List<Map<String, Object>> getFilesByBranch(int branchId) {
        List<Map<String, Object>> files = new ArrayList<>();

        try (Connection conn = dbManager.getConnection()) {
            // ブランチのHEADコミット取得
            Integer headCommitId = null;
            String branchSql = "SELECT head_commit_id FROM branch WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(branchSql)) {
                stmt.setInt(1, branchId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        headCommitId = rs.getInt("head_commit_id");
                        if (rs.wasNull() || headCommitId == 0) {
                            headCommitId = null;
                        }
                    }
                }
            }

            if (headCommitId != null) {
                // ファイル内容取得
                String fileSql = "SELECT id, commit_id, content FROM file WHERE commit_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(fileSql)) {
                    stmt.setInt(1, headCommitId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> file = new LinkedHashMap<>();
                            file.put("commit_id", rs.getInt("commit_id"));
                            file.put("file_id", rs.getInt("id"));
                            file.put("text", rs.getString("content"));
                            files.add(file);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Get files error: " + e.getMessage());
        }

        return files;
    }
}