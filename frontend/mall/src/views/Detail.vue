<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showFailToast, showSuccessToast } from 'vant'
import { cartApi, productApi } from '../api'
import { getToken } from '../api/http'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const detail = ref(null)
const selected = ref({})   // { attributeId: valueId }
const quantity = ref(1)
const adding = ref(false)

/** 根据已选规格找到对应的 SKU；规格没选全时为 null */
const currentSku = computed(() => {
  if (!detail.value) return null
  const chosen = Object.values(selected.value).filter((v) => v != null)
  if (chosen.length !== (detail.value.specAttributes || []).length) return null
  return (detail.value.skus || []).find((sku) => {
    const ids = (sku.specValues || []).map((s) => s.valueId)
    return chosen.every((v) => ids.includes(v)) && ids.length === chosen.length
  }) || null
})

const canBuy = computed(() => !!currentSku.value && (currentSku.value.availableStock || 0) > 0)
const displayPrice = computed(() => {
  if (currentSku.value) return currentSku.value.priceYuan
  return detail.value?.priceYuan || '0.00'
})

async function load() {
  loading.value = true
  try {
    detail.value = await productApi.detail(route.params.spuId)
    // 默认选中第一个规格值，减少演示时的点击次数
    for (const attr of detail.value.specAttributes || []) {
      if (attr.values?.length) selected.value[attr.attributeId] = attr.values[0].valueId
    }
  } catch (e) {
    showFailToast(e.message)
  } finally {
    loading.value = false
  }
}

function pick(attributeId, valueId) {
  selected.value = { ...selected.value, [attributeId]: valueId }
}

function requireLogin() {
  if (getToken()) return true
  showFailToast('请先登录')
  router.push({ path: '/login', query: { redirect: route.fullPath } })
  return false
}

async function addToCart() {
  if (!requireLogin()) return
  if (!currentSku.value) {
    showFailToast('请选择完整规格')
    return
  }
  adding.value = true
  try {
    await cartApi.add(currentSku.value.id, quantity.value)
    showSuccessToast('已加入购物车')
  } catch (e) {
    showFailToast(e.message)
  } finally {
    adding.value = false
  }
}

async function buyNow() {
  if (!requireLogin()) return
  if (!currentSku.value) {
    showFailToast('请选择完整规格')
    return
  }
  try {
    await cartApi.add(currentSku.value.id, quantity.value)
    showSuccessToast('已加入购物车，请去结算')
    router.push('/cart')
  } catch (e) {
    showFailToast(e.message)
  }
}

onMounted(load)
</script>

<template>
  <div>
    <van-nav-bar title="商品详情" left-arrow fixed placeholder @click-left="router.back()" />

    <van-loading v-if="loading" style="padding: 80px 0; text-align: center" vertical>加载中…</van-loading>

    <template v-else-if="detail">
      <div class="banner">
        <span>{{ (detail.spuName || '?').slice(0, 1) }}</span>
      </div>

      <div class="block">
        <div class="price-row">
          <span class="price">¥{{ displayPrice }}</span>
          <span class="origin">¥{{ detail.originalPriceYuan }}</span>
          <span class="sold">已售 {{ detail.saleCount || 0 }}</span>
        </div>
        <h3>{{ detail.spuName }}</h3>
        <p class="sub">{{ detail.subTitle }}</p>
        <div class="tags">
          <van-tag plain>{{ detail.brandName }}</van-tag>
          <van-tag plain>{{ detail.categoryName }}</van-tag>
        </div>
      </div>

      <div class="block">
        <div v-for="attr in detail.specAttributes" :key="attr.attributeId" class="spec">
          <div class="spec-name">{{ attr.attributeName }}</div>
          <div class="spec-values">
            <span
              v-for="v in attr.values"
              :key="v.valueId"
              class="spec-value"
              :class="{ active: selected[attr.attributeId] === v.valueId }"
              @click="pick(attr.attributeId, v.valueId)"
            >{{ v.valueName }}</span>
          </div>
        </div>

        <van-cell v-if="currentSku" :title="`已选：${currentSku.specDesc}`" :label="`可售 ${currentSku.availableStock}`" />
        <van-cell v-else title="请选择完整规格" label="选完才能加购" />

        <van-cell title="数量">
          <template #value>
            <van-stepper v-model="quantity" :min="1" :max="currentSku ? currentSku.availableStock : 1" />
          </template>
        </van-cell>
      </div>

      <div class="block">
        <van-cell title="商品详情" />
        <div class="detail-html" v-html="detail.detailHtml || '<p style=\'color:#969799\'>暂无详情</p>'"></div>
      </div>

      <div class="footer">
        <van-button block plain type="warning" :loading="adding" @click="addToCart">加入购物车</van-button>
        <van-button block type="danger" style="margin-left: 10px" :disabled="!canBuy" @click="buyNow">
          {{ canBuy ? '立即购买' : '已售罄' }}
        </van-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.banner {
  height: 220px; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #ff8a65, #f4511e);
  color: #fff; font-size: 76px; font-weight: 700;
}
.block { background: #fff; padding: 12px 16px; margin-bottom: 8px; }
.price-row { display: flex; align-items: baseline; gap: 8px; }
.price { color: #ee0a24; font-size: 22px; font-weight: 700; }
.origin { color: #c8c9cc; font-size: 12px; text-decoration: line-through; }
.sold { color: #969799; font-size: 12px; margin-left: auto; }
h3 { margin: 8px 0 4px; font-size: 16px; }
.sub { color: #969799; font-size: 13px; margin: 0 0 8px; }
.tags { display: flex; gap: 6px; }
.spec { margin-bottom: 12px; }
.spec-name { font-size: 13px; color: #646566; margin-bottom: 8px; }
.spec-values { display: flex; flex-wrap: wrap; gap: 8px; }
.spec-value {
  padding: 5px 14px; border-radius: 14px; background: #f7f8fa;
  font-size: 13px; border: 1px solid transparent;
}
.spec-value.active { border-color: #ee0a24; color: #ee0a24; background: #fff5f5; }
.detail-html { padding: 8px 0; font-size: 13px; color: #646566; }
.footer {
  position: sticky; bottom: 0; display: flex; padding: 10px 16px;
  background: #fff; border-top: 1px solid #ebedf0;
}
</style>
