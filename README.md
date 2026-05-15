# GenshinGo（deepseek-agent）

基于 **Spring Boot 3** 的本地 Web 应用：内置中文对话界面，后端将请求转发至 **DeepSeek 官方 API**（`/chat/completions`），并支持可选的 **云端聊天代理**，便于在服务器侧集中保管密钥。

| 项目 | 说明 |
|------|------|
| Maven 坐标 | `com.zyf:deepseek-agent` |
| 前端品牌名 | GenshinGo（静态页 + JS） |
| Spring 应用名 | `genshingo` |
| 默认 HTTP 端口 | `8080`（可被启动参数或脚本覆盖） |

---

## 功能概览

- **对话网页**：访问根路径 `/` 使用内置 UI，通过同源 `POST /api/chat` 与后端通信。
- **双模式**：`flash`（快速）与 `pro`（专家），模型名在配置中映射到 DeepSeek 模型 ID。
- **深度思考**：请求体中的 `deepThinking` 会映射为 DeepSeek 的 `thinking` 与 `reasoning_effort` 字段。
- **直连与代理**：未配置云端代理时使用 `DEEPSEEK_API_KEY` 直连官方接口；配置 `CLOUD_CHAT_API_URL` 后，后端将同一套 JSON 请求体 POST 到代理，由代理返回兼容格式的 JSON。
- **Windows 一键启动**：`run.bat` 自动解析 `JAVA_HOME`、在 8080–8099 中选空闲端口或校验指定端口，并在应用就绪后尝试用系统默认浏览器打开首页。

---

## 技术栈

- **Java**：`17`（`pom.xml` 中 `maven-compiler-plugin` 使用 `release`；更高版本 JDK 通常也可运行，以本机验证为准）
- **Spring Boot**：`3.3.9`
- **Web**：`spring-boot-starter-web`（Tomcat + MVC）
- **HTTP 客户端**：`spring-boot-starter-webflux`（`WebClient` 调用 DeepSeek / 云端代理）
- **校验**：`spring-boot-starter-validation`（`@Valid` 请求体）

---

## 目录结构（节选）

```text
deepseek-agent/
├── mvnw / mvnw.cmd          # Maven Wrapper，无需全局安装 Maven
├── pom.xml
├── run.bat                  # Windows 启动脚本（UTF-8、端口、浏览器）
├── scripts/
│   ├── print-java-home.ps1  # 辅助解析 JAVA_HOME
│   ├── find-free-port.ps1   # 在端口区间内选未监听端口
│   └── test-port-free.ps1   # 检测指定端口是否已被占用
├── src/main/java/com/zyf/deepseek/
│   ├── DeepseekAgentApplication.java
│   ├── config/              # WebClient、DeepSeek 配置、就绪后打开浏览器
│   ├── dto/                 # 请求/响应 DTO
│   ├── service/             # 聊天与云端解析逻辑
│   └── web/                 # REST 与全局异常处理
└── src/main/resources/
    ├── application.yml
    └── static/              # 前端静态资源（/、/css、/js）
```

---

## 环境要求

- **JDK**：建议 **17 及以上**，并保证 `JAVA_HOME` 指向 JDK 根目录（其下存在 `bin\java.exe`）。
- **操作系统**：开发与脚本以 **Windows** 为主（`run.bat`、PowerShell 辅助脚本）；用 `mvnw`/`java -jar` 在 Linux/macOS 上运行后端一般也可行，但无等价的 `run.bat` 整合脚本。
- **网络**：需能访问 `https://api.deepseek.com`（直连模式），或能访问你自建的云端代理 URL。

---

## 快速开始（Windows）

### 1. 克隆仓库

仓库在 GitHub 上建议命名为 **GenshinGo**（与产品名一致）。若已完成重命名，使用：

```bash
git clone https://github.com/XXYoLoong/GenshinGo.git
cd GenshinGo
```

若你尚未在 GitHub 上改名、仍使用旧仓库名 **JAVA-DEEPSEEK**，可暂时：

```bash
git clone https://github.com/XXYoLoong/JAVA-DEEPSEEK.git
cd JAVA-DEEPSEEK
```

**在 GitHub 上改名**：仓库页 → **Settings** → **General** → **Repository name** → 改为 `GenshinGo` → **Rename**。改名后旧地址会重定向，但建议本地执行：

```bash
git remote set-url origin https://github.com/XXYoLoong/GenshinGo.git
```

以便与文档中的克隆地址一致。

### 2. 配置密钥（直连官方 API）

**不要将密钥写入仓库。** 在系统或当前会话中设置环境变量：

```bat
set DEEPSEEK_API_KEY=你的_API_Key
```

或在 PowerShell：

```powershell
$env:DEEPSEEK_API_KEY = "你的_API_Key"
```

### 3. 启动

双击或在命令行执行项目根目录下的 **`run.bat`**：

- **未传参数**：在 **8080–8099** 中自动挑选第一个未被占用的端口，并传入 `--deepseek.launch-browser=true`，就绪后在 Windows 上通过 `cmd /c start` 尝试打开默认浏览器。
- **指定端口**：第一个参数为纯数字时视为端口，例如：

  ```bat
  run.bat 8082
  ```

- **附加 Maven 参数**：在端口（若已指定）之后附加，例如：

  ```bat
  run.bat 8082 -DskipTests
  ```

脚本会：

1. 将工作目录切换到脚本所在目录，并检查 `pom.xml`；
2. 解析 `JAVA_HOME`（环境变量 → 注册表 JDK → `scripts\print-java-home.ps1`）；
3. 校验端口或自动选端口（PowerShell + `Get-NetTCPConnection`）；
4. 调用 **`mvnw.cmd spring-boot:run`**，并注入 `--server.port` 与 `--deepseek.launch-browser=true`。

### 4. 打开网页

控制台会打印 **`[信息] 服务地址: http://localhost:端口号`**。若自动打开浏览器失败（安全软件拦截等），请手动在浏览器中打开该地址。

---

## 使用 Maven / JAR（跨平台）

### 开发运行

```bash
./mvnw spring-boot:run
# Windows:
mvnw.cmd spring-boot:run
```

可选：与 `run.bat` 一致的行为（指定端口 + 自动打开浏览器，仅 Windows 下 `start` 生效）：

```bash
mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8080 --deepseek.launch-browser=true"
```

### 打包与运行

```bash
./mvnw -DskipTests package
java -jar target/deepseek-agent-1.0.0-SNAPSHOT.jar
```

运行前同样需设置环境变量或改用云端代理（见下文）。

---

## 配置说明（`application.yml`）

| 配置项 | 含义 |
|--------|------|
| `server.port` | 默认 HTTP 端口（可被命令行 `--server.port` 覆盖） |
| `deepseek.base-url` | DeepSeek API 根地址，默认 `https://api.deepseek.com` |
| `deepseek.api-key` | 默认从环境变量 **`DEEPSEEK_API_KEY`** 读取；勿写入仓库 |
| `deepseek.request-timeout-seconds` | 请求超时秒数（与官方通信时至少 10 秒下限） |
| `deepseek.cloud.chat-url` | 云端代理完整 URL，来自 **`CLOUD_CHAT_API_URL`**；非空时优先走代理 |
| `deepseek.cloud.token` | 调用代理时可选的 Bearer Token，来自 **`CLOUD_API_TOKEN`** |
| `deepseek.models.flash` / `pro` | 分别对应前端 `flash` / `pro` 模式下的模型名 |
| `deepseek.launch-browser` | 为 `true` 时在应用就绪后尝试打开浏览器；`run.bat` 会传入 |

直连与代理的优先级逻辑见 `DeepSeekChatService`：**若云端 `chat-url` 已配置，则不再要求本地配置 `DEEPSEEK_API_KEY`**；否则必须提供 `DEEPSEEK_API_KEY`。

---

## 环境变量一览

| 变量名 | 必填 | 说明 |
|--------|------|------|
| `DEEPSEEK_API_KEY` | 直连时必填 | DeepSeek 官方 API Key |
| `CLOUD_CHAT_API_URL` | 可选 | 配置后请求发往该 URL，请求体与直连时一致（`ClientChatRequest` JSON） |
| `CLOUD_API_TOKEN` | 可选 | 调用云端代理时附加 `Authorization: Bearer ...` |

---

## HTTP API

### `POST /api/chat`

- **Content-Type**：`application/json`
- **请求体**：`ClientChatRequest`

| 字段 | 类型 | 说明 |
|------|------|------|
| `mode` | `flash` \| `pro` | 默认 `flash`；决定使用的模型别名 |
| `messages` | 数组 | 至少一条；每条含 `role` 与 `content` |
| `deepThinking` | 布尔 | 是否开启深度思考（映射到官方 `thinking` 等字段） |

**`messages[].role` 允许取值**：`system`、`user`、`assistant`。

**限制（服务端校验）**：

- 单次最多 **40** 条消息；
- 单条 `content` 最长 **24000** 字符。

**响应体**：`ClientChatResponse`

| 字段 | 说明 |
|------|------|
| `content` | 助手回复正文 |
| `reasoning` | 若有，为推理/思考过程文本 |
| `model` | 实际使用的模型名或占位（云端解析失败时可能为简化标识） |

错误时由全局异常处理返回 JSON（含 `error` 字段等），HTTP 状态码与异常类型相关（如密钥未配置可能为 `503`）。

### 云端代理响应格式

后端对代理返回的 JSON 做了宽松解析，支持包括但不限于：顶层 `content` / `text` / `message`、`data` 子对象，或类 OpenAI 的 `choices[0].message` 结构。具体逻辑见 `DeepSeekChatService.parseCloudResponse`。

---

## 请求与模型行为（直连 DeepSeek）

- 调用路径：`POST {baseUrl}/chat/completions`
- 请求中设置 `stream: false`，并根据 UI 设置 `thinking`、`reasoning_effort` 等字段。
- 从返回的 `choices[0].message` 中提取 `content` 与 `reasoning_content`。

---

## 测试

```bash
./mvnw test
```

---

## 常见问题

1. **`run.bat` 提示找不到 `JAVA_HOME`**  
   安装 JDK 后设置系统环境变量 `JAVA_HOME` 为 JDK 根目录，或确保注册表中存在 JDK 安装信息。

2. **端口被占用**  
   使用 `run.bat 其他端口` 指定空闲端口，或关闭占用 8080–8099 的进程。

3. **Maven 日志里项目路径乱码**  
   仓库放在含中文或特殊字符的路径时，部分工具链日志可能出现乱码；一般不影响运行。若遇异常，可尝试将项目放在纯英文路径下再试。

4. **浏览器未自动打开**  
   Windows 下由 `LaunchBrowserOnReady` 使用 `cmd /c start` 调起默认浏览器；若被安全软件拦截，请手动打开控制台打印的本地 URL。

5. **密钥安全**  
   不要将 `DEEPSEEK_API_KEY` 或代理 Token 写入 `application.yml` 并提交到 Git；生产环境建议使用容器或进程管理器注入环境变量。

---

## AI 开发提示词

使用 Cursor、Copilot 等 AI 工具维护本项目时，请先阅读 [`AI_PROMPT.md`](AI_PROMPT.md)（含可粘贴的系统提示词、架构说明与任务模板）。

---

## 开源协议

本项目使用 **Apache License 2.0**，详见仓库根目录 [`LICENSE`](LICENSE)。

---

## 相关链接

- DeepSeek 开放平台：<https://platform.deepseek.com/>
- Spring Boot 文档：<https://spring.io/projects/spring-boot>
