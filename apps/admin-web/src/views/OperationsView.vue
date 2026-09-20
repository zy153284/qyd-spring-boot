<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { accountApi, contentApi, riskApi, settlementApi } from '@/api/modules'
import type { Account, AuditLog, ContentItem, RiskBlacklist, RiskCheck, RiskRule, Settlement } from '@/types'

const props = defineProps<{ domain: 'accounts' | 'content' | 'settlement' | 'risk' }>()
const loading = ref(false)
const accounts = ref<Account[]>([]); const contents = ref<ContentItem[]>([]); const settlements = ref<Settlement[]>([])
const blacklists = ref<RiskBlacklist[]>([]); const rules = ref<RiskRule[]>([]); const checks = ref<RiskCheck[]>([]); const audits = ref<AuditLog[]>([])
const title = computed(() => ({ accounts: '账号与权限', content: '内容运营', settlement: '结算管理', risk: '风控中心' })[props.domain])
const accountForm = reactive({ username: '', password: '', role: 'OPERATOR' })
const contentForm = reactive({ type: 'NEWS', title: '', summary: '', body: '' })
const settlementForm = reactive({ venueId: '', periodStart: '', periodEnd: '' })
const blacklistForm = reactive({ subjectType: 'USER_ID', subjectValue: '', reason: '', active: true })
const ruleForm = reactive({ name: '', code: '', expression: 'amount>=1000', level: 'HIGH', enabled: true })
const orderId = ref('')
const transitions = ['DRAFT', 'CONFIRMED', 'FROZEN', 'PAYING', 'PAID', 'COMPLETED']

async function load(): Promise<void> {
  loading.value = true
  try {
    if (props.domain === 'accounts') accounts.value = await accountApi.list()
    if (props.domain === 'content') contents.value = await contentApi.list()
    if (props.domain === 'settlement') settlements.value = await settlementApi.list()
    if (props.domain === 'risk') {
      ;[blacklists.value, rules.value, checks.value, audits.value] = await Promise.all([
        riskApi.blacklists(), riskApi.rules(), riskApi.checks(), riskApi.audit().catch(() => []),
      ])
    }
  } finally { loading.value = false }
}
async function createAccount(): Promise<void> { await accountApi.create(accountForm); Object.assign(accountForm, { username: '', password: '', role: 'OPERATOR' }); ElMessage.success('账号已创建'); await load() }
async function toggleAccount(row: Account): Promise<void> { await accountApi.update(row.id, { role: row.role, enabled: !row.enabled }); await load() }
async function createContent(): Promise<void> { await contentApi.create(contentForm); Object.assign(contentForm, { type: 'NEWS', title: '', summary: '', body: '' }); ElMessage.success('草稿已创建'); await load() }
async function contentAction(row: ContentItem): Promise<void> { if (row.status === 'DRAFT' || row.status === 'OFFLINE') await contentApi.publish(row.id); else await contentApi.offline(row.id); await load() }
async function generateSettlement(): Promise<void> { await settlementApi.generate(settlementForm); ElMessage.success('结算单已生成'); await load() }
async function advance(row: Settlement): Promise<void> { const next = transitions[transitions.indexOf(row.status) + 1]; if (next) { await settlementApi.transition(row.id, next); await load() } }
async function createBlacklist(): Promise<void> { await riskApi.createBlacklist({ ...blacklistForm }); Object.assign(blacklistForm, { subjectValue: '', reason: '' }); await load() }
async function createRule(): Promise<void> { await riskApi.createRule({ ...ruleForm }); Object.assign(ruleForm, { name: '', code: '' }); await load() }
async function checkOrder(): Promise<void> { if (orderId.value) { await riskApi.check(orderId.value); orderId.value = ''; await load() } }
watch(() => props.domain, load); onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="page-header"><div><h2>{{ title }}</h2><p class="muted">数据直接来自后端扩展领域接口</p></div><el-button @click="load">刷新</el-button></div>

    <template v-if="domain === 'accounts'">
      <el-card class="form-card"><el-form inline>
        <el-form-item label="用户名"><el-input v-model="accountForm.username" /></el-form-item>
        <el-form-item label="初始密码"><el-input v-model="accountForm.password" type="password" show-password /></el-form-item>
        <el-form-item label="角色"><el-select v-model="accountForm.role" style="width:180px"><el-option v-for="role in ['CUSTOMER','PLATFORM_ADMIN','OPERATOR','FINANCE','VENUE_ADMIN','VENUE_STAFF']" :key="role" :value="role" /></el-select></el-form-item>
        <el-button type="primary" :disabled="!accountForm.username || accountForm.password.length < 8" @click="createAccount">新建账号</el-button>
      </el-form></el-card>
      <el-table :data="accounts"><el-table-column prop="username" label="用户名" /><el-table-column prop="role" label="角色" /><el-table-column label="权限"><template #default="{ row }">{{ row.permissions.join(', ') }}</template></el-table-column><el-table-column label="状态"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column><el-table-column label="操作"><template #default="{ row }"><el-button link type="primary" @click="toggleAccount(row)">{{ row.enabled ? '停用' : '启用' }}</el-button></template></el-table-column></el-table>
    </template>

    <template v-else-if="domain === 'content'">
      <el-card class="form-card"><el-form inline>
        <el-form-item label="类型"><el-select v-model="contentForm.type" style="width:140px"><el-option label="公告" value="NOTICE" /><el-option label="资讯" value="NEWS" /><el-option label="广告" value="ADVERTISEMENT" /></el-select></el-form-item>
        <el-form-item label="标题"><el-input v-model="contentForm.title" /></el-form-item><el-form-item label="摘要"><el-input v-model="contentForm.summary" /></el-form-item>
        <el-form-item label="正文"><el-input v-model="contentForm.body" type="textarea" /></el-form-item><el-button type="primary" :disabled="!contentForm.title || !contentForm.body" @click="createContent">保存草稿</el-button>
      </el-form></el-card>
      <el-table :data="contents"><el-table-column prop="type" label="类型" /><el-table-column prop="title" label="标题" /><el-table-column prop="status" label="状态" /><el-table-column prop="publishedAt" label="发布时间" /><el-table-column label="操作"><template #default="{ row }"><el-button link type="primary" @click="contentAction(row)">{{ row.status === 'PUBLISHED' ? '下线' : '发布' }}</el-button></template></el-table-column></el-table>
    </template>

    <template v-else-if="domain === 'settlement'">
      <el-card class="form-card"><el-form inline>
        <el-form-item label="场馆 ID"><el-input v-model="settlementForm.venueId" /></el-form-item>
        <el-form-item label="周期开始"><el-date-picker v-model="settlementForm.periodStart" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss[Z]" /></el-form-item>
        <el-form-item label="周期结束"><el-date-picker v-model="settlementForm.periodEnd" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss[Z]" /></el-form-item>
        <el-button type="primary" :disabled="!settlementForm.venueId || !settlementForm.periodStart || !settlementForm.periodEnd" @click="generateSettlement">生成结算单</el-button>
        <el-button @click="settlementApi.syncAdjustments().then(load)">同步退款调整</el-button>
      </el-form></el-card>
      <el-table :data="settlements"><el-table-column prop="statementNo" label="结算单号" /><el-table-column prop="venueId" label="场馆" /><el-table-column prop="grossAmount" label="应结" /><el-table-column prop="adjustmentAmount" label="调整" /><el-table-column prop="netAmount" label="净额" /><el-table-column prop="status" label="状态" /><el-table-column label="操作"><template #default="{ row }"><el-button link type="primary" :disabled="row.status === 'COMPLETED'" @click="advance(row)">推进状态</el-button></template></el-table-column></el-table>
    </template>

    <template v-else>
      <el-row :gutter="16"><el-col :md="12"><el-card class="form-card"><template #header>新增黑名单</template><el-form>
        <el-form-item label="主体类型"><el-input v-model="blacklistForm.subjectType" /></el-form-item><el-form-item label="主体值"><el-input v-model="blacklistForm.subjectValue" /></el-form-item><el-form-item label="原因"><el-input v-model="blacklistForm.reason" /></el-form-item><el-button type="primary" @click="createBlacklist">保存</el-button>
      </el-form></el-card></el-col><el-col :md="12"><el-card class="form-card"><template #header>新增金额规则</template><el-form>
        <el-form-item label="名称"><el-input v-model="ruleForm.name" /></el-form-item><el-form-item label="代码"><el-input v-model="ruleForm.code" /></el-form-item><el-form-item label="表达式"><el-input v-model="ruleForm.expression" /></el-form-item><el-button type="primary" @click="createRule">保存</el-button>
      </el-form></el-card></el-col></el-row>
      <el-card class="form-card"><el-input v-model="orderId" placeholder="输入订单 ID 执行风险检查" style="width:360px" /><el-button type="primary" @click="checkOrder">检查订单</el-button></el-card>
      <el-tabs><el-tab-pane label="黑名单"><el-table :data="blacklists"><el-table-column prop="subjectType" label="类型" /><el-table-column prop="subjectValue" label="主体" /><el-table-column prop="reason" label="原因" /></el-table></el-tab-pane>
        <el-tab-pane label="规则"><el-table :data="rules"><el-table-column prop="code" label="代码" /><el-table-column prop="expression" label="表达式" /><el-table-column prop="level" label="等级" /></el-table></el-tab-pane>
        <el-tab-pane label="检查记录"><el-table :data="checks"><el-table-column prop="orderId" label="订单" /><el-table-column prop="decision" label="决策" /><el-table-column prop="matchedRules" label="命中规则" /></el-table></el-tab-pane>
        <el-tab-pane label="审计日志"><el-table :data="audits"><el-table-column prop="actorId" label="操作人" /><el-table-column prop="action" label="操作" /><el-table-column prop="resourceType" label="资源" /><el-table-column prop="detail" label="详情" /></el-table></el-tab-pane>
      </el-tabs>
    </template>
  </div>
</template>

<style scoped>.form-card { margin-bottom: 18px }.form-card :deep(.el-form-item) { margin-bottom: 12px }.page-header { margin-bottom: 16px }</style>
