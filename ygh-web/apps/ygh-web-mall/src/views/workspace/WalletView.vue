<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { getWallet, listWalletTransactions, rechargeWallet, type Wallet, type WalletTransaction } from '@/api/wallet'

const dialog = ref(false)
const loading = ref(true)
const recharging = ref(false)
const amount = ref(500)
const wallet = ref<Wallet>()
const records = ref<WalletTransaction[]>([])
const total = (type: string) => computed(() => records.value.filter(item => item.type === type).reduce((sum, item) => sum + Number(item.amount), 0).toFixed(2))
const totalRecharge = total('RECHARGE')
const totalPayment = total('PAYMENT')
const totalRefund = total('REFUND')

async function load() {
  loading.value = true
  try { [wallet.value, records.value] = await Promise.all([getWallet(), listWalletTransactions()]) }
  catch { ElMessage.error('钱包数据加载失败') }
  finally { loading.value = false }
}

async function recharge() {
  recharging.value = true
  try {
    await rechargeWallet(amount.value.toFixed(2))
    dialog.value = false
    ElMessage.success(`已模拟充值 ¥${amount.value.toFixed(2)}`)
    await load()
  } catch { ElMessage.error('虚拟充值失败，请勿重复提交') }
  finally { recharging.value = false }
}

onMounted(load)
</script>
<template><div v-loading="loading"><PageHeader title="模拟钱包" description="仅用于平台充值、支付与退款流程演示，不对应真实资金账户。"><el-button type="primary" @click="dialog=true">虚拟充值</el-button></PageHeader><section class="wallet-hero"><div><span>可用余额（元）</span><b class="serif">{{Number(wallet?.availableBalance||0).toFixed(2)}}</b><small>账户编号 WALLET-{{wallet?.userId||'--'}}</small></div><div class="watermark">SIMULATION<br/>ONLY</div><p>余额由钱包服务作为唯一交易事实保存，Redis 不作为余额来源。</p></section><div class="metric-grid"><div class="metric paper-card"><span>累计虚拟充值</span><b>¥{{totalRecharge}}</b></div><div class="metric paper-card"><span>累计模拟支付</span><b>¥{{totalPayment}}</b></div><div class="metric paper-card"><span>累计模拟退款</span><b>¥{{totalRefund}}</b></div><div class="metric paper-card"><span>冻结金额</span><b>¥{{Number(wallet?.frozenBalance||0).toFixed(2)}}</b></div></div><section class="paper-card ledger"><h3 class="serif">钱包流水</h3><el-table :data="records" empty-text="暂无钱包流水"><el-table-column label="时间"><template #default="s">{{new Date(s.row.createdAt).toLocaleString('zh-CN')}}</template></el-table-column><el-table-column prop="type" label="业务类型"/><el-table-column prop="referenceId" label="业务单号"/><el-table-column label="变动金额"><template #default="s"><b :class="s.row.type==='PAYMENT'?'minus':'plus'">{{s.row.type==='PAYMENT'?'-':'+'}}{{Number(s.row.amount).toFixed(2)}}</b></template></el-table-column><el-table-column prop="status" label="状态"/></el-table></section><el-dialog v-model="dialog" title="虚拟充值" width="420"><el-alert title="不会发起真实支付" type="warning" :closable="false"/><p>请选择或输入充值金额：</p><el-radio-group v-model="amount"><el-radio-button v-for="n in [100,500,1000,2000]" :key="n" :value="n">¥{{n}}</el-radio-button></el-radio-group><el-input-number v-model="amount" :min="1" :max="100000" :precision="2" style="width:100%;margin-top:15px"/><template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="recharging" @click="recharge">确认模拟充值</el-button></template></el-dialog></div></template>
<style scoped>.wallet-hero{height:220px;position:relative;overflow:hidden;margin-bottom:20px;padding:32px 38px;background:linear-gradient(120deg,#0b4b46,#0e6b61);color:#fff;border-radius:16px;box-shadow:var(--shadow)}.wallet-hero span,.wallet-hero b,.wallet-hero small{display:block}.wallet-hero span{color:#bad0ca}.wallet-hero b{margin:12px 0 5px;font-size:43px}.wallet-hero small{color:#8aada5}.wallet-hero p{position:absolute;bottom:20px;color:#b8cbc6;font-size:11px}.watermark{position:absolute;right:30px;top:35px;color:rgba(255,255,255,.09);font:900 38px/1.1 serif;letter-spacing:.12em;transform:rotate(-8deg)}.ledger{margin-top:20px;padding:22px}.ledger h3{margin-top:0}.plus{color:#2b8d68}.minus{color:var(--cinnabar)}</style>
