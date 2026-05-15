# GenshinGo（deepseek-agent）AI 使用提示词

本文档供 Cursor、Copilot、ChatGPT 等 AI 助手在参与本仓库开发时参考。可将「快速粘贴版」整段复制到对话开头，或把本文件加入 AI 上下文。

---

## 快速粘贴版（System / 项目上下文）

```
你是 GenshinGo（Maven 坐标 com.zyf:deepseek-agent）项目的开发助手。

【项目定位】
基于 Spring Boot 3 的本地 Web 应用：内置中文对话 UI，后端将聊天请求转发至 DeepSeek 官方 API（/chat/completions），或可选的云端聊天代理。前端品牌名 GenshinGo，Spring 应用名 genshingo，默认端口 8080。

【技术栈】
- Java 17、Spring Boot 3.3.9
- spring-boot-starter-web（MVC + Tomcat）
- spring-boot-starter-webflux（WebClient 调用外部 API）
- spring-boot-starter-validation（@Valid 请求校验）
- 前端：纯静态 HTML/CSS/JS（src/main/resources/static/），无构建工具链

【核心架构】
- ChatController（/api/chat）→ DeepSeekChatService → WebClient
- 双路由：CLOUD_CHAT_API_URL 已配置时走云端代理；否则用 DEEPSEEK_API_KEY 直连官方 API
- DTO：ClientChatRequest / ClientChatResponse / ChatMessageDto
- 配置：DeepSeekProperties（prefix=deepseek）+ application.yml
- 异常：ApiExceptionHandler 统一返回 JSON { "error": "..." }

【请求契约 POST /api/chat】
{
  "mode": "flash" | "pro",        // 映射到 deepseek.models.flash / pro
  "deepThinking": boolean,        // 映射 thinking.type 与 reasoning_effort
  "messages": [{ "role": "system|user|assistant", "content": "..." }]
}
限制：最多 40 条消息，单条 content 最长 24000 字符。
响应：{ "content", "reasoning", "model" }

【目录约定】
com.zyf.deepseek/
  config/   — WebClient、DeepSeekProperties、LaunchBrowserOnReady
  dto/      — 请求/响应数据类
  service/  — DeepSeekChatService（核心业务）
  web/      — ChatController、ApiExceptionHandler
static/     — index.html、css/app.css、js/app.js

【开发原则】
1. 最小改动：只改与任务相关的文件，不做无关重构。
2. 风格一致：沿用现有包结构、构造器注入、中文异常/日志文案。
3. 密钥安全：绝不将 DEEPSEEK_API_KEY、CLOUD_API_TOKEN 写入代码或提交到 Git；只用环境变量或占位符 ${DEEPSEEK_API_KEY:}。
4. 前端保持轻量：不引入 npm/webpack，除非用户明确要求。
5. 云端代理兼容：扩展响应解析时保持 parseCloudResponse 的宽松多格式支持。
6. 直连模式 stream 固定为 false；深度思考通过 thinking 与 reasoning_effort 控制。
7. Windows 友好：run.bat 与 scripts/*.ps1 是主要启动路径，改动端口/浏览器逻辑时需同步考虑。

【验证方式】
mvnw test                          # 单元测试（目前仅 contextLoads）
mvnw spring-boot:run               # 开发运行
run.bat [端口]                     # Windows 一键启动
curl -X POST http://localhost:8080/api/chat -H "Content-Type: application/json" -d "{\"mode\":\"flash\",\"messages\":[{\"role\":\"user\",\"content\":\"你好\"}]}"

【常见任务指引】
- 新增 API 端点 → web/ 下新建 Controller 或扩展现有 Controller，必要时加 DTO 与 Service
- 改模型映射 → application.yml 的 deepseek.models
- 改 UI/交互 → static/ 下三文件，localStorage 键 genshingo-guide-messages-v1
- 改错误提示 → ApiExceptionHandler 或 Service 中 IllegalStateException / IllegalArgumentException 文案
- 加配置项 → DeepSeekProperties + application.yml，用 @ConfigurationProperties 绑定

【禁止事项】
- 不要硬编码 API Key 或 Token
- 不要删除云端代理的宽松 JSON 解析逻辑（除非明确要求简化且已确认代理格式）
- 不要擅自把前端改成 React/Vue 等重型框架
- 不要修改 LICENSE、随意新增与用户无关的 markdown 文档
- 不要在没有要求时执行 git commit / push
```

---

## 项目速览

| 项 | 值 |
|----|-----|
| 产品名 | GenshinGo |
| Maven artifact | `deepseek-agent` |
| GroupId | `com.zyf` |
| 主类 | `com.zyf.deepseek.DeepseekAgentApplication` |
| 默认端口 | `8080`（`run.bat` 可在 8080–8099 自动选端口） |
| 前端入口 | `/` → `static/index.html` |
| 唯一业务 API | `POST /api/chat` |

---

## 数据流（直连模式）

```text
浏览器 app.js
  └─ fetch POST /api/chat  (ClientChatRequest JSON)
       └─ ChatController
            └─ DeepSeekChatService.chat()
                 ├─ validateRequest()          # 40 条 / 24000 字符限制
                 ├─ 构建 OpenAI 兼容请求体
                 │    model, stream=false, thinking, messages
                 └─ WebClient → {baseUrl}/chat/completions
                      └─ 解析 choices[0].message.content / reasoning_content
                           └─ ClientChatResponse
```

## 数据流（云端代理模式）

当 `CLOUD_CHAT_API_URL`（即 `deepseek.cloud.chat-url`）非空时：

```text
DeepSeekChatService.chatViaCloud()
  └─ POST {chatUrl}，body 为原始 ClientChatRequest（不经转换）
  └─ 可选 Authorization: Bearer {CLOUD_API_TOKEN}
  └─ parseCloudResponse() 宽松解析多种 JSON 结构
```

优先级：**云端代理 > 直连**；配置代理后不再要求本地 `DEEPSEEK_API_KEY`。

---

## 关键文件职责

| 文件 | 职责 |
|------|------|
| `DeepSeekChatService.java` | 校验、双路由、官方 API 组包、云端响应解析、超时与错误处理 |
| `ChatController.java` | `POST /api/chat` 入口 |
| `ApiExceptionHandler.java` | 密钥缺失 → 503；上游失败 → 502；参数错误 → 400 |
| `DeepSeekProperties.java` | `deepseek.*` 配置绑定 |
| `WebClientConfig.java` | 配置 WebClient baseUrl |
| `LaunchBrowserOnReady.java` | `deepseek.launch-browser=true` 时 Windows 打开浏览器 |
| `static/js/app.js` | 对话 UI、localStorage 持久化、调用 `/api/chat` |
| `application.yml` | 端口、模型名、超时、环境变量占位符 |
| `run.bat` | JAVA_HOME 解析、端口选择、`mvnw spring-boot:run` |

---

## 编码与风格约定

### Java

- 包名统一 `com.zyf.deepseek.*`
- 使用构造器注入，不用 `@Autowired` 字段注入
- 业务异常：`IllegalArgumentException`（客户端错误）、`IllegalStateException`（服务/上游错误）
- 错误信息使用中文，面向终端用户可读
- HTTP 客户端统一用已配置的 `WebClient`，不新增 `RestTemplate` / `HttpURLConnection`
- 新增配置遵循 `@ConfigurationProperties` + `application.yml` 模式

### 前端

- 原生 JS IIFE，无模块打包
- DOM 操作优先 `replaceChildren`、`classList`，与现有 `app.js` 一致
- 消息历史存 `localStorage`，键名 `genshingo-guide-messages-v1`，最多 40 条
- 发送时 `mode` 当前固定为 `"flash"`；若加模式切换需同步后端 `ClientChatRequest.ChatMode`
- `deepThinking` 绑定页面「深度思考」开关（`aria-pressed`）

### 配置与环境变量

| 变量 | 用途 |
|------|------|
| `DEEPSEEK_API_KEY` | 直连官方 API（代理未配置时必填） |
| `CLOUD_CHAT_API_URL` | 云端代理完整 URL |
| `CLOUD_API_TOKEN` | 调用代理时的 Bearer Token（可选） |

---

## 按场景的任务提示词

### 场景 A：修复聊天 API  bug

```
请排查 GenshinGo 的 POST /api/chat 问题。先读 DeepSeekChatService、ApiExceptionHandler、
ClientChatRequest/Response。确认是直连还是云端代理模式，检查 WebClient 超时、
请求体字段（thinking/reasoning_effort）与响应解析。改动保持最小，补充或更新相关测试。
```

### 场景 B：调整前端 UI

```
请修改 GenshinGo 前端（static/index.html、css/app.css、js/app.js）。
保持无构建工具、中文界面、现有 localStorage 键名与 /api/chat 调用方式。
不要破坏种子对话（如何下载原神）与安全提醒文案。移动端断点参考 820px。
```

### 场景 C：新增配置项

```
为 deepseek-agent 新增配置项：在 DeepSeekProperties 增加字段，
application.yml 添加默认值与环境变量占位符，在 DeepSeekChatService 或相关类中使用。
不要硬编码密钥。更新 README 配置表（若用户要求更新文档）。
```

### 场景 D：支持流式输出（stream）

```
当前实现 stream 固定为 false。若实现 SSE 流式响应，需同时改：
1) DeepSeekChatService 支持 stream=true 与分块读取
2) ChatController 返回 text/event-stream 或专用端点
3) app.js 用 fetch ReadableStream 或 EventSource 渲染
保持非流式路径可用，云端代理模式需单独评估。
```

### 场景 E：编写测试

```
为 com.zyf.deepseek 编写 JUnit 5 测试。使用 @SpringBootTest 或 @WebMvcTest。
Mock WebClient 或 DeepSeekChatService，覆盖校验逻辑、异常映射、云端 JSON 解析。
运行 mvnw test 确认通过。测试环境不要依赖真实 DEEPSEEK_API_KEY。
```

---

## 安全与合规清单

AI 助手在生成代码前必须自检：

- [ ] 未在源码、配置、测试、示例命令中写入真实 API Key
- [ ] `.gitignore` 未因改动而暴露敏感文件
- [ ] 错误响应未泄露完整上游堆栈或原始 Token
- [ ] 前端外链使用 `rel="noopener noreferrer"`
- [ ] 不实现「静默下载」「隐藏安装」「自动同意权限」等违规能力（项目种子对话已明确拒绝此类行为）

---

## 测试与本地运行

```bat
:: Windows 推荐
set DEEPSEEK_API_KEY=你的密钥
run.bat

:: 或指定端口
run.bat 8082

:: 跨平台
mvnw.cmd spring-boot:run
mvnw.cmd test
mvnw.cmd -DskipTests package
```

手动 API 测试：

```bash
curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d "{\"mode\":\"flash\",\"deepThinking\":false,\"messages\":[{\"role\":\"user\",\"content\":\"你好\"}]}"
```

---

## 与 README 的关系

- **README.md**：面向人类用户的使用说明、克隆、部署、FAQ
- **本文档（AI_PROMPT.md）**：面向 AI 助手的项目上下文、约束与任务模板

修改架构或 API 契约时，若用户要求同步文档，应同时更新 README 与本文件中的相关段落。

---

## 版本信息

- 文档对应代码版本：`1.0.0-SNAPSHOT`
- Spring Boot：`3.3.9`
- 默认模型：`deepseek-v4-flash` / `deepseek-v4-pro`
