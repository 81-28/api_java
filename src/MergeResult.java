package src;

/**
 * マージ結果の種類（Java 17のSealed Classes使用）
 */
public sealed interface MergeResult
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