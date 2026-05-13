(function () {
    const STORAGE_KEY = "genshingo-guide-messages-v1";
    const OLD_STORAGE_KEY = ["deep", "seek-agent-conversations-v2"].join("");
    const GUIDE_TITLE = "如何下载原神";
    const OFFICIAL_DOWNLOAD_URL = "https://ys.mihoyo.com/main/";
    const seedMessages = [
        { role: "user", content: "如何下载原神？" },
        {
            role: "assistant",
            content:
                "建议只走官方或系统应用商店渠道下载：\n\n" +
                "1. 电脑端：打开原神官网，选择对应平台的官方客户端。\n" +
                "2. iPhone / iPad：在 App Store 搜索“原神”，确认开发者信息后安装。\n" +
                "3. Android：优先使用官网或手机自带应用商店，避免不明安装包。\n" +
                "4. 安装前确认磁盘空间、网络环境和账号登录方式，安装过程中只同意你看得懂、确认需要的权限。\n\n" +
                "我不会也不能设计后台静默下载、隐藏安装窗口或自动同意权限请求；下载和安装必须由用户明确点击并确认。",
            actions: [
                { label: "打开官方页面", href: OFFICIAL_DOWNLOAD_URL },
                { label: "查看安全提醒", href: "#safety" }
            ]
        }
    ];

    const appShell = document.getElementById("appShell");
    const input = document.getElementById("input");
    const btnSend = document.getElementById("btnSend");
    const chatForm = document.getElementById("chatForm");
    const messagesEl = document.getElementById("messages");
    const statusHint = document.getElementById("statusHint");
    const historyList = document.getElementById("historyList");
    const btnGuide = document.getElementById("btnGuide");
    const btnSearch = document.getElementById("btnSearch");
    const toggleThink = document.getElementById("toggleThink");
    const btnCollapse = document.getElementById("btnCollapse");
    const btnRestore = document.getElementById("btnRestore");

    let sending = false;
    let messages = loadMessages();

    localStorage.removeItem(OLD_STORAGE_KEY);
    if (window.matchMedia("(max-width: 820px)").matches) {
        appShell.classList.add("sidebar-collapsed");
    }

    function loadMessages() {
        try {
            const raw = localStorage.getItem(STORAGE_KEY);
            const parsed = raw ? JSON.parse(raw) : null;
            if (!Array.isArray(parsed)) return cloneSeed();
            const clean = parsed.filter(isRenderableMessage).slice(0, 40);
            return clean.length ? clean : cloneSeed();
        } catch {
            return cloneSeed();
        }
    }

    function cloneSeed() {
        return seedMessages.map((message) => ({
            role: message.role,
            content: message.content,
            actions: message.actions ? message.actions.map((action) => ({ ...action })) : undefined
        }));
    }

    function saveMessages() {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(messages.filter(isRenderableMessage).slice(0, 40)));
    }

    function isRenderableMessage(message) {
        return message
            && (message.role === "user" || message.role === "assistant")
            && typeof message.content === "string";
    }

    function currentGroupLabel() {
        const date = new Date();
        return date.getFullYear() + "-" + String(date.getMonth() + 1).padStart(2, "0");
    }

    function renderHistory() {
        historyList.replaceChildren();

        const section = document.createElement("section");
        section.className = "history-group";

        const title = document.createElement("h2");
        title.className = "history-group-title";
        title.textContent = currentGroupLabel();
        section.appendChild(title);

        const item = document.createElement("button");
        item.type = "button";
        item.className = "history-item active";
        item.title = GUIDE_TITLE;
        const text = document.createElement("span");
        text.textContent = GUIDE_TITLE;
        item.appendChild(text);
        item.addEventListener("click", () => {
            renderMessages();
            input.focus();
        });
        section.appendChild(item);
        historyList.appendChild(section);
    }

    function renderMessages() {
        appShell.classList.add("has-messages");
        messagesEl.hidden = false;
        const list = document.createElement("div");
        list.className = "message-list";
        messages.forEach((message, index) => {
            list.appendChild(renderMessage(message, index));
        });
        messagesEl.replaceChildren(list);
        requestAnimationFrame(() => {
            messagesEl.scrollTop = messagesEl.scrollHeight;
        });
    }

    function renderMessage(message, index) {
        const row = document.createElement("article");
        row.className = "message-row " + message.role;

        if (message.role === "assistant") {
            const avatar = document.createElement("span");
            avatar.className = "message-avatar";
            avatar.textContent = "G";
            row.appendChild(avatar);
        }

        const card = document.createElement("div");
        card.className = "message-card" + (message.error ? " error" : "");
        if (index === 1) {
            card.id = "safety";
        }

        if (message.pending) {
            card.appendChild(renderTyping());
        } else {
            const text = document.createElement("div");
            text.className = "message-text";
            text.textContent = message.content || "";
            card.appendChild(text);
            if (message.actions && message.actions.length) {
                card.appendChild(renderActions(message.actions));
            }
        }

        row.appendChild(card);
        return row;
    }

    function renderActions(actions) {
        const wrap = document.createElement("div");
        wrap.className = "assistant-actions";
        actions.forEach((action) => {
            const link = document.createElement("a");
            link.className = "assistant-action";
            link.textContent = action.label;
            link.href = action.href;
            if (action.href.startsWith("http")) {
                link.target = "_blank";
                link.rel = "noopener noreferrer";
            }
            wrap.appendChild(link);
        });
        return wrap;
    }

    function renderTyping() {
        const wrap = document.createElement("span");
        wrap.className = "typing";
        wrap.setAttribute("aria-label", "正在生成");
        for (let i = 0; i < 3; i += 1) {
            wrap.appendChild(document.createElement("i"));
        }
        return wrap;
    }

    function buildPayloadMessages() {
        return messages
            .filter((message) => !message.pending && !message.error)
            .map((message) => ({ role: message.role, content: message.content }));
    }

    async function sendMessage() {
        const text = input.value.trim();
        if (!text || sending) return;

        const userMessage = { role: "user", content: text };
        const assistantMessage = { role: "assistant", content: "", pending: true };
        messages.push(userMessage, assistantMessage);

        input.value = "";
        autoGrowInput();
        sending = true;
        clearStatus();
        updateSendState();
        renderMessages();

        try {
            const response = await fetch("/api/chat", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    mode: "flash",
                    deepThinking: toggleThink.getAttribute("aria-pressed") === "true",
                    messages: buildPayloadMessages()
                })
            });
            const data = await readJson(response);
            if (!response.ok) {
                throw new Error(data.error || response.statusText || "请求失败");
            }
            assistantMessage.pending = false;
            assistantMessage.content = data.content || "GenshinGo 暂时没有返回内容。";
            saveMessages();
        } catch (error) {
            assistantMessage.pending = false;
            assistantMessage.error = true;
            assistantMessage.content = "请求失败：" + (error.message || "请稍后重试");
            setStatus(error.message || "请求失败", true);
            saveMessages();
        } finally {
            sending = false;
            updateSendState();
            renderMessages();
        }
    }

    async function readJson(response) {
        const raw = await response.text();
        if (!raw) return {};
        try {
            return JSON.parse(raw);
        } catch {
            return { error: raw };
        }
    }

    function setStatus(text, error) {
        statusHint.textContent = text || "";
        statusHint.classList.toggle("error", Boolean(error));
    }

    function clearStatus() {
        setStatus("", false);
    }

    function updateSendState() {
        const ready = input.value.trim().length > 0 && !sending;
        btnSend.disabled = !ready;
        btnSend.classList.toggle("ready", ready);
    }

    function autoGrowInput() {
        input.style.height = "auto";
        input.style.height = Math.min(input.scrollHeight, 190) + "px";
    }

    function resetGuide() {
        messages = cloneSeed();
        saveMessages();
        clearStatus();
        input.value = "";
        autoGrowInput();
        updateSendState();
        renderHistory();
        renderMessages();
        input.focus();
    }

    toggleThink.addEventListener("click", () => {
        const isPressed = toggleThink.getAttribute("aria-pressed") === "true";
        toggleThink.setAttribute("aria-pressed", String(!isPressed));
    });

    btnCollapse.addEventListener("click", () => appShell.classList.add("sidebar-collapsed"));
    btnRestore.addEventListener("click", () => appShell.classList.remove("sidebar-collapsed"));
    btnGuide.addEventListener("click", resetGuide);

    btnSearch.addEventListener("click", () => {
        input.focus();
        input.select();
    });

    chatForm.addEventListener("submit", (event) => {
        event.preventDefault();
        sendMessage();
    });

    input.addEventListener("input", () => {
        autoGrowInput();
        updateSendState();
    });

    input.addEventListener("keydown", (event) => {
        if (event.key === "Enter" && !event.shiftKey && !event.isComposing) {
            event.preventDefault();
            sendMessage();
        }
    });

    document.addEventListener("keydown", (event) => {
        if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "j") {
            event.preventDefault();
            resetGuide();
        }
    });

    renderHistory();
    renderMessages();
    autoGrowInput();
    updateSendState();
})();
