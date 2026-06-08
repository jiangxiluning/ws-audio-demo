const MIN_DURATION = 10
const MAX_DURATION = 300

function readAscii(view, offset, length) {
  let s = ''
  for (let i = 0; i < length; i++) {
    s += String.fromCharCode(view.getUint8(offset + i))
  }
  return s
}

/** Parse WAV duration from file header (supports common PCM WAV). */
export async function probeWavDuration(file) {
  const header = await file.slice(0, 64).arrayBuffer()
  const view = new DataView(header)
  if (readAscii(view, 0, 4) !== 'RIFF' || readAscii(view, 8, 4) !== 'WAVE') {
    throw new Error('请选择 WAV 文件')
  }

  let offset = 12
  let sampleRate = 0
  let channels = 0
  let bitsPerSample = 0
  let dataSize = 0

  while (offset + 8 <= header.byteLength) {
    const chunkId = readAscii(view, offset, 4)
    const chunkSize = view.getUint32(offset + 4, true)
    if (chunkId === 'fmt ') {
      channels = view.getUint16(offset + 10, true)
      sampleRate = view.getUint32(offset + 12, true)
      bitsPerSample = view.getUint16(offset + 22, true)
    } else if (chunkId === 'data') {
      dataSize = chunkSize
      break
    }
    offset += 8 + chunkSize
  }

  if (!sampleRate || !channels || !bitsPerSample) {
    throw new Error('无法解析 WAV 格式')
  }

  const bytesPerSample = bitsPerSample / 8
  const duration = dataSize / (sampleRate * channels * bytesPerSample)
  return { duration, sampleRate, channels, bitsPerSample }
}

export function validateDuration(duration) {
  if (duration < MIN_DURATION || duration > MAX_DURATION) {
    throw new Error(`时长须在 ${MIN_DURATION}s ~ ${MAX_DURATION}s 之间，当前 ${duration.toFixed(1)}s`)
  }
}

export function useAudioValidator() {
  return { probeWavDuration, validateDuration, MIN_DURATION, MAX_DURATION }
}
