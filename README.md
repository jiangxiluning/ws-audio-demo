# WS Audio Demo

三层音频处理演示：Vue3 前端 · Spring Boot 编排 · Python PCM 服务。

## 架构

| 服务 | 端口 | 职责 |
|------|------|------|
| **web** (Vite) | 5173 | WAV→FLAC (ffmpeg.wasm)、REST/WS 客户端、边播边下 |
| **orchestrator** (Spring Boot) | 8080 | 上传、分块、FFmpeg 编解码、WS 流、合并下载 |
| **pcm-service** (gRPC) | 8090 | 24k→48k PCM 流式处理、增益、RTF 0.6 |

## 前置依赖

- **Java 17+**（sdkman）
- **Gradle 9.5.1**（sdkman；`./gradlew` 会优先调用 sdkman 的 `gradle`，不重复下载 wrapper）
- **uv**（Python）
- **pnpm**
- **ffmpeg / ffprobe**（Spring 编排层必需）

```bash
# sdkman
sdk install java 17.0.13-tem   # 或已安装版本
sdk install gradle 9.5.1

# Ubuntu/Debian
sudo apt install ffmpeg

# 验证
java -version && gradle -version && ffmpeg -version
```

## 快速启动（开发）

```bash
# 终端 1 — Python PCM (gRPC)
cd pcm-service && uv sync && uv run python -m app.grpc_server

# 终端 2 — Spring
cd orchestrator && ./gradlew bootRun

# 终端 3 — 前端
cd web && pnpm install && pnpm dev
```

前端 `web/.npmrc` 已配置淘宝镜像（`registry.npmmirror.com`）。

或一键：`chmod +x scripts/dev.sh && ./scripts/dev.sh`

浏览器打开 http://localhost:5173 ，上传 WAV（10s~300s），默认测试文件见项目根目录 `褪黑素.wav`（281s，4 块）。

## 生产构建

```bash
chmod +x scripts/build-all.sh && ./scripts/build-all.sh
java -jar orchestrator/build/libs/orchestrator-1.0.0.jar
```

## API 摘要

- `POST /api/v1/audio/upload` — multipart FLAC
- `POST /api/v1/audio/process` — `{ uri, gainDb }`
- `WS /ws/v1/stream/{jobId}` — 流式 OGG 段（每 10s PCM 一段）+ `segment_complete` / 进度
- `GET /api/v1/audio/download/{jobId}` — 合并完整 OGG（完成后）

## 测试

```bash
# Python
cd pcm-service && uv run pytest -q

# Spring（默认不含 @Tag(integration) 的 WS 全链路测试）
cd orchestrator && ./gradlew test

# Spring WS 全链路（Fake PCM + 真 FFmpeg）
cd orchestrator && ./gradlew integrationTest

# 全栈（需先停掉占用 8080/8090 的进程）
PCM_RTF=0.01 ./scripts/integration-test.sh

# 服务启动后冒烟
./scripts/smoke-test.sh

# E2E UI（Playwright）
cd e2e && pnpm install && pnpm exec playwright install chromium && pnpm test
```

## 文档

- [架构设计（类图 / 时序图 / 部署图）](docs/architecture.md)
- [API 规格](docs/api-spec.md)
- [测试计划](docs/test-plan.md)
- [项目状态](docs/PROGRESS.md)

## 目录

```
pcm-service/     Python Service A
orchestrator/    Spring Boot 编排
web/             Vue 3 前端
test-fixtures/   测试音频
scripts/         dev.sh / build-all.sh
```
