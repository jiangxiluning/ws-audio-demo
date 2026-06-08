<script setup>
import { ref, watch, computed } from 'vue'
import { useStreamPlayer } from '../composables/useStreamPlayer.js'

const props = defineProps({
  job: { type: Object, default: null },
})

const seekInput = ref(0)

const player = useStreamPlayer()
const {
  status,
  error,
  completedChunks,
  totalChunks,
  playProgress,
  downloadProgress,
  bufferedUntilSec,
  sourceDurationSec,
  canSave,
  canSeekAnywhere,
  isPlaying,
  connect,
  play,
  pause,
  seek,
  saveFile,
} = player

watch(
  () => props.job,
  (job) => {
    if (job?.streamPath) {
      connect(job.streamPath, {
        totalChunks: job.totalChunks,
        sourceDurationSeconds: job.sourceDurationSeconds,
      })
    }
  },
  { immediate: true },
)

const statusLabel = computed(() => {
  const map = {
    idle: '等待',
    connecting: '连接中',
    streaming: '流式接收',
    playing: '播放中',
    paused: '已暂停',
    downloading: '下载完整文件',
    complete: '完成',
    error: '错误',
    closed: '连接关闭',
  }
  return map[status.value] ?? status.value
})

function onSeek() {
  seek(Number(seekInput.value))
}

function formatPct(v) {
  return `${Math.min(100, Math.max(0, v)).toFixed(1)}%`
}
</script>

<template>
  <section class="panel">
    <h2>3. 播放与下载</h2>
    <p class="status">
      状态: {{ statusLabel }}
      <span v-if="totalChunks"> · 块 {{ completedChunks }}/{{ totalChunks }}</span>
      <span v-if="bufferedUntilSec"> · 已缓冲 {{ bufferedUntilSec.toFixed(1) }}s</span>
    </p>

    <div class="progress-wrap">
      <div class="progress-label">
        <span>播放进度</span>
        <span>{{ formatPct(playProgress) }}</span>
      </div>
      <div class="progress-bar">
        <div class="progress-fill play" :style="{ width: formatPct(playProgress) }" />
      </div>
    </div>

    <div class="progress-wrap">
      <div class="progress-label">
        <span>下载进度（完整文件）</span>
        <span>{{ formatPct(downloadProgress) }}</span>
      </div>
      <div class="progress-bar">
        <div class="progress-fill" :style="{ width: formatPct(downloadProgress) }" />
      </div>
    </div>

    <div class="row" style="margin-top: 1rem">
      <button v-if="!isPlaying" class="secondary" :disabled="completedChunks === 0" @click="play">
        播放
      </button>
      <button v-else class="secondary" @click="pause">暂停</button>
      <label>
        Seek (s):
        <input v-model.number="seekInput" type="number" min="0" :max="sourceDurationSec" step="0.5" />
      </label>
      <button class="secondary" @click="onSeek">跳转</button>
      <button :disabled="!canSave" @click="saveFile">保存完整 OGG</button>
    </div>
    <p class="meta" v-if="!canSeekAnywhere">
      下载未完成前仅可在已缓冲范围内 seek（{{ bufferedUntilSec.toFixed(1) }}s）
    </p>
    <p v-if="error" class="error">{{ error }}</p>
  </section>
</template>
