/**
 * Git風バージョン管理システム - メインJavaScriptファイル
 */

class GitGUI {
  constructor() {
    this.apiBaseUrl = "http://localhost:8080/api";
    this.currentUser = null;
    this.currentRepo = null;
    this.currentBranch = null;
    this.network = null;
    this.currentEditingFile = null;

    this.init();
  }

  async init() {
    this.setupEventListeners();
    this.setupTabs();
    await this.loadUsers();
    await this.restoreState();
    await this.loadRepositories();
  }

  async restoreState() {
    try {
      const savedUser = localStorage.getItem("currentUser");
      const savedRepo = localStorage.getItem("currentRepo");
      const savedBranch = localStorage.getItem("currentBranch");

      if (savedUser) {
        const userData = JSON.parse(savedUser);
        this.currentUser = userData;
        document.getElementById("currentUser").value = userData.id;

        await this.loadRepositories();

        if (savedRepo) {
          const repoData = JSON.parse(savedRepo);
          const repoElement = document.querySelector(
            `[data-repo-id="${repoData.id}"]`
          );
          if (repoElement) {
            this.currentRepo = repoData;
            repoElement.classList.add("active");
            document.getElementById(
              "currentRepo"
            ).textContent = `リポジトリ: ${repoData.name} (ID: ${repoData.id})`;

            await this.loadBranches();

            if (savedBranch) {
              const branchData = JSON.parse(savedBranch);
              const branchElement = document.querySelector(
                `[data-branch-id="${branchData.id}"]`
              );
              if (branchElement) {
                this.currentBranch = branchData;
                branchElement.classList.add("active");
                document.getElementById(
                  "currentBranch"
                ).textContent = `ブランチ: ${branchData.name}`;

                const activeTab =
                  document.querySelector(".tab.active")?.dataset.tab;
                if (activeTab) {
                  await this.onTabChange(activeTab);
                }
              }
            }
          }
        }
      }
    } catch (error) {
      console.error("状態の復元に失敗しました:", error);
    }
  }

  saveState() {
    try {
      if (this.currentUser) {
        localStorage.setItem("currentUser", JSON.stringify(this.currentUser));
      }
      if (this.currentRepo) {
        localStorage.setItem("currentRepo", JSON.stringify(this.currentRepo));
      }
      if (this.currentBranch) {
        localStorage.setItem(
          "currentBranch",
          JSON.stringify(this.currentBranch)
        );
      }
    } catch (error) {
      console.error("状態の保存に失敗しました:", error);
    }
  }

  setupEventListeners() {
    document.getElementById("currentUser").addEventListener("change", (e) => {
      this.setCurrentUser(e.target.value);
    });
    document.getElementById("addUserBtn").addEventListener("click", () => {
      this.showAddUserModal();
    });

    document.getElementById("createRepoBtn").addEventListener("click", () => {
      this.showCreateRepoModal();
    });
    document.getElementById("cloneRepoBtn").addEventListener("click", () => {
      this.showCloneRepoModal();
    });

    document.getElementById("createBranchBtn").addEventListener("click", () => {
      this.showCreateBranchModal();
    });
    document.getElementById("checkoutBtn").addEventListener("click", () => {
      this.showCheckoutModal();
    });

    document.getElementById("mergeBtn").addEventListener("click", () => {
      this.showMergeModal();
    });
    document.getElementById("resetBtn").addEventListener("click", () => {
      this.showResetModal();
    });

    document.getElementById("updateFileBtn").addEventListener("click", () => {
      this.showUpdateFileModal();
    });
    document.getElementById("refreshFilesBtn").addEventListener("click", () => {
      this.loadFiles();
    });
    document.getElementById("commitFileBtn").addEventListener("click", () => {
      this.commitCurrentFile();
    });
    document.getElementById("cancelEditBtn").addEventListener("click", () => {
      this.cancelFileEdit();
    });

    document.getElementById("commitBtn").addEventListener("click", () => {
      this.createCommit();
    });

    document
      .getElementById("refreshHistoryBtn")
      .addEventListener("click", () => {
        this.loadCommitHistory();
      });
    document
      .getElementById("historyBranchFilter")
      .addEventListener("change", () => {
        this.loadCommitHistory();
      });

    document.getElementById("refreshGraphBtn").addEventListener("click", () => {
      this.loadCommitGraph();
    });

    document.getElementById("modalClose").addEventListener("click", () => {
      this.hideModal();
    });
    document.getElementById("modalCancel").addEventListener("click", () => {
      this.hideModal();
    });
    document.getElementById("modal").addEventListener("click", (e) => {
      if (e.target.id === "modal") {
        this.hideModal();
      }
    });
  }

  setupTabs() {
    const tabs = document.querySelectorAll(".tab");
    const tabContents = document.querySelectorAll(".tab-content");

    tabs.forEach((tab) => {
      tab.addEventListener("click", () => {
        const tabName = tab.dataset.tab;
        tabs.forEach((t) => t.classList.remove("active"));
        tabContents.forEach((tc) => tc.classList.remove("active"));

        tab.classList.add("active");
        document.getElementById(`${tabName}-tab`).classList.add("active");

        this.onTabChange(tabName);
      });
    });
  }

  async onTabChange(tabName) {
    switch (tabName) {
      case "files":
        await this.loadFiles();
        break;
      case "commit":
        await this.loadStagedFiles();
        break;
      case "history":
        await this.loadCommitHistory();
        break;
      case "graph":
        await this.loadCommitGraph();
        break;
    }
  }

  async apiRequest(endpoint, options = {}) {
    try {
      const response = await fetch(`${this.apiBaseUrl}${endpoint}`, {
        headers: {
          "Content-Type": "application/json",
          ...options.headers,
        },
        ...options,
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      if (response.status === 204) {
        return {};
      }

      return await response.json();
    } catch (error) {
      console.error("API request failed:", error);
      this.showError(`API リクエストに失敗しました: ${error.message}`);
      throw error;
    }
  }

  async loadUsers() {
    try {
      const data = await this.apiRequest("/user");
      const userSelect = document.getElementById("currentUser");

      userSelect.innerHTML = '<option value="">ユーザーを選択</option>';

      if (data.users) {
        data.users.forEach((user) => {
          const option = document.createElement("option");
          option.value = user.id;
          option.textContent = user.username;
          userSelect.appendChild(option);
        });
      }
    } catch (error) {
      console.error("Failed to load users:", error);
    }
  }

  async setCurrentUser(userId) {
    this.currentRepo = null;
    this.currentBranch = null;

    document.getElementById("currentRepo").textContent = "リポジトリ: 未選択";
    document.getElementById("currentBranch").textContent = "ブランチ: 未選択";
    document.getElementById("repoList").innerHTML = "";
    document.getElementById("branchList").innerHTML = "";
    document.getElementById("fileList").innerHTML =
      '<div class="text-center text-muted">ユーザーを選択してください</div>';
    document.getElementById("commitHistory").innerHTML = "";
    document.getElementById("fileChanges").innerHTML = "";
    document.getElementById("graphContainer").innerHTML = "";

    localStorage.removeItem("currentRepo");
    localStorage.removeItem("currentBranch");

    if (!userId) {
      this.currentUser = null;
      localStorage.removeItem("currentUser");
      return;
    }

    try {
      const data = await this.apiRequest("/user");
      const user = data.users?.find((u) => u.id == userId);
      if (user) {
        this.currentUser = user;
        this.saveState();
        await this.loadRepositories();
      }
    } catch (error) {
      console.error("Failed to set current user:", error);
    }
  }

  async loadRepositories() {
    try {
      const data = await this.apiRequest("/repository");

      const repoList = document.getElementById("repoList");
      repoList.innerHTML = "";

      if (data.repositories) {
        data.repositories.forEach((repo) => {
          const repoItem = document.createElement("div");
          repoItem.className = "repo-item";
          repoItem.dataset.repoId = repo.id;

          repoItem.innerHTML = `
            <div class="repo-item-name">${repo.name}</div>
            <div class="repo-item-owner">ID: ${repo.id} | 所有者: ${repo.owner_id}</div>
          `;

          repoItem.addEventListener("click", () => {
            this.selectRepository(repo);
          });

          repoList.appendChild(repoItem);
        });
      }
    } catch (error) {
      console.error("Failed to load repositories:", error);
    }
  }

  async selectRepository(repo) {
    this.currentBranch = null;
    document.getElementById("currentBranch").textContent = "ブランチ: 未選択";
    localStorage.removeItem("currentBranch");

    document.querySelectorAll(".repo-item").forEach((item) => {
      item.classList.remove("active");
    });
    document
      .querySelector(`[data-repo-id="${repo.id}"]`)
      ?.classList.add("active");

    this.currentRepo = repo;
    document.getElementById(
      "currentRepo"
    ).textContent = `リポジトリ: ${repo.name} (ID: ${repo.id})`;

    this.saveState();

    document.getElementById("branchList").innerHTML = "";
    await this.loadBranches();
  }

  async loadBranches() {
    if (this.currentBranch) {
      this.currentBranch = null;
      document.getElementById("currentBranch").textContent = "ブランチ: 未選択";
    }

    if (!this.currentRepo) {
      document.getElementById("branchList").innerHTML = "";
      document.getElementById("historyBranchFilter").innerHTML =
        '<option value="">すべてのブランチ</option>';
      return;
    }

    try {
      const data = await this.apiRequest(
        `/branch?repository_id=${this.currentRepo.id}`
      );
      const branchList = document.getElementById("branchList");
      const historyBranchFilter = document.getElementById(
        "historyBranchFilter"
      );

      branchList.innerHTML = "";
      historyBranchFilter.innerHTML =
        '<option value="">すべてのブランチ</option>';

      if (data.branches && data.branches.length > 0) {
        data.branches.forEach((branch) => {
          const branchItem = document.createElement("div");
          branchItem.className = "branch-item";
          branchItem.dataset.branchId = branch.id;

          branchItem.innerHTML = `
            <div class="branch-item-name">${branch.name}</div>
            <div class="branch-item-info">HEAD: ${
              branch.head_commit_id || "なし"
            }</div>
          `;

          branchItem.addEventListener("click", () => {
            this.selectBranch(branch);
          });

          branchList.appendChild(branchItem);

          const option = document.createElement("option");
          option.value = branch.id;
          option.textContent = branch.name;
          historyBranchFilter.appendChild(option);
        });

        const defaultBranch =
          data.branches.find((b) => b.name === "main" || b.name === "master") ||
          data.branches[0];
        if (defaultBranch) {
          this.selectBranch(defaultBranch);
        }
      } else {
        branchList.innerHTML =
          '<div class="text-center text-muted">ブランチがありません</div>';
      }
    } catch (error) {
      console.error("Failed to load branches:", error);
    }
  }

  async selectBranch(branch) {
    document.querySelectorAll(".branch-item").forEach((item) => {
      item.classList.remove("active");
    });
    document
      .querySelector(`[data-branch-id="${branch.id}"]`)
      ?.classList.add("active");

    this.currentBranch = branch;
    document.getElementById(
      "currentBranch"
    ).textContent = `ブランチ: ${branch.name}`;

    this.saveState();

    const activeTab = document.querySelector(".tab.active")?.dataset.tab;
    if (activeTab) {
      await this.onTabChange(activeTab);
    }
  }

  async loadFiles() {
    if (!this.currentBranch) {
      document.getElementById("fileList").innerHTML =
        '<div class="text-center text-muted">ブランチを選択してください</div>';
      return;
    }

    try {
      const data = await this.apiRequest(
        `/file?branch_id=${this.currentBranch.id}`
      );
      const fileList = document.getElementById("fileList");

      fileList.innerHTML = "";

      if (data.files && data.files.length > 0) {
        data.files.forEach((file) => {
          const fileItem = document.createElement("div");
          fileItem.className = "file-item";

          fileItem.innerHTML = `
            <span class="file-icon">📄</span>
            <span class="file-name">${file.filename || "main.txt"}</span>
            <span class="file-size">${
              file.text ? file.text.length : 0
            } 文字</span>
          `;

          fileItem.addEventListener("click", () => {
            this.editFile(file);
          });

          fileList.appendChild(fileItem);
        });
      } else {
        fileList.innerHTML =
          '<div class="text-center text-muted">ファイルがありません</div>';
      }
    } catch (error) {
      console.error("Failed to load files:", error);
    }
  }

  editFile(file) {
    const editorContainer = document.getElementById("fileEditorContainer");
    const editingFileName = document.getElementById("editingFileName");
    const fileEditor = document.getElementById("fileEditor");

    editingFileName.textContent = file.filename || "main.txt";
    fileEditor.value = file.text || "";
    editorContainer.style.display = "block";

    this.currentEditingFile = file;
  }

  cancelFileEdit() {
    document.getElementById("fileEditorContainer").style.display = "none";
    this.currentEditingFile = null;
  }

  async commitCurrentFile() {
    if (!this.currentEditingFile || !this.currentBranch || !this.currentUser)
      return;

    const content = document.getElementById("fileEditor").value;
    const message = `Update ${this.currentEditingFile.filename || "main.txt"}`;

    try {
      await this.apiRequest("/commit", {
        method: "POST",
        body: JSON.stringify({
          branch_id: this.currentBranch.id,
          message: message,
          author_id: this.currentUser.id,
          content: content,
        }),
      });

      this.showSuccess("ファイルがコミットされました");
      this.cancelFileEdit();
      await this.loadFiles();
      await this.loadBranches();
    } catch (error) {
      console.error("Failed to commit file:", error);
    }
  }

  async loadStagedFiles() {
    const fileChanges = document.getElementById("fileChanges");

    if (!this.currentBranch) {
      fileChanges.innerHTML =
        '<div class="text-center text-muted">ブランチを選択してください</div>';
      return;
    }

    try {
      const data = await this.apiRequest(
        `/file?branch_id=${this.currentBranch.id}`
      );
      fileChanges.innerHTML = "";

      if (data.files && data.files.length > 0) {
        data.files.forEach((file) => {
          const changeItem = document.createElement("div");
          changeItem.className = "change-item";

          changeItem.innerHTML = `
            <div class="change-status modified">M</div>
            <span>${file.filename || "main.txt"}</span>
          `;

          fileChanges.appendChild(changeItem);
        });
      } else {
        fileChanges.innerHTML =
          '<div class="text-center text-muted">ファイルがありません</div>';
      }
    } catch (error) {
      console.error("Failed to load staged files:", error);
    }
  }

  async createCommit() {
    if (!this.currentBranch || !this.currentUser) {
      this.showError("ブランチとユーザーを選択してください");
      return;
    }

    const message = document.getElementById("commitMessage").value.trim();
    if (!message) {
      this.showError("コミットメッセージを入力してください");
      return;
    }

    try {
      const fileData = await this.apiRequest(
        `/file?branch_id=${this.currentBranch.id}`
      );
      const content = fileData.files?.[0]?.text || "";

      const response = await this.apiRequest("/commit", {
        method: "POST",
        body: JSON.stringify({
          branch_id: this.currentBranch.id,
          message: message,
          author_id: this.currentUser.id,
          content: content,
        }),
      });

      if (response.success) {
        document.getElementById("commitMessage").value = "";
        this.showSuccess("コミットが作成されました");
        await this.loadStagedFiles();
        await this.loadBranches();
      } else {
        this.showError("コミットの作成に失敗しました");
      }
    } catch (error) {
      console.error("Failed to create commit:", error);
      this.showError("コミットの作成に失敗しました");
    }
  }

  async loadCommitHistory() {
    if (!this.currentRepo) {
      document.getElementById("commitHistory").innerHTML =
        '<div class="text-center text-muted">リポジトリを選択してください</div>';
      return;
    }

    try {
      const branchFilter = document.getElementById("historyBranchFilter").value;
      let commits = [];

      if (branchFilter) {
        const branchData = await this.apiRequest(
          `/branch?repository_id=${this.currentRepo.id}`
        );
        const selectedBranch = branchData.branches?.find(
          (b) => b.id == branchFilter
        );

        if (selectedBranch && selectedBranch.head_commit_id) {
          commits = await this.getCommitChain(selectedBranch.head_commit_id);
        }
      } else {
        const data = await this.apiRequest(
          `/commit?repository_id=${this.currentRepo.id}`
        );
        commits = data.commits || [];
      }

      const commitHistory = document.getElementById("commitHistory");
      commitHistory.innerHTML = "";

      if (commits.length > 0) {
        commits.sort((a, b) => new Date(b.created_at) - new Date(a.created_at));

        commits.forEach((commit) => {
          const commitItem = document.createElement("div");
          commitItem.className = "commit-item";

          const authorInitial = commit.author_id
            ? commit.author_id.toString().charAt(0).toUpperCase()
            : "U";
          const commitDate = new Date(commit.created_at).toLocaleString(
            "ja-JP"
          );

          commitItem.innerHTML = `
            <div class="commit-avatar">${authorInitial}</div>
            <div class="commit-details">
              <div class="commit-message">${commit.message}</div>
              <div class="commit-meta">
                ${commitDate}
                <span class="commit-hash">${commit.id
                  .toString()
                  .padStart(7, "0")}</span>
                ${
                  commit.parent_commit_id_2
                    ? '<span class="merge-indicator">マージ</span>'
                    : ""
                }
              </div>
            </div>
          `;

          commitHistory.appendChild(commitItem);
        });
      } else {
        commitHistory.innerHTML =
          '<div class="text-center text-muted">コミット履歴がありません</div>';
      }
    } catch (error) {
      console.error("Failed to load commit history:", error);
    }
  }

  async getCommitChain(commitId) {
    try {
      const allCommitsData = await this.apiRequest(
        `/commit?repository_id=${this.currentRepo.id}`
      );
      const allCommits = allCommitsData.commits || [];

      const commitMap = {};
      allCommits.forEach((commit) => {
        commitMap[commit.id] = commit;
      });

      const chain = [];
      let currentId = commitId;

      while (currentId && commitMap[currentId]) {
        const commit = commitMap[currentId];
        chain.push(commit);
        currentId = commit.parent_commit_id;
      }

      return chain;
    } catch (error) {
      console.error("Failed to get commit chain:", error);
      return [];
    }
  }

  async loadCommitGraph() {
    if (!this.currentRepo) {
      document.getElementById("graphContainer").innerHTML =
        '<div class="text-center text-muted">リポジトリを選択してください</div>';
      return;
    }

    try {
      const data = await this.apiRequest(
        `/graph?repository_id=${this.currentRepo.id}`
      );
      this.renderCommitGraph(data);
    } catch (error) {
      console.error("Failed to load commit graph:", error);
      document.getElementById("graphContainer").innerHTML =
        '<div class="text-center text-muted">グラフの読み込みに失敗しました</div>';
    }
  }

  renderCommitGraph(graphData) {
    const container = document.getElementById("graphContainer");

    if (!graphData.nodes || !graphData.edges) {
      container.innerHTML =
        '<div class="text-center text-muted">グラフデータがありません</div>';
      return;
    }

    const nodes = new vis.DataSet(
      graphData.nodes.map((node) => {
        let color = { background: "#0366d6", border: "#0256cc" };
        let shape = "dot";

        if (typeof node.id === "string" && node.id.startsWith("branch-")) {
          color = { background: "#28a745", border: "#20a039" };
          shape = "box";
        }

        return {
          id: node.id,
          label: node.label,
          shape: shape,
          color: color,
          font: { color: "white", size: 12 },
          size: shape === "box" ? 15 : 20,
        };
      })
    );

    const edges = new vis.DataSet(
      graphData.edges.map((edge) => ({
        from: edge.from,
        to: edge.to,
        arrows: "to",
        color: edge.color || { color: "#586069" },
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
          nodeSpacing: 200,
          levelSeparation: 150,
          blockShifting: true,
          edgeMinimization: true,
          treeSpacing: 100,
        },
      },
      physics: {
        enabled: false,
      },
      nodes: {
        font: {
          size: 12,
          color: "#24292e",
        },
        margin: 10,
      },
      edges: {
        arrows: "to",
        smooth: {
          type: "continuous",
          roundness: 0.1,
        },
      },
      interaction: {
        dragNodes: false,
        zoomView: true,
        dragView: true,
      },
    };

    container.innerHTML = "";
    this.network = new vis.Network(container, { nodes, edges }, options);

    this.network.on("click", (params) => {
      if (params.nodes.length > 0) {
        const nodeId = params.nodes[0];
        console.log("クリックされたノード:", nodeId);
      }
    });
  }

  showModal(title, content, onConfirm = null) {
    document.getElementById("modalTitle").textContent = title;
    document.getElementById("modalBody").innerHTML = content;
    document.getElementById("modal").style.display = "flex";

    const confirmBtn = document.getElementById("modalConfirm");
    confirmBtn.onclick = onConfirm;
    confirmBtn.style.display = onConfirm ? "block" : "none";
  }

  hideModal() {
    document.getElementById("modal").style.display = "none";
  }

  showAddUserModal() {
    const content = `
      <div class="form-group">
        <label for="newUsername">ユーザー名</label>
        <input type="text" id="newUsername" placeholder="ユーザー名を入力">
      </div>
    `;

    this.showModal("新規ユーザー作成", content, async () => {
      const username = document.getElementById("newUsername").value.trim();
      if (!username) {
        this.showError("ユーザー名を入力してください");
        return;
      }

      try {
        await this.apiRequest("/user", {
          method: "POST",
          body: JSON.stringify({ username }),
        });

        this.hideModal();
        this.showSuccess("ユーザーが作成されました");
        await this.loadUsers();
      } catch (error) {
        console.error("Failed to create user:", error);
      }
    });
  }

  showCreateRepoModal() {
    if (!this.currentUser) {
      this.showError("ユーザーを選択してください");
      return;
    }

    const content = `
      <div class="form-group">
        <label for="newRepoName">リポジトリ名</label>
        <input type="text" id="newRepoName" placeholder="リポジトリ名を入力">
      </div>
    `;

    this.showModal("新規リポジトリ作成", content, async () => {
      const name = document.getElementById("newRepoName").value.trim();
      if (!name) {
        this.showError("リポジトリ名を入力してください");
        return;
      }

      try {
        const response = await this.apiRequest("/repository", {
          method: "POST",
          body: JSON.stringify({
            name: name,
            owner_id: parseInt(this.currentUser.id),
          }),
        });

        this.hideModal();
        this.showSuccess("リポジトリが作成されました");
        await this.loadRepositories();
      } catch (error) {
        console.error("Failed to create repository:", error);
      }
    });
  }

  showCloneRepoModal() {
    if (!this.currentUser) {
      this.showError("ユーザーを選択してください");
      return;
    }

    const content = `
      <div class="form-group">
        <label for="cloneRepoId">クローン元リポジトリID</label>
        <input type="number" id="cloneRepoId" placeholder="リポジトリIDを入力">
      </div>
      <div class="form-group">
        <label for="cloneRepoName">新しいリポジトリ名</label>
        <input type="text" id="cloneRepoName" placeholder="新しいリポジトリ名を入力">
      </div>
      <div class="text-muted" style="font-size: 0.9rem;">
        既存のリポジトリを複製して、自分のアカウントで作業できるようになります。
      </div>
    `;

    this.showModal("リポジトリクローン", content, async () => {
      const sourceRepoId = document.getElementById("cloneRepoId").value;
      const newRepoName = document.getElementById("cloneRepoName").value.trim();

      if (!sourceRepoId || !newRepoName) {
        this.showError("すべての項目を入力してください");
        return;
      }

      try {
        const allReposData = await this.apiRequest("/repository");
        const sourceRepo = allReposData.repositories?.find(
          (r) => r.id == sourceRepoId
        );

        if (!sourceRepo) {
          this.showError("指定されたリポジトリが見つかりません");
          return;
        }

        await this.apiRequest("/repository", {
          method: "POST",
          body: JSON.stringify({
            name: newRepoName,
            owner_id: this.currentUser.id,
          }),
        });

        this.hideModal();
        this.showSuccess(
          `リポジトリ「${sourceRepo.name}」が「${newRepoName}」として複製されました`
        );
        await this.loadRepositories();
      } catch (error) {
        console.error("Failed to clone repository:", error);
        this.showError("リポジトリのクローンに失敗しました");
      }
    });
  }

  showCreateBranchModal() {
    if (!this.currentRepo) {
      this.showError("リポジトリを選択してください");
      return;
    }

    const content = `
      <div class="form-group">
        <label for="newBranchName">ブランチ名</label>
        <input type="text" id="newBranchName" placeholder="ブランチ名を入力">
      </div>
    `;

    this.showModal("新規ブランチ作成", content, async () => {
      const name = document.getElementById("newBranchName").value.trim();
      if (!name) {
        this.showError("ブランチ名を入力してください");
        return;
      }

      try {
        await this.apiRequest("/branch", {
          method: "POST",
          body: JSON.stringify({
            name: name,
            repository_id: this.currentRepo.id,
          }),
        });

        this.hideModal();
        this.showSuccess("ブランチが作成されました");
        await this.loadBranches();
      } catch (error) {
        console.error("Failed to create branch:", error);
      }
    });
  }

  showUpdateFileModal() {
    if (!this.currentBranch || !this.currentUser) {
      this.showError("ブランチとユーザーを選択してください");
      return;
    }

    const content = `
      <div class="form-group">
        <label for="updateFileContent">ファイル内容</label>
        <textarea id="updateFileContent" rows="10" placeholder="ファイルの内容を入力してください..."></textarea>
      </div>
    `;

    this.showModal("ファイル更新", content, async () => {
      const content = document.getElementById("updateFileContent").value;

      try {
        await this.apiRequest("/commit", {
          method: "POST",
          body: JSON.stringify({
            branch_id: this.currentBranch.id,
            message: "Update file content",
            author_id: this.currentUser.id,
            content: content,
          }),
        });

        this.hideModal();
        this.showSuccess("ファイルが更新されました");
        await this.loadFiles();
        await this.loadBranches();
      } catch (error) {
        console.error("Failed to update file:", error);
      }
    });
  }

  async showCheckoutModal() {
    if (!this.currentRepo) {
      this.showError("リポジトリを選択してください");
      return;
    }

    try {
      const data = await this.apiRequest(
        `/branch?repository_id=${this.currentRepo.id}`
      );
      const branches = data.branches || [];

      const branchOptions = branches
        .map((branch) => `<option value="${branch.id}">${branch.name}</option>`)
        .join("");

      const content = `
        <div class="form-group">
          <label for="checkoutBranch">チェックアウトするブランチ</label>
          <select id="checkoutBranch">
            <option value="">ブランチを選択</option>
            ${branchOptions}
          </select>
        </div>
      `;

      this.showModal("ブランチチェックアウト", content, async () => {
        const branchId = document.getElementById("checkoutBranch").value;
        if (!branchId) {
          this.showError("ブランチを選択してください");
          return;
        }

        const selectedBranch = branches.find((b) => b.id == branchId);
        if (selectedBranch) {
          this.selectBranch(selectedBranch);
          this.hideModal();
          this.showSuccess(
            `ブランチ '${selectedBranch.name}' にチェックアウトしました`
          );
        }
      });
    } catch (error) {
      console.error("Failed to load branches for checkout:", error);
    }
  }

  async showMergeModal() {
    if (!this.currentRepo || !this.currentBranch) {
      this.showError("リポジトリとブランチを選択してください");
      return;
    }

    try {
      const data = await this.apiRequest(
        `/branch?repository_id=${this.currentRepo.id}`
      );
      const branches =
        data.branches?.filter((b) => b.id !== this.currentBranch.id) || [];

      const branchOptions = branches
        .map((branch) => `<option value="${branch.id}">${branch.name}</option>`)
        .join("");

      const content = `
        <div class="form-group">
          <label>現在のブランチ: <strong>${this.currentBranch.name}</strong></label>
        </div>
        <div class="form-group">
          <label for="mergeBranch">マージするブランチ</label>
          <select id="mergeBranch">
            <option value="">ブランチを選択</option>
            ${branchOptions}
          </select>
        </div>
      `;

      this.showModal("ブランチマージ", content, async () => {
        const branchId = document.getElementById("mergeBranch").value;
        if (!branchId) {
          this.showError("マージするブランチを選択してください");
          return;
        }

        try {
          const result = await this.apiRequest("/merge", {
            method: "POST",
            body: JSON.stringify({
              branch_id_1: this.currentBranch.id,
              branch_id_2: branchId,
            }),
          });

          if (result.success) {
            this.hideModal();
            this.showSuccess("マージが完了しました");
            await this.loadFiles();
            await this.loadCommitHistory();
            await this.loadBranches();
          } else {
            this.handleMergeConflict(result, branchId);
          }
        } catch (error) {
          console.error("Failed to merge:", error);
        }
      });
    } catch (error) {
      console.error("Failed to load branches for merge:", error);
    }
  }

  handleMergeConflict(conflictData, branchId) {
    const content = `
      <div class="mb-2">
        <strong>マージコンフリクトが発生しました</strong>
      </div>
      <div style="display: flex; gap: 1rem; margin-bottom: 1rem;">
        <div style="flex: 1;">
          <label>現在のブランチの内容</label>
          <textarea id="conflictText1" rows="8" readonly>${
            conflictData.text_1 || ""
          }</textarea>
        </div>
        <div style="flex: 1;">
          <label>マージするブランチの内容</label>
          <textarea id="conflictText2" rows="8" readonly>${
            conflictData.text_2 || ""
          }</textarea>
        </div>
      </div>
      <div class="form-group">
        <label for="resolvedText">解決後の内容</label>
        <textarea id="resolvedText" rows="8" placeholder="マージ後の内容を入力してください...">${
          conflictData.text_1 || ""
        }</textarea>
      </div>
    `;

    this.showModal("マージコンフリクトの解決", content, async () => {
      const resolvedText = document.getElementById("resolvedText").value;

      try {
        await this.apiRequest("/force-merge", {
          method: "POST",
          body: JSON.stringify({
            branch_id_1: this.currentBranch.id,
            branch_id_2: branchId,
            text: resolvedText,
          }),
        });

        this.hideModal();
        this.showSuccess("マージコンフリクトが解決されました");
        await this.loadFiles();
        await this.loadCommitHistory();
        await this.loadBranches();
      } catch (error) {
        console.error("Failed to resolve merge conflict:", error);
      }
    });
  }

  async showResetModal() {
    if (!this.currentRepo || !this.currentBranch) {
      this.showError("リポジトリとブランチを選択してください");
      return;
    }

    try {
      const data = await this.apiRequest(
        `/commit?repository_id=${this.currentRepo.id}`
      );
      const commits = data.commits || [];

      const commitOptions = commits
        .map(
          (commit) =>
            `<option value="${commit.id}">${commit.message} (${commit.id})</option>`
        )
        .join("");

      const content = `
        <div class="form-group">
          <label>現在のブランチ: <strong>${this.currentBranch.name}</strong></label>
        </div>
        <div class="form-group">
          <label for="resetCommit">リセット先コミット</label>
          <select id="resetCommit">
            <option value="">コミットを選択</option>
            ${commitOptions}
          </select>
        </div>
        <div class="text-muted" style="font-size: 0.9rem; color: #dc3545;">
          警告: リセットは取り消せない操作です。
        </div>
      `;

      this.showModal("ブランチリセット", content, async () => {
        const commitId = document.getElementById("resetCommit").value;
        if (!commitId) {
          this.showError("リセット先コミットを選択してください");
          return;
        }

        try {
          await this.apiRequest("/branch", {
            method: "PUT",
            body: JSON.stringify({
              id: this.currentBranch.id,
              commit_id: commitId,
            }),
          });

          this.hideModal();
          this.showSuccess("リセットが完了しました");
          await this.loadBranches();
          await this.loadFiles();
          await this.loadCommitHistory();
        } catch (error) {
          console.error("Failed to reset:", error);
        }
      });
    } catch (error) {
      console.error("Failed to load commits for reset:", error);
    }
  }

  showSuccess(message) {
    this.showNotification(message, "success");
  }

  showError(message) {
    this.showNotification(message, "error");
  }

  showNotification(message, type = "info") {
    const existingNotification = document.querySelector(".notification");
    if (existingNotification) {
      existingNotification.remove();
    }

    const notification = document.createElement("div");
    notification.className = `notification notification-${type}`;
    notification.textContent = message;

    notification.style.cssText = `
      position: fixed;
      top: 20px;
      right: 20px;
      padding: 1rem 1.5rem;
      border-radius: 6px;
      color: white;
      font-weight: 500;
      z-index: 10000;
      opacity: 0;
      transform: translateX(100%);
      transition: all 0.3s ease;
      ${type === "success" ? "background-color: #28a745;" : ""}
      ${type === "error" ? "background-color: #dc3545;" : ""}
      ${type === "info" ? "background-color: #0366d6;" : ""}
    `;

    document.body.appendChild(notification);

    setTimeout(() => {
      notification.style.opacity = "1";
      notification.style.transform = "translateX(0)";
    }, 100);

    setTimeout(() => {
      notification.style.opacity = "0";
      notification.style.transform = "translateX(100%)";
      setTimeout(() => {
        if (notification.parentNode) {
          notification.parentNode.removeChild(notification);
        }
      }, 300);
    }, 3000);
  }
}

document.addEventListener("DOMContentLoaded", () => {
  window.gitGUI = new GitGUI();
});

function escapeHtml(str) {
  const div = document.createElement("div");
  div.textContent = str;
  return div.innerHTML;
}

function getRelativeTime(date) {
  const now = new Date();
  const targetDate = new Date(date);
  const diffMs = now - targetDate;
  const diffSecs = Math.floor(diffMs / 1000);
  const diffMins = Math.floor(diffSecs / 60);
  const diffHours = Math.floor(diffMins / 60);
  const diffDays = Math.floor(diffHours / 24);

  if (diffSecs < 60) {
    return "数秒前";
  } else if (diffMins < 60) {
    return `${diffMins}分前`;
  } else if (diffHours < 24) {
    return `${diffHours}時間前`;
  } else if (diffDays < 7) {
    return `${diffDays}日前`;
  } else {
    return targetDate.toLocaleDateString("ja-JP");
  }
}

function formatFileSize(bytes) {
  if (bytes === 0) return "0 B";

  const k = 1024;
  const sizes = ["B", "KB", "MB", "GB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));

  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + " " + sizes[i];
}

function shortenCommitHash(hash) {
  const hashStr = hash.toString();
  return hashStr.length > 7
    ? hashStr.substring(0, 7)
    : hashStr.padStart(7, "0");
}
