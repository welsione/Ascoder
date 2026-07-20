import { defineStore } from 'pinia'
import { computed } from 'vue'
import * as api from '../services/mcpServerApi'
import type { McpServer, McpTransport } from '../types/mcpServer'
import { useCrudStore } from '../composables/useCrudStore'

interface McpServerForm {
  name: string
  description: string
  transport: McpTransport
  command: string
  argumentsJson: string
  endpointUrl: string
  headersJson: string
  queryParamsJson: string
  enabledToolsJson: string
  disabledToolsJson: string
  timeoutSeconds: number
  enabled: boolean
}

export const useMcpServerStore = defineStore('mcpServer', () => {
  const crud = useCrudStore<McpServer, McpServerForm>({
    initialForm: () => ({
      name: '',
      description: '',
      transport: 'STDIO',
      command: '',
      argumentsJson: '[]',
      endpointUrl: '',
      headersJson: '{}',
      queryParamsJson: '{}',
      enabledToolsJson: '[]',
      disabledToolsJson: '[]',
      timeoutSeconds: 30,
      enabled: false,
    }),
  })

  const enabledCount = computed(() =>
    crud.items.value.filter((s) => s.enabled).length
  )

  async function fetch() {
    await crud.fetchAll(() => api.getAll(), '无法加载 MCP Server')
  }

  async function create() {
    return crud.create(
      (form) =>
        api.create({
          ...form,
          name: form.name.trim(),
          description: form.description.trim(),
          command: form.command.trim(),
          endpointUrl: form.endpointUrl.trim(),
        }),
      (form) => {
        if (!form.name.trim()) return '请填写 MCP Server 名称'
        if (form.transport === 'STDIO' && !form.command.trim()) {
          return 'STDIO MCP Server 必须填写 command'
        }
        if (form.transport !== 'STDIO' && !form.endpointUrl.trim()) {
          return 'HTTP/SSE MCP Server 必须填写 endpointUrl'
        }
        return null
      },
      '无法创建 MCP Server',
      async () => {
        crud.resetForm()
        await fetch()
      }
    ) as Promise<McpServer | null>
  }

  async function toggleEnabled(serverId: number, enabled: boolean) {
    crud.error.value = ''
    try {
      const updated = await api.updateEnabled(serverId, enabled)
      const idx = crud.items.value.findIndex((s) => s.id === serverId)
      if (idx !== -1) {
        crud.items.value[idx] = updated
      }
    } catch (err) {
      crud.error.value = err instanceof Error ? err.message : '无法更新 MCP Server 状态'
    }
  }

  return {
    servers: crud.items,
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
