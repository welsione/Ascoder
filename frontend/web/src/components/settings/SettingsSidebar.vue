<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Blocks, BotMessageSquare, Cpu, FolderGit2, Settings, SlidersHorizontal, WandSparkles } from 'lucide-vue-next'
import { useRepositoryStore } from '../../stores/repository'
import { useSkillStore } from '../../stores/skill'
import { useMcpServerStore } from '../../stores/mcpServer'
import { useAgentToolStore } from '../../stores/agentTool'
import { useAgentStore } from '../../stores/agent'
import { useLlmProviderStore } from '../../stores/llmProvider'
import type { Section } from '../../types/settings'

const route = useRoute()
const router = useRouter()
const repositoryStore = useRepositoryStore()
const skillStore = useSkillStore()
const mcpStore = useMcpServerStore()
const toolStore = useAgentToolStore()
const agentStore = useAgentStore()
const llmProviderStore = useLlmProviderStore()
const mcpConfigEnabled = true

function currentSection(): Section {
  const s = route.params.section as string
  if (s === 'repository' || s === 'repositories') return 'repositories'
  if (s === 'skill' || s === 'skills') return 'skills'
  if (s === 'tool' || s === 'tools') return 'tools'
  if (s === 'agents') return 'agents'
  if (s === 'llm-providers') return 'llm-providers'
  if (s === 'mcp') return s
  return 'repositories'
}

function navigate(section: Section) {
  if (section === 'mcp' && !mcpConfigEnabled) {
    return
  }
  router.push(`/settings/${section}`)
}
</script>

<template>
  <aside class="settings-drawer-sidebar">
    <div class="settings-drawer-brand">
      <span class="icon-badge settings-brand-icon" aria-hidden="true">
        <Settings :size="18" :stroke-width="1.8" />
      </span>
      <div>
        <p class="eyebrow">Settings</p>
        <h2>工作台设置</h2>
      </div>
      <router-link to="/projects">
        <el-button text>
          <ArrowLeft class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
          返回项目空间
        </el-button>
      </router-link>
    </div>

    <div class="settings-nav-list">
      <button
        class="settings-nav-item"
        :class="{ active: currentSection() === 'repositories' }"
        type="button"
        @click="navigate('repositories')"
      >
        <span>
          <FolderGit2 class="inline-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
          仓库管理
        </span>
        <small>{{ repositoryStore.repositories.length }} 个仓库</small>
      </button>
      <button
        class="settings-nav-item"
        :class="{ active: currentSection() === 'skills' }"
        type="button"
        @click="navigate('skills')"
      >
        <span>
          <WandSparkles class="inline-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
          Skill 管理
        </span>
        <small>{{ skillStore.enabledCount }} 个已启用</small>
      </button>
      <button
        class="settings-nav-item"
        :class="{ active: currentSection() === 'tools' }"
        type="button"
        @click="navigate('tools')"
      >
        <span>
          <SlidersHorizontal class="inline-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
          工具管理
        </span>
        <small>{{ toolStore.enabledCount }} / {{ toolStore.totalCount }} 已启用</small>
      </button>
      <button
        class="settings-nav-item"
        :class="{ active: currentSection() === 'agents' }"
        type="button"
        @click="navigate('agents')"
      >
        <span>
          <BotMessageSquare class="inline-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
          Agent 管理
        </span>
        <small>{{ agentStore.enabledAgents.length }} 个已启用</small>
      </button>
      <button
        class="settings-nav-item"
        :class="{ active: currentSection() === 'llm-providers' }"
        type="button"
        @click="navigate('llm-providers')"
      >
        <span>
          <Cpu class="inline-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
          模型供应商
        </span>
        <small>{{ llmProviderStore.enabledProviders.length }} 个已启用</small>
      </button>
      <button
        class="settings-nav-item"
        :class="{ active: currentSection() === 'mcp', disabled: !mcpConfigEnabled }"
        type="button"
        :disabled="!mcpConfigEnabled"
        title="MCP 配置暂未开放"
        @click="navigate('mcp')"
      >
        <span>
          <Blocks class="inline-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
          MCP 管理
        </span>
        <small>{{ mcpConfigEnabled ? `${mcpStore.enabledCount} 个已启用` : '暂未开放' }}</small>
      </button>
    </div>
  </aside>
</template>
