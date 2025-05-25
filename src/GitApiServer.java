package src;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Git-like Version Control System API Server
 * Java 17対応のシンプルなバージョン管理システム
 */
public class GitApiServer {
    private static final int PORT = 8080;

    /** 
     * サーバーを起動
     * 
     * @param args コマンドライン引数
     * @throws IOException サーバー起動エラー
     */
    public static void main(String[] args) throws IOException {
        // データベース初期化
        DatabaseManager.getInstance().initializeDatabase();

        // HTTPサーバー作成
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // APIハンドラー登録
        server.createContext("/api/user", new UserHandler());
        server.createContext("/api/repository", new RepositoryHandler());
        server.createContext("/api/branch", new BranchHandler());
        server.createContext("/api/commit", new CommitHandler());
        server.createContext("/api/file", new FileHandler());
        server.createContext("/api/merge", new MergeHandler());
        server.createContext("/api/force-merge", new ForceMergeHandler());
        server.createContext("/api/graph", new GraphHandler());

        server.setExecutor(null);
        server.start();

        System.out.println("Git API Server is running on http://localhost:" + PORT + "/api");
    }
}