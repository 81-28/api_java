package src;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Git風バージョン管理システムAPIサーバー
 * Java 17のSealed Classesとデザインパターンを活用した実装
 */
public class GitApiServer {
    private static final String DB_URL = "jdbc:sqlite:database/git_database.db";
    private static final String CONTENT_TYPE_JSON = "application/json; charset=UTF-8";

    // Singleton Pattern - データベース接続管理
    private static final DatabaseManager dbManager = DatabaseManager.getInstance();

    // Factory Pattern - APIハンドラーファクトリー
    private static final ApiHandlerFactory handlerFactory = new ApiHandlerFactory();

    /**
     * サーバー起動メソッド
     * 
     * @param args コマンドライン引数
     * @throws IOException サーバー起動エラー
     */
    public static void main(String[] args) throws IOException {
        // データベース初期化
        dbManager.initializeDatabase();

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // 各APIエンドポイントを登録
        server.createContext("/api/user", handlerFactory.createHandler(ApiType.USER));
        server.createContext("/api/repository", handlerFactory.createHandler(ApiType.REPOSITORY));
        server.createContext("/api/branch", handlerFactory.createHandler(ApiType.BRANCH));
        server.createContext("/api/commit", handlerFactory.createHandler(ApiType.COMMIT));
        server.createContext("/api/file", handlerFactory.createHandler(ApiType.FILE));
        server.createContext("/api/merge", handlerFactory.createHandler(ApiType.MERGE));
        server.createContext("/api/force-merge", handlerFactory.createHandler(ApiType.FORCE_MERGE));
        server.createContext("/api/graph", handlerFactory.createHandler(ApiType.GRAPH));

        server.setExecutor(null);
        server.start();
        System.out.println("Git API Server is running on http://localhost:8080/api");
    }

    /**
     * Sealed Classes（Java 17）- APIタイプの定義
     * APIの種類を制限し、型安全性を向上
     */
    public static sealed interface ApiType
            permits ApiType.UserType, ApiType.RepositoryType, ApiType.BranchType,
            ApiType.CommitType, ApiType.FileType, ApiType.MergeType,
            ApiType.ForceMergeType, ApiType.GraphType {

        static final UserType USER = new UserType();
        static final RepositoryType REPOSITORY = new RepositoryType();
        static final BranchType BRANCH = new BranchType();
        static final CommitType COMMIT = new CommitType();
        static final FileType FILE = new FileType();
        static final MergeType MERGE = new MergeType();
        static final ForceMergeType FORCE_MERGE = new ForceMergeType();
        static final GraphType GRAPH = new GraphType();

        record UserType() implements ApiType {
        }

        record RepositoryType() implements ApiType {
        }

        record BranchType() implements ApiType {
        }

        record CommitType() implements ApiType {
        }

        record FileType() implements ApiType {
        }

        record MergeType() implements ApiType {
        }

        record ForceMergeType() implements ApiType {
        }

        record GraphType() implements ApiType {
        }
    }

    /**
     * Singleton Pattern - データベース管理クラス
     * データベース接続を一元管理し、リソースの効率的な利用を実現
     */
    public static class DatabaseManager {
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
         * @throws SQLException データベース接続エラー
         */
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(DB_URL);
        }

        /**
         * データベースの初期化
         * 必要なテーブルを作成する
         */
        public void initializeDatabase() {
            String[] createTableQueries = {
                    """
                            CREATE TABLE IF NOT EXISTS users (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                username TEXT UNIQUE NOT NULL,
                                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                            )
                            """,
                    """
                            CREATE TABLE IF NOT EXISTS repositories (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                name TEXT NOT NULL,
                                owner_id INTEGER NOT NULL,
                                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                                FOREIGN KEY (owner_id) REFERENCES users(id)
                            )
                            """,
                    """
                            CREATE TABLE IF NOT EXISTS branches (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                name TEXT NOT NULL,
                                repository_id INTEGER NOT NULL,
                                head_commit_id INTEGER,
                                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                                FOREIGN KEY (repository_id) REFERENCES repositories(id),
                                FOREIGN KEY (head_commit_id) REFERENCES commits(id)
                            )
                            """,
                    """
                            CREATE TABLE IF NOT EXISTS commits (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                repository_id INTEGER NOT NULL,
                                author_id INTEGER NOT NULL,
                                message TEXT NOT NULL,
                                parent_commit_id INTEGER,
                                parent_commit_id_2 INTEGER,
                                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                                FOREIGN KEY (repository_id) REFERENCES repositories(id),
                                FOREIGN KEY (author_id) REFERENCES users(id),
                                FOREIGN KEY (parent_commit_id) REFERENCES commits(id),
                                FOREIGN KEY (parent_commit_id_2) REFERENCES commits(id)
                            )
                            """,
                    """
                            CREATE TABLE IF NOT EXISTS files (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                commit_id INTEGER NOT NULL,
                                filename TEXT NOT NULL,
                                content TEXT,
                                file_size INTEGER DEFAULT 0,
                                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                                FOREIGN KEY (commit_id) REFERENCES commits(id)
                            )
                            """
            };

            try (Connection conn = getConnection()) {
                for (String query : createTableQueries) {
                    try (PreparedStatement stmt = conn.prepareStatement(query)) {
                        stmt.executeUpdate();
                    }
                }
                System.out.println("Database initialized successfully");
            } catch (SQLException e) {
                System.err.println("Database initialization failed: " + e.getMessage());
            }
        }
    }

    /**
     * Factory Pattern - APIハンドラーファクトリー
     * APIタイプに応じて適切なハンドラーを生成
     */
    public static class ApiHandlerFactory {

        /**
         * APIタイプに応じたハンドラーを作成
         * 
         * @param apiType APIタイプ
         * @return HttpHandler実装
         */
        public HttpHandler createHandler(ApiType apiType) {
            return switch (apiType) {
                case ApiType.UserType userType -> new UserHandler();
                case ApiType.RepositoryType repoType -> new RepositoryHandler();
                case ApiType.BranchType branchType -> new BranchHandler();
                case ApiType.CommitType commitType -> new CommitHandler();
                case ApiType.FileType fileType -> new FileHandler();
                case ApiType.MergeType mergeType -> new MergeHandler();
                case ApiType.ForceMergeType forceMergeType -> new ForceMergeHandler();
                case ApiType.GraphType graphType -> new GraphHandler();
            };
        }
    }

    /**
     * Strategy Pattern - 基底HTTPハンドラークラス
     * 各APIハンドラーの共通機能を提供
     */
    public abstract static class BaseHttpHandler implements HttpHandler {

        /**
         * HTTPリクエストを処理
         * 
         * @param exchange HTTPExchange
         * @throws IOException IO処理エラー
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);

            String method = exchange.getRequestMethod();
            if ("OPTIONS".equals(method)) {
                sendResponse(exchange, 204, "");
                return;
            }

            try {
                processRequest(exchange, method);
            } catch (Exception e) {
                System.err.println("Error processing request: " + e.getMessage());
                sendErrorResponse(exchange, 500, "Internal server error");
            }
        }

        /**
         * 具象クラスでの実装が必要なリクエスト処理メソッド
         * 
         * @param exchange HTTPExchange
         * @param method   HTTPメソッド
         * @throws Exception 処理中のエラー
         */
        protected abstract void processRequest(HttpExchange exchange, String method) throws Exception;

        /**
         * CORSヘッダーを追加
         * 
         * @param exchange HTTPExchange
         */
        protected void addCorsHeaders(HttpExchange exchange) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        }

        /**
         * レスポンスを送信
         * 
         * @param exchange   HTTPExchange
         * @param statusCode ステータスコード
         * @param body       レスポンスボディ
         * @throws IOException IO処理エラー
         */
        protected void sendResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
            byte[] bytes = body.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE_JSON);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        /**
         * エラーレスポンスを送信
         * 
         * @param exchange   HTTPExchange
         * @param statusCode ステータスコード
         * @param message    エラーメッセージ
         * @throws IOException IO処理エラー
         */
        protected void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
            String errorJson = String.format("{\"error\":\"%s\"}", message);
            sendResponse(exchange, statusCode, errorJson);
        }

        /**
         * リクエストボディを読み込み
         * 
         * @param exchange HTTPExchange
         * @return リクエストボディ文字列
         * @throws IOException IO処理エラー
         */
        protected String readRequestBody(HttpExchange exchange) throws IOException {
            try (Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A")) {
                return scanner.hasNext() ? scanner.next() : "";
            }
        }

        /**
         * JSONから指定フィールドの値を抽出（簡易実装）
         * 
         * @param json      JSON文字列
         * @param fieldName フィールド名
         * @return フィールド値
         */

        protected String parseJsonField(String json, String fieldName) {
            String searchPattern = "\"" + fieldName + "\"";
            int startIndex = json.indexOf(searchPattern);
            if (startIndex == -1)
                return "";

            startIndex = json.indexOf(":", startIndex) + 1;

            // 空白をスキップ
            while (startIndex < json.length() && Character.isWhitespace(json.charAt(startIndex))) {
                startIndex++;
            }

            if (startIndex >= json.length())
                return "";

            char firstChar = json.charAt(startIndex);

            // 文字列値の場合（クォートで囲まれている）
            if (firstChar == '"') {
                startIndex++; // 開始クォートをスキップ
                int endIndex = json.indexOf("\"", startIndex);
                if (endIndex > startIndex) {
                    return json.substring(startIndex, endIndex);
                }
            }
            // 数値やブール値の場合（クォートなし）
            else {
                int endIndex = startIndex;
                while (endIndex < json.length() &&
                        json.charAt(endIndex) != ',' &&
                        json.charAt(endIndex) != '}' &&
                        json.charAt(endIndex) != ']') {
                    endIndex++;
                }

                if (endIndex > startIndex) {
                    return json.substring(startIndex, endIndex).trim();
                }
            }

            return "";
        }
    }

    /**
     * Template Method Pattern - JSONレスポンス構築クラス
     * JSON構築の共通処理を定義し、具象クラスで詳細を実装
     */
    public static class JsonResponseBuilder {

        /**
         * 成功レスポンスを構築
         * 
         * @param success 成功フラグ
         * @return JSON文字列
         */
        public static String buildSuccessResponse(boolean success) {
            return String.format("{\"success\":%s}", success);
        }

        /**
         * リストレスポンスを構築
         * 
         * @param items     アイテムリスト
         * @param arrayName 配列名
         * @return JSON文字列
         */
        public static String buildListResponse(List<? extends JsonSerializable> items, String arrayName) {
            StringBuilder json = new StringBuilder();
            json.append("{\"").append(arrayName).append("\":[");

            for (int i = 0; i < items.size(); i++) {
                json.append(items.get(i).toJson());
                if (i < items.size() - 1) {
                    json.append(",");
                }
            }

            json.append("]}");
            return json.toString();
        }

        /**
         * エラーレスポンスを構築
         * 
         * @param message エラーメッセージ
         * @return JSON文字列
         */
        public static String buildErrorResponse(String message) {
            return String.format("{\"error\":\"%s\"}", message.replace("\"", "\\\""));
        }
    }

    /**
     * JSON シリアライズ可能なインターフェース
     */
    public interface JsonSerializable {
        /**
         * オブジェクトをJSON文字列に変換
         * 
         * @return JSON文字列
         */
        String toJson();
    }

    /**
     * データ転送オブジェクト（DTO）クラス群
     */

    /**
     * ユーザーDTO
     */
    public static class User implements JsonSerializable {
        private final int id;
        private final String username;
        private final String createdAt;

        public User(int id, String username, String createdAt) {
            this.id = id;
            this.username = username;
            this.createdAt = createdAt;
        }

        @Override
        public String toJson() {
            return String.format(
                    "{\"id\":%d,\"username\":\"%s\",\"created_at\":\"%s\"}",
                    id, username, createdAt != null ? createdAt : "");
        }

        // Getters
        public int getId() {
            return id;
        }

        public String getUsername() {
            return username;
        }

        public String getCreatedAt() {
            return createdAt;
        }
    }

    /**
     * リポジトリDTO
     */
    public static class Repository implements JsonSerializable {
        private final int id;
        private final String name;
        private final int ownerId;
        private final String createdAt;

        public Repository(int id, String name, int ownerId, String createdAt) {
            this.id = id;
            this.name = name;
            this.ownerId = ownerId;
            this.createdAt = createdAt;
        }

        @Override
        public String toJson() {
            return String.format(
                    "{\"id\":%d,\"name\":\"%s\",\"owner_id\":%d,\"created_at\":\"%s\"}",
                    id, name, ownerId, createdAt != null ? createdAt : "");
        }

        // Getters
        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public int getOwnerId() {
            return ownerId;
        }

        public String getCreatedAt() {
            return createdAt;
        }
    }

    /**
     * ブランチDTO
     */
    public static class Branch implements JsonSerializable {
        private final int id;
        private final String name;
        private final int repositoryId;
        private final Integer headCommitId;
        private final String createdAt;

        public Branch(int id, String name, int repositoryId, Integer headCommitId, String createdAt) {
            this.id = id;
            this.name = name;
            this.repositoryId = repositoryId;
            this.headCommitId = headCommitId;
            this.createdAt = createdAt;
        }

        @Override
        public String toJson() {
            String headCommitStr = headCommitId != null ? headCommitId.toString()
                    : "null";
            return String.format(
                    "{\"id\":%d,\"name\":\"%s\",\"repository_id\":%d,\"head_commit_id\":%s,\"created_at\":\"%s\"}",
                    id, name, repositoryId, headCommitStr, createdAt != null ? createdAt : "");
        }

        // Getters
        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public int getRepositoryId() {
            return repositoryId;
        }

        public Integer getHeadCommitId() {
            return headCommitId;
        }

        public String getCreatedAt() {
            return createdAt;
        }
    }

    /**
     * コミットDTO
     */
    public static class Commit implements JsonSerializable {
        private final int id;
        private final int repositoryId;
        private final int authorId;
        private final String message;
        private final Integer parentCommitId;
        private final Integer parentCommitId2;
        private final String createdAt;

        public Commit(int id, int repositoryId, int authorId, String message,
                Integer parentCommitId, Integer parentCommitId2, String createdAt) {
            this.id = id;
            this.repositoryId = repositoryId;
            this.authorId = authorId;
            this.message = message;
            this.parentCommitId = parentCommitId;
            this.parentCommitId2 = parentCommitId2;
            this.createdAt = createdAt;
        }

        @Override
        public String toJson() {
            String parent1Str = parentCommitId != null ? parentCommitId.toString() : "null";
            String parent2Str = parentCommitId2 != null ? parentCommitId2.toString() : "null";
            return String.format(
                    "{\"id\":%d,\"repository_id\":%d,\"author_id\":%d,\"message\":\"%s\"," +
                            "\"parent_commit_id\":%s,\"parent_commit_id_2\":%s,\"created_at\":\"%s\"}",
                    id, repositoryId, authorId, message.replace("\"", "\\\""),
                    parent1Str, parent2Str, createdAt != null ? createdAt : "");
        }

        // Getters
        public int getId() {
            return id;
        }

        public int getRepositoryId() {
            return repositoryId;
        }

        public int getAuthorId() {
            return authorId;
        }

        public String getMessage() {
            return message;
        }

        public Integer getParentCommitId() {
            return parentCommitId;
        }

        public Integer getParentCommitId2() {
            return parentCommitId2;
        }

        public String getCreatedAt() {
            return createdAt;
        }
    }

    /**
     * ファイルDTO
     */
    public static class GitFile implements JsonSerializable {
        private final int id;
        private final int commitId;
        private final String filename;
        private final String content;
        private final int fileSize;
        private final String createdAt;

        public GitFile(int id, int commitId, String filename, String content, int fileSize, String createdAt) {
            this.id = id;
            this.commitId = commitId;
            this.filename = filename;
            this.content = content;
            this.fileSize = fileSize;
            this.createdAt = createdAt;
        }

        @Override
        public String toJson() {
            return String.format(
                    "{\"file_id\":%d,\"commit_id\":%d,\"filename\":\"%s\",\"text\":\"%s\",\"file_size\":%d,\"created_at\":\"%s\"}",
                    id, commitId, filename != null ? filename : "",
                    content != null ? content.replace("\"", "\\\"").replace("\n", "\\n") : "",
                    fileSize, createdAt != null ? createdAt : "");
        }

        // Getters
        public int getId() {
            return id;
        }

        public int getCommitId() {
            return commitId;
        }

        public String getFilename() {
            return filename;
        }

        public String getContent() {
            return content;
        }

        public int getFileSize() {
            return fileSize;
        }

        public String getCreatedAt() {
            return createdAt;
        }
    }

    // ===== APIハンドラー実装 =====

    /**
     * ユーザーAPIハンドラー
     * ユーザーの作成、取得、削除機能を提供
     */
    public static class UserHandler extends BaseHttpHandler {

        @Override
        protected void processRequest(HttpExchange exchange, String method) throws Exception {
            switch (method) {
                case "GET" -> handleGetUsers(exchange);
                case "POST" -> handleCreateUser(exchange);
                case "DELETE" -> handleDeleteUser(exchange);
                default -> sendErrorResponse(exchange, 405, "Method not allowed");
            }
        }

        /**
         * ユーザー一覧を取得
         * 
         * @param exchange HTTPExchange
         * @throws Exception 処理エラー
         */
        private void handleGetUsers(HttpExchange exchange) throws Exception {
            List<User> users = new ArrayList<>();
            String sql = "SELECT id, username, created_at FROM users ORDER BY id";

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql);
                    ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    users.add(new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("created_at")));
                }
            }

            String response = JsonResponseBuilder.buildListResponse(users, "users");
            sendResponse(exchange, 200, response);
        }

        /**
         * ユーザーを作成
         * 
         * @param exchange HTTPExchange
         * @throws Exception 処理エラー
         */
        private void handleCreateUser(HttpExchange exchange) throws Exception {
            String requestBody = readRequestBody(exchange);
            String username = parseJsonField(requestBody, "username");

            if (username.isEmpty()) {
                sendErrorResponse(exchange, 400, "Username is required");
                return;
            }

            String sql = "INSERT INTO users (username) VALUES (?)";
            boolean success = false;

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, username);
                int affected = stmt.executeUpdate();
                success = affected > 0;

            } catch (SQLException e) {
                if (e.getMessage().contains("UNIQUE constraint failed")) {
                    sendErrorResponse(exchange, 409, "Username already exists");
                    return;
                }
                throw e;
            }

            String response = JsonResponseBuilder.buildSuccessResponse(success);
            sendResponse(exchange, success ? 201 : 500, response);
        }

        /**
         * ユーザーを削除
         * 
         * @param exchange HTTPExchange
         * @throws Exception 処理エラー
         */
        private void handleDeleteUser(HttpExchange exchange) throws Exception {
            String requestBody = readRequestBody(exchange);
            String idStr = parseJsonField(requestBody, "id");

            if (idStr.isEmpty()) {
                sendErrorResponse(exchange, 400, "User ID is required");
                return;
            }

            String sql = "DELETE FROM users WHERE id = ?";
            boolean success = false;

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, Integer.parseInt(idStr));
                int affected = stmt.executeUpdate();
                success = affected > 0;
            }

            String response = JsonResponseBuilder.buildSuccessResponse(success);
            sendResponse(exchange, 200, response);
        }
    }

    /**
     * リポジトリAPIハンドラー
     * リポジトリの作成、取得、削除機能を提供
     */
    public static class RepositoryHandler extends BaseHttpHandler {

        @Override
        protected void processRequest(HttpExchange exchange, String method) throws Exception {
            switch (method) {
                case "GET" -> handleGetRepositories(exchange);
                case "POST" -> handleCreateRepository(exchange);
                case "DELETE" -> handleDeleteRepository(exchange);
                default -> sendErrorResponse(exchange, 405, "Method not allowed");
            }
        }

        /**
         * リポジトリ一覧を取得
         * 
         * @param exchange HTTPExchange
         * @throws Exception 処理エラー
         */
        private void handleGetRepositories(HttpExchange exchange) throws Exception {
            String query = exchange.getRequestURI().getQuery();
            String ownerId = null;

            if (query != null && query.contains("owner_id=")) {
                String[] params = query.split("&");
                for (String param : params) {
                    if (param.startsWith("owner_id=")) {
                        ownerId = param.substring("owner_id=".length());
                        break;
                    }
                }
            }

            List<Repository> repositories = new ArrayList<>();
            String sql = ownerId != null
                    ? "SELECT id, name, owner_id, created_at FROM repositories WHERE owner_id = ? ORDER BY id"
                    : "SELECT id, name, owner_id, created_at FROM repositories ORDER BY id";

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                if (ownerId != null) {
                    stmt.setInt(1, Integer.parseInt(ownerId));
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        repositories.add(new Repository(
                                rs.getInt("id"),
                                rs.getString("name"),
                                rs.getInt("owner_id"),
                                rs.getString("created_at")));
                    }
                }
            }

            String response = JsonResponseBuilder.buildListResponse(repositories, "repositories");
            sendResponse(exchange, 200, response);
        }

        /**
         * リポジトリを作成
         * 
         * @param exchange HTTPExchange
         * @throws Exception 処理エラー
         */
        private void handleCreateRepository(HttpExchange exchange) throws Exception {
            String requestBody = readRequestBody(exchange);
            String name = parseJsonField(requestBody, "name");
            String ownerIdStr = parseJsonField(requestBody, "owner_id");

            if (name.isEmpty() || ownerIdStr.isEmpty()) {
                sendErrorResponse(exchange, 400, "Name and owner_id are required");
                return;
            }

            String sql = "INSERT INTO repositories (name, owner_id) VALUES (?, ?)";
            boolean success = false;

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, name);
                stmt.setInt(2, Integer.parseInt(ownerIdStr));
                int affected = stmt.executeUpdate();
                success = affected > 0;
            }

            String response = JsonResponseBuilder.buildSuccessResponse(success);
            sendResponse(exchange, success ? 201 : 500, response);
        }

        /**
         * リポジトリを削除
         * 
         * @param exchange HTTPExchange
         * @throws Exception 処理エラー
         */
        private void handleDeleteRepository(HttpExchange exchange) throws Exception {
            String requestBody = readRequestBody(exchange);
            String idStr = parseJsonField(requestBody, "id");

            if (idStr.isEmpty()) {
                sendErrorResponse(exchange, 400, "Repository ID is required");
                return;
            }

            String sql = "DELETE FROM repositories WHERE id = ?";
            boolean success = false;

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, Integer.parseInt(idStr));
                int affected = stmt.executeUpdate();
                success = affected > 0;
            }

            String response = JsonResponseBuilder.buildSuccessResponse(success);
            sendResponse(exchange, 200, response);
        }
    }

    /**
     * ブランチAPIハンドラー
     * ブランチの作成、取得、削除、更新機能を提供
     */
    public static class BranchHandler extends BaseHttpHandler {

        @Override
        protected void processRequest(HttpExchange exchange, String method) throws Exception {
            switch (method) {
                case "GET" -> handleGetBranches(exchange);
                case "POST" -> handleCreateBranch(exchange);
                case "PUT" -> handleUpdateBranch(exchange);
                case "DELETE" -> handleDeleteBranch(exchange);
                default -> sendErrorResponse(exchange, 405, "Method not allowed");
            }
        }

        /**
         * ブランチ一覧を取得
         * 
         * @param exchange HTTPExchange
         * @throws Exception 処理エラー
         */
        private void handleGetBranches(HttpExchange exchange) throws Exception {
            String query = exchange.getRequestURI().getQuery();
            String repositoryId = null;

            if (query != null && query.contains("repository_id=")) {
                String[] params = query.split("&");
                for (String param : params) {
                    if (param.startsWith("repository_id=")) {
                        repositoryId = param.substring("repository_id=".length());
                        break;
                    }
                }
            }

            List<Branch> branches = new ArrayList<>();
            String sql = repositoryId != null
                    ? "SELECT id, name, repository_id, head_commit_id, created_at FROM branches WHERE repository_id = ? ORDER BY id"
                    : "SELECT id, name, repository_id, head_commit_id, created_at FROM branches ORDER BY id";

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                if (repositoryId != null) {
                    stmt.setInt(1, Integer.parseInt(repositoryId));
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Integer headCommitId = rs.getInt("head_commit_id");
                        if (rs.wasNull())
                            headCommitId = null;

                        branches.add(new Branch(
                                rs.getInt("id"),
                                rs.getString("name"),
                                rs.getInt("repository_id"),
                                headCommitId,
                                rs.getString("created_at")));
                    }
                }
            }

            String response = JsonResponseBuilder.buildListResponse(branches, "branches");
            sendResponse(exchange, 200, response);
        }

        /**
         * ブランチを作成
         * 
         * @param exchange HTTPExchange
         * @throws Exception 処理エラー
         */
        private void handleCreateBranch(HttpExchange exchange) throws Exception {
            String requestBody = readRequestBody(exchange);
            String name = parseJsonField(requestBody, "name");
            String repositoryIdStr = parseJsonField(requestBody, "repository_id");

            if (name.isEmpty() || repositoryIdStr.isEmpty()) {
                sendErrorResponse(exchange, 400, "Name and repository_id are required");
                return;
            }

            String sql = "INSERT INTO branches (name, repository_id) VALUES (?, ?)";
            boolean success = false;

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, name);
                stmt.setInt(2, Integer.parseInt(repositoryIdStr));
                int affected = stmt.executeUpdate();
                success = affected > 0;
            }

            String response = JsonResponseBuilder.buildSuccessResponse(success);
            sendResponse(exchange, success ? 201 : 500, response);
        }

        /**
         * ブランチのHEADコミットを更新
         * 
         * @param exchange HTTPExchange
         * @throws Exception 処理エラー
         */
        private void handleUpdateBranch(HttpExchange exchange) throws Exception {
            String requestBody = readRequestBody(exchange);
            String branchIdStr = parseJsonField(requestBody, "id");
            String commitIdStr = parseJsonField(requestBody, "commit_id");

            if (branchIdStr.isEmpty() || commitIdStr.isEmpty()) {
                sendErrorResponse(exchange, 400, "Branch ID and commit ID are required");
                return;
            }

            String sql = "UPDATE branches SET head_commit_id = ? WHERE id = ?";
            boolean success = false;

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, Integer.parseInt(commitIdStr));
                stmt.setInt(2, Integer.parseInt(branchIdStr));
                int affected = stmt.executeUpdate();
                success = affected > 0;
            }

            String response = JsonResponseBuilder.buildSuccessResponse(success);
            sendResponse(exchange, 200, response);
        }

        /**
         * ブランチを削除
         * 
         * @param exchange HTTPExchange
         * @throws Exception 処理エラー
         */
        private void handleDeleteBranch(HttpExchange exchange) throws Exception {
            String requestBody = readRequestBody(exchange);
            String idStr = parseJsonField(requestBody, "id");

            if (idStr.isEmpty()) {
                sendErrorResponse(exchange, 400, "Branch ID is required");
                return;
            }

            String sql = "DELETE FROM branches WHERE id = ?";
            boolean success = false;

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, Integer.parseInt(idStr));
                int affected = stmt.executeUpdate();
                success = affected > 0;
            }

            String response = JsonResponseBuilder.buildSuccessResponse(success);
            sendResponse(exchange, 200, response);
        }
    }

    /**
     * コミットAPIハンドラー
     * コミットの作成、取得、削除機能を提供
     */
    public static class CommitHandler extends BaseHttpHandler {

        @Override
        protected void processRequest(HttpExchange exchange, String method) throws Exception {
            switch (method) {
                case "GET" -> handleGetCommits(exchange);
                case "POST" -> handleCreateCommit(exchange);
                case "DELETE" -> handleDeleteCommit(exchange);
                default -> sendErrorResponse(exchange, 405, "Method not allowed");
            }
        }

        /**
         * コミット一覧を取得
         * 
         * @param exchange HTTPExchange
         * @throws Exception 処理エラー
         */
        private void handleGetCommits(HttpExchange exchange) throws Exception {
            String query = exchange.getRequestURI().getQuery();
            String repositoryId = null;

            if (query != null && query.contains("repository_id=")) {
                String[] params = query.split("&");
                for (String param : params) {
                    if (param.startsWith("repository_id=")) {
                        repositoryId = param.substring("repository_id=".length());
                        break;
                    }
                }
            }

            List<Commit> commits = new ArrayList<>();
            String sql = repositoryId != null
                    ? "SELECT id, repository_id, author_id, message, parent_commit_id, parent_commit_id_2, created_at FROM commits WHERE repository_id = ? ORDER BY created_at DESC"
                    : "SELECT id, repository_id, author_id, message, parent_commit_id, parent_commit_id_2, created_at FROM commits ORDER BY created_at DESC";

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                if (repositoryId != null) {
                    stmt.setInt(1, Integer.parseInt(repositoryId));
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Integer parentId1 = rs.getInt("parent_commit_id");
                        if (rs.wasNull())
                            parentId1 = null;

                        Integer parentId2 = rs.getInt("parent_commit_id_2");
                        if (rs.wasNull())
                            parentId2 = null;

                        commits.add(new Commit(
                                rs.getInt("id"),
                                rs.getInt("repository_id"),
                                rs.getInt("author_id"),
                                rs.getString("message"),
                                parentId1,
                                parentId2,
                                rs.getString("created_at")));
                    }
                }
            }

            String response = JsonResponseBuilder.buildListResponse(commits, "commits");
            sendResponse(exchange, 200, response);
        }

        /**
         * コミットを作成
         * 
         * @param exchange HTTPExchange
         * @throws Exception 処理エラー
         */
        private void handleCreateCommit(HttpExchange exchange) throws Exception {
            String requestBody = readRequestBody(exchange);
            String branchIdStr = parseJsonField(requestBody, "branch_id");
            String message = parseJsonField(requestBody, "message");
            String authorIdStr = parseJsonField(requestBody, "author_id");
            String content = parseJsonField(requestBody, "content");

            if (branchIdStr.isEmpty() || message.isEmpty() || authorIdStr.isEmpty()) {
                sendErrorResponse(exchange, 400, "Branch ID, message, and author ID are required");
                return;
            }

            boolean success = createCommitWithTransaction(
                    Integer.parseInt(branchIdStr),
                    message,
                    Integer.parseInt(authorIdStr),
                    content);

            String response = JsonResponseBuilder.buildSuccessResponse(success);
            sendResponse(exchange, success ? 201 : 500, response);
        }

        /**
         * トランザクション内でコミットを作成
         * 
         * @param branchId ブランチID
         * @param message  コミットメッセージ
         * @param authorId 作成者ID
         * @param content  ファイル内容
         * @return 成功フラグ
         */
        private boolean createCommitWithTransaction(int branchId, String message, int authorId, String content) {
            try (Connection conn = dbManager.getConnection()) {
                conn.setAutoCommit(false);

                // ブランチ情報取得
                Integer parentCommitId = null;
                Integer repositoryId = null;

                String branchSql = "SELECT head_commit_id, repository_id FROM branches WHERE id = ?";
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
                String commitSql = "INSERT INTO commits (repository_id, author_id, message, parent_commit_id) VALUES (?, ?, ?, ?)";
                int newCommitId = -1;

                try (PreparedStatement stmt = conn.prepareStatement(commitSql,
                        PreparedStatement.RETURN_GENERATED_KEYS)) {
                    stmt.setInt(1, repositoryId);
                    stmt.setInt(2, authorId);
                    stmt.setString(3, message);
                    if (parentCommitId != null) {
                        stmt.setInt(4, parentCommitId);
                    } else {
                        stmt.setNull(4, java.sql.Types.INTEGER);
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
                String fileSql = "INSERT INTO files (commit_id, filename, content, file_size) VALUES (?, ?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(fileSql)) {
                    stmt.setInt(1, newCommitId);
                    stmt.setString(2, "main.txt");
                    stmt.setString(3, content);
                    stmt.setInt(4, content != null ? content.length() : 0);
                    stmt.executeUpdate();
                }

                // ブランチのHEAD更新
                String updateBranchSql = "UPDATE branches SET head_commit_id = ? WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(updateBranchSql)) {
                    stmt.setInt(1, newCommitId);
                    stmt.setInt(2, branchId);
                    stmt.executeUpdate();
                }

                conn.commit();
                return true;

            } catch (SQLException | NumberFormatException e) {
                System.err.println("Failed to create commit: " + e.getMessage());
                return false;
            }
        }

        /**
         * コミットを削除
         * 
         * @param exchange HTTPExchange
         * @throws Exception 処理エラー
         */
        private void handleDeleteCommit(HttpExchange exchange) throws Exception {
            String requestBody = readRequestBody(exchange);
            String idStr = parseJsonField(requestBody, "id");

            if (idStr.isEmpty()) {
                sendErrorResponse(exchange, 400, "Commit ID is required");
                return;
            }

            String sql = "DELETE FROM commits WHERE id = ?";
            boolean success = false;

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, Integer.parseInt(idStr));
                int affected = stmt.executeUpdate();
                success = affected > 0;
            }

            String response = JsonResponseBuilder.buildSuccessResponse(success);
            sendResponse(exchange, 200, response);
        }
    }

    /**
     * ファイルAPIハンドラー
     * ファイルの取得機能を提供
     */
    public static class FileHandler extends BaseHttpHandler {

        @Override
        protected void processRequest(HttpExchange exchange, String method) throws Exception {
            if ("GET".equals(method)) {
                handleGetFiles(exchange);
            } else {
                sendErrorResponse(exchange, 405, "Method not allowed");
            }
        }

        /**
         * 指定ブランチのファイル一覧を取得
         * 
         * @param exchange HTTPExchange
         * @throws Exception 処理エラー
         */
        private void handleGetFiles(HttpExchange exchange) throws Exception {
            String query = exchange.getRequestURI().getQuery();
            String branchId = null;

            if (query != null && query.contains("branch_id=")) {
                String[] params = query.split("&");
                for (String param : params) {
                    if (param.startsWith("branch_id=")) {
                        branchId = param.substring("branch_id=".length());
                        break;
                    }
                }
            }

            if (branchId == null) {
                sendErrorResponse(exchange, 400, "Branch ID is required");
                return;
            }

            List<GitFile> files = new ArrayList<>();

            // ブランチのHEADコミット取得
            Integer headCommitId = null;
            String branchSql = "SELECT head_commit_id FROM branches WHERE id = ?";

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(branchSql)) {

                stmt.setInt(1, Integer.parseInt(branchId));
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        headCommitId = rs.getInt("head_commit_id");
                        if (rs.wasNull())
                            headCommitId = null;
                    }
                }

                // HEADコミットのファイル取得
                if (headCommitId != null) {
                    String fileSql = "SELECT id, commit_id, filename, content, file_size, created_at FROM files WHERE commit_id = ?";
                    try (PreparedStatement fileStmt = conn.prepareStatement(fileSql)) {
                        fileStmt.setInt(1, headCommitId);
                        try (ResultSet fileRs = fileStmt.executeQuery()) {
                            while (fileRs.next()) {
                                files.add(new GitFile(
                                        fileRs.getInt("id"),
                                        fileRs.getInt("commit_id"),
                                        fileRs.getString("filename"),
                                        fileRs.getString("content"),
                                        fileRs.getInt("file_size"),
                                        fileRs.getString("created_at")));
                            }
                        }
                    }
                }
            }

            String response = JsonResponseBuilder.buildListResponse(files, "files");
            sendResponse(exchange, 200, response);
        }
    }

    /**
     * マージAPIハンドラー
     * ブランチのマージ機能を提供
     */
    public static class MergeHandler extends BaseHttpHandler {

        @Override
        protected void processRequest(HttpExchange exchange, String method) throws Exception {
            if ("POST".equals(method)) {
                handleMerge(exchange);
            } else {
                sendErrorResponse(exchange, 405, "Method not allowed");
            }
        }

        /**
         * ブランチマージを実行
         * 
         * @param exchange HTTPExchange
         * @throws Exception 処理エラー
         */
        private void handleMerge(HttpExchange exchange) throws Exception {
            String requestBody = readRequestBody(exchange);
            String branchId1Str = parseJsonField(requestBody, "branch_id_1");
            String branchId2Str = parseJsonField(requestBody, "branch_id_2");

            if (branchId1Str.isEmpty() || branchId2Str.isEmpty()) {
                sendErrorResponse(exchange, 400, "Both branch IDs are required");
                return;
            }

            try {
                MergeResult result = performMerge(Integer.parseInt(branchId1Str), Integer.parseInt(branchId2Str));
                sendResponse(exchange, 200, result.toJson());
            } catch (NumberFormatException e) {
                sendErrorResponse(exchange, 400, "Invalid branch ID format");
            }
        }

        /**
         * マージを実行
         * 
         * @param branchId1 マージ元ブランチID
         * @param branchId2 マージ先ブランチID
         * @return マージ結果
         */
        private MergeResult performMerge(int branchId1, int branchId2) {
            try (Connection conn = dbManager.getConnection()) {
                conn.setAutoCommit(false);

                // 各ブランチのHEADコミット取得
                Integer head1 = null, head2 = null, repositoryId = null;
                String sql = "SELECT head_commit_id, repository_id FROM branches WHERE id = ?";

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, branchId1);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            head1 = rs.getInt("head_commit_id");
                            if (rs.wasNull())
                                head1 = null;
                            repositoryId = rs.getInt("repository_id");
                        }
                    }

                    stmt.setInt(1, branchId2);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            head2 = rs.getInt("head_commit_id");
                            if (rs.wasNull())
                                head2 = null;
                        }
                    }
                }

                if (head1 == null || head2 == null || repositoryId == null) {
                    return new MergeResult(false, "No HEAD commit found", null, null, null, null);
                }

                // 各HEADコミットのファイル内容取得
                String content1 = getFileContent(conn, head1);
                String content2 = getFileContent(conn, head2);

                if (content1.equals(content2)) {
                    // 内容が同じ場合：マージコミット作成
                    int newCommitId = createMergeCommit(conn, repositoryId, head1, head2, content1);
                    if (newCommitId > 0) {
                        // 両ブランチのHEAD更新
                        updateBranchHead(conn, branchId1, newCommitId);
                        updateBranchHead(conn, branchId2, newCommitId);
                        conn.commit();
                        return new MergeResult(true, "Merge successful", branchId1, branchId2, null, null);
                    }
                }

                // コンフリクトの場合
                conn.rollback();
                return new MergeResult(false, "Merge conflict", branchId1, branchId2, content1, content2);

            } catch (SQLException e) {
                System.err.println("Merge failed: " + e.getMessage());
                return new MergeResult(false, "Merge error: " + e.getMessage(), null, null, null, null);
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
                    if (rs.next()) {
                        return rs.getString("content") != null ? rs.getString("content") : "";
                    }
                }
            }
            return "";
        }

        /**
         * マージコミットを作成
         * 
         * @param conn         データベース接続
         * @param repositoryId リポジトリID
         * @param parent1      親コミット1
         * @param parent2      親コミット2
         * @param content      ファイル内容
         * @return 新規コミットID
         * @throws SQLException データベースエラー
         */
        private int createMergeCommit(Connection conn, int repositoryId, int parent1, int parent2, String content)
                throws SQLException {
            String commitSql = "INSERT INTO commits (repository_id, author_id, message, parent_commit_id, parent_commit_id_2) VALUES (?, 1, ?, ?, ?)";
            int newCommitId = -1;

            try (PreparedStatement stmt = conn.prepareStatement(commitSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, repositoryId);
                stmt.setString(2, "Merge commit");
                stmt.setInt(3, parent1);
                stmt.setInt(4, parent2);

                stmt.executeUpdate();

                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        newCommitId = rs.getInt(1);
                    }
                }
            }

            if (newCommitId > 0) {
                // ファイル作成
                String fileSql = "INSERT INTO files (commit_id, filename, content, file_size) VALUES (?, ?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(fileSql)) {
                    stmt.setInt(1, newCommitId);
                    stmt.setString(2, "main.txt");
                    stmt.setString(3, content);
                    stmt.setInt(4, content.length());
                    stmt.executeUpdate();
                }
            }

            return newCommitId;
        }

        /**
         * ブランチのHEADを更新
         * 
         * @param conn     データベース接続
         * @param branchId ブランチID
         * @param commitId コミットID
         * @throws SQLException データベースエラー
         */
        private void updateBranchHead(Connection conn, int branchId, int commitId) throws SQLException {
            String sql = "UPDATE branches SET head_commit_id = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, commitId);
                stmt.setInt(2, branchId);
                stmt.executeUpdate();
            }
        }
    }

    /**
     * 強制マージAPIハンドラー
     * コンフリクトを解決して強制的にマージを実行
     */
    public static class ForceMergeHandler extends BaseHttpHandler {

        @Override
        protected void processRequest(HttpExchange exchange, String method) throws Exception {
            if ("POST".equals(method)) {
                handleForceMerge(exchange);
            } else {
                sendErrorResponse(exchange, 405, "Method not allowed");
            }
        }

        /**
         * 強制マージを実行
         * 
         * @param exchange HTTPExchange
         * @throws Exception 処理エラー
         */
        private void handleForceMerge(HttpExchange exchange) throws Exception {
            String requestBody = readRequestBody(exchange);
            String branchId1Str = parseJsonField(requestBody, "branch_id_1");
            String branchId2Str = parseJsonField(requestBody, "branch_id_2");
            String text = parseJsonField(requestBody, "text");

            if (branchId1Str.isEmpty() || branchId2Str.isEmpty()) {
                sendErrorResponse(exchange, 400, "Both branch IDs are required");
                return;
            }

            boolean success = performForceMerge(
                    Integer.parseInt(branchId1Str),
                    Integer.parseInt(branchId2Str),
                    text);

            String response = JsonResponseBuilder.buildSuccessResponse(success);
            sendResponse(exchange, 200, response);
        }

        /**
         * 強制マージを実行
         * 
         * @param branchId1       ブランチ1ID
         * @param branchId2       ブランチ2ID
         * @param resolvedContent 解決済み内容
         * @return 成功フラグ
         */
        private boolean performForceMerge(int branchId1, int branchId2, String resolvedContent) {
            try (Connection conn = dbManager.getConnection()) {
                conn.setAutoCommit(false);

                // ブランチ情報取得
                Integer head1 = null, head2 = null, repositoryId = null;
                String sql = "SELECT head_commit_id, repository_id FROM branches WHERE id = ?";

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, branchId1);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            head1 = rs.getInt("head_commit_id");
                            if (rs.wasNull())
                                head1 = null;
                            repositoryId = rs.getInt("repository_id");
                        }
                    }

                    stmt.setInt(1, branchId2);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            head2 = rs.getInt("head_commit_id");
                            if (rs.wasNull())
                                head2 = null;
                        }
                    }
                }

                if (head1 == null || head2 == null || repositoryId == null) {
                    return false;
                }

                // 強制マージコミット作成
                String commitSql = "INSERT INTO commits (repository_id, author_id, message, parent_commit_id, parent_commit_id_2) VALUES (?, 1, ?, ?, ?)";
                int newCommitId = -1;

                try (PreparedStatement stmt = conn.prepareStatement(commitSql,
                        PreparedStatement.RETURN_GENERATED_KEYS)) {
                    stmt.setInt(1, repositoryId);
                    stmt.setString(2, "Force merge commit");
                    stmt.setInt(3, head1);
                    stmt.setInt(4, head2);

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

                // ファイル作成
                String fileSql = "INSERT INTO files (commit_id, filename, content, file_size) VALUES (?, ?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(fileSql)) {
                    stmt.setInt(1, newCommitId);
                    stmt.setString(2, "main.txt");
                    stmt.setString(3, resolvedContent);
                    stmt.setInt(4, resolvedContent.length());
                    stmt.executeUpdate();
                }

                // 両ブランチのHEAD更新
                String updateSql = "UPDATE branches SET head_commit_id = ? WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                    stmt.setInt(1, newCommitId);
                    stmt.setInt(2, branchId1);
                    stmt.executeUpdate();

                    stmt.setInt(2, branchId2);
                    stmt.executeUpdate();
                }

                conn.commit();
                return true;

            } catch (SQLException | NumberFormatException e) {
                System.err.println("Force merge failed: " + e.getMessage());
                return false;
            }
        }
    }

    /**
     * グラフAPIハンドラー
     * コミットグラフの取得機能を提供
     */
    public static class GraphHandler extends BaseHttpHandler {

        @Override
        protected void processRequest(HttpExchange exchange, String method) throws Exception {
            if ("GET".equals(method)) {
                handleGetGraph(exchange);
            } else {
                sendErrorResponse(exchange, 405, "Method not allowed");
            }
        }

        /**
         * コミットグラフを取得
         * 
         * @param exchange HTTPExchange
         * @throws Exception 処理エラー
         */
        private void handleGetGraph(HttpExchange exchange) throws Exception {
            String query = exchange.getRequestURI().getQuery();
            String repositoryId = null;

            if (query != null && query.contains("repository_id=")) {
                String[] params = query.split("&");
                for (String param : params) {
                    if (param.startsWith("repository_id=")) {
                        repositoryId = param.substring("repository_id=".length());
                        break;
                    }
                }
            }

            if (repositoryId == null) {
                sendErrorResponse(exchange, 400, "Repository ID is required");
                return;
            }

            String graphJson = generateGraphJson(Integer.parseInt(repositoryId));
            sendResponse(exchange, 200, graphJson);
        }

        /**
         * グラフJSONを生成
         * 
         * @param repositoryId リポジトリID
         * @return グラフJSON文字列
         */
        private String generateGraphJson(int repositoryId) {
            List<String> nodes = new ArrayList<>();
            List<String> edges = new ArrayList<>();

            try (Connection conn = dbManager.getConnection()) {
                // コミットノード追加
                String commitSql = "SELECT id, message, parent_commit_id, parent_commit_id_2 FROM commits WHERE repository_id = ? ORDER BY created_at";
                try (PreparedStatement stmt = conn.prepareStatement(commitSql)) {
                    stmt.setInt(1, repositoryId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            int commitId = rs.getInt("id");
                            String message = rs.getString("message");
                            Integer parent1 = rs.getInt("parent_commit_id");
                            if (rs.wasNull())
                                parent1 = null;
                            Integer parent2 = rs.getInt("parent_commit_id_2");
                            if (rs.wasNull())
                                parent2 = null;

                            // ノード追加
                            nodes.add(String.format(
                                    "{\"id\":%d,\"label\":\"%s\",\"shape\":\"dot\"}",
                                    commitId, message.replace("\"", "\\\"")));

                            // エッジ追加
                            if (parent1 != null) {
                                edges.add(String.format("{\"from\":%d,\"to\":%d}", parent1, commitId));
                            }
                            if (parent2 != null) {
                                edges.add(String.format(
                                        "{\"from\":%d,\"to\":%d,\"dashes\":true,\"color\":\"#ff6b6b\"}",
                                        parent2, commitId));
                            }
                        }
                    }
                }

                // ブランチノード追加
                String branchSql = "SELECT id, name, head_commit_id FROM branches WHERE repository_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(branchSql)) {
                    stmt.setInt(1, repositoryId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            int branchId = rs.getInt("id");
                            String branchName = rs.getString("name");
                            Integer headCommitId = rs.getInt("head_commit_id");
                            if (rs.wasNull())
                                headCommitId = null;

                            if (headCommitId != null) {
                                // ブランチノード
                                nodes.add(String.format(
                                        "{\"id\":\"branch-%d\",\"label\":\"%s\",\"shape\":\"ellipse\",\"color\":\"#4ecdc4\"}",
                                        branchId, branchName));

                                // ブランチ→コミットエッジ
                                edges.add(String.format(
                                        "{\"from\":\"branch-%d\",\"to\":%d,\"color\":\"#4ecdc4\",\"label\":\"%s\"}",
                                        branchId, headCommitId, branchName));
                            }
                        }
                    }
                }

            } catch (SQLException e) {
                System.err.println("Failed to generate graph: " + e.getMessage());
                return String.format("{\"nodes\":[],\"edges\":[],\"error\":\"%s\"}", e.getMessage());
            }

            return String.format(
                    "{\"nodes\":[%s],\"edges\":[%s]}",
                    String.join(",", nodes),
                    String.join(",", edges));
        }
    }

    /**
     * マージ結果を表すクラス
     */
    public static class MergeResult {
        private final boolean success;
        private final String message;
        private final Integer branchId1;
        private final Integer branchId2;
        private final String text1;
        private final String text2;

        public MergeResult(boolean success, String message, Integer branchId1, Integer branchId2, String text1,
                String text2) {
            this.success = success;
            this.message = message;
            this.branchId1 = branchId1;
            this.branchId2 = branchId2;
            this.text1 = text1;
            this.text2 = text2;
        }

        /**
         * JSON形式に変換
         * 
         * @return JSON文字列
         */
        public String toJson() {
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append(String.format("\"success\":%s", success));

            if (message != null) {
                json.append(String.format(",\"message\":\"%s\"", message.replace("\"", "\\\"")));
            }

            if (branchId1 != null) {
                json.append(String.format(",\"branch_id_1\":%d", branchId1));
            }

            if (branchId2 != null) {
                json.append(String.format(",\"branch_id_2\":%d", branchId2));
            }

            if (text1 != null) {
                json.append(String.format(",\"text_1\":\"%s\"", text1.replace("\"", "\\\"").replace("\n", "\\n")));
            }

            if (text2 != null) {
                json.append(String.format(",\"text_2\":\"%s\"", text2.replace("\"", "\\\"").replace("\n", "\\n")));
            }

            json.append("}");
            return json.toString();
        }

        // Getters
        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public Integer getBranchId1() {
            return branchId1;
        }

        public Integer getBranchId2() {
            return branchId2;
        }

        public String getText1() {
            return text1;
        }

        public String getText2() {
            return text2;
        }
    }

    // 静的初期化ブロック - JDBCドライバーの読み込み
    static {
        try {
            Class.forName("org.sqlite.JDBC");
            System.out.println("SQLite JDBC driver loaded successfully");
        } catch (ClassNotFoundException e) {
            System.err.println("Failed to load SQLite JDBC driver: " + e.getMessage());
            System.exit(1);
        }
    }
}