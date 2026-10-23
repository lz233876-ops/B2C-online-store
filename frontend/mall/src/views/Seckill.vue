<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast, showSuccessToast, showToast } from 'vant'
import { seckillApi } from '../api'
import { getToken } from '../api/http'

const router = useRouter()
const loading = ref(true)
const activities = ref([])
const grabbing = ref('')
const results = ref({})   // { seckillSkuId: { text, type } }

async function load() {
  loading.value = true
  try {
    activities.value = await seckillApi.list()
  } catch (e) {
    showFailToast(e.message)
  } finally {
    loading.value = false
  }
}

function setResult(skuId, text, type = 'primary') {
  results.value = { ...results.value, [skuId]: { text, type } }
}

async function grab(activity, sku) {
  if (!getToken()) {
    showToast('请先登录再抢购')
    router.push({ path: '/login', query: { redirect: '/seckill' } })
    return
  }
  grabbing.value = sku.seckillSkuId
  setResult(sku.seckillSkuId, '抢购中…')
  try {
    const r = await seckillApi.grab(sku.seckillSkuId)
    setResult(sku.seckillSkuId, `已抢到名额，订单创建中（剩余 ${r.remainStock}）`, 'success')
    showSuccessToast('抢到了！正在创建订单')
    // 轮询：秒杀是异步建单，前端必须自己收敛（最多等 20 次 × 500ms）
    for (let i = 0; i < 20; i++) {
      await new Promise((resolve) => setTimeout(resolve, 500))
      const res = await seckillApi.result(activity.id)
      if (res.grabbed && res.orderNo) {
        setResult(sku.seckillSkuId, `订单已创建：${res.orderNo}`, 'success')
        showSuccessToast('订单已创建，去「我的」支付')
        await load()
        return
      }
    }
    setResult(sku.seckillSkuId, '订单仍在创建中，请稍后到「我的」查看', 'warning')
  } catch (e) {
    // 5103 已售罄 / 5104 已参与 / 其他
    setResult(sku.seckillSkuId, e.message, 'danger')
    showFailToast(e.message)
    await load()
  } finally {
    grabbing.value = ''
  }
}

function yuan(n) {
  return n == null ? '0.00' : (n / 100).toFixed(2)
}

onMounted(load)
</script>

<template>
  <div>
    <van-nav-bar title="限时秒杀" fixed placeholder />
    <van-loading v-if="loading" style="padding: 80px 0; text-align: center" vertical>加载中…</van-loading>

    <template v-else>
      <van-empty v-if="!activities.length" description="暂无进行中的秒杀活动">
        <p style="font-size: 12px; color: #969799">
          执行 sql/data/dev_seckill_demo.sql 导入演示活动后刷新
        </p>
      </van-empty>

      <div v-for="act in activities" :key="act.id" class="activity">
        <div class="head">
          <div>
            <div class="name">{{ act.activityName }}</div>
            <div class="time">剩余 {{ Math.floor((act.remainSeconds || 0) / 3600) }} 小时</div>
          </div>
          <van-tag type="danger" effect="dark">{{ act.statusDesc }}</van-tag>
        </div>

        <div v-for="sku in act.skus" :key="sku.seckillSkuId" class="sku">
          <div class="thumb">{{ (sku.skuName || '?').slice(0, 1) }}</div>
          <div class="info">
            <div class="sku-name">{{ sku.skuName }}</div>
            <div class="spec">{{ sku.specDesc }}</div>
            <div class="price-row">
              <span class="price">¥{{ sku.seckillPriceYuan }}</span>
              <span class="origin">¥{{ sku.originalPriceYuan }}</span>
              <van-tag type="warning" plain>{{ sku.discount }}</van-tag>
            </div>
            <div class="stock">
              剩余
              <b>{{ sku.remainStock == null ? '未预热' : sku.remainStock }}</b>
              / {{ sku.seckillStock }}
              <span class="limit">（每人限 {{ sku.limitPerUser }} 件）</span>
            </div>
            <div v-if="results[sku.seckillSkuId]" class="result">
              <van-tag :type="results[sku.seckillSkuId].type" plain>
                {{ results[sku.seckillSkuId].text }}
              </van-tag>
            </div>
          </div>
          <van-button
            type="danger" size="small" round
            :loading="grabbing === sku.seckillSkuId"
            :disabled="sku.remainStock === 0"
            @click="grab(act, sku)"
          >{{ sku.remainStock === 0 ? '已抢光' : '抢' }}</van-button>
        </div>
      </div>

      <div class="tip">
        抢购接口同步只做 Redis 预扣 + 投递 MQ，<b>订单由消费者异步创建</b>，
        所以这里抢到后要轮询结果拿订单号。
      </div>
    </template>
  </div>
</template>

<style scoped>
.activity { background: #fff; margin-bottom: 10px; padding: 12px 16px; }
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.name { font-size: 15px; font-weight: 700; }
.time { font-size: 12px; color: #969799; margin-top: 2px; }
.sku { display: flex; gap: 10px; align-items: center; padding: 10px 0; border-top: 1px solid #f2f3f5; }
.thumb {
  width: 56px; height: 56px; border-radius: 8px; flex: 0 0 56px;
  background: linear-gradient(135deg, #ff8a65, #e64a19);
  color: #fff; font-size: 24px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
}
.info { flex: 1; min-width: 0; }
.sku-name { font-size: 14px; font-weight: 600; }
.spec { font-size: 12px; color: #969799; }
.price-row { display: flex; align-items: baseline; gap: 6px; margin: 4px 0; }
.price { color: #ee0a24; font-weight: 700; font-size: 16px; }
.origin { color: #c8c9cc; font-size: 12px; text-decoration: line-through; }
.stock { font-size: 12px; color: #646566; }
.stock b { color: #ee0a24; }
.limit { color: #969799; }
.result { margin-top: 4px; }
.tip { padding: 12px 16px; font-size: 12px; color: #969799; line-height: 1.6; }
</style>
