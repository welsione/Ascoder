<script setup lang="ts">
defineProps<{
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'
}>()

const typeMap = {
  PENDING: 'info',
  RUNNING: 'warning',
  SUCCEEDED: 'success',
  FAILED: 'danger',
} as const
</script>

<template>
  <el-tag size="small" effect="plain" :type="typeMap[status]">
    {{ status }}
  </el-tag>
</template>
