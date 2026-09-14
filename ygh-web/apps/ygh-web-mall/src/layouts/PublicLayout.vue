<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSessionStore } from '@ygh/web-shared'
import { ChatDotRound, ShoppingCart, UserFilled } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const active = computed(() => route.path.startsWith('/products') ? '/products' : route.path.startsWith('/knowledge') ? '/knowledge' : route.path.startsWith('/ai-service') ? '/ai-service' : '/')
const isInternalEmployee = computed(() => {
  const roles = session.user?.roles ?? []
  return roles.includes('EMPLOYEE') && !roles.includes('ADMIN')
})
;</script>

<template>
  <div class="public-layout" :class="{ 'ai-service-layout': active === '/ai-service' }">
    <div class="trade-ribbon">粤港澳跨境甄选 · 商品可溯源 · 政策有出处 · 支付为教学模拟</div>
    <header class="public-header">
      <div class="container header-inner">
        <RouterLink to="/" class="brand">
          <span class="brand-seal">粤</span>
          <span><b class="serif">粤港甄选</b><small>CROSS-BORDER INTELLIGENCE</small></span>
        </RouterLink>
        <nav>
          <RouterLink to="/" :class="{ active: active === '/' }">首页</RouterLink>
          <RouterLink to="/products" :class="{ active: active === '/products' }">跨境商城</RouterLink>
          <RouterLink to="/knowledge" :class="{ active: active === '/knowledge' }">政策知识</RouterLink>
          <RouterLink to="/ai-service" :class="{ active: active === '/ai-service' }">AI 专业客服</RouterLink>
        </nav>
        <div class="header-actions">
          <el-button text circle :icon="ChatDotRound" @click="router.push('/ai-service')" />
          <el-button text circle :icon="ShoppingCart" @click="router.push('/workspace/cart')" />
          <el-button v-if="!session.authenticated" type="primary" @click="router.push('/login')">登录 / 注册</el-button>
          <el-button v-else :icon="UserFilled" @click="router.push('/workspace/profile')">{{ session.user?.displayName }}</el-button>
        </div>
      </div>
    </header>
    <main><RouterView /></main>
    <footer v-if="active !== '/ai-service'">
      <div class="container footer-grid">
        <div><div class="brand footer-brand"><span class="brand-seal">粤</span><b class="serif">粤港甄选</b></div><p>服务跨境消费、知识检索与岗位成长的一体化企业平台。</p></div>
        <div><b>消费者服务</b><RouterLink to="/products">商品选购</RouterLink><RouterLink to="/workspace/orders">我的订单</RouterLink><RouterLink to="/workspace/wallet">模拟钱包</RouterLink></div>
        <div><b>知识服务</b><RouterLink to="/knowledge">政策法规</RouterLink><RouterLink to="/ai-service">AI 客服</RouterLink><RouterLink v-if="isInternalEmployee" to="/workspace/training">员工培训</RouterLink></div>
        <div><b>平台声明</b><span>不接入真实支付</span><span>不产生真实物流</span><span>内容引用以发布版本为准</span></div>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.trade-ribbon{height:30px;display:grid;place-items:center;background:var(--jade-dark);color:#d8e8df;font-size:12px;letter-spacing:.08em}.public-header{height:72px;position:sticky;top:0;z-index:30;background:rgba(247,244,236,.9);border-bottom:1px solid var(--line);backdrop-filter:blur(16px)}.header-inner{height:100%;display:flex;align-items:center;gap:40px}.brand{display:flex;align-items:center;gap:11px;min-width:max-content}.brand-seal{display:grid;place-items:center;width:38px;height:38px;color:#fff;background:var(--cinnabar);border:1px solid #8c321f;outline:1px solid rgba(255,255,255,.5);outline-offset:-4px;font:700 20px 'Noto Serif SC',serif}.brand b{display:block;font-size:19px;letter-spacing:.12em}.brand small{display:block;margin-top:2px;color:var(--muted);font-size:8px;letter-spacing:.12em}nav{height:100%;display:flex;align-items:center;gap:28px;flex:1}nav a{position:relative;color:#445652;font-size:14px}nav a.active{color:var(--jade);font-weight:700}nav a.active:after{content:'';position:absolute;left:0;right:0;bottom:-26px;height:3px;background:var(--cinnabar)}.header-actions{display:flex;align-items:center}.ai-service-layout{height:100vh;overflow:hidden}.ai-service-layout>main{height:calc(100vh - 102px);overflow:hidden}.footer-grid{display:grid;grid-template-columns:1.7fr repeat(3,1fr);gap:54px;padding:52px 0}footer{background:#102f2d;color:#d9e2dd}.footer-grid>div{display:flex;flex-direction:column;gap:11px;font-size:13px}.footer-grid p,.footer-grid span,.footer-grid a{color:#aebfba}.footer-brand{color:white}.footer-brand .brand-seal{width:32px;height:32px;font-size:16px}@media(max-width:900px){nav{display:none}.header-actions{margin-left:auto}.footer-grid{grid-template-columns:1fr 1fr}}@media(max-width:600px){.trade-ribbon{font-size:10px}.brand small{display:none}.footer-grid{grid-template-columns:1fr}.header-actions .el-button:first-child{display:none}}
</style>
