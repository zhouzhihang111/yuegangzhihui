<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login as authenticate, sessionUserFromAuthentication, useSessionStore } from '@ygh/web-shared'
import { useHttp } from '@/api/client'

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const session = useSessionStore()
const router = useRouter()
const route = useRoute()

async function login() {
  loading.value = true
  // A role change invalidates old refresh tokens. Never let an earlier browser
  // session influence a new credential login.
  session.clear()
  localStorage.removeItem('ygh.refresh-token')
  sessionStorage.removeItem('ygh.session-user')
  try {
    const result = await authenticate(useHttp(), { principal: form.username, password: form.password })
    const user = sessionUserFromAuthentication(result, form.username)
    if (!user.roles.includes('ADMIN')) {
      session.clear()
      ElMessage.error('当前账号没有运营后台访问权限')
      return
    }
    session.establish(result.tokens, user)
    await router.replace(String(route.query.redirect || '/dashboard'))
  } catch (error) {
    const response = typeof error === 'object' && error !== null && 'response' in error
      ? (error as { response?: { data?: { code?: string; message?: string; traceId?: string } } }).response?.data
      : undefined
    const code = response?.code ? `${response.code}：` : ''
    const detail = `${code}${response?.message || (error instanceof Error ? error.message : '未知错误')}`
    const trace = response?.traceId ? `（traceId: ${response.traceId}）` : ''
    ElMessage.error({ message: `登录失败：${detail}${trace}`, duration: 8000, showClose: true })
  } finally {
    loading.value = false
  }
}
</script>
<template><div class="login-page"><div class="institution"><div class="seal">粤</div><span>粤港甄选跨境智汇 AI 平台</span><small>企业运营与知识治理后台</small></div><div class="login-card"><div class="security-label">AUTHORIZED PERSONNEL ONLY · DEV 17:25</div><h1 class="serif">运营人员登录</h1><p>后台操作将记录用户、时间、资源与 traceId，请使用个人工作账号。</p><el-form label-position="top" @submit.prevent="login"><el-form-item label="工作账号"><el-input v-model="form.username" size="large" autocomplete="username"/></el-form-item><el-form-item label="登录密码"><el-input v-model="form.password" size="large" type="password" show-password autocomplete="current-password"/></el-form-item><el-button native-type="submit" type="primary" size="large" :loading="loading">进入运营后台</el-button></el-form><el-alert title="身份由 Auth 验证，后台访问权由 System RBAC 实时签发。" type="warning" :closable="false"/></div><footer>本系统仅供授权员工使用 · 所有敏感操作均写入审计日志</footer></div></template>
<style scoped>.login-page{min-height:100vh;display:grid;place-content:center;position:relative;background:radial-gradient(circle at 70% 20%,rgba(199,156,74,.15),transparent 30rem),linear-gradient(135deg,#062e2b,#0d514b);color:#fff}.institution{display:flex;align-items:center;gap:12px;position:absolute;left:38px;top:30px}.institution .seal{width:38px;height:38px;display:grid;place-items:center;background:var(--accent);outline:1px solid white;outline-offset:-4px}.institution span,.institution small{display:block}.institution small{color:#829f98;font-size:10px}.login-card{width:430px;padding:38px;background:#f8f6ef;color:#162b29;box-shadow:30px 30px 0 rgba(0,0,0,.16)}.security-label{color:var(--accent);font-size:9px;letter-spacing:.2em}.login-card h1{margin:10px 0;font-size:30px}.login-card>p{margin-bottom:25px;color:var(--muted);line-height:1.7;font-size:13px}.login-card .el-button{width:100%;margin-bottom:20px}.login-page>footer{position:absolute;bottom:24px;left:0;right:0;color:#77958e;text-align:center;font-size:10px}@media(max-width:500px){.login-card{width:calc(100vw - 30px);padding:28px}.institution{left:20px}}</style>
