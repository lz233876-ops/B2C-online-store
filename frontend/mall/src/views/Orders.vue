<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import { orderApi, payApi } from '../api'
import { clearSession, getUser } from '../api/http'

const router = useRouter()
const loading = ref(true)
const rows = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10 })
const paying = ref(null)
const user = ref(getUser() || {})

const STATUS = {
  0: { text: '待付款', type: 'warning' },
  1: { text: '待发货', type: 'primary' },
  2: { text: '待收货', type: '' },
  3: { text: '已完成', type: 'success' },
  4: { text: '已取消', type: 'default' },
  5: { text: '售后中', type: 'danger' },
  6: { text: '已退款', type: 'default' }
}

async function load(reset = false) {
  if (reset) { query.pageNum = 1; rows.value = [] }
  loading.value = true
  try {
    const page = await orderApi.page({ pageNum: query.pageNum, pageSize: query.pageSize })
    rows.value = rows.value.concat(page.records || [])
    total.value = page.total || 0
    query.pageNum += 1
  } catch (e) {
    showFailToast(e.message)
  } finally {
    loading.value = false
  }
}

function yuan(n) {
  return n == null ? '0.00' : (n / 100).toFixed(2)
}

/**
 * 支付：创建支付单 → 调模拟收银台（扮演渠道侧）完成付款。
 * 真实接入支付宝时，第一步会返回渠道的支付链接/二维码，用户在那里付款，
 * 渠道再异步回调我们的 /api/pay/notify —— 这里只是把两步都放在前端点了。
 */
async function pay(order) {
  paying.value = order.id
  try {
    const payment = await payApi.create(order.orderNo, 1)
    const result = await payApi.mockPay(payment.paymentNo)
    if (result.success) {
      showSuccessToast(`支付成功 ¥${(result.message ? order.payAmount / 100 : 0).toFixed(2)}`)
    } else {
      showFailToast(`支付未成功：${result.message}`)
    }
    await load(true)
  } catch (e) {
    showFailToast(e.message)
  } finally {
    paying.value = null
  }
}

async function cancel(order) {
  try {
    await showConfirmDialog({ title: '取消订单', message: '确定要取消这笔订单吗？' })
  } catch {
    return
  }
  try {
    await orderApi.cancel(order.id, '用户在前端取消')
    showSuccessToast('已取消')
    await load(true)
  } catch (e) {
    showFailToast(e.message)
  }
}

async function receive(order) {
  try {
    await showConfirmDialog({ title: '确认收货', message: '确认已经收到货了吗？' })
  } catch {
    return
  }
  try {
    await orderApi.receive(order.id)
    showSuccessToast('已确认收货')
    await load(true)
  } catch (e) {
    showFailToast(e.message)
  }
}

function logout() {
  clearSession()
  router.replace('/login')
}

onMounted(() => load(true))
</script>

<template>
  <div>
    <van-nav-bar title="我的订单" fixed placeholder>
      <template #right>
        <span style="font-size: 13px; color: #969799" @click="logout">{{ user.nickname || '退出' }}</span>
      </template>
    </van-nav-bar>

    <van-loading v-if="loading && !rows.length" style="padding: 80px 0; text-align: center" vertical>
      加载中…
    </van-loading>

    <van-empty v-if="!loading && !rows.length" description="还没有订单，去首页下单试试" />

    <div v-for="o in rows" :key="o.id" class="order">
      <div class="head">
        <span class="no">{{ o.orderNo }}</span>
        <van-tag :type="(STATUS[o.status] || {}).type" plain>{{ o.statusDesc }}</van-tag>
      </div>
      <div class="body">
        <div class="line"><span>收货人</span><b>{{ o.receiverName }} {{ o.receiverPhone }}</b></div>
        <div class="line"><span>地址</span><b>{{ o.receiverFullAddress }}</b></div>
        <div class="line"><span>件数</span><b>{{ o.totalQuantity }} 件</b></div>
        <div class="line"><span>应付</span><b class="amount">¥{{ yuan(o.payAmount) }}</b></div>
        <div v-if="o.deliveryNo" class="line"><span>物流</span><b>{{ o.deliveryCompany }} {{ o.deliveryNo }}</b></div>
        <div v-if="o.orderType === 2" class="line"><span>类型</span><b style="color: #ee0a24">秒杀订单</b></div>
      </div>
      <div class="actions">
        <van-button v-if="o.status === 0" size="small" plain @click="cancel(o)">取消</van-button>
        <van-button v-if="o.status === 0" size="small" type="danger" :loading="paying === o.id" @click="pay(o)">
          去支付
        </van-button>
        <van-button v-if="o.status === 2" size="small" type="primary" @click="receive(o)">确认收货</van-button>
        <van-tag v-if="o.status === 1" type="primary" plain>等待商家发货（去后台发货）</van-tag>
      </div>
    </div>

    <van-button v-if="rows.length < total" block plain style="margin: 12px 0" @click="load()">
      加载更多（{{ rows.length }}/{{ total }}）
    </van-button>
  </div>
</template>

<style scoped>
.order { background: #fff; margin-bottom: 8px; padding: 12px 16px; }
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.no { font-size: 12px; color: #969799; }
.body { font-size: 13px; }
.line { display: flex; justify-content: space-between; padding: 3px 0; gap: 12px; }
.line span { color: #969799; flex: 0 0 auto; }
.line b { font-weight: 500; text-align: right; word-break: break-all; }
.amount { color: #ee0a24; font-weight: 700; }
.actions {
  display: flex; justify-content: flex-end; align-items: center; gap: 8px;
  margin-top: 10px; padding-top: 10px; border-top: 1px solid #f2f3f5;
}
</style>
