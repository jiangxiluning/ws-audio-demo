<script setup>
import { ref } from 'vue'
import { useAudioValidator } from '../composables/useAudioValidator.js'
import { useFlacEncoder } from '../composables/useFlacEncoder.js'

const emit = defineEmits(['uploaded'])

const { probeWavDuration, validateDuration } = useAudioValidator()
const { loading, progress, error: encodeError, wavToFlac } = useFlacEncoder()

const selectedFile = ref(null)
const duration = ref(0)
const status = ref('')
const error = ref('')
const uploading = ref(false)

async function onFileChange(event) {
  error.value = ''
  status.value = ''
  const file = event.target.files?.[0]
  if (!file) return
  selectedFile.value = file
  try {
    const info = await probeWavDuration(file)
    validateDuration(info.duration)
    duration.value = info.duration
    status.value = `已选择：${file.name}（${info.duration.toFixed(1)}s，${info.sampleRate}Hz ${info.channels}ch）`
  } catch (e) {
    error.value = e.message
    selectedFile.value = null
    duration.value = 0
  }
}

async function upload() {
  if (!selectedFile.value) return
  error.value = ''
  uploading.value = true
  status.value = '正在 WAV → FLAC…'
  try {
    const flacBlob = await wavToFlac(selectedFile.value)
    status.value = '正在上传…'
    const form = new FormData()
    form.append('file', flacBlob, selectedFile.value.name.replace(/\.wav$/i, '.flac'))
    const res = await fetch('/api/v1/audio/upload', { method: 'POST', body: form })
    if (!res.ok) {
      const body = await res.json().catch(() => ({}))
      throw new Error(body.message || `上传失败 HTTP ${res.status}`)
    }
    const data = await res.json()
    status.value = `上传成功 URI: ${data.uri}`
    emit('uploaded', data)
  } catch (e) {
    error.value = e.message
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <section class="panel">
    <h2>1. 上传 WAV</h2>
    <div class="row">
      <input type="file" accept=".wav,audio/wav" @change="onFileChange" />
      <button :disabled="!selectedFile || uploading || loading" @click="upload">
        {{ uploading || loading ? '处理中…' : '转 FLAC 并上传' }}
      </button>
    </div>
    <p v-if="loading" class="meta">ffmpeg.wasm 编码进度 {{ progress }}%</p>
    <p v-if="status" class="status">{{ status }}</p>
    <p v-if="error || encodeError" class="error">{{ error || encodeError }}</p>
  </section>
</template>
