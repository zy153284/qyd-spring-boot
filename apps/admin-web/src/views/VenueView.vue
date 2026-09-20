<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { venueApi } from '@/api/modules'
import { useAuthStore } from '@/stores/auth'
import type { Venue } from '@/types'

const auth = useAuthStore()
const rows = ref<Venue[]>([])
const loading = ref(false)
const error = ref('')
const status = ref<'' | Venue['status']>('')
const dialog = ref(false)
const editingId = ref('')
const saving = ref(false)
const form = reactive<{ name: string; address: string; status: Venue['status'] }>({ name: '', address: '', status: 'ACTIVE' })
const scopedRows = computed(() => {
  if (auth.user?.dataScope !== 'VENUE' || !auth.user.venueIds.length) return rows.value
  return rows.value.filter((row) => auth.user?.venueIds.includes(row.id))
})

async function load(): Promise<void> {
  loading.value = true; error.value = ''
  try { rows.value = await venueApi.list(status.value || undefined) }
  catch { error.value = '场馆列表加载失败' } finally { loading.value = false }
}
function open(row?: Venue): void {
  editingId.value = row?.id ?? ''; form.name = row?.name ?? ''; form.address = row?.address ?? ''; form.status = row?.status ?? 'ACTIVE'; dialog.value = true
}
async function save(): Promise<void> {
  if (!form.name.trim()) { ElMessage.warning('请输入场馆名称'); return }
  saving.value = true
  try {
    const data = { name: form.name, address: form.address || null, status: form.status }
    if (editingId.value) await venueApi.update(editingId.value, data); else await venueApi.create(data)
    ElMessage.success('保存成功'); dialog.value = false; await load()
  } finally { saving.value = false }
}
async function remove(row: Venue): Promise<void> {
  await ElMessageBox.confirm(`删除场馆“${row.name}”后无法恢复，是否继续？`, '二次确认', { type: 'warning', confirmButtonText: '确认删除' })
  await venueApi.remove(row.id); ElMessage.success('删除成功'); await load()
}
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header"><div><h2>场馆管理</h2><p class="muted">维护场馆基础资料和营业状态</p></div><el-button v-permission="'merchant:venue:create'" type="primary" @click="open()">新增场馆</el-button></div>
    <el-alert class="danger-note" type="info" :closable="false" show-icon title="前端按令牌中的 venueIds 辅助过滤；后端必须再次校验场馆数据范围。" />
    <div class="panel">
      <div class="toolbar"><el-select v-model="status" clearable placeholder="全部状态" style="width: 160px" @change="load"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="INACTIVE" /></el-select><el-button @click="load">刷新</el-button></div>
      <el-alert v-if="error" type="error" :title="error" show-icon><el-button text @click="load">重试</el-button></el-alert>
      <el-table v-else v-loading="loading" :data="scopedRows" empty-text="暂无场馆">
        <el-table-column prop="name" label="场馆名称" min-width="180" /><el-table-column prop="address" label="地址" min-width="240" />
        <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="180" fixed="right"><template #default="{ row }"><el-button v-permission="'merchant:venue:update'" link type="primary" @click="open(row)">编辑</el-button><el-button v-permission="'merchant:venue:approve'" link type="danger" @click="remove(row)">删除</el-button></template></el-table-column>
      </el-table>
    </div>
    <el-dialog v-model="dialog" :title="editingId ? '编辑场馆' : '新增场馆'" width="min(520px, 94vw)">
      <el-form label-position="top"><el-form-item label="场馆名称" required><el-input v-model="form.name" maxlength="200" /></el-form-item><el-form-item label="地址"><el-input v-model="form.address" maxlength="500" /></el-form-item><el-form-item label="状态"><el-radio-group v-model="form.status"><el-radio value="ACTIVE">启用</el-radio><el-radio value="INACTIVE">停用</el-radio></el-radio-group></el-form-item></el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>
