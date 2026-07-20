<script setup lang="ts">
import type { AnswerEvidenceRecord } from '../../types/question'

defineProps<{
  summary: string | null
  evidence: AnswerEvidenceRecord[]
  uncertainty: string | null
  nextStep: string | null
}>()
</script>

<template>
  <div class="analysis-card">
    <div class="section-heading compact">
      <div>
        <p class="kicker">结构化结果</p>
        <h3>Evidence</h3>
      </div>
    </div>
    <div class="stacked-meta">
      <div v-if="summary" class="meta-row">
        <span class="meta-label">摘要</span>
        <p class="analysis-copy">{{ summary }}</p>
      </div>
      <div v-if="evidence.length" class="meta-row">
        <span class="meta-label">代码证据</span>
        <ul class="detail-listing">
          <li v-for="item in evidence" :key="`${item.reference}-${item.title}`">
            <strong>{{ item.reference }}</strong>
            <span>{{ item.detail }}</span>
          </li>
        </ul>
      </div>
      <div v-if="uncertainty" class="meta-row">
        <span class="meta-label">不确定性</span>
        <p class="analysis-copy">{{ uncertainty }}</p>
      </div>
      <div v-if="nextStep" class="meta-row">
        <span class="meta-label">下一步</span>
        <p class="analysis-copy">{{ nextStep }}</p>
      </div>
    </div>
  </div>
</template>
