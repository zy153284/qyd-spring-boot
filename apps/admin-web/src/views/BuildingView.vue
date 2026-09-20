<script setup lang="ts">
defineProps<{ feature: string }>()
</script>
<template>
  <div class="page"><div class="panel state-box"><el-result icon="info" :title="`${feature} · 功能建设中`" sub-title="后端尚未提供可用接口，当前页面不展示模拟结果。"><template #extra><el-tag type="warning">等待后端能力</el-tag></template></el-result></div></div>
</template>
