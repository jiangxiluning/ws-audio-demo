import { ref, computed } from 'vue'
import { useFlacEncoder } from './useFlacEncoder.js'

const WS_PHASE_WEIGHT = 0.85
const FETCH_PHASE_WEIGHT = 0.15

export function useStreamPlayer() {
  const { oggToAudioBuffer } = useFlacEncoder()

  const status = ref('idle')
  const error = ref('')
  const totalChunks = ref(0)
  const sourceDurationSec = ref(0)
  const completedChunks = ref(0)
  const playProgress = ref(0)
  const downloadProgress = ref(0)
  const mergedBytes = ref(0)
  const downloadUrl = ref('')
  const fullAudioBlob = ref(null)
  const bufferedUntilSec = ref(0)
  const currentChunkIndex = ref(-1)
  const isPlaying = ref(false)

  let ws = null
  let audioContext = null
  let segmentBuffers = []
  let segmentDurations = []
  let currentSegmentBytes = []
  let scheduledSources = []
  let scheduledFromIndex = 0
  let playbackStartTime = 0
  let playbackOffsetSec = 0
  let playheadSec = 0
  let playTimer = null

  const canSave = computed(() => downloadProgress.value >= 100 && fullAudioBlob.value != null)
  const canSeekAnywhere = computed(() => downloadProgress.value >= 100)

  function reset() {
    stopPlayback()
    if (ws) {
      ws.close()
      ws = null
    }
    status.value = 'idle'
    error.value = ''
    totalChunks.value = 0
    sourceDurationSec.value = 0
    completedChunks.value = 0
    playProgress.value = 0
    downloadProgress.value = 0
    mergedBytes.value = 0
    downloadUrl.value = ''
    fullAudioBlob.value = null
    bufferedUntilSec.value = 0
    currentChunkIndex.value = -1
    segmentBuffers = []
    segmentDurations = []
    currentSegmentBytes = []
    scheduledFromIndex = 0
  }

  function stopPlayback() {
    isPlaying.value = false
    scheduledSources.forEach((s) => {
      try {
        s.stop()
      } catch {
        /* already stopped */
      }
    })
    scheduledSources = []
    if (playTimer) {
      clearInterval(playTimer)
      playTimer = null
    }
  }

  function updateDownloadProgressPhase1() {
    if (totalChunks.value === 0) return
    downloadProgress.value = (completedChunks.value / totalChunks.value) * WS_PHASE_WEIGHT * 100
  }

  async function decodeAndStoreSegment(segmentDurationSec, oggBytes) {
    if (!audioContext) {
      audioContext = new AudioContext({ sampleRate: 48000 })
    }
    const buffer = await oggToAudioBuffer(oggBytes, audioContext)
    segmentBuffers.push(buffer)
    segmentDurations.push(segmentDurationSec)
    bufferedUntilSec.value = segmentDurations.reduce((sum, d) => sum + d, 0)
    if (isPlaying.value) {
      scheduleFrom(scheduledFromIndex)
    }
  }

  function scheduleFrom(startIndex) {
    if (!audioContext || audioContext.state === 'suspended') {
      audioContext?.resume()
    }
    let when = audioContext.currentTime
    if (scheduledSources.length === 0) {
      playbackStartTime = audioContext.currentTime
      when = playbackStartTime + 0.05
    } else {
      const last = scheduledSources[scheduledSources.length - 1]
      when = last._endTime ?? audioContext.currentTime
    }

    for (let i = startIndex; i < segmentBuffers.length; i++) {
      const buf = segmentBuffers[i]
      if (!buf) break
      const source = audioContext.createBufferSource()
      source.buffer = buf
      source.connect(audioContext.destination)
      source.start(when)
      source._endTime = when + buf.duration
      scheduledSources.push(source)
      when += buf.duration
      scheduledFromIndex = i + 1
    }
  }

  function startPlayTimer() {
    if (playTimer) return
    playTimer = setInterval(() => {
      if (!isPlaying.value || !sourceDurationSec.value) return
      if (audioContext) {
        const elapsed = audioContext.currentTime - playbackStartTime + playbackOffsetSec
        playheadSec = Math.min(elapsed, bufferedUntilSec.value)
        playProgress.value = (playheadSec / sourceDurationSec.value) * 100
      }
    }, 200)
  }

  function play() {
    if (segmentBuffers.length === 0) return
    isPlaying.value = true
    if (!audioContext) {
      audioContext = new AudioContext({ sampleRate: 48000 })
    }
    audioContext.resume()
    scheduleFrom(0)
    startPlayTimer()
    status.value = 'playing'
  }

  function pause() {
    stopPlayback()
    scheduledFromIndex = 0
    status.value = 'paused'
  }

  function seek(targetSec) {
    if (canSeekAnywhere.value && fullAudioBlob.value) {
      playheadSec = targetSec
      playProgress.value = (targetSec / sourceDurationSec.value) * 100
      return
    }
    if (targetSec > bufferedUntilSec.value) {
      error.value = `尚未下载到该位置（已缓冲 ${bufferedUntilSec.value.toFixed(1)}s）`
      return
    }
    error.value = ''
    stopPlayback()
    playbackOffsetSec = targetSec
    playheadSec = targetSec
    playProgress.value = (targetSec / sourceDurationSec.value) * 100
    scheduledFromIndex = 0

    let acc = 0
    let startIndex = 0
    let offsetInSegment = targetSec
    for (let i = 0; i < segmentDurations.length; i++) {
      const d = segmentDurations[i]
      if (acc + d > targetSec) {
        startIndex = i
        offsetInSegment = targetSec - acc
        break
      }
      acc += d
    }

    if (!audioContext) {
      audioContext = new AudioContext({ sampleRate: 48000 })
    }
    isPlaying.value = true
    playbackStartTime = audioContext.currentTime
    let when = playbackStartTime + 0.05
    for (let i = startIndex; i < segmentBuffers.length; i++) {
      const buf = segmentBuffers[i]
      if (!buf) break
      const source = audioContext.createBufferSource()
      source.buffer = buf
      source.connect(audioContext.destination)
      const startAt = i === startIndex ? offsetInSegment : 0
      source.start(when, startAt)
      source._endTime = when + (buf.duration - startAt)
      scheduledSources.push(source)
      when += buf.duration - startAt
      scheduledFromIndex = i + 1
    }
    startPlayTimer()
  }

  async function fetchMergedFile(url, expectedBytes) {
    status.value = 'downloading'
    const response = await fetch(url)
    if (!response.ok) {
      throw new Error(`下载失败: HTTP ${response.status}`)
    }
    const reader = response.body.getReader()
    const chunks = []
    let loaded = 0
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      chunks.push(value)
      loaded += value.length
      const fetchPart = expectedBytes > 0 ? (loaded / expectedBytes) * FETCH_PHASE_WEIGHT * 100 : 0
      downloadProgress.value = WS_PHASE_WEIGHT * 100 + fetchPart
    }
    fullAudioBlob.value = new Blob(chunks, { type: 'audio/ogg' })
    downloadProgress.value = 100
    status.value = 'complete'
  }

  function connect(streamPath, jobMeta) {
    reset()
    status.value = 'connecting'
    totalChunks.value = jobMeta.totalChunks ?? 0
    sourceDurationSec.value = jobMeta.sourceDurationSeconds ?? 0

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const wsUrl = `${protocol}//${window.location.host}${streamPath}`
    ws = new WebSocket(wsUrl)
    ws.binaryType = 'arraybuffer'

    ws.onopen = () => {
      status.value = 'streaming'
    }

    ws.onmessage = async (event) => {
      if (typeof event.data === 'string') {
        const msg = JSON.parse(event.data)
        switch (msg.type) {
          case 'session_meta':
            totalChunks.value = msg.totalChunks
            sourceDurationSec.value = msg.sourceDurationSec
            break
          case 'chunk_start':
            currentChunkIndex.value = msg.chunkIndex
            currentSegmentBytes = []
            break
          case 'progress':
            break
          case 'segment_complete': {
            const bytes = concatBytes(currentSegmentBytes)
            currentSegmentBytes = []
            await decodeAndStoreSegment(msg.segmentDurationSec, bytes)
            if (segmentBuffers.length === 1 && !isPlaying.value) {
              play()
            }
            break
          }
          case 'chunk_complete':
            completedChunks.value += 1
            updateDownloadProgressPhase1()
            break
          case 'complete':
            mergedBytes.value = msg.mergedBytes
            downloadUrl.value = msg.downloadUrl
            try {
              await fetchMergedFile(msg.downloadUrl, msg.mergedBytes)
            } catch (e) {
              error.value = e.message
            }
            break
          case 'error':
            error.value = msg.message
            status.value = 'error'
            break
          default:
            break
        }
      } else {
        currentSegmentBytes.push(new Uint8Array(event.data))
      }
    }

    ws.onerror = () => {
      error.value = 'WebSocket 连接错误'
      status.value = 'error'
    }

    ws.onclose = () => {
      if (status.value === 'streaming') {
        status.value = 'closed'
      }
    }
  }

  function saveFile() {
    if (!fullAudioBlob.value) return
    const a = document.createElement('a')
    a.href = URL.createObjectURL(fullAudioBlob.value)
    a.download = 'processed.ogg'
    a.click()
    URL.revokeObjectURL(a.href)
  }

  return {
    status,
    error,
    totalChunks,
    sourceDurationSec,
    completedChunks,
    playProgress,
    downloadProgress,
    mergedBytes,
    bufferedUntilSec,
    canSave,
    canSeekAnywhere,
    isPlaying,
    connect,
    play,
    pause,
    seek,
    saveFile,
    reset,
  }
}

function concatBytes(parts) {
  const total = parts.reduce((n, p) => n + p.length, 0)
  const out = new Uint8Array(total)
  let offset = 0
  for (const p of parts) {
    out.set(p, offset)
    offset += p.length
  }
  return out
}
