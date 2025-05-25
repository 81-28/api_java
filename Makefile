# Git APIサーバー用Makefile

# 変数定義(デバッグ用)
local_url = api_java/public/test.html

# 変数定義(デバッグ用)
SRC_DIR = src
BIN_DIR = bin
LIB_DIR = lib
DB_DIR = database
CLASSPATH = $(LIB_DIR)/sqlite-jdbc.jar
MAIN_CLASS = src.GitApiServer


# OS判定
ifeq ($(OS),Windows_NT)
    CP_SEP = ;
else
    CP_SEP = :
endif

# コンパイル時のクラスパス
COMPILE_CP = .$(CP_SEP)$(CLASSPATH)

# 実行時のクラスパス
RUN_CP = $(BIN_DIR)$(CP_SEP)$(CLASSPATH)

# コンパイル対象のJavaファイル
JAVA_FILES = $(wildcard src/*.java)

# デフォルトターゲット（コマンド一覧表示）
all:
	@echo "利用可能なコマンド:"
	@echo "  make setup    - データベースディレクトリを作成"
	@echo "  make compile  - ソースコードをコンパイル"
	@echo "  make run      - アプリケーションを実行"
	@echo "  make open     - ブラウザでテストページを開く"
	@echo "  make clean    - コンパイル生成物を削除"

# セットアップ（データベースディレクトリ作成）
setup:
	@mkdir -p $(DB_DIR)
	@echo "データベースディレクトリを作成しました"
	make compile
	make run

# テストページを開く
open:
	open http://127.0.0.1:5501/$(local_url)

	
# ビルド前準備
prepare:
	@mkdir -p $(BIN_DIR)

# コンパイル
compile: prepare
	javac -cp $(COMPILE_CP) -d $(BIN_DIR) $(JAVA_FILES)
	@echo "コンパイル完了"

# 実行
run: compile
	java -cp $(RUN_CP) $(MAIN_CLASS)


# クリーン
clean:
	@rm -rf $(BIN_DIR)
	@echo "コンパイル生成物を削除しました"

.PHONY: all setup prepare compile run clean