SHELL = cmd.exe
.SHELLFLAGS = /C

# Git API�T�[�o�[�pMakefile�i�e�X�g�Ή��Łj

# �ϐ���`(�f�o�b�O�p)
local_url = public/test.html

# �ϐ���`
SRC_DIR = src
TEST_DIR = test
BIN_DIR = bin
LIB_DIR = lib
DB_DIR = database
JUNIT_JAR = $(LIB_DIR)/junit-4.13.2.jar
HAMCREST_JAR = $(LIB_DIR)/hamcrest-core-1.3.jar

# OS�ƃA�[�L�e�N�`���̔���
ifeq ($(OS),Windows_NT)
    SQLITE_JAR = $(LIB_DIR)/sqlite-jdbc-3.30.1.jar
else
    # Mac�̏ꍇ
    ifeq ($(shell uname),Darwin)
        # M2�`�b�v�iARM64�j�̏ꍇ
        ifeq ($(shell uname -m),arm64)
            SQLITE_JAR = $(LIB_DIR)/sqlite-jdbc.jar
        else
            SQLITE_JAR = $(LIB_DIR)/sqlite-jdbc-3.30.1.jar
        endif
    else
        # ���̑���OS�̏ꍇ
        SQLITE_JAR = $(LIB_DIR)/sqlite-jdbc-3.30.1.jar
    endif
endif

MAIN_CLASS = src.GitApiServer

# OS����
ifeq ($(OS),Windows_NT)
    CP_SEP = ;
else
    CP_SEP = :
endif

# �N���X�p�X�ݒ�
LIBS = $(SQLITE_JAR)$(CP_SEP)$(JUNIT_JAR)$(CP_SEP)$(HAMCREST_JAR)
COMPILE_CP = .$(CP_SEP)$(LIBS)
RUN_CP = $(BIN_DIR)$(CP_SEP)$(LIBS)
TEST_CP = $(BIN_DIR)$(CP_SEP)$(LIBS)

# �R���p�C���Ώۂ̃t�@�C��
JAVA_FILES = $(wildcard src/*.java)
TEST_FILES = $(wildcard test/*.java)

# ===== OS���ƂɃR�}���h��ϐ��ňꌳ�Ǘ� =====
ifeq ($(OS),Windows_NT)
	MKDIR_DB = if not exist $(DB_DIR) mkdir $(DB_DIR)
	MKDIR_TEST = if not exist $(TEST_DIR) mkdir $(TEST_DIR)
	MKDIR_BIN = if not exist $(BIN_DIR) mkdir $(BIN_DIR)
	RM_BIN = if exist $(BIN_DIR) rmdir /s /q $(BIN_DIR)
	OPEN = start
	TEST_COMPILE = javac -cp "$(TEST_CP)" -d $(BIN_DIR) $(TEST_DIR)\*.java && echo �e�X�g�R�[�h�̃R���p�C������
else
	MKDIR_DB = mkdir -p $(DB_DIR)
	MKDIR_TEST = mkdir -p $(TEST_DIR)
	MKDIR_BIN = mkdir -p $(BIN_DIR)
	RM_BIN = rm -rf $(BIN_DIR)
	OPEN = open
	TEST_COMPILE = if [ -n "$(TEST_FILES)" ]; then \
		javac -cp $(TEST_CP) -d $(BIN_DIR) $(TEST_FILES); \
		echo "�e�X�g�R�[�h�̃R���p�C������"; \
	else \
		echo "�e�X�g�t�@�C����������܂���"; \
	fi
endif

# �f�t�H���g�^�[�Q�b�g�i�R�}���h�ꗗ�\���j
all:
	@echo "���p�\�ȃR�}���h:"
	@echo "  make setup      - �f�[�^�x�[�X�f�B���N�g�����쐬"
	@echo "  make compile    - �\�[�X�R�[�h���R���p�C��"
	@echo "  make compile-test - �e�X�g�R�[�h���R���p�C��"
	@echo "  make test       - �e�X�g�����s"
	@echo "  make run        - �A�v���P�[�V���������s"
	@echo "  make open       - �u���E�U�Ńe�X�g�y�[�W���J��"
	@echo "  make clean      - �R���p�C�����������폜"

# �Z�b�g�A�b�v�i�f�[�^�x�[�X�f�B���N�g���쐬�j
setup:
	@$(MKDIR_DB)
	@$(MKDIR_TEST)
	@echo "�f�[�^�x�[�X�f�B���N�g�����쐬���܂���"
	$(MAKE) compile
	$(MAKE) run

# �e�X�g�y�[�W���J��
open:
	@$(OPEN) http://127.0.0.1:5501/$(local_url)

# �r���h�O����
prepare:
	@$(MKDIR_BIN)

# �\�[�X�R�[�h���R���p�C��
compile: prepare
	javac -cp $(COMPILE_CP) -d $(BIN_DIR) $(JAVA_FILES)
	@echo "�\�[�X�R�[�h�̃R���p�C������"

# �e�X�g�R�[�h���R���p�C��
compile-test: compile
	@$(TEST_COMPILE)

# �e�X�g�����s
test: compile-test
	@echo "�e�X�g�����s��..."
	java -cp $(TEST_CP) org.junit.runner.JUnitCore test.DatabaseManagerTest
	java -cp $(TEST_CP) org.junit.runner.JUnitCore test.CommitManagerTest
	java -cp $(TEST_CP) org.junit.runner.JUnitCore test.MergeManagerTest
	java -cp $(TEST_CP) org.junit.runner.JUnitCore test.IntegrationTest
	java -cp $(TEST_CP) org.junit.runner.JUnitCore test.ApiHandlerTest

# �A�v���P�[�V���������s
run: compile
	java -cp $(RUN_CP) $(MAIN_CLASS)

# �N���[��
clean:
	@$(RM_BIN)
	@echo "�R���p�C�����������폜���܂���"

.PHONY: all setup prepare compile compile-test test run open clean