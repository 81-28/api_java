package src;

import java.sql.*;
import java.util.*;

/**
 * データベース管理クラス（Singletonパターン使用）
 */
public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:database/database.db";
    private static DatabaseManager instance;

    private DatabaseManager() {
    }

    /**
     * シングルトンインスタンスを取得
     * 
     * @return DatabaseManagerインスタンス
     */
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * データベース接続を取得
     * 
     * @return データベース接続
     * @throws SQLException SQL例外
     */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    /**
     * データベースとテーブルを初期化
     */
    public void initializeDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("SQLite JDBC driver not found: " + e.getMessage());
            return;
        }

        String[] dropTableQueries = {
                "DROP TABLE IF EXISTS file",
                "DROP TABLE IF EXISTS git_commit",
                "DROP TABLE IF EXISTS branch",
                "DROP TABLE IF EXISTS repository",
                "DROP TABLE IF EXISTS name"
        };

        String[] createTableQueries = {
                "CREATE TABLE IF NOT EXISTS name (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE)",
                "CREATE TABLE IF NOT EXISTS repository (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, owner_id INTEGER)",
                "CREATE TABLE IF NOT EXISTS branch (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, repository_id INTEGER, head_commit_id INTEGER)",
                "CREATE TABLE IF NOT EXISTS git_commit (id INTEGER PRIMARY KEY AUTOINCREMENT, repository_id INTEGER, author_id INTEGER, message TEXT, parent_commit_id INTEGER, parent_commit_id_2 INTEGER, created_at DATETIME)",
                "CREATE TABLE IF NOT EXISTS file (id INTEGER PRIMARY KEY AUTOINCREMENT, commit_id INTEGER, filename TEXT, content TEXT)"
        };

        try (Connection conn = getConnection()) {
            // 既存のテーブルを削除
            for (String query : dropTableQueries) {
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.executeUpdate();
                }
            }

            // テーブルを再作成
            for (String query : createTableQueries) {
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.executeUpdate();
                }
            }
            System.out.println("Database initialized successfully");
        } catch (SQLException e) {
            System.err.println("Database initialization error: " + e.getMessage());
        }
    }

    /**
     * ユーザーを作成
     * 
     * @param username ユーザー名
     * @return 作成成功フラグ
     */
    public boolean createUser(String username) {
        String sql = "INSERT INTO name(username) VALUES(?)";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("User creation error: " + e.getMessage());
            return false;
        }
    }

    /**
     * 全ユーザーを取得
     * 
     * @return ユーザーリスト
     */
    public List<Map<String, Object>> getAllUsers() {
        List<Map<String, Object>> users = new ArrayList<>();
        String sql = "SELECT id, username FROM name ORDER BY id";

        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> user = new LinkedHashMap<>();
                user.put("id", rs.getInt("id"));
                user.put("username", rs.getString("username"));
                users.add(user);
            }
        } catch (SQLException e) {
            System.err.println("Get users error: " + e.getMessage());
        }

        return users;
    }

    /**
     * リポジトリを作成
     * 
     * @param name    リポジトリ名
     * @param ownerId 所有者ID
     * @return 作成成功フラグ
     */
    public boolean createRepository(String name, int ownerId) {
        String sql = "INSERT INTO repository(name, owner_id) VALUES(?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setInt(2, ownerId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Repository creation error: " + e.getMessage());
            return false;
        }
    }

    /**
     * リポジトリ一覧を取得
     * 
     * @param ownerId 所有者ID（nullの場合は全て）
     * @return リポジトリリスト
     */
    public List<Map<String, Object>> getRepositories(Integer ownerId) {
        List<Map<String, Object>> repositories = new ArrayList<>();
        String sql = ownerId != null ? "SELECT id, name, owner_id FROM repository WHERE owner_id = ? ORDER BY id"
                : "SELECT id, name, owner_id FROM repository ORDER BY id";

        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (ownerId != null) {
                stmt.setInt(1, ownerId);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> repo = new LinkedHashMap<>();
                    repo.put("id", rs.getInt("id"));
                    repo.put("name", rs.getString("name"));
                    repo.put("owner_id", rs.getInt("owner_id"));
                    repositories.add(repo);
                }
            }
        } catch (SQLException e) {
            System.err.println("Get repositories error: " + e.getMessage());
        }

        return repositories;
    }

    /**
     * ブランチを作成
     * 
     * @param name         ブランチ名
     * @param repositoryId リポジトリID
     * @return 作成成功フラグ
     */
    public boolean createBranch(String name, int repositoryId) {
        String sql = "INSERT INTO branch(name, repository_id, head_commit_id) VALUES(?, ?, 0)";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setInt(2, repositoryId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Branch creation error: " + e.getMessage());
            return false;
        }
    }

    /**
     * ブランチ一覧を取得
     * 
     * @param repositoryId リポジトリID（nullの場合は全て）
     * @return ブランチリスト
     */
    public List<Map<String, Object>> getBranches(Integer repositoryId) {
        List<Map<String, Object>> branches = new ArrayList<>();
        String sql = repositoryId != null
                ? "SELECT id, name, repository_id, head_commit_id FROM branch WHERE repository_id = ? ORDER BY id"
                : "SELECT id, name, repository_id, head_commit_id FROM branch ORDER BY id";

        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (repositoryId != null) {
                stmt.setInt(1, repositoryId);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> branch = new LinkedHashMap<>();
                    branch.put("id", rs.getInt("id"));
                    branch.put("name", rs.getString("name"));
                    branch.put("repository_id", rs.getInt("repository_id"));
                    branch.put("head_commit_id", rs.getInt("head_commit_id"));
                    branches.add(branch);
                }
            }
        } catch (SQLException e) {
            System.err.println("Get branches error: " + e.getMessage());
        }

        return branches;
    }

    /**
     * データベース接続を閉じる
     */
    public void closeConnection() {
        try {
            Connection conn = getConnection();
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            System.err.println("Connection close error: " + e.getMessage());
        }
    }
}