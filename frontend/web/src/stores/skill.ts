import { defineStore } from 'pinia'
import { computed } from 'vue'
import * as api from '../services/skillApi'
import type { AgentSkill } from '../types/skill'
import { useCrudStore } from '../composables/useCrudStore'

interface SkillForm {
  name: string
  description: string
  skillContent: string
  source: string
  enabled: boolean
}

export const useSkillStore = defineStore('skill', () => {
  const crud = useCrudStore<AgentSkill, SkillForm>({
    initialForm: () => ({
      name: '',
      description: '',
      skillContent: '',
      source: 'manual',
      enabled: true,
    }),
  })

  const enabledCount = computed(() =>
    crud.items.value.filter((s) => s.enabled).length
  )

  async function fetch() {
    await crud.fetchAll(() => api.getAll(), '无法加载 Skill')
  }

  async function create() {
    return crud.create(
      (form) =>
        api.create({
          name: form.name.trim(),
          description: form.description.trim(),
          skillContent: form.skillContent.trim(),
          source: form.source.trim() || 'manual',
          enabled: form.enabled,
        }),
      (form) =>
        !form.name.trim() || !form.description.trim() || !form.skillContent.trim()
          ? '请填写 Skill 名称、描述和内容'
          : null,
      '无法创建 Skill',
      async () => {
        crud.resetForm()
        await fetch()
      }
    ) as Promise<AgentSkill | null>
  }

  async function toggleEnabled(skillId: number, enabled: boolean) {
    crud.error.value = ''
    try {
      const updated = await api.updateEnabled(skillId, enabled)
      const idx = crud.items.value.findIndex((s) => s.id === skillId)
      if (idx !== -1) {
        crud.items.value[idx] = updated
      }
    } catch (err) {
      crud.error.value = err instanceof Error ? err.message : '无法更新 Skill 状态'
    }
  }

  return {
    skills: crud.items,
    loading: crud.loading,
    error: crud.error,
    createLoading: crud.createLoading,
    form: crud.form,
    enabledCount,
    fetch,
    create,
    toggleEnabled,
    resetForm: crud.resetForm,
  }
})
