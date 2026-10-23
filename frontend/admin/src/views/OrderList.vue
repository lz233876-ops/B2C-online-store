<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { orderApi } from '../api'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, status: null })

const statusOptions = [
  { value: 0, label: '待付款', type: 'warning' },
  { value: 1, label: '待发货', type: 'primary' },
  { value: 2, label: '待收货', type: '' },
  { value: 3, label: '已完成', type: 'success' },
  { value: 4, label: '已取消', type: 'info' },
  { value: 5, label: '售后中', type: 'danger' },
  { value: 6, label: '已退款', type: 'info' }
]

const deliverVisible = ref(false)
const deliverForm = reactive({ id: null, orderNo: '', deliveryCompany: '顺丰速运', deliveryNo: '' })
const delivering = ref(false)

async function load() {
  loading.value = true
  try {
    const params = { pageNum: query.pageNum, pageSize: query.pageSize }
    if (query.status !== null && query.status !== '' && query.status !== undefined) {
      params.status = query.status
    }
    const page = await orderApi.page(params)
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

function openDeliver(row) {
  deliverForm.id = row.id
  deliverForm.orderNo = row.orderNo
  deliverForm.deliveryCompany = '顺丰速运'
  deliverForm.deliveryNo = ''
  deliverVisible.value = true
}

async function submitDeliver() {
  if (!deliverForm.deliveryNo) {
    ElMessage.warning('请填写物流单号')
    return
  }
  delivering.value = true
  try {
    await orderApi.deliver(deliverForm.id, {
      deliveryCompany: deliverForm.deliveryCompany,
      deliveryNo: deliverForm.deliveryNo
    })
    ElMessage.success('发货成功')
    deliverVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    delivering.value = false
  }
}

async function closeTimeout() {
  try {
    const n = await orderApi.closeTimeout()
    ElMessage.success(`已执行，关闭超时订单 ${n} 笔`)
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
      <el-button type="warning" plain @click="closeTimeout">触发超时关单</el-button>
      <div class="hint">「触发超时关单」= 立即扫描并关闭已超时未支付的订单</div>
    </div>

    <el-table v-loading="loading" :data="rows" stripe style="width: 100%">
      <el-table-column prop="orderNo" label="订单号" width="240" show-overflow-tooltip />
      <el-table-column prop="receiverName" label="收货人" width="100" />
      <el-table-column prop="receiverPhone" label="手机号" width="130" />
      <el-table-column label="应付（元）" width="110">
        <template #default="{ row }">{{ yuan(row.payAmount) }}</template>
      </el-table-column>
      <el-table-column prop="totalQuantity" label="件数" width="70" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="statusMeta(row.status).type" effect="plain">
            {{ row.statusDesc || statusMeta(row.status).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.orderType === 2" size="small" type="danger" effect="dark">秒杀</el-tag>
          <span v-else style="color: #909399">普通</span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="下单时间" width="180" />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 1" link type="primary" @click="openDeliver(row)">发货</el-button>
          <span v-else style="color: #c0c4cc; font-size: 12px">-</span>
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

    <el-dialog v-model="deliverVisible" title="订单发货" width="420px">
      <el-form label-width="90px">
        <el-form-item label="订单号"><span style="font-size: 12px">{{ deliverForm.orderNo }}</span></el-form-item>
        <el-form-item label="物流公司">
          <el-input v-model="deliverForm.deliveryCompany" />
        </el-form-item>
        <el-form-item label="物流单号">
          <el-input v-model="deliverForm.deliveryNo" placeholder="必填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deliverVisible = false">取消</el-button>
        <el-button type="primary" :loading="delivering" @click="submitDeliver">确认发货</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.hint { color: #909399; font-size: 12px; }
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
