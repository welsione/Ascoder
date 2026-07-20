export type RuntimeSettingValueType = 'INT' | 'LONG' | 'BOOLEAN' | 'STRING' | 'DOUBLE'

export type RuntimeSettingCategory = 'agent' | 'codegraph' | 'git'

export type RuntimeSetting = {
  key: string
  value: string | null
  defaultValue: string
  valueType: RuntimeSettingValueType
  category: RuntimeSettingCategory
  description: string
  overridden: boolean
}

export type UpdateRuntimeSettingRequest = {
  value: string
}