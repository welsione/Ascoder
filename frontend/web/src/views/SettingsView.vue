<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import SettingsSidebar from '../components/settings/SettingsSidebar.vue'
import RepositorySection from '../components/settings/RepositorySection.vue'
import SkillSection from '../components/settings/SkillSection.vue'
import ToolSection from '../components/settings/ToolSection.vue'
import AgentSection from '../components/settings/AgentSection.vue'
import LlmProviderSection from '../components/settings/LlmProviderSection.vue'
import GeneralSection from '../components/settings/GeneralSection.vue'
import type { Section } from '../types/settings'

const route = useRoute()

function normalizeSection(value: string | undefined): Section {
  if (value === 'repository' || value === 'repositories') return 'repositories'
  if (value === 'skill' || value === 'skills') return 'skills'
  if (value === 'tool' || value === 'tools') return 'tools'
  if (value === 'agents') return 'agents'
  if (value === 'llm-providers') return 'llm-providers'
  if (value === 'general') return 'general'
  if (value === 'mcp') return 'mcp'
  return 'repositories'
}

const section = computed<Section>(() => {
  return normalizeSection(route.params.section as string | undefined)
})

const sectionMeta = computed(() => {
  if (section.value === 'repositories') {
    return { title: '仓库管理', description: '维护代码仓库接入、同步与分支发现状态。' }
  }
  if (section.value === 'skills') {
    return { title: 'Skill 管理', description: '配置回答策略与规则，让 Ascoder 的回答更贴近团队协作方式。' }
  }
  if (section.value === 'tools') {
    return { title: '工具管理', description: '管理 Agent 可调用工具，控制能力边界与风险暴露。' }
  }
  if (section.value === 'agents') {
    return { title: 'Agent 管理', description: '配置 Agent 定义、提示词、工具装配、触发条件与模型参数。' }
  }
  if (section.value === 'llm-providers') {
    return { title: '模型供应商', description: '管理 LLM 供应商配置与连接状态，确保问答功能可用。' }
  }
  if (section.value === 'general') {
    return { title: '通用设置', description: '运行时调参与默认值管理，立即对后续请求生效。' }
  }
  return { title: 'MCP 管理', description: '配置代理可调用的外部工具服务，扩展问答时的执行能力。' }
})
</script>

<template>
  <section class="settings-drawer-layout">
    <SettingsSidebar />
    <section class="settings-drawer-main">
      <header class="settings-drawer-header">
        <div>
          <p class="kicker">{{ sectionMeta.title }}</p>
          <h2>{{ sectionMeta.description }}</h2>
        </div>
      </header>

      <RepositorySection v-if="section === 'repositories'" />
      <SkillSection v-else-if="section === 'skills'" />
      <ToolSection v-else-if="section === 'tools'" />
      <AgentSection v-else-if="section === 'agents'" />
      <LlmProviderSection v-else-if="section === 'llm-providers'" />
      <GeneralSection v-else-if="section === 'general'" />
      <section v-else class="surface-panel settings-block settings-disabled-panel">
        <div class="section-heading">
          <div>
            <p class="kicker">暂未开放</p>
            <h2>MCP 配置入口已临时关闭</h2>
          </div>
        </div>
        <p class="settings-disabled-copy">
          CodeGraph MCP 接入方案确认前，暂不允许在前端新增或修改 MCP Server。
        </p>
      </section>
    </section>
  </section>
</template>
