<script setup lang="ts">
import { onMounted } from 'vue'
import { useAppStore } from '../stores/app'
import { useRepositoryStore } from '../stores/repository'
import { useSkillStore } from '../stores/skill'
import { useMcpServerStore } from '../stores/mcpServer'
import { useAgentToolStore } from '../stores/agentTool'
import { useAgentStore } from '../stores/agent'

onMounted(async () => {
  await Promise.all([
    useAppStore().check(),
    useRepositoryStore().fetch(),
    useSkillStore().fetch(),
    useMcpServerStore().fetch(),
    useAgentToolStore().fetch(),
    useAgentStore().fetch(),
  ])
})
</script>

<template>
  <section class="settings-layout">
    <router-view />
  </section>
</template>
