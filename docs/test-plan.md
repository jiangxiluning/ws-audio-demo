# 测试计划

## 单元测试

| 模块 | 命令 | 覆盖 |
|------|------|------|
| pcm-service | `cd pcm-service && uv run pytest -q` | 增益、重采样、RTF、gRPC ProcessStream（8 项） |
| orchestrator | `cd orchestrator && ./gradlew test` | 分块规划、REST 上传/处理 |
| orchestrator WS | `cd orchestrator && ./gradlew integrationTest` | 全流水线 + 下载（FakeGrpcPcmClient） |

## 集成测试（需 ffmpeg）

```bash
./test-fixtures/generate.sh

# 服务已启动时
./scripts/smoke-test.sh
uv run --directory pcm-service python scripts/ws_integration_test.py -v

# 或一键（自动启服，建议 PCM_RTF=0.01）
PCM_RTF=0.01 ./scripts/integration-test.sh
```

`ws_integration_test.py` 校验 WS 帧序列，含 `segment_complete` 与 `chunk_complete` 计数。

## E2E（Playwright）

```bash
cd e2e && pnpm install && pnpm exec playwright install chromium
# 需 web + spring + pcm 已启动，或仅测 UI：
pnpm test
```

手动流程（`褪黑素.wav`，281s → 4 chunks）：

1. 上传 WAV → FLAC → `POST /upload` 返回 uri
2. `POST /process` 返回 jobId、4 块计划
3. 30s 内连接 `WS /ws/v1/stream/{jobId}`
4. 收到 `segment_complete`（每 10s PCM 一段，共 29 次）及 4 次 `chunk_complete`，播放进度 > 0
5. 收到 `complete`，`GET /download/{jobId}` 返回完整 OGG
6. 下载进度 100%，保存按钮可用

## Segment 数量参考

gRPC 按输入 PCM **10s 一帧**；编排 chunk 为 10–90s。

| chunk 时长 | segment 数 | 末段时长 |
|------------|------------|----------|
| 10s | 1 | 10s |
| 11s | 2 | 1s |
| 90s | 9 | 10s |
| 281s 全文件 | 29 | 末 chunk 11s → 2 segments（10s + 1s） |

## 边界用例

| 用例 | 输入 | 期望 |
|------|------|------|
| 过短 | 8s WAV | 前端/服务端 400 |
| 过长 | 301s WAV | 前端/服务端 400 |
| 95s rebalance | 合成 95s | 分块 [85, 10] → segments 9+1 |
| 增益越界 | gainDb=30 | 400 |
| WS 超时 | 30s 不连 WS | job EXPIRED |
| 重复 WS | 同一 job 双连接 | 第二连接 error |
| gRPC 参数非法 | duration 与 PCM 字节不匹配 | INVALID_ARGUMENT |

## 性能参考（281s 主素材）

| 指标 | 值 |
|------|-----|
| 编排分块 | [90, 90, 90, 11] |
| gRPC segment 总数 | 29 |
| Python RTF 预估（墙钟下限） | ~168.6s |
| 首次可播 | 首个 `segment_complete`（~10s 段 + encode/WS 延迟） |

## 脚本一览

| 脚本 | 用途 |
|------|------|
| `scripts/dev.sh` | 并行启动 pcm / orchestrator / web |
| `scripts/build-all.sh` | 前端 build + JAR |
| `scripts/generate-proto.sh` | 生成 Python protobuf |
| `scripts/integration-test.sh` | 自动启服 + 10s FLAC 全链路 |
| `scripts/smoke-test.sh` | 假定服务已启动的 REST/WS 冒烟 |

## 已知限制

- Java 25 下 Mockito `@WebMvcTest` 不可用，REST 层以手工/集成测试为主
- 端到端需本机安装 ffmpeg/ffprobe
- Playwright 默认不启动真实后端，完整 UI 流程需手动启服
