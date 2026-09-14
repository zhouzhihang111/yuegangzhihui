<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { listNotifications, markAllNotificationsRead, markNotificationRead, type Notification } from '@/api/notification'

const tab = ref('全部')
const loading = ref(true)
const messages = ref<Notification[]>([])
const visible = computed(() => messages.value.filter(message => tab.value === '全部' || (tab.value === '未读' && !message.read)))

async function load() {
  loading.value = true
  try { messages.value = await listNotifications() }
  catch { ElMessage.error('消息列表加载失败') }
  finally { loading.value = false }
}

async function read(message: Notification) {
  if (message.read) return
  try { await markNotificationRead(message.id); message.read = true }
  catch { ElMessage.error('消息状态更新失败') }
}

async function readAll() {
  try { await markAllNotificationsRead(); messages.value.forEach(message => { message.read = true }); ElMessage.success('全部消息已标为已读') }
  catch { ElMessage.error('批量更新失败') }
}

onMounted(load)
</script>
<template><PageHeader title="消息中心" description="接收订单、培训、知识审核与系统通知。"><el-button :disabled="!messages.some(x=>!x.read)" @click="readAll">全部标为已读</el-button></PageHeader><div v-loading="loading" class="paper-card notice"><el-tabs v-model="tab"><el-tab-pane v-for="t in ['全部','未读']" :key="t" :label="t" :name="t"/></el-tabs><el-empty v-if="!loading&&!visible.length" description="暂无消息"/><article v-for="m in visible" :key="m.id" :class="{unread:!m.read}" @click="read(m)"><span class="dot"/><div><header><el-tag size="small" effect="plain">{{m.status}}</el-tag><b>{{m.title}}</b><time>{{new Date(m.createdAt).toLocaleString('zh-CN')}}</time></header><p>{{m.content}}</p></div></article></div></template><style scoped>.notice{padding:20px;min-height:260px}.notice article{display:grid;grid-template-columns:10px 1fr;gap:10px;padding:18px 8px;border-top:1px solid var(--line);cursor:pointer}.notice article.unread{background:#f1f6f2}.dot{width:7px;height:7px;margin-top:8px;border-radius:50%;background:transparent}.unread .dot{background:var(--cinnabar)}.notice header{display:flex;align-items:center;gap:10px}.notice time{margin-left:auto;color:var(--muted);font-size:11px}.notice p{margin:8px 0 0;color:var(--muted);font-size:13px}</style>
