<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast } from 'vant'
import { productApi } from '../api'

const router = useRouter()
const loading = ref(false)
const finished = ref(false)
const rows = ref([])
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '' })
const total = ref(0)

async function load(reset = false) {
  if (reset) {
    query.pageNum = 1
    rows.value = []
    finished.value = false
  }
  loading.value = true
  try {
    const params = { pageNum: query.pageNum, pageSize: query.pageSize }
    if (query.keyword) params.keyword = query.keyword
    const page = await productApi.list(params)
    total.value = page.total || 0
    rows.value = rows.value.concat(page.records || [])
    query.pageNum += 1
    finished.value = rows.value.length >= total.value
  } catch (e) {
    showFailToast(e.message)
    finished.value = true
  } finally {
    loading.value = false
  }
}

function price(n) {
  return n == null ? '0.00' : (n / 100).toFixed(2)
}

/**
 * 种子里的图片是 cdn.example.com 的占位地址，加载不出来。
 * 与其显示一堆破图，不如用「首字 + 渐变色」的占位块 —— 演示时更干净，
 * 也顺带说明「图片是外部 CDN，未接对象存储」。
 */
function placeholderStyle(id) {
  const hue = (id * 47) % 360
  return { background: `linear-gradient(135deg, hsl(${hue} 70% 62%), hsl(${(hue + 40) % 360} 70% 48%))` }
}

onMounted(() => load(true))
</script>

<template>
  <div>
    <van-nav-bar title="商城首页" fixed placeholder />
    <van-search
      v-model="query.keyword"
      placeholder="搜索商品名称"
      @search="load(true)"
      @clear="load(true)"
    />

    <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="load()">
      <div v-for="p in rows" :key="p.id" class="card" @click="router.push(`/detail/${p.id}`)">
        <div class="thumb" :style="placeholderStyle(p.id)">
          <span>{{ (p.spuName || '?').slice(0, 1) }}</span>
        </div>
        <div class="info">
          <div class="name">{{ p.spuName }}</div>
          <div class="sub">{{ p.subTitle }}</div>
          <div class="tags">
            <van-tag v-if="p.isNew === 1" type="danger" plain>新品</van-tag>
            <van-tag v-if="p.isRecommend === 1" type="primary" plain>推荐</van-tag>
            <van-tag plain>{{ p.brandName }}</van-tag>
          </div>
          <div class="price-row">
            <span class="price">¥{{ p.priceYuan }}</span>
            <span class="origin">¥{{ p.originalPriceYuan }}</span>
            <span class="sold">已售 {{ p.saleCount || 0 }}</span>
          </div>
          <div class="stock">
            {{ p.skuCount }} 个规格 · 可售 {{ p.availableStock }}
            <van-tag v-if="p.availableStock <= 0" type="danger" style="margin-left: 6px">售罄</van-tag>
          </div>
        </div>
      </div>
    </van-list>

    <van-empty v-if="!loading && rows.length === 0" description="暂无商品（确认后台已上架商品）" />
  </div>
</template>

<style scoped>
.card {
  display: flex; gap: 12px; padding: 12px 16px; background: #fff; margin-bottom: 8px;
}
.thumb {
  width: 92px; height: 92px; border-radius: 8px; flex: 0 0 92px;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 34px; font-weight: 700;
}
.info { flex: 1; min-width: 0; }
.name { font-size: 15px; font-weight: 600; margin-bottom: 2px; }
.sub { font-size: 12px; color: #969799; margin-bottom: 6px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tags { display: flex; gap: 6px; margin-bottom: 6px; }
.price-row { display: flex; align-items: baseline; gap: 8px; }
.price { color: #ee0a24; font-size: 17px; font-weight: 700; }
.origin { color: #c8c9cc; font-size: 12px; text-decoration: line-through; }
.sold { color: #969799; font-size: 12px; margin-left: auto; }
.stock { color: #969799; font-size: 12px; margin-top: 4px; }
</style>
