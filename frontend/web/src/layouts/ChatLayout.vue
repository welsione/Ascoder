<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useAppStore } from '../stores/app'
import { useRepositoryStore } from '../stores/repository'
import { useProjectStore } from '../stores/project'
import { useProjectSpaceStore } from '../stores/projectSpace'
import { useQuestionStore } from '../stores/question'
import { useSkillStore } from '../stores/skill'
import { useMcpServerStore } from '../stores/mcpServer'
import ChatSidebar from '../components/chat/ChatSidebar.vue'

const questionStore = useQuestionStore()
const sidebarVisible = ref(false)

function toggleSidebar() {
  sidebarVisible.value = !sidebarVisible.value
}

onMounted(async () => {
  await Promise.all([
    useAppStore().check(),
    useRepositoryStore().fetch(),
    useProjectStore().fetch(),
    useProjectSpaceStore().fetch(),
    questionStore.fetch(),
    useSkillStore().fetch(),
    useMcpServerStore().fetch(),
  ])
})

onUnmounted(() => {
  questionStore.cleanup()
})
</script>

<template>
  <section class="chat-layout">
    <!-- 移动端遮罩 -->
    <div
      v-if="sidebarVisible"
      class="mobile-overlay"
      @click="sidebarVisible = false"
    />
    <ChatSidebar :class="{ 'sidebar-open': sidebarVisible }" @close="sidebarVisible = false" />
    <section class="chat-main">
      <div class="chat-main-inner">
        <button class="mobile-menu-btn" type="button" @click="toggleSidebar">
          <span class="menu-icon">☰</span>
        </button>
        <router-view />
      </div>
    </section>
  </section>
</template>

<style scoped>
.mobile-menu-btn {
  display: none;
  position: absolute;
  top: var(--spacing-3);
  left: var(--spacing-3);
  z-index: 50;
  width: 36px;
  height: 36px;
  border: 1px solid var(--stroke);
  border-radius: var(--radius-md);
  background: var(--surface);
  cursor: pointer;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: var(--text);
  transition:
    background var(--transition-fast),
    border-color var(--transition-fast),
    transform var(--press-duration) var(--ease-snappy);
}

.mobile-menu-btn:hover {
  background: var(--primary-soft);
  border-color: var(--stroke-strong);
}

.mobile-menu-btn:active {
  transform: scale(var(--press-scale));
}

.mobile-overlay {
  display: none;
  position: fixed;
  inset: 0;
  z-index: 90;
  background: rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
}

@media (max-width: 600px) {
  .mobile-menu-btn {
    display: flex;
  }

  .mobile-overlay {
    display: block;
  }
}
</style>
