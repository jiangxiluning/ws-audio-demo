<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  uri: { type: String, default: '' },
  duration: { type: Number, default: 0 },
})

const emit = defineEmits(['started'])

const gainDb = ref(6)
const processing = ref(false)
const error = ref('')
const job = ref(null)

const chunkSummary = computed(() => {
  if (!job.value?.chunks?.length) return ''
  return job.value.chunks
    .map((c) => `#${c.index} ${c.offsetSec}s + ${c.durationSec}s`)
    .join(' · ')
})

async function startProcess() {
  if (!props.uri) return
  error.value = ''
  processing.value = true
  job.value = null
  try {
    const res = await fetch('/api/v1/audio/process', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ uri: props.uri, gainDb: gainDb.value }),
    })
    if (!res.ok) {
      const body = await res.json().catch(() => ({}))
      throw new Error(body.message || `处理请求失败 HTTP ${res.status}`)
    }
    job.value = await res.json()
    emit('started', job.value)
  } catch (e) {
    error.value = e.message
  } finally {
    processing.value = false
  }
}
</script>

<template>
  <section class="panel">
    <h2>2. 发起处理</h2>
    <p class="meta" v-if="uri">URI: {{ uri }} · {{ duration.toFixed(1) }}s</p>
    <div class="row">
      <label>
        增益 (dB):
        <input v-model.number="gainDb" type="number" min="-24" max="24" step="0.5" />
      </label>
      <button :disabled="!uri || processing" @click="startProcess">
        {{ processing ? '提交中…' : '开始处理' }}
      </button>
    </div>
    <template v-if="job">
      <p class="status">
        Job {{ job.jobId }} · {{ job.totalChunks }} 块 · Python RTF 预估
        {{ job.estimatedProcessingSeconds?.toFixed(1) ?? '—' }}s
      </p>
      <ul class="chunk-list">
        <li v-for="c in job.chunks" :key="c.index">
          块 {{ c.index }}: offset {{ c.offsetSec }}s, 时长 {{ c.durationSec }}s
        </li>
      </ul>
    </template>
    <p v-if="error" class="error">{{ error }}</p>
  </section>
</template>
