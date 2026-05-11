(function () {
    const STORAGE_KEY = "deepseek-agent-conversations";
    const input = document.getElementById("input");
    const btnSend = document.getElementById("btnSend");
    const messagesEl = document.getElementById("messages");
    const emptyState = document.getElementById("emptyState");
    const statusHint = document.getElementById("statusHint");
    const historyList = document.getElementById("historyList");
    const btnNewChat = document.getElementById("btnNewChat");
    const modeFlash = document.getElementById("modeFlash");
    const modePro = document.getElementById("modePro");
    const toggleThink = document.getElementById("toggleThink");
    const toggleSearch = document.getElementById("toggleSearch");
    const btnCollapse = document.getElementById("btnCollapse");
    const sidebar = document.querySelector(".sidebar");

    let mode = "flash";
    let conversations = loadAll();
    let activeId = conversations[0]?.id || null;
    let sending = false;

    function loadAll() {
        try {
            const raw = localStorage.getItem(STORAGE_KEY);
            if (!raw) return [];
            const arr = JSON.parse(raw);
            return Array.isArray(arr) ? arr : [];
        } catch {
            return [];
        }
    }

    function saveAll() {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(conversations));
    }

    function activeConv() {
        return conversations.find((c) => c.id === activeId) || null;
    }

    function ensureConversation() {
        if (!activeId) {
            const c = {
                id: crypto.randomUUID(),
                title: "新对话",
                updatedAt: Date.now(),
                mode: mode,
                messages: [],
            };
            conversations.unshift(c);
            activeId = c.id;
            saveAll();
        }
    }

    function groupLabel(ts) {
        const d = new Date(ts);
        const y = d.getFullYear();
        const m = String(d.getMonth() + 1).padStart(2, "0");
        return y + "-" + m;
    }

    function renderHistory() {
        historyList.innerHTML = "";
        const groups = {};
        conversations.forEach((c) => {
            const g = groupLabel(c.updatedAt);
            if (!groups[g]) groups[g] = [];
            groups[g].push(c);
        });
        Object.keys(groups)
            .sort()
            .reverse()
            .forEach((g) => {
                const title = document.createElement("div");
                title.className = "history-group-title";
                title.textContent = g;
                historyList.appendChild(title);
                groups[g].forEach((c) => {
                    const el = document.createElement("div");
                    el.className = "history-item" + (c.id === activeId ? " active" : "");
                    el.textContent = c.title || "新对话";
                    el.title = c.title || "新对话";
                    el.addEventListener("click", () => {
                        activeId = c.id;
                        mode = c.mode || "flash";
                        syncModeUi();
                        renderHistory();
                        renderMessages();
                    });
                    historyList.appendChild(el);
                });
            });
    }

    function renderMessages() {
        const c = activeConv();
        if (!c || c.messages.length === 0) {
            messagesEl.innerHTML = "";
            messagesEl.hidden = true;
            emptyState.hidden = false;
            updateHeroTitle();
            return;
        }
        emptyState.hidden = true;
        messagesEl.hidden = false;
        messagesEl.innerHTML = "";
        c.messages.forEach((m) => {
            const div = document.createElement("div");
            div.className = "msg " + (m.role === "user" ? "user" : "assistant");
            const label = document.createElement("div");
            label.className = "role-label";
            label.textContent = m.role === "user" ? "你" : "DeepSeek";
            div.appendChild(label);
            div.appendChild(document.createTextNode(m.content));
            messagesEl.appendChild(div);
        });
        messagesEl.scrollTop = messagesEl.scrollHeight;
    }

    function updateHeroTitle() {
        const hero = document.querySelector(".hero-title");
        if (!hero) return;
        hero.textContent = mode === "flash" ? "使用快速模式开始对话" : "使用专家模式开始对话";
    }

    function syncModeUi() {
        modeFlash.classList.toggle("active", mode === "flash");
        modePro.classList.toggle("active", mode === "pro");
        updateHeroTitle();
    }

    function setMode(next) {
        mode = next;
        const c = activeConv();
        if (c) {
            c.mode = mode;
            saveAll();
        }
        syncModeUi();
    }

    modeFlash.addEventListener("click", () => setMode("flash"));
    modePro.addEventListener("click", () => setMode("pro"));

    toggleThink.addEventListener("click", () => {
        const on = toggleThink.getAttribute("aria-pressed") === "true";
        toggleThink.setAttribute("aria-pressed", String(!on));
    });

    toggleSearch.addEventListener("click", () => {
        const on = toggleSearch.getAttribute("aria-pressed") === "true";
        toggleSearch.setAttribute("aria-pressed", String(!on));
        if (!on) {
            statusHint.textContent = "智能搜索为界面演示，后端未接联网检索。";
            statusHint.classList.remove("error");
        } else {
            statusHint.textContent = "";
        }
    });

    btnCollapse.addEventListener("click", () => {
        sidebar.classList.toggle("collapsed");
    });

    btnNewChat.addEventListener("click", () => {
        activeId = null;
        ensureConversation();
        renderHistory();
        renderMessages();
        statusHint.textContent = "";
    });

    async function send() {
        const text = (input.value || "").trim();
        if (!text || sending) return;
        ensureConversation();
        const c = activeConv();
        c.mode = mode;
        c.messages.push({ role: "user", content: text });
        if (c.title === "新对话") {
            c.title = text.slice(0, 18) + (text.length > 18 ? "…" : "");
        }
        c.updatedAt = Date.now();
        input.value = "";
        statusHint.textContent = "";
        statusHint.classList.remove("error");
        renderHistory();
        renderMessages();
        sending = true;
        btnSend.disabled = true;

        const payload = {
            mode: mode,
            deepThinking: toggleThink.getAttribute("aria-pressed") === "true",
            messages: c.messages.map((m) => ({ role: m.role, content: m.content })),
        };

        try {
            const res = await fetch("/api/chat", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload),
            });
            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                throw new Error(data.error || res.statusText);
            }
            const reply = data.content || "";
            c.messages.push({ role: "assistant", content: reply });
            c.updatedAt = Date.now();
            saveAll();
            renderHistory();
            renderMessages();
        } catch (e) {
            statusHint.textContent = e.message || "请求失败";
            statusHint.classList.add("error");
            c.messages.pop();
            saveAll();
            renderMessages();
        } finally {
            sending = false;
            btnSend.disabled = false;
        }
    }

    btnSend.addEventListener("click", send);
    input.addEventListener("keydown", (ev) => {
        if (ev.key === "Enter" && !ev.shiftKey) {
            ev.preventDefault();
            send();
        }
    });

    if (!activeId && conversations.length) {
        activeId = conversations[0].id;
        mode = conversations[0].mode || "flash";
    }
    syncModeUi();
    renderHistory();
    renderMessages();
})();
