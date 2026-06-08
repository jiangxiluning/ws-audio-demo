<script setup>
import { ref } from 'vue'
import UploadPanel from './components/UploadPanel.vue'
import ProcessPanel from './components/ProcessPanel.vue'
import PlayerPanel from './components/PlayerPanel.vue'

const upload = ref(null)
const job = ref(null)

function onUploaded(data) {
  upload.value = data
  job.value = null
}

function onStarted(j) {
  job.value = j
}
</script>

<template>
  <header>
    <h1>WS Audio Demo</h1>
    <p class="subtitle">WAV → FLAC 上传 · 分块 PCM 处理 · WebSocket 流式播放 · 完整 OGG 下载</p>
  </header>

  <UploadPanel @uploaded="onUploaded" />
  <ProcessPanel
    :uri="upload?.uri ?? ''"
    :duration="upload?.durationSeconds ?? 0"
    @started="onStarted"
  />
  <PlayerPanel v-if="job" :job="job" />
</template>
