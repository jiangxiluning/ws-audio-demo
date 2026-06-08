# API 规格

Base URL（开发经 Vite 代理）：`http://localhost:5173/api` 或直连 `http://localhost:8080/api`

编排层配置见 [`application.yml`](../orchestrator/src/main/resources/application.yml) 中 `media.*`（分块 10–90s、WS 超时 30s、`pcm-grpc-target: localhost:8090` 等）。

---

## POST `/v1/audio/upload`

上传 FLAC 文件。

**Request**

- `Content-Type: multipart/form-data`
- Field: `file`（`audio/flac`）

**Response 200**

```json
{
  "uri": "audio://550e8400-e29b-41d4-a716-446655440000",
  "durationSeconds": 281.0,
  "originalFormat": "flac"
}
```

**Errors**

| 状态 | 说明 |
|------|------|
| 400 | 时长不在 10s–300s 或格式非法 |
| 413 | 文件过大（默认上限 100MB） |

---

## POST `/v1/audio/process`

创建处理任务（仅规划分块，**不**启动流水线；流水线在 WebSocket 连接后启动）。

**Request**

```json
{
  "uri": "audio://550e8400-e29b-41d4-a716-446655440000",
  "gainDb": 6.0
}
```

- `gainDb` 可选，默认 +6，范围 ±24

**Response 200**

```json
{
  "jobId": "job-uuid",
  "sourceDurationSeconds": 281.0,
  "totalChunks": 4,
  "chunks": [
    { "index": 0, "offsetSec": 0, "durationSec": 90 },
    { "index": 1, "offsetSec": 90, "durationSec": 90 },
    { "index": 2, "offsetSec": 180, "durationSec": 90 },
    { "index": 3, "offsetSec": 270, "durationSec": 11 }
  ],
  "estimatedProcessingSeconds": 168.6,
  "streamPath": "/ws/v1/stream/job-uuid"
}
```

- `estimatedProcessingSeconds` = Σ(chunk.durationSec × 0.6)，**仅** Python PCM RTF，不含 FFmpeg 编解码墙钟
- `chunks` 为编排层分块（每块 10–90s）；块内 PCM 处理另按 **10s segment** 通过 gRPC 流式返回（见下文）

**Errors**

| 状态 | 说明 |
|------|------|
| 400 | URI 无效、增益超范围 |
| 404 | URI 不存在 |

**Job 超时**：创建后 30s 内须连接 WebSocket，否则 `EXPIRED`。

---

## WebSocket `/ws/v1/stream/{jobId}`

连接成功后立即在后台线程启动 `ChunkPipelineScheduler` 流水线。同一 job 仅允许 **一条** WS 连接。

### 帧时序（单 chunk 内）

```
session_meta
chunk_start
  → (binary × N) → progress …
  → segment_complete          # 每个 10s PCM segment 编码完成后
  → … 重复至 chunk 内全部 segment …
chunk_complete
… 全部 chunk …
complete
```

### Text 帧（JSON）

| type | 字段 | 说明 |
|------|------|------|
| `session_meta` | `totalChunks`, `sourceDurationSec`, `estimatedMergedBytes` | 会话开始 |
| `chunk_start` | `chunkIndex`, `chunkOffsetSec`, `chunkDurationSec` | 编排层块开始 |
| `progress` | `chunkIndex`, `bytesSentInChunk`, `chunkBytes`, `bytesSentTotal` | 当前 chunk 已推送字节（含本 chunk 内所有 segment） |
| `segment_complete` | `chunkIndex`, `segmentIndex`, `segmentDurationSec`, `segmentBytes`, `isLastInChunk` | 一个 10s OGG 段推送完毕；**前端在此解码并追加播放** |
| `chunk_complete` | `chunkIndex`, `chunkBytes` | 块内 segment concat 完成，更新下载进度 Phase1 |
| `complete` | `totalDurationSec`, `downloadUrl`, `mergedBytes`, `mergedReady` | 全文件合并完成，可拉取下载 |
| `error` | `message` | 流水线失败或连接被拒绝（过期 / 重复连接） |

`segment_complete` 示例：

```json
{
  "type": "segment_complete",
  "chunkIndex": 0,
  "segmentIndex": 0,
  "segmentDurationSec": 10.0,
  "segmentBytes": 156274,
  "isLastInChunk": false
}
```

### Binary 帧

当前 **segment** 的 OGG-FLAC 数据，按 `media.stream-chunk-bytes`（默认 32KB）切片推送。二进制仅属于上一个 `chunk_start` 到下一个 `segment_complete` 之间的区间；收到 `segment_complete` 后前端应清空本段缓冲并开始解码。

---

## GET `/v1/audio/download/{jobId}`

返回合并后的完整 OGG-FLAC（`jobs/{jobId}/full.ogg`）。

**Response 200**

- `Content-Type: audio/ogg`
- `Content-Disposition: attachment; filename="{jobId}_processed.ogg"`

**Errors**

| 状态 | 说明 |
|------|------|
| 409 | 任务未完成或合并文件未就绪 |
| 404 | jobId 不存在 |

---

## GET `/v1/health`

编排层健康检查；`pcmService` 通过 gRPC `PcmProcessor.Check` 探活 pcm-service。

```json
{
  "status": "ok",
  "pcmService": "ok"
}
```

| `pcmService` | 含义 |
|--------------|------|
| `ok` | gRPC Check 返回 `status: ok` |
| `unreachable` | 无法连接或 Check 失败 |
| `disabled` | 未配置 `pcm-grpc-target` |

---

## PCM Service — gRPC (`localhost:8090`, plain text)

契约：[`proto/pcm/v1/pcm.proto`](../proto/pcm/v1/pcm.proto)

启动：`cd pcm-service && uv run python -m app.grpc_server`

环境变量：

| 变量 | 默认 | 说明 |
|------|------|------|
| `PCM_RTF` | `0.6` | RTF 系数，整次 RPC 结束时一次性 sleep 补齐 |
| `PCM_GRPC_PORT` | `8090` | 监听端口 |

消息大小上限：16MB（Java `ManagedChannel` 与 Python server 均已配置）。

### `PcmProcessor.ProcessStream`（Server Streaming）

编排层每个 chunk 调用 **一次** RPC，请求体为整块 24k PCM（10–90s）。

**Request** `ProcessRequest`：

| 字段 | 说明 |
|------|------|
| `pcm_s16le` | 24kHz mono s16le |
| `gain_db` | -24 ~ 24 |
| `duration_sec` | 10.0 ~ 90.0，须满足 `len(pcm) ≈ duration × 24000 × 2` |
| `input_sample_rate` | 24000 |
| `output_sample_rate` | 48000 |

**Response stream** `ProcessSegment`（按输入 PCM 每 **10s** 一帧，末段可不足 10s）：

| 字段 | 说明 |
|------|------|
| `pcm_s16le` | 48kHz mono s16le |
| `segment_index` | 0-based |
| `segment_duration_sec` | 本段时长（秒） |
| `is_last` | 是否为本 RPC 最后一帧 |

处理顺序：校验 → 按 10s 切分 → 增益 → 24k→48k 重采样 → **立即 yield** → 全部 segment 发送完毕后 `enforce_rtf(total_duration × PCM_RTF)`。

**Errors**：参数非法时 gRPC `INVALID_ARGUMENT`（如空 body、时长超界、字节数不匹配）。

### `PcmProcessor.Check`

返回 `{ status: "ok" }`。

---

## 生成 Protobuf 代码

修改 `proto/pcm/v1/pcm.proto` 后：

```bash
./scripts/generate-proto.sh          # Python → pcm-service/app/pcm/v1/
cd orchestrator && ./gradlew compileJava   # Java → build/generated/...
```
