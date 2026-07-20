export type AgentToolRiskLevel = 'low' | 'medium' | 'high'

export type AgentTool = {
  id: number
  toolKey: string
  displayName: string
  groupName: string
  riskLevel: AgentToolRiskLevel
  description: string
  enabled: boolean
  builtin: boolean
  createdAt: string
  updatedAt: string
}
