export type Option = {
  label: string
  value: string
  description: string
}

export const roleOptions: Option[] = [
  { label: '开发人员', value: 'developer', description: '技术视角：完整代码证据、调用链和实现细节。' },
  { label: '产品经理', value: 'product_manager', description: '业务视角：功能描述与业务流程为主，代码仅作证据引用。' },
  { label: '测试', value: 'tester', description: '测试视角：可测试点与边界条件为主，代码仅保留关键路径。' },
]

export const exampleQuestions = [
  '用户登录后系统做了哪些事情？',
  '仓库索引的完整流程是怎样的？',
  'Skill 和 MCP Server 在回答问题时分别起什么作用？',
]
