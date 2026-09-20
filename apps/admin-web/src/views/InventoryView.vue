<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { catalogApi, inventoryApi } from '@/api/modules'
import type { Sku, Slot } from '@/types'

const rows = ref<Slot[]>([]), skus = ref<Sku[]>([])
const skuId = ref(''), loading = ref(false), error = ref(''), dialog = ref(false), saving = ref(false)
const form = reactive({ skuId: '', startsAt: [] as Date[], capacity: 1 })
async function load(): Promise<void> {
  loading.value = true; error.value = ''
  try { [rows.value, skus.value] = await Promise.all([inventoryApi.list(skuId.value || undefined), catalogApi.skus()]) }
  catch { error.value = '库存加载失败' } finally { loading.value = false }
}
function skuName(id: string): string { return skus.value.find((item) => item.id === id)?.name ?? id }
async function save(): Promise<void> {
  if (!form.skuId || form.startsAt.length !== 2) { ElMessage.warning('请完整填写 SKU 和时段'); return }
  await ElMessageBox.confirm('确认创建该销售时段及初始库存？创建后当前后端暂不支持修改或关闭。', '二次确认', { type: 'warning' })
  saving.value = true
  try {
    await inventoryApi.create({ skuId: form.skuId, startsAt: form.startsAt[0]!.toISOString(), endsAt: form.startsAt[1]!.toISOString(), capacity: form.capacity })
    ElMessage.success('时段创建成功'); dialog.value = false; await load()
  } finally { saving.value = false }
}
onMounted(load)
</script>
<template>
  <div class="page">
    <div class="page-header"><div><h2>时段库存</h2><p class="muted">查看容量、预占与已售库存</p></div><el-button v-permission="'inventory:slot:update'" type="primary" @click="dialog = true">新增时段</el-button></div>
    <el-alert class="danger-note" type="warning" :closable="false" show-icon title="后端当前仅支持创建和查询时段，不支持修改容量或关闭时段。" />
    <div class="panel"><div class="toolbar"><el-select v-model="skuId" clearable filterable placeholder="按 SKU 筛选" @change="load"><el-option v-for="s in skus" :key="s.id" :label="s.name" :value="s.id" /></el-select><el-button @click="load">刷新</el-button></div>
      <el-alert v-if="error" type="error" :title="error" show-icon><el-button text @click="load">重试</el-button></el-alert>
      <el-table v-else v-loading="loading" :data="rows" empty-text="暂无销售时段">
        <el-table-column label="SKU" min-width="160"><template #default="{ row }">{{ skuName(row.skuId) }}</template></el-table-column><el-table-column label="开始时间" min-width="180"><template #default="{ row }">{{ new Date(row.startsAt).toLocaleString() }}</template></el-table-column><el-table-column label="结束时间" min-width="180"><template #default="{ row }">{{ new Date(row.endsAt).toLocaleString() }}</template></el-table-column><el-table-column prop="capacity" label="容量" /><el-table-column prop="reserved" label="预占" /><el-table-column prop="sold" label="已售" /><el-table-column label="可售"><template #default="{ row }"><b>{{ row.capacity - row.reserved - row.sold }}</b></template></el-table-column>
      </el-table>
    </div>
    <el-dialog v-model="dialog" title="新增销售时段" width="min(540px, 94vw)"><el-form label-position="top"><el-form-item label="SKU" required><el-select v-model="form.skuId" filterable><el-option v-for="s in skus" :key="s.id" :label="s.name" :value="s.id" /></el-select></el-form-item><el-form-item label="起止时间" required><el-date-picker v-model="form.startsAt" type="datetimerange" start-placeholder="开始" end-placeholder="结束" /></el-form-item><el-form-item label="容量" required><el-input-number v-model="form.capacity" :min="1" /></el-form-item></el-form><template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">确认创建</el-button></template></el-dialog>
  </div>
</template>
