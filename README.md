# Xiaozhi ESP32 Server Custom

面向 ESP32 语音终端的端到端 AI 语音助手平台。这个仓库把嵌入式固件、实时语音服务、管理后端和 Web/移动端控制台放在同一套工程中，适合自建语音交互设备、IoT 控制、Agent/MCP 集成和 OTA 管理。

## 仓库包含什么

- **ESP32 固件**：基于 ESP-IDF 的 C/C++ 固件，负责 Wi-Fi/4G、唤醒词、录音、Opus 音频、播放、屏幕、摄像头、电源管理和设备端 MCP 工具。
- **实时 AI 服务 `xiaozhi-server`**：Python 异步服务，通过 WebSocket 与设备保持实时双向通信，串联 VAD、ASR、意图识别、LLM、记忆、TTS 和对话状态。
- **管理后端 `manager-api`**：Java Spring Boot 管理 API，负责用户、Agent、设备、模型、声纹、聊天记录、知识库、插件、MCP 接入点和 OTA 等持久化业务。
- **Web 控制台 `manager-web`**：Vue 单页管理端，用于配置 AI Provider、管理设备和 Agent、维护知识库、查看聊天历史及管理系统配置。
- **移动管理端 `manager-mobile`**：基于 uni-app/Vue 3 的跨端管理客户端。

## 已实现能力

### 设备与协议

- 支持 ESP32-C3、ESP32-S3、ESP32-P4 等平台和多种开源开发板。
- 支持 WebSocket，以及 MQTT + UDP 两种设备通信方式。
- 使用 Opus 音频编解码，支持流式语音交互和实时打断。
- 支持设备绑定、设备状态上报、OTA 固件升级和设备端 MCP 工具。
- MCP 工具可暴露设备状态、音量、屏幕亮度、主题、拍照、重启和固件升级等能力。

### AI 与扩展

- ASR、LLM、TTS、VAD、意图和记忆都采用 Provider 抽象，便于按配置切换服务。
- ASR 侧包含本地 FunASR、SenseVoice/Sherpa、云端流式服务，以及本仓库加入的 Gladia 流式识别实现。
- LLM 侧支持 OpenAI 兼容接口、Ollama、Gemini、Dify、FastGPT、Home Assistant 等接入方式。
- TTS 侧提供多种云端和本地实现，并统一为流式音频输出。
- 插件系统支持天气、新闻、音乐、Home Assistant、RAGFlow 等服务端函数。
- 支持云端 MCP、设备 MCP、MCP Endpoint 和知识库检索，让语音助手可以调用外部工具。
- 支持声纹识别、对话记忆、聊天历史、Agent 模板和训练数据管理。

## 系统架构

```text
ESP32 设备
   │ WebSocket / MQTT + UDP
   ▼
xiaozhi-server (Python asyncio)
   ├─ VAD / ASR / Intent / LLM / Memory / TTS
   ├─ 插件、设备 MCP、云端 MCP、知识库工具
   └─ OTA / 视觉分析等 HTTP 接口
   │ HTTP 配置与业务 API
   ▼
manager-api (Java Spring Boot)
   ├─ 用户、Agent、设备、模型和权限
   ├─ 声纹、聊天记录、知识库和插件
   └─ OTA、配置和系统管理
   │
   ├─ manager-web (Vue)
   └─ manager-mobile (uni-app + Vue 3)
```

## 目录结构

```text
.
├─ xiaozhi-esp32-main/
│  └─ xiaozhi-esp32-main/         # ESP-IDF 固件、板级实现和设备端 MCP
└─ xiaozhi-esp32-server-main/
   ├─ main/xiaozhi-server/        # Python 实时 AI 服务
   ├─ main/manager-api/           # Java Spring Boot 管理 API
   ├─ main/manager-web/           # Vue Web 控制台
   ├─ main/manager-mobile/        # uni-app 移动端
   └─ docs/                       # 部署、协议和 Provider 集成文档
```

## 启动方式

这是一个多组件工程，建议先按组件目录下的文档准备依赖。最常用的入口如下：

### ESP32 固件

需要 ESP-IDF 5.4 或更高版本，并按目标开发板选择 board 配置：

```bash
cd xiaozhi-esp32-main/xiaozhi-esp32-main
idf.py set-target esp32s3
idf.py build
idf.py flash monitor
```

固件默认通过设置中的 WebSocket/MQTT 参数连接后端；通信协议和板级适配见 `docs/` 与 `main/boards/`。

### Python 语音服务

```bash
cd xiaozhi-esp32-server-main/main/xiaozhi-server
python -m venv .venv

# Windows
.venv\Scripts\activate

# Linux/macOS
source .venv/bin/activate

pip install -r requirements.txt
python app.py
```

服务需要根据 `config.yaml`/环境变量配置 ASR、LLM、TTS、VAD、数据库管理 API 和设备认证信息。部分本地模型和音频处理流程还需要 FFmpeg。

### Java 管理 API

```bash
cd xiaozhi-esp32-server-main/main/manager-api
mvn spring-boot:run
```

需要本机安装 Maven。数据库、Redis、JWT 和第三方 AI 服务密钥请使用部署配置注入，不要提交到仓库。

### Web 与移动端

```bash
cd xiaozhi-esp32-server-main/main/manager-web
npm install
npm run serve
```

移动端位于 `main/manager-mobile`，按其 `package.json` 和 uni-app 工具链运行。

## 常见端口

不同部署方式可能会覆盖端口，以下是项目文档中的常见默认值：

| 组件 | 端口 | 用途 |
| --- | ---: | --- |
| `xiaozhi-server` | 8000 | 设备 WebSocket 服务 |
| `manager-web` | 8001 | Web 管理控制台 |
| `manager-api` | 8002 | 管理 REST API |
| `xiaozhi-server` HTTP | 8003 | OTA 和视觉分析等 HTTP 接口 |

## 相关文档

- 服务端部署：[`xiaozhi-esp32-server-main/README.md`](xiaozhi-esp32-server-main/README.md)
- 服务端技术说明：[`xiaozhi-esp32-server-main/main/README.md`](xiaozhi-esp32-server-main/main/README.md)
- 固件中文说明：[`xiaozhi-esp32-main/xiaozhi-esp32-main/README_zh.md`](xiaozhi-esp32-main/xiaozhi-esp32-main/README_zh.md)
- WebSocket 协议：[`xiaozhi-esp32-main/xiaozhi-esp32-main/docs/websocket.md`](xiaozhi-esp32-main/xiaozhi-esp32-main/docs/websocket.md)
- Docker 部署：[`xiaozhi-esp32-server-main/docs/docker-build.md`](xiaozhi-esp32-server-main/docs/docker-build.md)

## 注意事项

- 项目需要配合 ESP32 硬件、数据库、Redis 以及至少一组可用的 AI Provider 才能完整运行。
- 第三方 ASR、LLM、TTS、声纹和知识库服务各自有账号、费用和隐私策略，请自行核对服务商条款。
- 当前工程适合学习、开发和自建部署；对公网部署前应补齐鉴权、TLS、密钥管理、日志和访问控制，并先完成安全评估。
