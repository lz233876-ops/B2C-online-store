<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import { addressApi, cartApi, orderApi } from '../api'
import { getUser } from '../api/http'

const router = useRouter()
const loading = ref(true)
const cart = ref({ items: [], selectedAmount: 0, selectedCount: 0 })
const checkedIds = ref([])
const submitting = ref(false)

const items = computed(() => cart.value.items || [])
const checkedItems = computed(() => items.value.filter((i) => checkedIds.value.includes(i.id) && !i.invalid))
const totalAmount = computed(() =>
  checkedItems.value.reduce((sum, i) => sum + (i.currentPrice || 0) * i.quantity, 0)
)
const totalYuan = computed(() => (totalAmount.value / 100).toFixed(2))

async function load() {
  loading.value = true
  try {
    cart.value = await cartApi.list()
    // 默认勾选后端标记为 selected 且有效的项
    checkedIds.value = (cart.value.items || [])
      .filter((i) => i.selected === 1 && !i.invalid)
      .map((i) => i.id)
  } catch (e) {
    showFailToast(e.message)
  } finally {
    loading.value = false
  }
}

async function changeQuantity(item, value) {
  try {
    await cartApi.updateQuantity(item.id, value)
    await load()
  } catch (e) {
    showFailToast(e.message)
    await load()
  }
}

/** 结算前把勾选状态同步给后端 —— 下单接口用的是「后端购物车里的 selected=1」，不是前端状态 */
async function syncSelection() {
  const allIds = items.value.map((i) => i.id)
  await cartApi.updateSelected(allIds, 0)
  if (checkedIds.value.length) {
    await cartApi.updateSelected(checkedIds.value, 1)
  }
}

/**
 * 兜底：演示账号可能还没填收货地址，而下单必须有地址。
 * 这里自动建一条「演示地址」，让演示不必先跳去地址管理页。
 * （真实产品当然要让用户自己填 —— 见 PROGRESS 技术债）
 */
async function ensureAddress() {
  const list = await addressApi.list()
  if (list && list.length) {
    return list.find((a) => a.defaultStatus === 1) || list[0]
  }
  const user = getUser() || {}
  const created = await addressApi.create({
    receiverName: '演示收货人',
    receiverPhone: '13800138000',
    province: '广东省',
    city: '深圳市',
    district: '南山区',
    detailAddress: '科技园路 1 号（演示自动创建）',
    tag: '演示',
    defaultStatus: 1
  })
  const after = await addressApi.list()
  return after.find((a) => a.id === created) || after[0]
}

async function checkout() {
  if (!checkedItems.value.length) {
    showFailToast('请先勾选要结算的商品')
    return
  }
  try {
    await showConfirmDialog({
      title: '确认下单',
      message: `共 ${checkedItems.value.length} 种商品，合计 ¥${totalYuan.value}`,
      confirmButtonText: '提交订单'
    })
  } catch {
    return
  }
  submitting.value = true
  try {
    await syncSelection()
    const addr = await ensureAddress()
    const order = await orderApi.submit(addr.id, '前端下单演示')
    showSuccessToast(`下单成功：${order.orderNo}`)
    router.push('/orders')
  } catch (e) {
    showFailToast(e.message)
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <van-nav-bar title="购物车" fixed placeholder />
    <van-loading v-if="loading" style="padding: 80px 0; text-align: center" vertical>加载中…</van-loading>

    <template v-else>
      <van-checkbox-group v-model="checkedIds">
        <div v-for="item in items" :key="item.id" class="row">
          <van-checkbox :name="item.id" :disabled="item.invalid" />
          <div class="thumb">{{ (item.skuName || '?').slice(0, 1) }}</div>
          <div class="info">
            <div class="name">{{ item.skuName }}</div>
            <div class="spec">{{ item.specDesc }}</div>
            <van-tag v-if="item.invalid" type="danger" plain>{{ item.invalidReason }}</van-tag>
            <div class="line">
              <span class="price">¥{{ ((item.currentPrice || 0) / 100).toFixed(2) }}</span>
              <van-stepper
                :model-value="item.quantity"
                :min="0"
                :max="item.availableStock || 1"
                @update:model-value="(v) => changeQuantity(item, v)"
              />
            </div>
          </div>
        </div>
      </van-checkbox-group>

      <van-empty v-if="items.length === 0" description="购物车是空的，去首页逛逛吧">
        <van-button type="primary" round @click="router.push('/home')">去逛逛</van-button>
      </van-empty>
    </template>

    <div v-if="items.length" class="footer">
      <div class="total">
        合计：<span>¥{{ totalYuan }}</span>
        <div class="count">已选 {{ checkedItems.length }} 种</div>
      </div>
      <van-button type="danger" :loading="submitting" @click="checkout">去结算</van-button>
    </div>
  </div>
</template>

<style scoped>
.row {
  display: flex; align-items: center; gap: 10px; padding: 12px 16px;
  background: #fff; margin-bottom: 8px;
}
.thumb {
  width: 64px; height: 64px; border-radius: 8px; flex: 0 0 64px;
  background: linear-gradient(135deg, #90caf9, #1976d2);
  color: #fff; font-size: 26px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
}
.info { flex: 1; min-width: 0; }
.name { font-size: 14px; font-weight: 600; }
.spec { font-size: 12px; color: #969799; margin: 2px 0 6px; }
.line { display: flex; align-items: center; justify-content: space-between; }
.price { color: #ee0a24; font-weight: 700; }
.footer {
  position: fixed; left: 0; right: 0; bottom: 50px; z-index: 9;
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 16px; background: #fff; border-top: 1px solid #ebedf0;
  max-width: 420px; margin: 0 auto;
}
.total { font-size: 13px; color: #646566; }
.total span { color: #ee0a24; font-size: 18px; font-weight: 700; }
.count { font-size: 12px; color: #969799; }
</style>
