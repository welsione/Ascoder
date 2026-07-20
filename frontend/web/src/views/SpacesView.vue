<script setup lang="ts">
import { computed } from 'vue'
import { ArrowRight, FolderGit2, Plus, Settings } from 'lucide-vue-next'
import { useProjectStore } from '../stores/project'
import logoUrl from '../images/logo.svg'

const projectStore = useProjectStore()

const projectCards = computed(() => projectStore.projects)
</script>

<template>
  <section class="project-home-page">
    <div class="project-home-center">
      <img class="project-home-logo" :src="logoUrl" alt="Ascoder" />
      <router-link to="/settings" class="project-home-settings" aria-label="设置" title="设置">
        <Settings aria-hidden="true" :size="22" :stroke-width="1.8" />
        <span class="sr-only">设置</span>
      </router-link>
      <p class="project-home-subtitle">选择项目进入项目空间</p>
    </div>

    <section class="project-card-grid project-home-grid">
      <router-link class="project-card add-project-card" to="/projects/config">
        <span class="icon-badge" aria-hidden="true">
          <Plus :size="18" :stroke-width="1.8" />
        </span>
        <div class="add-project-copy">
          <h2>添加新项目</h2>
          <p>配置项目仓库、代码路径和项目空间</p>
        </div>
        <span class="add-project-hint">快速开始</span>
      </router-link>

      <article
        v-for="project in projectCards"
        :key="project.id"
        class="project-card project-entry-card"
      >
        <div class="project-card-head">
          <span class="icon-badge project-card-icon" aria-hidden="true">
            <FolderGit2 :size="18" :stroke-width="1.8" />
          </span>
          <div>
            <h2>{{ project.name }}</h2>
            <p v-if="project.description" class="project-card-description">
              {{ project.description }}
            </p>
          </div>
        </div>
        <div class="project-card-footer">
          <span>项目空间</span>
          <router-link class="project-card-enter" :to="`/projects/${project.id}`">
            进入项目空间 <ArrowRight aria-hidden="true" :size="16" :stroke-width="1.8" />
          </router-link>
        </div>
      </article>
    </section>

  </section>
</template>
