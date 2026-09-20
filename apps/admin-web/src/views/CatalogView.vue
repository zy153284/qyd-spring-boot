<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { catalogApi, venueApi } from '@/api/modules'
import type { Category, Resource, Sku, Venue } from '@/types'

const tab = ref('categories')
const loading = ref(false)
const error = ref('')
const categories = ref<Category[]>([]), resources = ref<Resource[]>([]), skus = ref<Sku[]>([]), venues = ref<Venue[]>([])
const filterVenue = ref(''), filterResource = ref('')
const dialog = ref(false), saving = ref(false), editingId = ref('')
const categoryForm = reactive({ name: '', description: '' })
const resourceForm = reactive({ venueId: '', categoryId: '', name: '', description: '', active: true })
const skuForm = reactive({ resourceId: '', name: '', price: 0, currency: 'CNY', active: true })

async function load(): Promise<void> {
  loading.value = true; error.value = ''
  try {
    const [c, r, s, v] = await Promise.all([catalogApi.categories(), catalogApi.resources(filterVenue.value || undefined), catalogApi.skus(filterResource.value || undefined), venueApi.list()])
    categories.value = c; resources.value = r; skus.value = s; venues.value = v
  } catch { error.value = '运动目录加载失败' } finally { loading.value = false }
}
function open(row?: Category | Resource | Sku): void {
  editingId.value = row?.id ?? ''
  if (tab.value === 'categories') Object.assign(categoryForm, { name: 'name' in (row ?? {}) ? (row as Category).name : '', description: (row as Category | undefined)?.description ?? '' })
  if (tab.value === 'resources') Object.assign(resourceForm, row ?? { venueId: '', categoryId: '', name: '', description: '', active: true })
  if (tab.value === 'skus') Object.assign(skuForm, row ?? { resourceId: '', name: '', price: 0, currency: 'CNY', active: true })
  dialog.value = true
}
async function save(): Promise<void> {
  saving.value = true
  try {
    if (tab.value === 'categories') {
      const data = { name: categoryForm.name, description: categoryForm.description || null }
      editingId.value ? await catalogApi.updateCategory(editingId.value, data) : await catalogApi.createCategory(data)
    } else if (tab.value === 'resources') {
      const data = { ...resourceForm, description: resourceForm.description || null }
      editingId.value ? await catalogApi.updateResource(editingId.value, data) : await catalogApi.createResource(data)
    } else {
      editingId.value ? await catalogApi.updateSku(editingId.value, skuForm) : await catalogApi.createSku(skuForm)
    }
    ElMessage.success('保存成功'); dialog.value = false; await load()
  } finally { saving.value = false }
}
async function remove(row: Category | Resource | Sku): Promise<void> {
  await ElMessageBox.confirm(`确认删除“${row.name}”？该操作不可恢复。`, '二次确认', { type: 'warning' })
  if (tab.value === 'categories') await catalogApi.removeCategory(row.id)
  else if (tab.value === 'resources') await catalogApi.removeResource(row.id)
  else await catalogApi.removeSku(row.id)
  ElMessage.success('删除成功'); await load()
}
function nameOf(list: { id: string; name: string }[], id: string): string { return list.find((item) => item.id === id)?.name ?? id }
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header"><div><h2>运动目录</h2><p class="muted">分类、场馆资源和销售 SKU</p></div><el-button v-permission="tab === 'categories' ? 'catalog:category:manage' : 'catalog:product:create'" type="primary" @click="open()">新增{{ tab === 'categories' ? '分类' : tab === 'resources' ? '资源' : 'SKU' }}</el-button></div>
    <div class="panel">
      <el-tabs v-model="tab">
        <el-tab-pane label="运动分类" name="categories" /><el-tab-pane label="场馆资源" name="resources" /><el-tab-pane label="销售 SKU" name="skus" />
      </el-tabs>
      <div class="toolbar" v-if="tab === 'resources'"><el-select v-model="filterVenue" clearable filterable placeholder="按场馆筛选" @change="load"><el-option v-for="v in venues" :key="v.id" :label="v.name" :value="v.id" /></el-select></div>
      <div class="toolbar" v-if="tab === 'skus'"><el-select v-model="filterResource" clearable filterable placeholder="按资源筛选" @change="load"><el-option v-for="r in resources" :key="r.id" :label="r.name" :value="r.id" /></el-select></div>
      <el-alert v-if="error" type="error" :title="error" show-icon><el-button text @click="load">重试</el-button></el-alert>
      <el-table v-else-if="tab === 'categories'" v-loading="loading" :data="categories" empty-text="暂无分类">
        <el-table-column prop="name" label="分类名称" /><el-table-column prop="description" label="说明" /><el-table-column label="操作" width="150"><template #default="{ row }"><el-button link type="primary" @click="open(row)">编辑</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></template></el-table-column>
      </el-table>
      <el-table v-else-if="tab === 'resources'" v-loading="loading" :data="resources" empty-text="暂无资源">
        <el-table-column prop="name" label="资源名称" /><el-table-column label="场馆"><template #default="{ row }">{{ nameOf(venues, row.venueId) }}</template></el-table-column><el-table-column label="分类"><template #default="{ row }">{{ nameOf(categories, row.categoryId) }}</template></el-table-column><el-table-column label="状态"><template #default="{ row }"><el-tag :type="row.active ? 'success' : 'info'">{{ row.active ? '启用' : '停用' }}</el-tag></template></el-table-column><el-table-column label="操作" width="150"><template #default="{ row }"><el-button link type="primary" @click="open(row)">编辑</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></template></el-table-column>
      </el-table>
      <el-table v-else v-loading="loading" :data="skus" empty-text="暂无 SKU">
        <el-table-column prop="name" label="SKU 名称" /><el-table-column label="资源"><template #default="{ row }">{{ nameOf(resources, row.resourceId) }}</template></el-table-column><el-table-column prop="price" label="价格"><template #default="{ row }">{{ row.currency }} {{ row.price }}</template></el-table-column><el-table-column label="状态"><template #default="{ row }"><el-tag :type="row.active ? 'success' : 'info'">{{ row.active ? '启用' : '停用' }}</el-tag></template></el-table-column><el-table-column label="操作" width="150"><template #default="{ row }"><el-button link type="primary" @click="open(row)">编辑</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></template></el-table-column>
      </el-table>
    </div>
    <el-dialog v-model="dialog" :title="editingId ? '编辑' : '新增'" width="min(560px, 94vw)">
      <el-form v-if="tab === 'categories'" label-position="top"><el-form-item label="名称" required><el-input v-model="categoryForm.name" /></el-form-item><el-form-item label="说明"><el-input v-model="categoryForm.description" type="textarea" /></el-form-item></el-form>
      <el-form v-else-if="tab === 'resources'" label-position="top"><el-form-item label="场馆" required><el-select v-model="resourceForm.venueId" filterable><el-option v-for="v in venues" :key="v.id" :label="v.name" :value="v.id" /></el-select></el-form-item><el-form-item label="分类" required><el-select v-model="resourceForm.categoryId"><el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" /></el-select></el-form-item><el-form-item label="名称" required><el-input v-model="resourceForm.name" /></el-form-item><el-form-item label="说明"><el-input v-model="resourceForm.description" /></el-form-item><el-switch v-model="resourceForm.active" active-text="启用" /></el-form>
      <el-form v-else label-position="top"><el-form-item label="资源" required><el-select v-model="skuForm.resourceId" filterable><el-option v-for="r in resources" :key="r.id" :label="r.name" :value="r.id" /></el-select></el-form-item><el-form-item label="名称" required><el-input v-model="skuForm.name" /></el-form-item><el-form-item label="价格" required><el-input-number v-model="skuForm.price" :min="0" :precision="2" /><el-input v-model="skuForm.currency" maxlength="3" style="width: 90px; margin-left: 8px" /></el-form-item><el-switch v-model="skuForm.active" active-text="启用" /></el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>
