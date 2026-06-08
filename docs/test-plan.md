# 测试计划

## 单元测试

| 模块 | 命令 | 覆盖 |
|------|------|------|
| pcm-service | `cd pcm-service && uv run pytest -q` | 增益、重采样、RTF、gRPC ProcessStream |
| orchestrator | `cd orchestrator && ./gradlew test` | 分块规划、REST 上传/处理 |
| orchestrator WS | `cd orchestrator && ./gradlew integrationTest` | 全流水线 + 下载 |

## 集成测试（需 ffmpeg）

```bash
./test-fixtures/generate.sh

# 服务已启动时
./scripts/smoke-test.sh
uv run --directory pcm-service python scripts/ws_integration_test.py -v

# 或一键（自动启服，建议 PCM_RTF=0.01）
PCM_RTF=0.01 ./scripts/integration-test.sh
```

## E2E（Playwright）

```bash
cd e2e && pnpm install && pnpm exec playwright install chromium
# 需 web + spring + pcm 已启动，或仅测 UI：
pnpm test
```

手动流程（`褪黑素.wav`，281s → 4 块）：

1. 上传 WAV → FLAC → `POST /upload` 返回 uri
2. `POST /process` 返回 jobId、4 块计划
3. 30s 内连接 `WS /ws/v1/stream/{jobId}`
4. 收到 `segment_complete`（每 10s PCM 一段）及 4 次 `chunk_complete`，播放进度 > 0
5. 收到 `complete`，`GET /download/{jobId}` 返回完整 OGG
6. 下载进度 100%，保存按钮可用

## 边界用例

| 用例 | 输入 | 期望 |
|------|------|------|
| 过短 | 8s WAV | 前端/服务端 400 |
| 过长 | 301s WAV | 前端/服务端 400 |
| 95s rebalance | 合成 95s | 分块 [85, 10] |
| 增益越界 | gainDb=30 | 400 |
| WS 超时 | 30s 不连 WS | job EXPIRED |
| 重复 WS | 同一 job 双连接 | 第二连接 error |

## 性能参考（281s 主素材）

| 指标 | 值 |
|------|-----|
| 分块 | [90, 90, 90, 11] |
| Python RTF 预估 | ~168.6s |
| 首块可播（约） | 首个 `segment_complete` 后（~10s 段） |

## 已知限制

- Java 25 下 Mockito `@WebMvcTest` 不可用，REST 层以手工/集成测试为主
- 端到端需本机安装 ffmpeg/ffprobe
