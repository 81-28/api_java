/**
 * Git-like Version Control System - Frontend JavaScript
 */

// API Configuration
const API_BASE = "http://localhost:8080/api";

// State Management
class AppState {
  constructor() {
    this.currentUser = null;
    this.currentRepository = null;
    this.currentBranch = null;
    this.users = [];
    this.repositories = [];
    this.branches = [];
    this.commits = [];
    this.loadState();
  }

  saveState() {
    const state = {
      currentUser: this.currentUser,
      currentRepository: this.currentRepository,
      currentBranch: this.currentBranch,
    };
    localStorage.setItem("appState", JSON.stringify(state));
  }

  loadState() {
    const savedState = localStorage.getItem("appState");
    if (savedState) {
      const state = JSON.parse(savedState);
      this.currentUser = state.currentUser;
      this.currentRepository = state.currentRepository;
      this.currentBranch = state.currentBranch;
    }
  }
}

const appState = new AppState();

// DOM Elements
const elements = {
  currentUser: document.getElementById("currentUser"),
  repositorySelect: document.getElementById("repositorySelect"),
  branchSelect: document.getElementById("branchSelect"),
  currentBranchName: document.getElementById("currentBranchName"),
  fileContent: document.getElementById("fileContent"),
  commitMessage: document.getElementById("commitMessage"),
  mergeSource: document.getElementById("mergeSource"),
  mergeTarget: document.getElementById("mergeTarget"),
  statusContent: document.getElementById("statusContent"),
  graphVisualization: document.getElementById("graphVisualization"),
  mergeModal: document.getElementById("mergeModal"),
  newUsername: document.getElementById("newUsername"),
  newRepoName: document.getElementById("newRepoName"),
  newBranchName: document.getElementById("newBranchName"),
};

// API Client
class ApiClient {
  /**
   * HTTP リクエストを実行
   * @param {string} endpoint - API エンドポイント
   * @param {Object} options - リクエストオプション
   * @returns {Promise<Object>} レスポンス
   */
  async request(endpoint, options = {}) {
    try {
      const url = `${API_BASE}${endpoint}`;
      const response = await fetch(url, {
        headers: {
          "Content-Type": "application/json",
          ...options.headers,
        },
        ...options,
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      return await response.json();
    } catch (error) {
      console.error("API request failed:", error);
      throw error;
    }
  }

  /**
   * ユーザー一覧を取得
   * @returns {Promise<Object>} ユーザー一覧
   */
  async getUsers() {
    return this.request("/user");
  }

  /**
   * ユーザーを作成
   * @param {string} username - ユーザー名
   * @returns {Promise<Object>} 作成結果
   */
  async createUser(username) {
    return this.request("/user", {
      method: "POST",
      body: JSON.stringify({ username }),
    });
  }

  /**
   * リポジトリ一覧を取得
   * @param {number} ownerId - 所有者ID (オプション)
   * @returns {Promise<Object>} リポジトリ一覧
   */
  async getRepositories(ownerId = null) {
    const query = ownerId ? `?owner_id=${ownerId}` : "";
    return this.request(`/repository${query}`);
  }

  /**
   * リポジトリを作成
   * @param {string} name - リポジトリ名
   * @param {number} ownerId - 所有者ID
   * @returns {Promise<Object>} 作成結果
   */
  async createRepository(name, ownerId) {
    return this.request("/repository", {
      method: "POST",
      body: JSON.stringify({ name, owner_id: ownerId }),
    });
  }

  /**
   * ブランチ一覧を取得
   * @param {number} repositoryId - リポジトリID (オプション)
   * @returns {Promise<Object>} ブランチ一覧
   */
  async getBranches(repositoryId = null) {
    const query = repositoryId ? `?repository_id=${repositoryId}` : "";
    return this.request(`/branch${query}`);
  }

  /**
   * ブランチを作成
   * @param {string} name - ブランチ名
   * @param {number} repositoryId - リポジトリID
   * @returns {Promise<Object>} 作成結果
   */
  async createBranch(name, repositoryId) {
    return this.request("/branch", {
      method: "POST",
      body: JSON.stringify({ name, repository_id: repositoryId }),
    });
  }

  /**
   * コミットを作成
   * @param {number} branchId - ブランチID
   * @param {string} message - コミットメッセージ
   * @param {number} authorId - 作成者ID
   * @param {string} content - ファイル内容
   * @returns {Promise<Object>} 作成結果
   */
  async createCommit(branchId, message, authorId, content) {
    return this.request("/commit", {
      method: "POST",
      body: JSON.stringify({
        branch_id: branchId,
        message,
        author_id: authorId,
        content,
      }),
    });
  }

  /**
   * ファイル内容を取得
   * @param {number} branchId - ブランチID
   * @returns {Promise<Object>} ファイル内容
   */
  async getFiles(branchId) {
    return this.request(`/file?branch_id=${branchId}`);
  }

  /**
   * ブランチをマージ
   * @param {number} sourceBranchId - マージ元ブランチID
   * @param {number} targetBranchId - マージ先ブランチID
   * @returns {Promise<Object>} マージ結果
   */
  async mergeBranches(sourceBranchId, targetBranchId) {
    return this.request("/merge", {
      method: "POST",
      body: JSON.stringify({
        branch_id_1: sourceBranchId,
        branch_id_2: targetBranchId,
      }),
    });
  }

  /**
   * 強制マージを実行
   * @param {number} sourceBranchId - マージ元ブランチID
   * @param {number} targetBranchId - マージ先ブランチID
   * @param {string} content - マージ後の内容
   * @returns {Promise<Object>} マージ結果
   */
  async forceMerge(sourceBranchId, targetBranchId, content) {
    return this.request("/force-merge", {
      method: "POST",
      body: JSON.stringify({
        branch_id_1: sourceBranchId,
        branch_id_2: targetBranchId,
        text: content,
      }),
    });
  }

  /**
   * コミットグラフを取得
   * @param {number} repositoryId - リポジトリID
   * @returns {Promise<Object>} グラフデータ
   */
  async getGraph(repositoryId) {
    return this.request(`/graph?repository_id=${repositoryId}`);
  }
}

const apiClient = new ApiClient();

// UI Helper Functions
class UIHelper {
  /**
   * ステータスメッセージを表示
   * @param {string} message - メッセージ
   * @param {string} type - メッセージタイプ ('success', 'error', '')
   */
  static showStatus(message, type = "") {
    elements.statusContent.textContent = message;
    elements.statusContent.className = type ? `${type}` : "";
  }

  /**
   * セレクトボックスにオプションを追加
   * @param {HTMLSelectElement} selectElement - セレクト要素
   * @param {Array} items - アイテム配列
   * @param {string} valueKey - 値のキー
   * @param {string} textKey - 表示テキストのキー
   * @param {string} placeholder - プレースホルダー
   */
  static populateSelect(
    selectElement,
    items,
    valueKey,
    textKey,
    placeholder = ""
  ) {
    selectElement.innerHTML = "";

    if (placeholder) {
      const defaultOption = document.createElement("option");
      defaultOption.value = "";
      defaultOption.textContent = placeholder;
      selectElement.appendChild(defaultOption);
    }

    items.forEach((item) => {
      const option = document.createElement("option");
      option.value = item[valueKey];
      option.textContent = `${item[valueKey]}: ${item[textKey]}`;
      selectElement.appendChild(option);
    });
  }

  /**
   * モーダルを表示
   * @param {HTMLElement} modal - モーダル要素
   */
  static showModal(modal) {
    modal.style.display = "block";
  }

  /**
   * モーダルを非表示
   * @param {HTMLElement} modal - モーダル要素
   */
  static hideModal(modal) {
    modal.style.display = "none";
  }
}

// Graph Visualization
class GraphVisualizer {
  constructor(containerId) {
    this.container = document.getElementById(containerId);
    this.network = null;
  }

  /**
   * グラフを描画
   * @param {Object} graphData - グラフデータ
   */
  drawGraph(graphData) {
    if (!graphData.nodes || !graphData.edges) {
      this.container.innerHTML =
        '<div style="padding: 2rem; text-align: center; color: #586069;">グラフデータがありません</div>';
      return;
    }

    // ノードの色とスタイルを設定
    const nodes = new vis.DataSet(
      graphData.nodes.map((node) => ({
        id: node.id,
        label: node.label + (node.branch ? `\n[${node.branch}]` : ""),
        shape: node.shape || "box",
        color: this.getNodeColor(node),
        font: { size: 12, color: "#24292e" },
        margin: 10,
      }))
    );

    // エッジの色とスタイルを設定
    const edges = new vis.DataSet(
      graphData.edges.map((edge) => ({
        from: edge.from,
        to: edge.to,
        arrows: "to",
        color: this.getEdgeColor(edge),
        dashes: edge.dashes || false,
        label: edge.label || "",
        width: edge.dashes ? 2 : 1,
      }))
    );

    const options = {
      layout: {
        hierarchical: {
          enabled: true,
          direction: "UD",
          sortMethod: "directed",
          nodeSpacing: 150,
          levelSeparation: 100,
        },
      },
      physics: {
        enabled: false,
      },
      nodes: {
        shape: "box",
        margin: 10,
        widthConstraint: { maximum: 200 },
      },
      edges: {
        smooth: {
          enabled: true,
          type: "cubicBezier",
          roundness: 0.4,
        },
      },
      interaction: {
        hover: true,
        selectConnectedEdges: false,
      },
    };

    this.container.innerHTML = "";
    this.network = new vis.Network(this.container, { nodes, edges }, options);
  }

  /**
   * ノードの色を取得
   * @param {Object} node - ノード情報
   * @returns {Object} 色設定
   */
  getNodeColor(node) {
    if (node.shape === "ellipse" || node.branch) {
      return {
        background: "#d73a49",
        border: "#cb2431",
        highlight: { background: "#e36578", border: "#cb2431" },
      };
    }
    return {
      background: "#0969da",
      border: "#0550ae",
      highlight: { background: "#318ce7", border: "#0550ae" },
    };
  }

  /**
   * エッジの色を取得
   * @param {Object} edge - エッジ情報
   * @returns {Object} 色設定
   */
  getEdgeColor(edge) {
    if (edge.dashes) {
      return { color: "#28a745", highlight: "#22863a" };
    }
    if (edge.label) {
      return { color: "#d73a49", highlight: "#cb2431" };
    }
    return { color: "#586069", highlight: "#24292e" };
  }
}

// Application Controller
class AppController {
  constructor() {
    this.graphVisualizer = new GraphVisualizer("graphVisualization");
    this.initializeEventListeners();
    this.initialize();
  }

  /**
   * アプリケーションを初期化
   */
  async initialize() {
    UIHelper.showStatus("アプリケーションを初期化中...");
    try {
      await this.loadUsers();

      // 保存された状態を復元
      if (appState.currentUser) {
        elements.currentUser.value = appState.currentUser.id;
        await this.loadRepositories();

        if (appState.currentRepository) {
          elements.repositorySelect.value = appState.currentRepository.id;
          await this.loadBranches();

          if (appState.currentBranch) {
            elements.branchSelect.value = appState.currentBranch.id;
            await this.loadFile();
          }
        }
      }

      UIHelper.showStatus("準備完了", "success");
    } catch (error) {
      UIHelper.showStatus(`初期化エラー: ${error.message}`, "error");
    }
  }

  /**
   * イベントリスナーを初期化
   */
  initializeEventListeners() {
    // User Management
    elements.currentUser.addEventListener(
      "change",
      this.onUserChange.bind(this)
    );
    document
      .getElementById("refreshUsers")
      .addEventListener("click", this.loadUsers.bind(this));
    document
      .getElementById("createUser")
      .addEventListener("click", this.createUser.bind(this));

    // Repository Management
    elements.repositorySelect.addEventListener(
      "change",
      this.onRepositoryChange.bind(this)
    );
    document
      .getElementById("refreshRepos")
      .addEventListener("click", this.loadRepositories.bind(this));
    document
      .getElementById("createRepo")
      .addEventListener("click", this.createRepository.bind(this));

    // Branch Management
    elements.branchSelect.addEventListener(
      "change",
      this.onBranchChange.bind(this)
    );
    document
      .getElementById("refreshBranches")
      .addEventListener("click", this.loadBranches.bind(this));
    document
      .getElementById("createBranch")
      .addEventListener("click", this.createBranch.bind(this));

    // File Operations
    document
      .getElementById("loadFile")
      .addEventListener("click", this.loadFile.bind(this));
    document
      .getElementById("createCommit")
      .addEventListener("click", this.createCommit.bind(this));

    // Merge Operations
    document
      .getElementById("performMerge")
      .addEventListener("click", this.performMerge.bind(this));

    // Graph
    document
      .getElementById("refreshGraph")
      .addEventListener("click", this.refreshGraph.bind(this));

    // Modal Events
    document
      .getElementById("selectSource")
      .addEventListener("click", () => this.resolveMergeConflict("source"));
    document
      .getElementById("selectTarget")
      .addEventListener("click", () => this.resolveMergeConflict("target"));
    document
      .getElementById("selectManual")
      .addEventListener("click", () => this.resolveMergeConflict("manual"));
    document
      .getElementById("cancelMerge")
      .addEventListener("click", () => UIHelper.hideModal(elements.mergeModal));
  }

  /**
   * ユーザー変更時の処理
   */
  async onUserChange() {
    const userId = elements.currentUser.value;
    if (userId) {
      appState.currentUser = appState.users.find((u) => u.id == userId);
      await this.loadRepositories();
      appState.saveState();
      UIHelper.showStatus(
        `ユーザーを ${appState.currentUser.username} に切り替えました`,
        "success"
      );
    } else {
      appState.currentUser = null;
      elements.repositorySelect.innerHTML =
        '<option value="">リポジトリを選択</option>';
      appState.saveState();
    }
  }

  /**
   * リポジトリ変更時の処理
   */
  async onRepositoryChange() {
    const repoId = elements.repositorySelect.value;
    if (repoId) {
      appState.currentRepository = appState.repositories.find(
        (r) => r.id == repoId
      );
      await this.loadBranches();
      await this.refreshGraph();
      appState.saveState();
      UIHelper.showStatus(
        `リポジトリを ${appState.currentRepository.name} に切り替えました`,
        "success"
      );
    } else {
      appState.currentRepository = null;
      elements.branchSelect.innerHTML =
        '<option value="">ブランチを選択</option>';
      elements.currentBranchName.textContent = "-";
      appState.saveState();
    }
  }

  /**
   * ブランチ変更時の処理
   */
  async onBranchChange() {
    const branchId = elements.branchSelect.value;
    if (branchId) {
      appState.currentBranch = appState.branches.find((b) => b.id == branchId);
      elements.currentBranchName.textContent = appState.currentBranch.name;
      await this.loadFile();
      appState.saveState();
      UIHelper.showStatus(
        `ブランチを ${appState.currentBranch.name} に切り替えました`,
        "success"
      );
    } else {
      appState.currentBranch = null;
      elements.currentBranchName.textContent = "-";
      elements.fileContent.value = "";
      appState.saveState();
    }
  }

  /**
   * ユーザー一覧を読み込み
   */
  async loadUsers() {
    try {
      const response = await apiClient.getUsers();
      appState.users = response.users || [];
      UIHelper.populateSelect(
        elements.currentUser,
        appState.users,
        "id",
        "username",
        "ユーザーを選択"
      );
    } catch (error) {
      UIHelper.showStatus(`ユーザー読み込みエラー: ${error.message}`, "error");
    }
  }

  /**
   * ユーザーを作成
   */
  async createUser() {
    const username = elements.newUsername.value.trim();
    if (!username) {
      UIHelper.showStatus("ユーザー名を入力してください", "error");
      return;
    }

    try {
      await apiClient.createUser(username);
      elements.newUsername.value = "";
      await this.loadUsers();
      UIHelper.showStatus(`ユーザー「${username}」を作成しました`, "success");
    } catch (error) {
      UIHelper.showStatus(`ユーザー作成エラー: ${error.message}`, "error");
    }
  }

  /**
   * リポジトリ一覧を読み込み
   */
  async loadRepositories() {
    if (!appState.currentUser) return;

    try {
      const response = await apiClient.getRepositories(appState.currentUser.id);
      appState.repositories = response.repositories || [];
      UIHelper.populateSelect(
        elements.repositorySelect,
        appState.repositories,
        "id",
        "name",
        "リポジトリを選択"
      );
    } catch (error) {
      UIHelper.showStatus(
        `リポジトリ読み込みエラー: ${error.message}`,
        "error"
      );
    }
  }

  /**
   * リポジトリを作成
   */
  async createRepository() {
    if (!appState.currentUser) {
      UIHelper.showStatus("ユーザーを選択してください", "error");
      return;
    }

    const repoName = elements.newRepoName.value.trim();
    if (!repoName) {
      UIHelper.showStatus("リポジトリ名を入力してください", "error");
      return;
    }

    try {
      await apiClient.createRepository(repoName, appState.currentUser.id);
      elements.newRepoName.value = "";
      await this.loadRepositories();
      UIHelper.showStatus(`リポジトリ「${repoName}」を作成しました`, "success");
    } catch (error) {
      UIHelper.showStatus(`リポジトリ作成エラー: ${error.message}`, "error");
    }
  }

  /**
   * ブランチ一覧を読み込み
   */
  async loadBranches() {
    if (!appState.currentRepository) return;

    try {
      const response = await apiClient.getBranches(
        appState.currentRepository.id
      );
      appState.branches = response.branches || [];
      UIHelper.populateSelect(
        elements.branchSelect,
        appState.branches,
        "id",
        "name",
        "ブランチを選択"
      );
      UIHelper.populateSelect(
        elements.mergeSource,
        appState.branches,
        "id",
        "name",
        "マージ元を選択"
      );
      UIHelper.populateSelect(
        elements.mergeTarget,
        appState.branches,
        "id",
        "name",
        "マージ先を選択"
      );
    } catch (error) {
      UIHelper.showStatus(`ブランチ読み込みエラー: ${error.message}`, "error");
    }
  }

  /**
   * ブランチを作成
   */
  async createBranch() {
    if (!appState.currentRepository) {
      UIHelper.showStatus("リポジトリを選択してください", "error");
      return;
    }

    const branchName = elements.newBranchName.value.trim();
    if (!branchName) {
      UIHelper.showStatus("ブランチ名を入力してください", "error");
      return;
    }

    try {
      await apiClient.createBranch(branchName, appState.currentRepository.id);
      elements.newBranchName.value = "";
      await this.loadBranches();
      UIHelper.showStatus(`ブランチ「${branchName}」を作成しました`, "success");
    } catch (error) {
      UIHelper.showStatus(`ブランチ作成エラー: ${error.message}`, "error");
    }
  }

  /**
   * ファイル内容を読み込み
   */
  async loadFile() {
    if (!appState.currentBranch) return;

    try {
      const response = await apiClient.getFiles(appState.currentBranch.id);
      const files = response.files || [];
      if (files.length > 0) {
        elements.fileContent.value = files[0].text || "";
      } else {
        elements.fileContent.value = "";
      }
    } catch (error) {
      UIHelper.showStatus(`ファイル読み込みエラー: ${error.message}`, "error");
    }
  }

  /**
   * コミットを作成
   */
  async createCommit() {
    if (!appState.currentBranch || !appState.currentUser) {
      UIHelper.showStatus("ブランチとユーザーを選択してください", "error");
      return;
    }

    const message = elements.commitMessage.value.trim();
    const content = elements.fileContent.value;

    if (!message) {
      UIHelper.showStatus("コミットメッセージを入力してください", "error");
      return;
    }

    try {
      await apiClient.createCommit(
        appState.currentBranch.id,
        message,
        appState.currentUser.id,
        content
      );
      elements.commitMessage.value = "";
      await this.refreshGraph();
      UIHelper.showStatus(`コミット「${message}」を作成しました`, "success");
    } catch (error) {
      UIHelper.showStatus(`コミット作成エラー: ${error.message}`, "error");
    }
  }

  /**
   * マージを実行
   */
  async performMerge() {
    const sourceId = elements.mergeSource.value;
    const targetId = elements.mergeTarget.value;

    if (!sourceId || !targetId) {
      UIHelper.showStatus("マージ元とマージ先を選択してください", "error");
      return;
    }

    if (sourceId === targetId) {
      UIHelper.showStatus("同じブランチはマージできません", "error");
      return;
    }

    try {
      const result = await apiClient.mergeBranches(sourceId, targetId);

      if (result.success) {
        await this.refreshGraph();
        UIHelper.showStatus("マージが完了しました", "success");
      } else {
        // コンフリクト発生時の処理
        this.showMergeConflictModal(result, sourceId, targetId);
      }
    } catch (error) {
      UIHelper.showStatus(`マージエラー: ${error.message}`, "error");
    }
  }

  /**
   * マージコンフリクトモーダルを表示
   * @param {Object} conflictData - コンフリクトデータ
   * @param {string} sourceId - マージ元ID
   * @param {string} targetId - マージ先ID
   */
  showMergeConflictModal(conflictData, sourceId, targetId) {
    document.getElementById("sourceContent").value = conflictData.text_1 || "";
    document.getElementById("targetContent").value = conflictData.text_2 || "";
    document.getElementById("manualContent").value = conflictData.text_1 || "";

    this.pendingMerge = { sourceId, targetId };
    UIHelper.showModal(elements.mergeModal);
  }

  /**
   * マージコンフリクトを解決
   * @param {string} resolution - 解決方法 ('source', 'target', 'manual')
   */
  async resolveMergeConflict(resolution) {
    if (!this.pendingMerge) return;

    let content = "";
    switch (resolution) {
      case "source":
        content = document.getElementById("sourceContent").value;
        break;
      case "target":
        content = document.getElementById("targetContent").value;
        break;
      case "manual":
        content = document.getElementById("manualContent").value;
        break;
    }

    try {
      await apiClient.forceMerge(
        this.pendingMerge.sourceId,
        this.pendingMerge.targetId,
        content
      );
      UIHelper.hideModal(elements.mergeModal);
      await this.refreshGraph();
      UIHelper.showStatus("マージコンフリクトを解決しました", "success");
    } catch (error) {
      UIHelper.showStatus(`マージ解決エラー: ${error.message}`, "error");
    }

    this.pendingMerge = null;
  }

  /**
   * グラフを更新
   */
  async refreshGraph() {
    if (!appState.currentRepository) return;

    try {
      const graphData = await apiClient.getGraph(appState.currentRepository.id);
      this.graphVisualizer.drawGraph(graphData);
    } catch (error) {
      UIHelper.showStatus(`グラフ更新エラー: ${error.message}`, "error");
    }
  }
}

// Initialize Application
document.addEventListener("DOMContentLoaded", () => {
  new AppController();
});
