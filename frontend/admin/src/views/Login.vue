<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../api'
import { saveSession } from '../api/http'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const formRef = ref()
const form = reactive({ username: 'admin', password: 'admin123' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

/** 演示账号：一键填充，方便面试演示时快速切换不同权限 */
const demoAccounts = [
  { label: '超管 admin（33 权限点）', username: 'admin', password: 'admin123' },
  { label: '运营 operator（19 权限点，无系统管理）', username: 'operator', password: 'admin123' },
  { label: '商家 merchant（0 权限点）', username: 'merchant', password: 'admin123' },
  { label: '禁用账号 disabled（登录应被拒）', username: 'disabled', password: 'admin123' }
]

function fill(acc) {
  form.username = acc.username
  form.password = acc.password
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    const loginVO = await authApi.login({ username: form.username, password: form.password })
    // 先存 token，再拉 /me（/me 需要带 token）
    saveSession(loginVO, {})
    const me = await authApi.me()
    saveSession(loginVO, me)
    ElMessage.success(`欢迎，${me.username}`)
    router.push(route.query.redirect || '/')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h2>商城管理后台</h2>
      <p class="sub">Spring Boot 3 · RBAC 动态菜单 · JWT 双令牌</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="submit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="admin" size="large" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password size="large" />
        </el-form-item>
        <el-button type="primary" size="large" :loading="loading" style="width: 100%" @click="submit">
          登录
        </el-button>
      </el-form>

      <el-divider>演示账号（点击填充）</el-divider>
      <div class="demo">
        <el-tag
          v-for="acc in demoAccounts"
          :key="acc.username"
          class="demo-tag"
          effect="plain"
          @click="fill(acc)"
        >{{ acc.label }}</el-tag>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.login-wrap {
  height: 100%; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #1f2d3d 0%, #3a4a63 100%);
}
.login-card { width: 420px; padding: 8px 12px; }
h2 { margin: 8px 0 4px; }
.sub { color: #909399; font-size: 13px; margin: 0 0 18px; }
.demo { display: flex; flex-wrap: wrap; gap: 8px; }
.demo-tag { cursor: pointer; }
</style>
