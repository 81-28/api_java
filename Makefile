SHELL = cmd.exe
.SHELLFLAGS = /C

# Git APIサーバー用Makefile（テスト対応版）

# 変数定義(デバッグ用)
local_url = public/test.html

# 変数定義
SRC_DIR = src
TEST_DIR = test
BIN_DIR = bin
LIB_DIR = lib
DB_DIR = database
JUNIT_JAR = $(LIB_DIR)/junit-4.13.2.jar
HAMCREST_JAR = $(LIB_DIR)/hamcrest-core-1.3.jar

# OSとアーキテクチャの判定
ifeq ($(OS),Windows_NT)
    SQLITE_JAR = $(LIB_DIR)/sqlite-jdbc-3.30.1.jar
else
    # Macの場合
    ifeq ($(shell uname),Darwin)
        # M2チップ（ARM64）の場合
        ifeq ($(shell uname -m),arm64)
            SQLITE_JAR = $(LIB_DIR)/sqlite-jdbc.jar
        else
            SQLITE_JAR = $(LIB_DIR)/sqlite-jdbc-3.30.1.jar
        endif
    else
        # その他のOSの場合
        SQLITE_JAR = $(LIB_DIR)/sqlite-jdbc-3.30.1.jar
    endif
endif

MAIN_CLASS = src.GitApiServer

# OS判定
ifeq ($(OS),Windows_NT)
    CP_SEP = ;
else
    CP_SEP = :
endif

# クラスパス設定
LIBS = $(SQLITE_JAR)$(CP_SEP)$(JUNIT_JAR)$(CP_SEP)$(HAMCREST_JAR)
COMPILE_CP = .$(CP_SEP)$(LIBS)
RUN_CP = $(BIN_DIR)$(CP_SEP)$(LIBS)
TEST_CP = $(BIN_DIR)$(CP_SEP)$(LIBS)

# コンパイル対象のファイル
JAVA_FILES = $(wildcard src/*.java)
TEST_FILES = $(wildcard test/*.java)

# ===== OSごとにコマンドを変数で一元管理 =====
ifeq ($(OS),Windows_NT)
	MKDIR_DB = if not exist $(DB_DIR) mkdir $(DB_DIR)
	MKDIR_TEST = if not exist $(TEST_DIR) mkdir $(TEST_DIR)
	MKDIR_BIN = if not exist $(BIN_DIR) mkdir $(BIN_DIR)
	RM_BIN = if exist $(BIN_DIR) rmdir /s /q $(BIN_DIR)
	OPEN = start
	TEST_COMPILE = javac -cp "$(TEST_CP)" -d $(BIN_DIR) $(TEST_DIR)\*.java && echo テストコードのコンパイル完了
else
	MKDIR_DB = mkdir -p $(DB_DIR)
	MKDIR_TEST = mkdir -p $(TEST_DIR)
	MKDIR_BIN = mkdir -p $(BIN_DIR)
	RM_BIN = rm -rf $(BIN_DIR)
	OPEN = open
	TEST_COMPILE = if [ -n "$(TEST_FILES)" ]; then \
		javac -cp $(TEST_CP) -d $(BIN_DIR) $(TEST_FILES); \
		echo "テストコードのコンパイル完了"; \
	else \
		echo "テストファイルが見つかりません"; \
	fi
endif

# デフォルトターゲット（コマンド一覧表示）
all:
	@echo "利用可能なコマンド:"
	@echo "  make setup      - データベースディレクトリを作成"
	@echo "  make compile    - ソースコードをコンパイル"
	@echo "  make compile-test - テストコードをコンパイル"
	@echo "  make test       - テストを実行"
	@echo "  make run        - アプリケーションを実行"
	@echo "  make open       - ブラウザでテストページを開く"
	@echo "  make clean      - コンパイル生成物を削除"

# セットアップ（データベースディレクトリ作成）
setup:
	@$(MKDIR_DB)
	@$(MKDIR_TEST)
	@echo "データベースディレクトリを作成しました"
	$(MAKE) compile
	$(MAKE) run

# テストページを開く
open:
	@$(OPEN) http://127.0.0.1:5501/$(local_url)

# ビルド前準備
prepare:
	@$(MKDIR_BIN)

# ソースコードをコンパイル
compile: prepare
	javac -cp $(COMPILE_CP) -d $(BIN_DIR) $(JAVA_FILES)
	@echo "ソースコードのコンパイル完了"

# テストコードをコンパイル
compile-test: compile
	@$(TEST_COMPILE)

# テストを実行
test: compile-test
	@echo "テストを実行中..."
	java -cp $(TEST_CP) org.junit.runner.JUnitCore test.DatabaseManagerTest
	java -cp $(TEST_CP) org.junit.runner.JUnitCore test.CommitManagerTest
	java -cp $(TEST_CP) org.junit.runner.JUnitCore test.MergeManagerTest
	java -cp $(TEST_CP) org.junit.runner.JUnitCore test.IntegrationTest
	java -cp $(TEST_CP) org.junit.runner.JUnitCore test.ApiHandlerTest

# アプリケーションを実行
run: compile
	java -cp $(RUN_CP) $(MAIN_CLASS)

# クリーン
clean:
	@$(RM_BIN)
	@echo "コンパイル生成物を削除しました"

.PHONY: all setup prepare compile compile-test test run open clean