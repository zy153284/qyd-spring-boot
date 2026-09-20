<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orderApi } from '@/api/modules'
import type { Order } from '@/types'

const router = useRouter()
const rows = ref<Order[]>([]), loading = ref(false), error = ref('')
const page = ref(1), size = ref(20), total = ref(0)
async function load(): Promise<void> {
  loading.value = true; error.value = ''
  try { const result = await orderApi.list(page.value - 1, size.value); rows.value = result.content; total.value = result.totalElements }
  catch { error.value = '订单列表加载失败' } finally { loading.value = false }
}
async function cancel(row: Order): Promise<void> {
  await ElMessageBox.confirm(`确认取消订单 ${row.orderNo}？库存预占将被释放。`, '二次确认', { type: 'warning', confirmButtonText: '确认取消' })
  await orderApi.cancel(row.id); ElMessage.success('订单已取消'); await load()
}
onMounted(load)
</script>
<template>
  <div class="page">
    <div class="page-header"><div><h2>订单中心</h2><p class="muted">查询订单、查看履约状态并执行允许的取消操作</p></div><el-button :loading="loading" @click="load">刷新</el-button></div>
    <el-alert class="danger-note" type="warning" :closable="false" show-icon title="当前后端列表仅返回登录主体本人的订单，尚未提供平台/场馆管理查询与筛选接口。" />
    <div class="panel">
      <el-alert v-if="error" type="error" :title="error" show-icon><el-button text @click="load">重试</el-button></el-alert>
      <el-table v-else v-loading="loading" :data="rows" empty-text="暂无订单">
        <el-table-column prop="orderNo" label="订单号" min-width="210" /><el-table-column label="金额" width="140"><template #default="{ row }">{{ row.currency }} {{ row.amount }}</template></el-table-column><el-table-column label="状态" width="150"><template #default="{ row }"><el-tag>{{ row.status }}</el-tag></template></el-table-column><el-table-column label="项目" width="90"><template #default="{ row }">{{ row.items.length }}</template></el-table-column><el-table-column label="操作" min-width="170"><template #default="{ row }"><el-button link type="primary" @click="router.push(`/orders/${row.id}`)">详情</el-button><el-button v-if="row.status === 'PENDING_PAYMENT'" v-permission="'order:order:cancel'" link type="danger" @click="cancel(row)">取消</el-button></template></el-table-column>
      </el-table>
      <el-pagination v-if="total" v-model:current-page="page" v-model:page-size="size" style="margin-top: 16px; justify-content: flex-end" layout="total, sizes, prev, pager, next" :total="total" @change="load" />
    </div>
  </div>
</template>
