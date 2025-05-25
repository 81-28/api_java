package src;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Command Pattern - Git操作のコマンドクラス群
 * 各Git操作をコマンドオブジェクトとして実装
 */
public class GitUtils {

    /**
     * コマンドの基底インターフェース
     */
    public interface GitCommand {
        /**
         * コマンドを実行
         * 
         * @return 実行結果
         */
        CommandResult execute();

        /**
         * コマンドを元に戻す（可能な場合）
         * 
         * @return 元に戻す結果
         */
        default CommandResult undo() {
            return new CommandResult(false, "Undo not supported for this command");
        }

        /**
         * コマンドの説明を取得
         * 
         * @return コマンド説明
         */
        String getDescription();
    }

    /**
     * コマンド実行結果
     */
    public static class CommandResult {
        private final boolean success;
        private final String message;
        private final Map<String, Object> data;

        public CommandResult(boolean success, String message) {
            this(success, message, new HashMap<>());
        }

        public CommandResult(boolean success, String message, Map<String, Object> data) {
            this.success = success;
            this.message = message;
            this.data = data != null ? data : new HashMap<>();
        }

        // Getters
        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public Map<String, Object> getData() {
            return data;
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
            json.append(String.format(",\"message\":\"%s\"", message.replace("\"", "\\\"")));

            if (!data.isEmpty()) {
                json.append(",\"data\":{");
                List<String> dataEntries = new ArrayList<>();
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    String value = entry.getValue() != null
                            ? String.format("\"%s\"", entry.getValue().toString().replace("\"", "\\\""))
                            : "null";
                    dataEntries.add(String.format("\"%s\":%s", entry.getKey(), value));
                }
                json.append(String.join(",", dataEntries));
                json.append("}");
            }

            json.append("}");
            return json.toString();
        }
    }

    /**
     * Invoker - コマンドを実行するクラス
     */
    public static class GitCommandInvoker {
        private final Stack<GitCommand> commandHistory;
        private final int maxHistorySize;

        public GitCommandInvoker() {
            this(100); // デフォルトで100コマンドまで履歴保持
        }

        public GitCommandInvoker(int maxHistorySize) {
            this.commandHistory = new Stack<>();
            this.maxHistorySize = maxHistorySize;
        }

        /**
         * コマンドを実行
         * 
         * @param command 実行するコマンド
         * @return 実行結果
         */
        public CommandResult executeCommand(GitCommand command) {
            CommandResult result = command.execute();

            if (result.isSuccess()) {
                commandHistory.push(command);

                // 履歴サイズ制限
                if (commandHistory.size() > maxHistorySize) {
                    // 古いコマンドを削除（FIFOではなくLIFO制限）
                    Stack<GitCommand> newHistory = new Stack<>();
                    for (int i = commandHistory.size() - maxHistorySize; i < commandHistory.size(); i++) {
                        newHistory.push(commandHistory.get(i));
                    }
                    commandHistory.clear();
                    commandHistory.addAll(newHistory);
                }
            }

            return result;
        }

        /**
         * 最後のコマンドを元に戻す
         * 
         * @return 元に戻す結果
         */
        public CommandResult undoLastCommand() {
            if (commandHistory.isEmpty()) {
                return new CommandResult(false, "No commands to undo");
            }

            GitCommand lastCommand = commandHistory.pop();
            return lastCommand.undo();
        }

        /**
         * コマンド履歴を取得
         * 
         * @return コマンド履歴のリスト
         */
        public List<String> getCommandHistory() {
            List<String> history = new ArrayList<>();
            for (GitCommand command : commandHistory) {
                history.add(command.getDescription());
            }
            return history;
        }

        /**
         * 履歴をクリア
         */
        public void clearHistory() {
            commandHistory.clear();
        }
    }

    /**
     * バリデーション用ユーティリティクラス
     */
    public static class ValidationUtils {

        // 各種パターン定義
        private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,30}$");
        private static final Pattern REPO_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]{1,100}$");
        private static final Pattern BRANCH_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_/-]{1,100}$");
        private static final Pattern FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]{1,255}$");

        /**
         * ユーザー名を検証
         * 
         * @param username ユーザー名
         * @return 検証結果
         */
        public static ValidationResult validateUsername(String username) {
            if (username == null || username.trim().isEmpty()) {
                return new ValidationResult(false, "ユーザー名は必須です");
            }

            if (!USERNAME_PATTERN.matcher(username).matches()) {
                return new ValidationResult(false, "ユーザー名は3-30文字の英数字、アンダースコア、ハイフンのみ使用可能です");
            }

            return new ValidationResult(true, "Valid username");
        }

        /**
         * リポジトリ名を検証
         * 
         * @param repoName リポジトリ名
         * @return 検証結果
         */
        public static ValidationResult validateRepositoryName(String repoName) {
            if (repoName == null || repoName.trim().isEmpty()) {
                return new ValidationResult(false, "リポジトリ名は必須です");
            }

            if (!REPO_NAME_PATTERN.matcher(repoName).matches()) {
                return new ValidationResult(false, "リポジトリ名は1-100文字の英数字、アンダースコア、ドット、ハイフンのみ使用可能です");
            }

            return new ValidationResult(true, "Valid repository name");
        }

        /**
         * ブランチ名を検証
         * 
         * @param branchName ブランチ名
         * @return 検証結果
         */
        public static ValidationResult validateBranchName(String branchName) {
            if (branchName == null || branchName.trim().isEmpty()) {
                return new ValidationResult(false, "ブランチ名は必須です");
            }

            if (!BRANCH_NAME_PATTERN.matcher(branchName).matches()) {
                return new ValidationResult(false, "ブランチ名は1-100文字の英数字、アンダースコア、スラッシュ、ハイフンのみ使用可能です");
            }

            // 予約語チェック
            if (isReservedBranchName(branchName)) {
                return new ValidationResult(false, "予約されたブランチ名は使用できません");
            }

            return new ValidationResult(true, "Valid branch name");
        }

        /**
         * ファイル名を検証
         * 
         * @param filename ファイル名
         * @return 検証結果
         */
        public static ValidationResult validateFilename(String filename) {
            if (filename == null || filename.trim().isEmpty()) {
                return new ValidationResult(false, "ファイル名は必須です");
            }

            if (!FILENAME_PATTERN.matcher(filename).matches()) {
                return new ValidationResult(false, "ファイル名は1-255文字の英数字、アンダースコア、ドット、ハイフンのみ使用可能です");
            }

            return new ValidationResult(true, "Valid filename");
        }

        /**
         * コミットメッセージを検証
         * 
         * @param message コミットメッセージ
         * @return 検証結果
         */
        public static ValidationResult validateCommitMessage(String message) {
            if (message == null || message.trim().isEmpty()) {
                return new ValidationResult(false, "コミットメッセージは必須です");
            }

            if (message.length() > 500) {
                return new ValidationResult(false, "コミットメッセージは500文字以内で入力してください");
            }

            return new ValidationResult(true, "Valid commit message");
        }

        /**
         * 予約されたブランチ名かどうかをチェック
         * 
         * @param branchName ブランチ名
         * @return 予約名かどうか
         */
        private static boolean isReservedBranchName(String branchName) {
            Set<String> reservedNames = Set.of("HEAD", "refs", "objects", "hooks", "info");
            return reservedNames.contains(branchName.toLowerCase());
        }
    }

    /**
     * 検証結果クラス
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * 日時ユーティリティクラス
     */
    public static class DateTimeUtils {

        private static final DateTimeFormatter STANDARD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        /**
         * 現在日時を標準フォーマットで取得
         * 
         * @return フォーマット済み日時文字列
         */
        public static String getCurrentTimestamp() {
            return LocalDateTime.now().format(STANDARD_FORMATTER);
        }

        /**
         * 現在日時をISOフォーマットで取得
         * 
         * @return ISO形式の日時文字列
         */
        public static String getCurrentISOTimestamp() {
            return LocalDateTime.now().format(ISO_FORMATTER);
        }

        /**
         * 相対時間を計算
         * 
         * @param timestamp タイムスタンプ文字列
         * @return 相対時間文字列
         */
        public static String getRelativeTime(String timestamp) {
            if (timestamp == null || timestamp.isEmpty()) {
                return "不明";
            }

            try {
                LocalDateTime dateTime = LocalDateTime.parse(timestamp, STANDARD_FORMATTER);
                LocalDateTime now = LocalDateTime.now();

                long minutes = java.time.Duration.between(dateTime, now).toMinutes();

                if (minutes < 1) {
                    return "たった今";
                } else if (minutes < 60) {
                    return minutes + "分前";
                } else if (minutes < 1440) { // 24時間
                    return (minutes / 60) + "時間前";
                } else if (minutes < 43200) { // 30日
                    return (minutes / 1440) + "日前";
                } else {
                    return dateTime.format(DateTimeFormatter.ofPattern("MM-dd"));
                }
            } catch (Exception e) {
                return timestamp;
            }
        }
    }

    /**
     * 文字列操作ユーティリティクラス
     */
    public static class StringUtils {

        /**
         * 文字列をJSONエスケープ
         * 
         * @param str エスケープする文字列
         * @return エスケープされた文字列
         */
        public static String escapeJson(String str) {
            if (str == null)
                return "null";

            return str.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }

        /**
         * 文字列を指定長で切り詰め
         * 
         * @param str       対象文字列
         * @param maxLength 最大長
         * @return 切り詰められた文字列
         */
        public static String truncate(String str, int maxLength) {
            if (str == null)
                return null;
            if (str.length() <= maxLength)
                return str;

            return str.substring(0, maxLength - 3) + "...";
        }

        /**
         * 文字列がnullまたは空かどうかをチェック
         * 
         * @param str チェックする文字列
         * @return nullまたは空の場合true
         */
        public static boolean isNullOrEmpty(String str) {
            return str == null || str.trim().isEmpty();
        }

        /**
         * 文字列をキャメルケースに変換
         * 
         * @param str 対象文字列
         * @return キャメルケース文字列
         */
        public static String toCamelCase(String str) {
            if (isNullOrEmpty(str))
                return str;

            StringBuilder result = new StringBuilder();
            boolean capitalizeNext = false;

            for (char c : str.toCharArray()) {
                if (c == '_' || c == '-' || c == ' ') {
                    capitalizeNext = true;
                } else if (capitalizeNext) {
                    result.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    result.append(Character.toLowerCase(c));
                }
            }

            return result.toString();
        }
    }

    /**
     * ファイル操作ユーティリティクラス
     */
    public static class FileUtils {

        /**
         * ファイルサイズを人間が読める形式に変換
         * 
         * @param sizeInBytes バイト単位のサイズ
         * @return 人間が読める形式のサイズ
         */
        public static String formatFileSize(long sizeInBytes) {
            if (sizeInBytes < 1024) {
                return sizeInBytes + " B";
            }

            String[] units = { "B", "KB", "MB", "GB", "TB" };
            int unitIndex = 0;
            double size = sizeInBytes;

            while (size >= 1024 && unitIndex < units.length - 1) {
                size /= 1024;
                unitIndex++;
            }

            return String.format("%.1f %s", size, units[unitIndex]);
        }

        /**
         * ファイル拡張子を取得
         * 
         * @param filename ファイル名
         * @return 拡張子（ドット含む）
         */
        public static String getFileExtension(String filename) {
            if (isNullOrEmpty(filename))
                return "";

            int lastDotIndex = filename.lastIndexOf('.');
            if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
                return "";
            }

            return filename.substring(lastDotIndex);
        }

        /**
         * ファイル名から拡張子を除いた部分を取得
         * 
         * @param filename ファイル名
         * @return 拡張子を除いたファイル名
         */
        public static String getFileNameWithoutExtension(String filename) {
            if (isNullOrEmpty(filename))
                return filename;

            int lastDotIndex = filename.lastIndexOf('.');
            if (lastDotIndex == -1) {
                return filename;
            }

            return filename.substring(0, lastDotIndex);
        }

        /**
         * ファイルタイプを推定
         * 
         * @param filename ファイル名
         * @return ファイルタイプ
         */
        public static String getFileType(String filename) {
            String extension = getFileExtension(filename).toLowerCase();

            return switch (extension) {
                case ".txt", ".md", ".readme" -> "text";
                case ".java", ".js", ".py", ".cpp", ".c", ".h" -> "code";
                case ".json", ".xml", ".yaml", ".yml" -> "config";
                case ".jpg", ".jpeg", ".png", ".gif", ".bmp" -> "image";
                case ".pdf", ".doc", ".docx" -> "document";
                default -> "unknown";
            };
        }

        /**
         * null または空文字列かどうかをチェック（プライベートヘルパー）
         * 
         * @param str チェックする文字列
         * @return null または空の場合 true
         */
        private static boolean isNullOrEmpty(String str) {
            return StringUtils.isNullOrEmpty(str);
        }
    }

    /**
     * セキュリティユーティリティクラス
     */
    public static class SecurityUtils {

        /**
         * 簡易的なハッシュ値を生成（実際のプロダクションでは適切なハッシュ関数を使用）
         * 
         * @param input 入力文字列
         * @return ハッシュ値
         */
        public static String generateSimpleHash(String input) {
            if (input == null)
                return "0";

            int hash = input.hashCode();
            return String.format("%08x", Math.abs(hash));
        }

        /**
         * コミットIDを生成（簡易実装）
         * 
         * @param content   コンテンツ
         * @param timestamp タイムスタンプ
         * @param author    作成者
         * @return コミットID
         */
        public static String generateCommitId(String content, String timestamp, String author) {
            String combined = (content != null ? content : "") +
                    (timestamp != null ? timestamp : "") +
                    (author != null ? author : "");
            return generateSimpleHash(combined);
        }

        /**
         * 入力値をサニタイズ
         * 
         * @param input 入力値
         * @return サニタイズされた値
         */
        public static String sanitizeInput(String input) {
            if (input == null)
                return null;

            return input.replaceAll("[<>&\"']", "")
                    .trim();
        }
    }
}