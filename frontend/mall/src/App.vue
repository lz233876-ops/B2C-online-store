<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getToken } from './api/http'

const route = useRoute()
const router = useRouter()

// 只在「一级页面」显示底部导航；详情页/登录页不显示
const activeTab = computed(() => route.meta.tab || '')
const showTabbar = computed(() => !!route.meta.tab)

function go(name) {
  if ((name === 'cart' || name === 'orders') && !getToken()) {
    router.push({ path: '/login', query: { redirect: '/' + name } })
    return
  }
  router.push('/' + name)
}
</script>

<template>
  <div class="app">
    <div class="page" :class="{ 'with-tabbar': showTabbar }">
      <router-view />
    </div>
    <van-tabbar v-if="showTabbar" :model-value="activeTab" fixed placeholder @change="go">
      <van-tabbar-item name="home" icon="shop-o">首页</van-tabbar-item>
      <van-tabbar-item name="seckill" icon="fire-o">秒杀</van-tabbar-item>
      <van-tabbar-item name="cart" icon="shopping-cart-o">购物车</van-tabbar-item>
      <van-tabbar-item name="orders" icon="orders-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<style>
html, body, #app { height: 100%; margin: 0; }
body { background: #f7f8fa; font-family: -apple-system, BlinkMacSystemFont, 'Microsoft YaHei', sans-serif; }
.app { min-height: 100%; }
.page { padding-bottom: 8px; }
/* 桌面浏览器打开时限制宽度，模拟手机视口，演示更好看 */
@media (min-width: 600px) {
  #app {
    max-width: 420px;
    margin: 0 auto;
    box-shadow: 0 0 24px rgba(0, 0, 0, .08);
    background: #f7f8fa;
    min-height: 100vh;
  }
}
</style>
