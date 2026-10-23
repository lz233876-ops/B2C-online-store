<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { aftersaleApi } from '../api'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, status: null })

const statusOptions = [
  { value: 0, label: '待审核', type: 'warning' },
  { value: 1, label: '已同意', type: 'primary' },
  { value: 2, label: '已拒绝', type: 'info' },
  { value: 3, label: '退货中', type: '' },
  { value: 4, label: '已收货', type: 'success' },
  { value: 5, label: '退款完成', type: 'success' },
  { value: 6, label: '已关闭', type: 'info' }
]

const auditVisible = ref(false)
const auditForm = reactive({ id: null, no: '', approve: true, remark: '' })
const auditing = ref(false)

async function load() {
  loading.value = true
  try {
    const params = { pageNum: query.pageNum, pageSize: query.pageSize }
    if (query.status !== null && query.status !== '' && query.status !== undefined) {
      params.status = query.status
    }
    const page = await aftersaleApi.page(params)
    rows.value = page.records || []
    total.value = page.total || 0
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function statusMeta(code) {
  return statusOptions.find((s) => s.value === code) || { label: '未知', type: 'info' }
}

function openAudit(row) {
  auditForm.id = row.id
  auditForm.no = row.aftersaleNo
  auditForm.approve = true
  auditForm.remark = ''
  auditVisible.value = true
}

async function submitAudit() {
  auditing.value = true
  try {
    await aftersaleApi.audit(auditForm.id, {
      approve: auditForm.approve,
      remark: auditForm.remark || (auditForm.approve ? '同意' : '审核拒绝')
    })
    ElMessage.success(
      auditForm.approve
        ? '审核通过（仅退款会立即退款；退货退款进入退货中）'
        : '已拒绝，订单已还原到售后前状态'
    )
    auditVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    auditing.value = false
  }
}

async function confirmReceive(row) {
  try {
    await aftersaleApi.confirmReceive(row.id)
    ElMessage.success('已确认收货并退款（退货退款会回补库存）')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function yuan(fen) {
  return fen == null ? '-' : (fen / 100).toFixed(2)
}

onMounted(load)
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 150px"
                 @change="query.pageNum = 1; load()">
        <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
      <el-button @click="load">刷新</el-button>
      <div class="hint">
        ⭐ 仅退款不回补库存；退货退款（确认收货后）才回补 —— 见 docs/02 坑 37
      </div>
    </div>

    <el-table v-loading="loading" :data="rows" stripe style="width: 100%">
      <el-table-column prop="aftersaleNo" label="售后单号" width="240" show-overflow-tooltip />
      <el-table-column prop="orderNo" label="订单号" width="240" show-overflow-tooltip />
      <el-table-column prop="itemName" label="商品" min-width="160" show-overflow-tooltip />
      <el-table-column label="类型" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="row.type === 2 ? 'warning' : 'info'" effect="plain">
            {{ row.typeDesc }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="申请金额（元）" width="130">
        <template #default="{ row }">{{ yuan(row.refundAmount) }}</template>
      </el-table-column>
      <el-table-column label="实际退款（元）" width="130">
        <template #default="{ row }">
          {{ row.actualRefundAmount > 0 ? yuan(row.actualRefundAmount) : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="statusMeta(row.status).type" effect="plain">
            {{ row.statusDesc || statusMeta(row.status).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="申请时间" width="180" />
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 0" link type="primary" @click="openAudit(row)">审核</el-button>
          <el-button v-if="row.status === 3" link type="success" @click="confirmReceive(row)">
            确认收货并退款
          </el-button>
          <span v-if="row.status !== 0 && row.status !== 3" style="color: #c0c4cc; font-size: 12px">-</span>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="query.pageSize"
      :current-page="query.pageNum"
      @current-change="(p) => { query.pageNum = p; load() }"
    />

    <el-dialog v-model="auditVisible" title="售后审核" width="460px">
      <el-form label-width="90px">
        <el-form-item label="售后单号"><span style="font-size: 12px">{{ auditForm.no }}</span></el-form-item>
        <el-form-item label="审核结果">
          <el-radio-group v-model="auditForm.approve">
            <el-radio :value="true">同意</el-radio>
            <el-radio :value="false">拒绝</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核意见">
          <el-input v-model="auditForm.remark" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
        <el-alert
          v-if="auditForm.approve"
          type="info" :closable="false" show-icon
          title="仅退款：立即完成退款；退货退款：进入「退货中」，等买家寄回后确认收货再退款并回补库存"
        />
        <el-alert
          v-else
          type="warning" :closable="false" show-icon
          title="拒绝后售后关闭，订单会还原到售后前的状态"
        />
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button type="primary" :loading="auditing" @click="submitAudit">提交审核</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.hint { color: #909399; font-size: 12px; margin-left: auto; }
.pager { margin-top: 14px; justify-content: flex-end; }
.el-alert { margin-top: 8px; }
</style>
