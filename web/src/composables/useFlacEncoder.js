import { FFmpeg } from '@ffmpeg/ffmpeg'
import { fetchFile, toBlobURL } from '@ffmpeg/util'
import { ref } from 'vue'

let ffmpegInstance = null
let loadPromise = null

async function getFfmpeg() {
  if (ffmpegInstance) return ffmpegInstance
  if (loadPromise) return loadPromise

  loadPromise = (async () => {
    const ffmpeg = new FFmpeg()
    const baseURL = 'https://registry.npmmirror.com/@ffmpeg/core/0.12.6/files/dist/esm'
    await ffmpeg.load({
      coreURL: await toBlobURL(`${baseURL}/ffmpeg-core.js`, 'text/javascript'),
      wasmURL: await toBlobURL(`${baseURL}/ffmpeg-core.wasm`, 'application/wasm'),
    })
    ffmpegInstance = ffmpeg
    return ffmpeg
  })()

  return loadPromise
}

export function useFlacEncoder() {
  const loading = ref(false)
  const progress = ref(0)
  const error = ref('')

  async function wavToFlac(wavFile) {
    loading.value = true
    error.value = ''
    progress.value = 0
    try {
      const ffmpeg = await getFfmpeg()
      ffmpeg.on('progress', ({ progress: p }) => {
        progress.value = Math.round(p * 100)
      })

      const inputName = 'input.wav'
      const outputName = 'output.flac'
      await ffmpeg.writeFile(inputName, await fetchFile(wavFile))
      await ffmpeg.exec(['-i', inputName, '-compression_level', '8', outputName])
      const data = await ffmpeg.readFile(outputName)
      await ffmpeg.deleteFile(inputName)
      await ffmpeg.deleteFile(outputName)
      return new Blob([data.buffer], { type: 'audio/flac' })
    } catch (e) {
      error.value = e.message || 'WAV 转 FLAC 失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function oggToAudioBuffer(oggBytes, audioContext) {
    const ffmpeg = await getFfmpeg()
    const inputName = 'chunk.ogg'
    const outputName = 'chunk.wav'
    await ffmpeg.writeFile(inputName, oggBytes)
    await ffmpeg.exec(['-i', inputName, '-ar', '48000', '-ac', '1', outputName])
    const wavData = await ffmpeg.readFile(outputName)
    await ffmpeg.deleteFile(inputName)
    await ffmpeg.deleteFile(outputName)
    const wavBlob = new Blob([wavData.buffer], { type: 'audio/wav' })
    const arrayBuffer = await wavBlob.arrayBuffer()
    return audioContext.decodeAudioData(arrayBuffer.slice(0))
  }

  return { loading, progress, error, wavToFlac, oggToAudioBuffer }
}
