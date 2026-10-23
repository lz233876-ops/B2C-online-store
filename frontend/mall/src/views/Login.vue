<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showFailToast, showSuccessToast } from 'vant'
import { authApi } from '../api'
import { saveSession, saveUser } from '../api/http'

const route = useRoute()
const router = useRouter()
const mode = ref('login')
const loading = ref(false)
const form = reactive({ phone: '13900000001', password: '123456' })

async function submit() {
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    showFailToast('请输入正确的手机号')
    return
  }
  if (form.password.length < 6) {
    showFailToast('密码至少 6 位')
    return
  }
  loading.value = true
  try {
    if (mode.value === 'register') {
      await authApi.register(form.phone, form.password, `用户${form.phone.slice(-4)}`)
      showSuccessToast('注册成功，正在登录')
    }
    const loginVO = await authApi.login(form.phone, form.password)
    saveSession(loginVO)
    const me = await authApi.me()
    saveUser(me)
    showSuccessToast(`欢迎，${me.nickname || me.phone}`)
    router.replace(route.query.redirect || '/home')
  } catch (e) {
    showFailToast(e.message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="wrap">
    <div class="hero">
      <div class="logo">🛒</div>
      <h2>欢迎来到商城</h2>
      <p>Spring Boot 3 · 秒杀 · 模拟支付</p>
    </div>

    <van-cell-group inset>
      <van-field v-model="form.phone" label="手机号" placeholder="11 位手机号" maxlength="11" type="tel" />
      <van-field v-model="form.password" label="密码" placeholder="至少 6 位" type="password" />
    </van-cell-group>

    <div class="btns">
      <van-button type="primary" block :loading="loading" @click="submit">
        {{ mode === 'login' ? '登录' : '注册并登录' }}
      </van-button>
      <van-button plain block style="margin-top: 12px" @click="mode = mode === 'login' ? 'register' : 'login'">
        {{ mode === 'login' ? '没有账号？去注册' : '已有账号？去登录' }}
      </van-button>
    </div>

    <van-notice-bar left-icon="info-o" wrapable
      text="演示账号：13900000001 / 123456（数据库种子里的 testuser）" />
  </div>
</template>

<style scoped>
.wrap { padding: 40px 16px; }
.hero { text-align: center; margin-bottom: 28px; }
.logo { font-size: 48px; line-height: 1; }
h2 { margin: 12px 0 4px; font-size: 20px; }
.hero p { color: #969799; font-size: 13px; margin: 0; }
.btns { padding: 24px 16px 16px; }
</style>
