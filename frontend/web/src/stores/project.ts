import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import * as api from '../services/projectApi'
import type { Project, ProjectRepositoryMember } from '../types/project'
import { useCrudStore } from '../composables/useCrudStore'

interface ProjectForm {
  name: string
  description: string
}

interface MemberForm {
  repositoryId: number | null
  alias: string
  role: string
  primaryRepository: boolean
  sortOrder: number
}

export const useProjectStore = defineStore('project', () => {
  const crud = useCrudStore<Project, ProjectForm>({
    initialForm: () => ({ name: '', description: '' }),
  })

  const members = ref<ProjectRepositoryMember[]>([])
  const selectedProjectId = ref<number | null>(null)
  const memberForm = reactive<MemberForm>({
    repositoryId: null,
    alias: '',
    role: 'repository',
    primaryRepository: false,
    sortOrder: 0,
  })

  async function fetch() {
    await crud.fetchAll(() => api.getAll(), '无法加载项目')
    if (!selectedProjectId.value && crud.items.value.length) {
      selectedProjectId.value = crud.items.value[0].id
      await fetchMembers(selectedProjectId.value)
    }
  }

  async function create() {
    return crud.create(
      (form) =>
        api.create({
          name: form.name.trim(),
          description: form.description.trim() || null,
        }),
      (form) => (!form.name.trim() ? '请填写项目名称' : null),
      '创建项目失败',
      async (project) => {
        crud.items.value.unshift(project)
        selectedProjectId.value = project.id
        members.value = []
        crud.form.name = ''
        crud.form.description = ''
        await fetchMembers(project.id)
      }
    )
  }

  async function fetchMembers(projectId: number) {
    selectedProjectId.value = projectId
    crud.error.value = ''
    try {
      members.value = await api.getMembers(projectId)
    } catch (err) {
      crud.error.value = err instanceof Error ? err.message : '无法加载项目仓库'
      members.value = []
    }
  }

  async function addRepository() {
    if (!selectedProjectId.value) {
      crud.error.value = '请先选择项目'
      return null
    }
    if (!memberForm.repositoryId) {
      crud.error.value = '请选择仓库'
      return null
    }
    crud.error.value = ''
    try {
      const member = await api.addRepository(selectedProjectId.value, {
        repositoryId: memberForm.repositoryId,
        alias: memberForm.alias.trim() || null,
        role: memberForm.role.trim() || null,
        primaryRepository: memberForm.primaryRepository,
        sortOrder: memberForm.sortOrder,
      })
      await fetchMembers(selectedProjectId.value)
      memberForm.repositoryId = null
      memberForm.alias = ''
      memberForm.role = 'repository'
      memberForm.primaryRepository = false
      memberForm.sortOrder = 0
      return member
    } catch (err) {
      crud.error.value = err instanceof Error ? err.message : '添加项目仓库失败'
      return null
    }
  }

  async function removeRepository(memberId: number) {
    if (!selectedProjectId.value) return false
    crud.error.value = ''
    try {
      await api.removeRepository(selectedProjectId.value, memberId)
      members.value = members.value.filter((member) => member.id !== memberId)
      return true
    } catch (err) {
      crud.error.value = err instanceof Error ? err.message : '移除项目仓库失败'
      return false
    }
  }

  return {
    projects: crud.items,
    members,
    selectedProjectId,
    loading: crud.loading,
    error: crud.error,
    form: crud.form,
    memberForm,
    fetch,
    create,
    fetchMembers,
    addRepository,
    removeRepository,
  }
})
