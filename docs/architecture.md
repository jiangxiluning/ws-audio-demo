# 架构设计 — WS Audio Demo

本文档描述三层音频处理系统的逻辑架构、模块设计、核心流程与部署方式。API 细节见 [api-spec.md](./api-spec.md)，测试见 [test-plan.md](./test-plan.md)。

---

## 1. 系统概述

用户上传 WAV（10s–300s），浏览器侧转 FLAC 并上传；Spring 编排层按块（≤90s）解码、通过 **gRPC Server Streaming** 调用 Python 做 PCM 处理（每 **10s** 一帧返回）、逐 segment 编码为 OGG-FLAC 并通过 WebSocket 推送；全部块完成后合并为完整 OGG 供下载。浏览器在收到 `segment_complete` 时解码并边播，下载进度 100% 后可任意 seek 与保存。

---

## 2. 系统上下文图（逻辑视图）

```mermaid
flowchart TB
    User([用户])

    subgraph Browser["浏览器 · Vue3 + ffmpeg.wasm :5173"]
        UI[UploadPanel / ProcessPanel / PlayerPanel]
        Wasm[useFlacEncoder]
        Player[useStreamPlayer]
        UI --> Wasm
        UI --> Player
    end

    subgraph Orchestrator["Spring Boot 编排层 :8080"]
        REST[REST API]
        WS[WebSocket Handler]
        Pipeline[ChunkPipelineScheduler]
        Planner[AudioChunkPlanner]
        Codec[FfmpegCodecService]
        Store[(临时存储 uploads / jobs)]
        REST --> Planner
        WS --> Pipeline
        Pipeline --> Planner
        Pipeline --> Codec
        Pipeline --> Store
    end

    subgraph PCM["Python Service A :8090 gRPC"]
        API[PcmProcessor.ProcessStream]
        Proc[processor: gain → resample → 10s stream]
        API --> Proc
    end

    FFmpeg[(系统 FFmpeg / FFprobe)]

    User --> UI
    UI -->|REST /api| REST
    UI -->|WS /ws| WS
    Pipeline -->|gRPC stream| API
    Codec --> FFmpeg
```

**边界原则**

| 层级 | 负责 | 不负责 |
|------|------|--------|
| 浏览器 | WAV 校验、WAV→FLAC、WS 收流、边播、双进度条、seek | 分块、PCM 算法、OGG 合并 |
| Spring | 上传、分块、FFmpeg 编解码、流水线、WS、合并、静态托管 | PCM 增益/重采样/RTF |
| Python | 24k s16 mono → 48k s16 mono、增益 clip、RTF 0.6 | 分块、文件 URI、OGG |
| FFmpeg | FLAC 解码、立体声→mono、OGG-FLAC 编码、concat | 业务状态 |

---

## 3. 部署图

### 3.1 开发环境

```mermaid
flowchart LR
    subgraph DevMachine["开发机"]
        Browser[Chrome / Edge]
        Vite["Vite Dev :5173"]
        Spring["Spring Boot :8080"]
        Python["gRPC server :8090"]
        FF[ffmpeg / ffprobe]
    end

    Browser -->|HTTP /api /ws proxy| Vite
    Vite -->|proxy| Spring
    Spring --> Python
    Spring --> FF
    Browser -->|ffmpeg.wasm CDN| NPMMirror[npmmirror.com]
```

### 3.2 生产环境

```mermaid
flowchart LR
    Browser[浏览器]
    JAR["orchestrator.jar :8080\n+ classpath:/static/"]
    Python["pcm-service :8090"]
    FF[ffmpeg]
    Tmp[("/tmp/ws-audio-demo")]

    Browser --> JAR
    JAR --> Python
    JAR --> FF
    JAR --> Tmp
```

| 模式 | 前端 | 后端入口 |
|------|------|----------|
| 开发 | `pnpm dev` → `:5173` | Vite 代理 `/api`、`/ws` → `:8080` |
| 生产 | `pnpm build` → `orchestrator/.../static/` | 单 JAR `:8080` 托管 SPA + API |

构建命令：`./scripts/build-all.sh`

---

## 4. 模块与包结构

```
ws-audio-demo/
├── web/                    # Vue 3 前端
│   └── src/
│       ├── components/     # UploadPanel, ProcessPanel, PlayerPanel
│       └── composables/    # useAudioValidator, useFlacEncoder, useStreamPlayer
├── orchestrator/           # Spring Boot
│   └── com.demo.orchestrator/
│       ├── api/              # REST Controllers + DTO
│       ├── websocket/        # AudioStreamWebSocketHandler
│       ├── service/          # 业务服务
│       ├── client/           # GrpcPcmClient
│       ├── domain/           # ProcessJob, AudioChunk, StoredAudio
│       └── config/           # AppConfig, MediaProperties, SpaConfig
├── pcm-service/            # Python gRPC (app/grpc_server, servicer, processor)
├── proto/pcm/v1/pcm.proto
└── test-fixtures/          # WAV / FLAC 测试素材
```

---

## 5. 类图

### 5.1 Spring 编排层

```mermaid
classDiagram
    direction TB

    class AudioController {
        +upload(file) UploadResponse
        +process(req) ProcessResponse
    }
    class AudioDownloadController {
        +download(jobId) StreamingResponseBody
    }
    class HealthController {
        +health() Map
    }
    class AudioStreamWebSocketHandler {
        +afterConnectionEstablished(session)
    }

    class ProcessJobService {
        +createJob(uri, gainDb) ProcessJob
        +requireJob(jobId) ProcessJob
    }
    class AudioChunkPlanner {
        +plan(totalSec) List~AudioChunk~
    }
    class ChunkPipelineScheduler {
        +runPipeline(job, session)
    }
    class FfmpegCodecService {
        +decodeChunkToPcm24k(path, offset, dur) byte[]
        +encodePcm48kToOggFlac(pcm) byte[]
        +concatOggFiles(chunks, output)
    }
    class GrpcPcmClient {
        +processPcmStream(pcm24k, gainDb, durationSec, onSegment)
        +checkHealth() boolean
    }
    class AudioStorageService {
        +save(file) StoredAudio
        +resolve(uri) StoredAudio
        +jobDir(jobId) Path
    }
    class AudioValidationService {
        +probeDurationSeconds(path) double
        +validateDuration(sec)
    }
    class JobStore {
        +put(job)
        +get(jobId) Optional
        +markWsConnected(jobId) boolean
        +expireIfAwaiting(jobId)
    }

    class ProcessJob {
        +jobId uri sourcePath gainDb chunks
        +state mergedPath mergedBytes
    }
    class AudioChunk {
        +index offsetSec durationSec
    }
    class StoredAudio {
        +uri durationSeconds path
    }

    AudioController --> ProcessJobService
    AudioController --> AudioStorageService
    AudioController --> AudioValidationService
    AudioDownloadController --> ProcessJobService
    AudioStreamWebSocketHandler --> ProcessJobService
    AudioStreamWebSocketHandler --> ChunkPipelineScheduler
    AudioStreamWebSocketHandler --> JobStore
    ProcessJobService --> AudioChunkPlanner
    ProcessJobService --> AudioStorageService
    ProcessJobService --> AudioValidationService
    ProcessJobService --> JobStore
    ChunkPipelineScheduler --> FfmpegCodecService
    ChunkPipelineScheduler --> GrpcPcmClient
    ChunkPipelineScheduler --> AudioStorageService
    ChunkPipelineScheduler --> ProcessJobService
    ProcessJobService ..> ProcessJob
    AudioChunkPlanner ..> AudioChunk
    AudioStorageService ..> StoredAudio
```

### 5.2 Python Service A

```mermaid
classDiagram
    direction TB

    class PcmProcessorServicer {
        +ProcessStream(request) stream ProcessSegment
        +Check() HealthCheckResponse
    }
    class processor {
        +iter_process_pcm_stream(...) Iterator
    }
    class gain {
        +apply_gain_s16le(pcm, gain_db) bytes
    }
    class resample {
        +resample_24k_to_48k_s16le(pcm, in, out) bytes
    }
    class rtf {
        +enforce_rtf(duration_sec, elapsed, rtf)
    }

    PcmProcessorServicer --> processor
    processor --> gain
    processor --> resample
    processor --> rtf
```

### 5.3 前端模块

```mermaid
classDiagram
    direction TB

    class AppVue {
        upload state
        job state
    }
    class UploadPanel {
        onFileChange()
        upload()
    }
    class ProcessPanel {
        startProcess()
    }
    class PlayerPanel {
        connect WS
        play / pause / seek / save
    }

    class useAudioValidator {
        +probeWavDuration(file)
        +validateDuration(sec)
    }
    class useFlacEncoder {
        +wavToFlac(wavFile) Blob
        +oggToAudioBuffer(bytes, ctx) AudioBuffer
    }
    class useStreamPlayer {
        +connect(streamPath, meta)
        +play() pause() seek(sec) saveFile()
    }

    AppVue --> UploadPanel
    AppVue --> ProcessPanel
    AppVue --> PlayerPanel
    UploadPanel --> useAudioValidator
    UploadPanel --> useFlacEncoder
    PlayerPanel --> useStreamPlayer
    useStreamPlayer --> useFlacEncoder
```

---

## 6. 领域模型与 Job 状态机

```mermaid
stateDiagram-v2
    [*] --> AWAITING_WS: POST /process
    AWAITING_WS --> RUNNING: WS 连接成功
    AWAITING_WS --> EXPIRED: 30s 无 WS
    RUNNING --> COMPLETED: 全部块处理 + concat
    RUNNING --> FAILED: 异常
    COMPLETED --> [*]
    FAILED --> [*]
    EXPIRED --> [*]
```

**ProcessJob 关键字段**

| 字段 | 说明 |
|------|------|
| `jobId` | UUID |
| `uri` | `audio://{uuid}` |
| `chunks` | 分块计划（index, offsetSec, durationSec） |
| `gainDb` | 默认 +6，范围 ±24 |
| `mergedPath` | concat 后的 `full.ogg` |
| `state` | AWAITING_WS → RUNNING → COMPLETED / FAILED / EXPIRED |

---

## 7. 时序图

### 7.1 上传流程

```mermaid
sequenceDiagram
    actor U as 用户
    participant B as 浏览器
    participant W as ffmpeg.wasm
    participant S as Spring Boot

    U->>B: 选择 WAV
    B->>B: probeWavDuration (10~300s)
    B->>W: WAV → FLAC
    W-->>B: FLAC Blob
    B->>S: POST /api/v1/audio/upload
    S->>S: 落盘 uploads/{uuid}.flac
    S->>S: ffprobe 校验时长
    S-->>B: { uri, durationSeconds }
    B-->>U: 显示 URI
```

### 7.2 处理与 WS 流水线

```mermaid
sequenceDiagram
    participant B as 浏览器
    participant S as Spring Boot
    participant F as FFmpeg
    participant P as Python :8090

    B->>S: POST /api/v1/audio/process { uri, gainDb }
    S->>S: ChunkPlanner.plan(duration)
    S->>S: JobStore.put(job)
    S-->>B: { jobId, chunks[], streamPath }

    B->>S: WS /ws/v1/stream/{jobId}
    S-->>B: session_meta

    loop 每块 chunkIndex
        S-->>B: chunk_start
        S->>F: 截取 + decode → 24k mono s16 PCM
        S->>P: gRPC ProcessStream (10–90s PCM)
        loop 每 10s segment
            Note over P: gain → 24k→48k → yield segment
            P-->>S: ProcessSegment 48k PCM
            S->>F: encode → OGG 段
            loop 32KB 网络帧
                S-->>B: binary
                S-->>B: progress
            end
            S-->>B: segment_complete
        end
        Note over P: enforce_rtf 整次一次
        S->>F: concat segment oggs → chunk_N.ogg
        S-->>B: chunk_complete
    end

    S->>F: concat → full.ogg
    S-->>B: complete { downloadUrl, mergedBytes }
```

### 7.3 边播边下与完整下载

```mermaid
sequenceDiagram
    participant B as 浏览器
    participant S as Spring Boot

    Note over B: 阶段 1 · WS 流（下载 0~85%）
    loop 每个 segment
        S-->>B: OGG 二进制
        S-->>B: segment_complete
        B->>B: ffmpeg.wasm 解码 → AudioBuffer
        B->>B: Web Audio 追加播放
        B->>B: downloadProgress += chunk/total × 85%（chunk_complete 时更新）
    end

    Note over B: 阶段 2 · 完整文件（85~100%）
    S-->>B: complete + downloadUrl
    B->>S: GET /api/v1/audio/download/{jobId}
    S-->>B: 完整 OGG 流
    B->>B: fullAudioBlob 就绪
    B->>B: downloadProgress = 100%
    Note over B: 任意 seek · 保存 OGG
```

---

## 8. 流程图

### 8.1 分块规划（AudioChunkPlanner）

```mermaid
flowchart TD
    A[输入 totalSec] --> B{10s ≤ totalSec ≤ 300s?}
    B -->|否| X[抛出 InvalidDurationException]
    B -->|是| C[贪心按 90s 切分]
    C --> D{末块 < 10s 且块数 > 1?}
    D -->|是| E[从前块借时长，末块 = 10s]
    D -->|否| F[保持切分结果]
    E --> G[生成 AudioChunk 列表 offset 连续]
    F --> G
    G --> H[返回 chunks]

    style X fill:#fee
```

**示例**

| 总时长 | 初始切分 | rebalance 后 |
|--------|----------|--------------|
| 95s | [90, 5] | **[85, 10]** |
| 100s | [90, 10] | [90, 10] |
| 185s | [90, 90, 5] | **[90, 85, 10]** |
| 281s | [90, 90, 90, 11] | [90, 90, 90, 11] |

### 8.2 单块处理流水线

编排层 **chunk**（10–90s）与 gRPC **segment**（10s）为两层粒度：每 chunk 一次 `ProcessStream` RPC，响应按 10s 流式返回；Spring 每收到一帧即 encode → WS 推送 → 落盘 `chunk_N_seg_M.ogg`，全部 segment 完成后 concat 为 `chunk_N.ogg`。

```mermaid
flowchart TD
    A[FFmpeg decode chunk → 24k mono s16] --> B[gRPC ProcessStream 一次 RPC]
    B --> C{Python 每 10s yield}
    C --> D[gain + 24k→48k]
    D --> E[Spring: encode OGG 段]
    E --> F[WS binary + segment_complete]
    E --> G[落盘 chunk_N_seg_M.ogg]
    C -->|全部 segment 发送完| H[Python: enforce_rtf 整次一次]
    G --> I[FFmpeg concat segments]
    I --> J[chunk_N.ogg + chunk_complete]
```

### 8.3 浏览器播放与 seek 决策

```mermaid
flowchart TD
    A[用户 seek 到 T 秒] --> B{downloadProgress = 100%?}
    B -->|是| C[使用 fullAudioBlob 任意 seek]
    B -->|否| D{T ≤ bufferedUntilSec?}
    D -->|是| E[在已缓冲块内 seek + 重调度 AudioBuffer]
    D -->|否| F[提示：尚未下载到该位置\n播放头不动]
```

---

## 9. 数据流与存储

```mermaid
flowchart LR
    subgraph Client
        WAV[WAV 文件]
        FLAC_B[FLAC Blob]
        OGG_chunks[OGG 块缓存]
        OGG_full[full.ogg Blob]
    end

    subgraph ServerTmp["${java.io.tmpdir}/ws-audio-demo"]
        UP["uploads/{uuid}.flac"]
        JOB["jobs/{jobId}/"]
        SEG["chunk_N_seg_M.ogg（中间段）"]
        CH["chunk_N.ogg（块合并）"]
        FULL["full.ogg"]
    end

    WAV -->|ffmpeg.wasm| FLAC_B
    FLAC_B -->|POST upload| UP
    UP -->|流水线| SEG
    SEG -->|concat per chunk| CH
    CH -->|concat all chunks| FULL
    SEG -->|WS 逐 segment| OGG_chunks
    FULL -->|GET download| OGG_full
```

**URI 约定**：`audio://{uuid}` — 逻辑标识，不暴露服务器路径。

---

## 10. WebSocket 协议摘要

| 帧 type | 方向 | 说明 |
|---------|------|------|
| `session_meta` | S→B | totalChunks, sourceDurationSec |
| `chunk_start` | S→B | chunkIndex, offsetSec, durationSec |
| *(binary)* | S→B | 当前 **segment** 的 OGG-FLAC（32KB 切片） |
| `progress` | S→B | bytesSentInChunk, chunkBytes |
| `segment_complete` | S→B | segmentIndex, segmentDurationSec, isLastInChunk；**触发前端解码播放** |
| `chunk_complete` | S→B | 块内 segment concat 完成 |
| `complete` | S→B | downloadUrl, mergedBytes |
| `error` | S→B | message |

---

## 11. 双进度条语义

```mermaid
flowchart LR
    subgraph PlayBar["播放进度"]
        P1[playedDuration / sourceDuration]
    end

    subgraph DownBar["下载进度"]
        D1["阶段1: completedChunks/total × 85%"]
        D2["阶段2: fetch loaded/mergedBytes × 15%"]
        D1 --> D2
    end
```

| 进度条 | 含义 | 100% 条件 |
|--------|------|-----------|
| 播放 | 已播放时长占比 | 播放到末尾 |
| 下载 | 完整可保存文件就绪 | `fullAudioBlob` fetch 完成 |

> WS 块全部收完 ≠ 下载 100%。必须拿到合并后的 `full.ogg` 才可任意 seek 与保存。

---

## 12. 分块与 RTF 时间估算

Python **仅**对 PCM 处理阶段施加 RTF 0.6（墙钟 ≥ `durationSec × 0.6`）。FFmpeg 编解码时间额外计入总墙钟，不计入 `estimatedProcessingSeconds`。

**281s 褪黑素示例**

| 指标 | 值 |
|------|-----|
| 编排分块 | [90, 90, 90, 11] → 4 chunks |
| gRPC segment 数 | 9+9+9+2 = **29**（每 chunk 内 10s 一帧） |
| estimatedProcessingSeconds | 281 × 0.6 ≈ **168.6s**（整次 Python RTF，非首段） |
| 首次可播 | 首个 `segment_complete`（约 10s 段编码 + 推送完成后，远早于首 chunk 的 54s RTF） |

---

## 13. 技术栈与依赖

| 层级 | 工具链 |
|------|--------|
| 前端 | pnpm, Vue 3, Vite, @ffmpeg/ffmpeg, npmmirror CDN |
| 编排 | Java 17+, Gradle 9.5.1 (sdkman), Spring Boot 3.4, WebSocket, gRPC (protobuf) |
| PCM | uv, grpcio, numpy, scipy |
| 系统 | **ffmpeg / ffprobe**（仅 Spring 调用） |

---

## 14. 测试与集成

| 类型 | 命令 |
|------|------|
| Python 单元 | `cd pcm-service && uv run pytest -q` |
| Spring 单元 + 集成 | `cd orchestrator && ./gradlew test` |
| 冒烟（服务已启动） | `./scripts/smoke-test.sh` |
| 全栈集成（自动启服） | `PCM_RTF=0.01 ./scripts/integration-test.sh` |

集成测试使用 `test-fixtures/flac/tone_10s_24k_mono.flac`（10s），Spring 侧 `PipelineIntegrationTest` 使用 `FakeGrpcPcmClient` 跳过 RTF 与 gRPC 网络。

---

## 15. 相关文档

- [API 规格](./api-spec.md)
- [测试计划](./test-plan.md)
- [项目状态](./PROGRESS.md)
- [README](../README.md)
