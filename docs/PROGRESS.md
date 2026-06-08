# 项目状态

仓库：https://github.com/jiangxiluning/ws-audio-demo

三层音频处理演示：**Vue3 前端** · **Spring Boot 编排** · **Python PCM gRPC 服务**。

## 模块完成度

| 模块 | 状态 | 说明 |
|------|------|------|
| **web** | ✅ | WAV 校验、ffmpeg.wasm 转 FLAC、WS 收流、segment 级边播、双进度条 |
| **orchestrator** | ✅ | REST/WS、90s 分块、FFmpeg 编解码、GrpcPcmClient、合并下载、生产 static |
| **pcm-service** | ✅ | gRPC Server Streaming、10s segment、整次 RTF 0.6 |
| **proto / scripts / docs** | ✅ | 共享契约、dev/build/integration 脚本 |

## 自动化测试

| 命令 | 说明 |
|------|------|
| `cd pcm-service && uv run pytest -q` | 8 项（流式分段、RTF、gRPC servicer） |
| `cd orchestrator && ./gradlew test` | 分块规划、REST 集成 |
| `cd orchestrator && ./gradlew integrationTest` | FakeGrpcPcmClient + 真 FFmpeg + WS + 下载 |
| `PCM_RTF=0.01 ./scripts/integration-test.sh` | 自动启服 + 10s FLAC 全链路 |
| `cd e2e && pnpm test` | Playwright UI 冒烟（不含真实后端） |

## 架构要点

| 层级 | 通信 / 粒度 |
|------|-------------|
| 浏览器 ↔ 编排 | REST + WebSocket（OGG 二进制 + JSON 控制帧） |
| 编排 ↔ PCM | **gRPC** `ProcessStream`（每 chunk 一次 RPC，响应按 **10s** 流式返回） |
| 编排分块 | 10–300s 素材 → 每块 ≤90s、末块 ≥10s（`AudioChunkPlanner`） |
| PCM segment | 每块 24k PCM → 10s 一帧 48k PCM（末段可 <10s） |
| WS 推送 | 每 segment 编码为 OGG 后立即推送；`segment_complete` 触发前端解码播放 |
| RTF | `PCM_RTF=0.6`，整次 gRPC RPC 结束时 sleep 一次（流式 yield 不等待） |

## 可选后续

- 浏览器手动 E2E：`褪黑素.wav`（281s，RTF 0.6 下 Python 阶段约 169s）
- Playwright 带真实后端的完整 UI 流程

## 相关文档

- [架构设计](./architecture.md)
- [API 规格](./api-spec.md)
- [测试计划](./test-plan.md)
- [README](../README.md)
