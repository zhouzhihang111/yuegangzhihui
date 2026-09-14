<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { logout as revokeSession, useSessionStore } from '@ygh/web-shared'
import { Bell, Collection, CreditCard, Document, Goods, House, Location, Reading, SwitchButton, User } from '@element-plus/icons-vue'
import { useHttp } from '@/api/client'

const route = useRoute(); const router = useRouter(); const session = useSessionStore()
const isInternalEmployee = computed(() => {
  const roles = session.user?.roles ?? []
  return roles.includes('EMPLOYEE') && !roles.includes('ADMIN')
})
const menu = computed(() => [
  { group:'账户', items:[{to:'/workspace/profile',label:'个人资料',icon:User},{to:'/workspace/addresses',label:'收货地址',icon:Location},{to:'/workspace/notifications',label:'消息中心',icon:Bell}]},
  { group:'商城', items:[{to:'/workspace/cart',label:'购物车',icon:Goods},{to:'/workspace/orders',label:'我的订单',icon:Document},{to:'/workspace/wallet',label:'模拟钱包',icon:CreditCard}]},
  ...(isInternalEmployee.value ? [{ group:'成长', items:[{to:'/workspace/training',label:'学习任务',icon:Reading},{to:'/workspace/training/courses',label:'课程中心',icon:Collection},{to:'/workspace/training/progress',label:'学习档案',icon:House}]}] : []),
])
async function logout(){
  const refreshToken = session.renewal
  try {
    if (refreshToken) await revokeSession(useHttp(), refreshToken)
  } finally {
    session.clear()
    await router.replace('/')
  }
}
;</script>
<template>
  <header class="workspace-head"><div class="container"><RouterLink to="/" class="workspace-brand"><span>粤</span><b class="serif">粤港甄选 · 个人中心</b></RouterLink><div><el-button text @click="router.push('/')">返回商城</el-button><el-button text :icon="SwitchButton" @click="logout">退出</el-button></div></div></header>
  <main class="page-shell"><div class="container workspace-grid">
    <aside class="paper-card side"><div class="identity"><el-avatar :size="48">{{ session.user?.displayName?.slice(0,1) || '用' }}</el-avatar><div><b>{{session.user?.displayName || '用户'}}</b><small>{{session.user?.username}}</small></div></div><template v-for="section in menu" :key="section.group"><p>{{section.group}}</p><RouterLink v-for="item in section.items" :key="item.to" :to="item.to" :class="{active:route.path===item.to}"><el-icon><component :is="item.icon"/></el-icon>{{item.label}}</RouterLink></template></aside>
    <section class="workspace-content"><RouterView /></section>
  </div></main>
</template>
<style scoped>
.workspace-head{height:68px;background:#fff;border-bottom:1px solid var(--line)}.workspace-head .container{height:100%;display:flex;align-items:center;justify-content:space-between}.workspace-brand{display:flex;align-items:center;gap:12px}.workspace-brand span{display:grid;place-items:center;width:32px;height:32px;background:var(--cinnabar);color:#fff}.side{padding:18px;height:max-content;position:sticky;top:24px}.identity{display:flex;align-items:center;gap:12px;padding:8px 6px 18px;border-bottom:1px solid var(--line)}.identity b,.identity small{display:block}.identity small{margin-top:3px;color:var(--muted);font-size:12px}.side p{margin:20px 8px 8px;color:#93a09c;font-size:11px;letter-spacing:.16em}.side a{display:flex;align-items:center;gap:10px;padding:10px 12px;border-radius:8px;color:#52635f;font-size:14px}.side a:hover,.side a.active{background:var(--jade-soft);color:var(--jade);font-weight:600}.workspace-content{min-width:0}
</style>
