<script setup lang="ts">
import { ChevronDown } from 'lucide-vue-next'
import type { QueryPlanRecord } from '../../types/question'

defineProps<{
  queryPlan: QueryPlanRecord
}>()
</script>

<template>
  <details class="analysis-card query-plan-card">
    <summary class="section-heading compact query-plan-head">
      <div>
        <p class="kicker">Query Plan</p>
        <h3>Query Plan 已生成，点击查看</h3>
      </div>
      <el-tag size="small" effect="plain" class="plan-type-tag">{{ queryPlan.type }}</el-tag>
      <ChevronDown class="query-plan-chevron" :size="16" :stroke-width="2" aria-hidden="true" />
    </summary>
    <div class="query-plan-body stacked-meta">
      <div class="meta-row">
        <span class="meta-label">推荐工具</span>
        <div class="chip-flow">
          <el-tag v-for="tool in queryPlan.recommendedTools" :key="tool" size="small">
            {{ tool }}
          </el-tag>
        </div>
      </div>
      <div v-if="queryPlan.recommendedSkills.length" class="meta-row">
        <span class="meta-label">推荐 Skill</span>
        <div class="chip-flow">
          <el-tag v-for="skill in queryPlan.recommendedSkills" :key="skill" size="small" type="success">
            {{ skill }}
          </el-tag>
        </div>
      </div>
      <div class="meta-row">
        <span class="meta-label">改写查询</span>
        <ul class="detail-listing">
          <li v-for="query in queryPlan.rewrittenQueries" :key="query">{{ query }}</li>
        </ul>
      </div>
    </div>
    <p v-if="queryPlan.reasoning" class="analysis-copy">{{ queryPlan.reasoning }}</p>
  </details>
</template>

<style scoped>
.query-plan-card {
  position: relative;
  gap: var(--spacing-3);
  padding: var(--spacing-4);
  border: 1px solid rgba(79, 110, 247, 0.14);
  border-radius: var(--radius-lg);
  background:
    linear-gradient(135deg, rgba(79, 110, 247, 0.08), transparent 36%),
    var(--surface);
  opacity: 1;
  overflow: hidden;
}

.query-plan-card::before {
  content: '';
  position: absolute;
  top: var(--spacing-4);
  bottom: var(--spacing-4);
  left: 0;
  width: 3px;
  border-radius: 0 var(--radius-full) var(--radius-full) 0;
  background: var(--chat-accent);
}

.query-plan-head {
  position: relative;
  cursor: pointer;
  list-style: none;
}

.query-plan-head::-webkit-details-marker {
  display: none;
}

.query-plan-head h3 {
  margin-top: var(--spacing-1);
  font-size: var(--font-size-xl);
}

.query-plan-chevron {
  color: var(--muted);
  transition: transform var(--transition-fast);
}

.query-plan-card[open] .query-plan-chevron {
  transform: rotate(180deg);
}

.query-plan-body {
  padding-top: var(--spacing-3);
}

.plan-type-tag {
  border-color: rgba(79, 110, 247, 0.28);
  background: rgba(79, 110, 247, 0.08);
  color: var(--chat-accent);
  font-weight: var(--font-weight-semibold);
}

.analysis-copy {
  padding-top: var(--spacing-3);
  border-top: 1px dashed var(--stroke);
}
</style>
