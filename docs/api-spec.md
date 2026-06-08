# API 规格

Base URL（开发经 Vite 代理）：`http://localhost:5173/api` 或直连 `http://localhost:8080/api`

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
| 413 | 文件过大 |

---

## POST `/v1/audio/process`

创建处理任务。

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

`estimatedProcessingSeconds` = Σ(chunk.durationSec × 0.6)（仅 Python RTF）

**Errors**

| 状态 | 说明 |
|------|------|
| 400 | URI 无效、增益超范围 |
| 404 | URI 不存在 |

**Job 超时**：创建后 30s 内须连接 WebSocket，否则过期。

---

## WebSocket `/ws/v1/stream/{jobId}`

连接后立即启动分块流水线。

### Text 帧（JSON）

| type | 字段 |
|------|------|
| `session_meta` | `totalChunks`, `sourceDurationSec`, `estimatedMergedBytes` |
| `chunk_start` | `chunkIndex`, `chunkOffsetSec`, `chunkDurationSec` |
| `progress` | `chunkIndex`, `bytesSentInChunk`, `chunkBytes`, `bytesSentTotal` |
| `chunk_complete` | `chunkIndex`, `chunkBytes` |
| `complete` | `totalDurationSec`, `downloadUrl`, `mergedBytes`, `mergedReady` |
| `error` | `message`, 可选 `chunkIndex` |

### Binary 帧

当前块的 OGG-FLAC 数据（约 32KB/帧）。

---

## GET `/v1/audio/download/{jobId}`

返回合并后的完整 OGG-FLAC。

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

编排层健康检查。

```json
{
  "status": "ok",
  "pcmService": "ok"
}
```

`pcmService` 为 `ok` / `unreachable` / `disabled`。

## 生成 Protobuf 代码

修改 [`proto/pcm/v1/pcm.proto`](proto/pcm/v1/pcm.proto) 后：

```bash
./scripts/generate-proto.sh   # Python stubs
cd orchestrator && ./gradlew compileJava   # Java stubs
```

---

## Python PCM Service (gRPC)

Target: `localhost:8090`（plain text）

### `PcmProcessor.ProcessStream` (server streaming)

**Request** `ProcessRequest`：

| 字段 | 说明 |
|------|------|
| `pcm_s16le` | 24kHz mono s16le，时长 10–90s |
| `gain_db` | -24 ~ 24 |
| `duration_sec` | 10.0 ~ 90.0，与 body 字节数一致 |
| `input_sample_rate` | 24000 |
| `output_sample_rate` | 48000 |

**Response stream** `ProcessSegment`（每帧最多 10s @ 48kHz）：

| 字段 | 说明 |
|------|------|
| `pcm_s16le` | 48kHz mono s16le |
| `segment_index` | 0-based |
| `segment_duration_sec` | 10.0 或最后短段 |
| `is_last` | 是否最后一帧 |

RTF 0.6 在整次 RPC 结束时一次性补齐（各 segment 处理完即推送）。

### `PcmProcessor.Check`

返回 `{ status: "ok" }`。编排层 `/api/v1/health` 通过 gRPC 探活。

---

## WebSocket 补充帧

`segment_complete`（每个 10s OGG 段编码并推送后）：

```json
{
  "type": "segment_complete",
  "chunkIndex": 0,
  "segmentIndex": 0,
  "segmentDurationSec": 10.0,
  "segmentBytes": 12345,
  "isLastInChunk": true
}
```

前端在收到 `segment_complete` 时解码本段二进制并开始/追加播放。
