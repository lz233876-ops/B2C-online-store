<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { authApi } from '../api'
import { clearSession, getUser, saveSession } from '../api/http'

const route = useRoute()
const router = useRouter()
const user = ref(getUser() || {})
const activeMenu = computed(() => route.path)

// 刷新页面后从后端补齐用户信息（localStorage 里只有登录时的那一份，可能过期）
onMounted(async () => {
  try {
    const me = await authApi.me()
    user.value = me
    localStorage.setItem('shopping_admin_user', JSON.stringify(me))
  } catch {
    /* 失败就沿用本地缓存，不打断使用 */
  }
})

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  clearSession()
  router.push('/login')
}

const permCount = computed(() => (user.value.perms || []).length)
const roleText = computed(() => (user.value.roles || []).join(', ') || '无角色')
</script>

<template>
  <el-container class="layout">
    <el-aside width="210px" class="aside">
      <div class="brand">🛒 商城管理后台</div>
      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item index="/product">
          <el-icon><Goods /></el-icon><span>商品管理</span>
        </el-menu-item>
        <el-menu-item index="/order">
          <el-icon><List /></el-icon><span>订单管理</span>
        </el-menu-item>
        <el-menu-item index="/aftersale">
          <el-icon><RefreshLeft /></el-icon><span>售后管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="title">{{ route.meta.title || '' }}</div>
        <div class="right">
          <el-tag type="info" effect="plain" size="small">{{ roleText }}</el-tag>
          <el-tag type="success" effect="plain" size="small">权限点 {{ permCount }}</el-tag>
          <span class="user">{{ user.username || user.nickname || '-' }}</span>
          <el-button link type="primary" @click="handleLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout { height: 100%; }
.aside { background: #1f2d3d; }
.brand { color: #fff; font-weight: 600; padding: 18px 16px; font-size: 15px; letter-spacing: .5px; }
.menu { border-right: none; background: transparent; }
.menu :deep(.el-menu-item) { color: #c0c4cc; }
.menu :deep(.el-menu-item.is-active) { color: #fff; background: #2b3a4d; }
.menu :deep(.el-menu-item:hover) { background: #263445; }
.header {
  display: flex; align-items: center; justify-content: space-between;
  border-bottom: 1px solid #ebeef5; background: #fff;
}
.title { font-size: 16px; font-weight: 600; }
.right { display: flex; align-items: center; gap: 10px; }
.user { color: #606266; font-size: 13px; }
.main { background: #f5f7fa; padding: 16px; }
</style>
