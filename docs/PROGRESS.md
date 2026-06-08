# 实现进度

## 已完成

| 模块 | 状态 | 说明 |
|------|------|------|
| **pcm-service** | ✅ | gRPC Server Streaming、10s 分段、整次 RTF、pytest |
| **orchestrator** | ✅ | GrpcPcmClient、增量 encode/WS、segment_complete |
| **web** | ✅ | segment_complete 增量解码播放 |
| **proto** | ✅ | `proto/pcm/v1/pcm.proto` + 代码生成脚本 |
| **docs/scripts** | ✅ | api-spec、dev/integration 脚本 |

## 测试

| 命令 | 状态 |
|------|------|
| `uv run pytest -q` | ✅ |
| `./gradlew test` | ✅ |
| `./gradlew integrationTest` | ✅ Fake gRPC PCM + 真 FFmpeg |
| `PCM_RTF=0.01 ./scripts/integration-test.sh` | ✅ gRPC 全链路 |

## 架构要点

- PCM 通信：**gRPC Server Streaming**（`ProcessStream`），非 HTTP
- 输入 10–90s @ 24k，输出按 **10s** 一帧 @ 48k
- 编排层每帧即 FFmpeg 编码 + WS 推送
- RTF 0.6：**整次 RPC 一次**（流式仅用于提前推送）

## 待办 / 可选

- [ ] 浏览器手动 E2E：`褪黑素.wav` 完整 281s 流程（需 `./scripts/dev.sh` + 约 3min 处理）
- [ ] Playwright 扩展：带真实后端的完整 UI 流程（当前 e2e 仅 UI 冒烟）
- [x] `build-all.sh` 生产 JAR（含 static SPA）
