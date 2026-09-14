<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
    Check,
    Connection,
    Document,
    Location,
    ShoppingCart,
} from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import type { ProductSummary } from "@ygh/web-shared";
import {
    getProduct,
    listProductBatches,
    listTraceEvents,
    productSummary,
    type ProductBatch,
    type TraceEvent,
} from "@/api/product";
import { addCartItem } from "@/api/cart";

const route = useRoute();
const router = useRouter();
const quantity = ref(1);
const loading = ref(true);
const product = ref<ProductSummary>({
    id: "",
    spuCode: "",
    name: "",
    categoryName: "",
    origin: "",
    price: "0",
    status: "",
    specifications: {},
});
const batches = ref<ProductBatch[]>([]);
const traceEvents = ref<TraceEvent[]>([]);

async function load() {
    loading.value = true;
    try {
        const skuId = String(route.params.id);
        const [detail, productBatches, events] = await Promise.all([
            getProduct(skuId),
            listProductBatches(skuId),
            listTraceEvents(skuId),
        ]);
        product.value = productSummary(detail);
        batches.value = productBatches;
        traceEvents.value = events;
    } catch {
        ElMessage.error("商品详情加载失败");
    } finally {
        loading.value = false;
    }
}

async function add(goCheckout = false) {
    try {
        await addCartItem(product.value.id, quantity.value);
        ElMessage.success("已加入购物车");
        await router.push(
            goCheckout ? "/workspace/checkout" : "/workspace/cart",
        );
    } catch {
        ElMessage.error("操作失败，请先登录或检查商品状态");
    }
}

onMounted(load);
</script>
<template>
    <div v-loading="loading" class="page-shell">
        <div class="container">
            <el-breadcrumb separator="/">
                <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
                <el-breadcrumb-item :to="{ path: '/products' }">跨境商城</el-breadcrumb-item>
                <el-breadcrumb-item>{{ product.name }}</el-breadcrumb-item>
            </el-breadcrumb>
            <section class="detail-grid">
                <div class="gallery paper-card">
                    <div class="main-visual"><span>{{ product.origin.slice(0, 2) }}</span><small>{{ product.categoryName }}</small></div>
                    <div class="trace-strip"><el-icon><Connection /></el-icon><div><b>该商品支持可信溯源</b><small>溯源码 {{ product.traceabilityCode }}</small></div></div>
                </div>
                <div class="detail-copy">
                    <span class="section-label">{{ product.categoryName }} · {{ product.spuCode }}</span>
                    <h1 class="serif">{{ product.name }}</h1>
                    <p>企业甄选渠道商品，展示产地、规格、批次与平台质检信息。下单及支付仅用于系统业务演示。</p>
                    <div class="price-panel"><span>商城价</span><div class="price">¥ <strong>{{ product.price }}</strong></div></div>
                    <dl><template v-for="(value, key) in product.specifications" :key="key"><dt>{{ key }}</dt><dd>{{ value }}</dd></template><dt>产地</dt><dd>{{ product.origin }}</dd><dt>状态</dt><dd>{{ product.status }}</dd></dl>
                    <div class="quantity"><span>数量</span><el-input-number v-model="quantity" :min="1" :max="999" /></div>
                    <div class="detail-actions"><el-button size="large" @click="add(false)">加入购物车<el-icon><ShoppingCart /></el-icon></el-button><el-button size="large" type="primary" @click="add(true)">立即购买</el-button></div>
                    <div class="promises"><span><Check />批次可追溯</span><span><Check />模拟支付</span><span><Check />售后流程演示</span></div>
                </div>
            </section>
            <section class="detail-info">
                <el-tabs>
                    <el-tab-pane label="商品详情">
                        <div class="content"><h2 class="serif">从产地到平台的可信链路</h2><p>{{ product.name }} 来自 {{ product.origin }}，平台按商品知识与合规规则维护其规格、价格和溯源资料。页面不会虚构真实物流状态。</p><div class="route"><article><el-icon><Location /></el-icon><b>原产信息</b><span>{{ product.origin }}</span></article><article><el-icon><Document /></el-icon><b>资料核验</b><span>批次与合规信息</span></article><article><el-icon><Connection /></el-icon><b>平台上架</b><span>审核后公开展示</span></article></div></div>
                    </el-tab-pane>
                    <el-tab-pane label="溯源档案">
                        <div class="content trace-content">
                            <el-descriptions :column="2" border><el-descriptions-item label="溯源码">{{ product.traceabilityCode }}</el-descriptions-item><el-descriptions-item label="SKU 编码">{{ product.spuCode }}</el-descriptions-item><el-descriptions-item label="原产地">{{ product.origin }}</el-descriptions-item><el-descriptions-item label="当前状态">{{ product.status }}</el-descriptions-item></el-descriptions>
                            <h3 class="serif">商品批次</h3>
                            <el-table :data="batches" empty-text="暂无公开批次信息" border>
                                <el-table-column prop="batchNo" label="批次号" min-width="140" />
                                <el-table-column prop="origin" label="批次产地" min-width="130" />
                                <el-table-column prop="producedOn" label="生产日期" width="120" />
                                <el-table-column prop="expiresOn" label="有效期至" width="120" />
                                <el-table-column prop="traceDescription" label="溯源说明" min-width="220" />
                                <el-table-column label="来源证明" width="100"><template #default="scope"><el-link v-if="scope.row.proofUrl" :href="scope.row.proofUrl" target="_blank" rel="noopener noreferrer" type="primary">查看</el-link><span v-else>--</span></template></el-table-column>
                            </el-table>
                            <h3 class="serif">溯源事件</h3>
                            <el-timeline v-if="traceEvents.length">
                                <el-timeline-item v-for="event in traceEvents" :key="event.id || `${event.eventType}-${event.occurredAt}`" :timestamp="event.occurredAt ? new Date(event.occurredAt).toLocaleString('zh-CN') : ''" placement="top">
                                    <b>{{ event.eventType || '溯源记录' }}</b><p>{{ event.location || '地点未公开' }} · {{ event.description || '无补充说明' }}</p>
                                </el-timeline-item>
                            </el-timeline>
                            <el-empty v-else description="暂无公开溯源事件" />
                        </div>
                    </el-tab-pane>
                    <el-tab-pane label="购买说明"><div class="content"><el-alert title="教学模拟说明" type="warning" :closable="false" description="充值、支付、退款和订单状态流转均为虚拟模式，不发生真实资金结算与物流发货。" /></div></el-tab-pane>
                </el-tabs>
            </section>
        </div>
    </div>
</template>
<style scoped>
.trace-content h3 { margin: 32px 0 14px; font-size: 22px; }
.trace-content .el-timeline { margin-top: 20px; padding-left: 8px; }
.trace-content .el-timeline p { margin: 6px 0; color: var(--muted); font-size: 13px; }
</style>
<style scoped>.detail-grid{display:grid;grid-template-columns:1fr 1fr;gap:58px;margin-top:26px}.gallery{overflow:hidden}.main-visual{height:450px;display:grid;place-content:center;text-align:center;background:radial-gradient(circle,#f4e7bd 0 19%,transparent 20%),linear-gradient(145deg,#d9e9e1,#e7dcc7)}.main-visual span{font:900 72px 'Noto Serif SC',serif;color:var(--jade)}.main-visual small{letter-spacing:.3em;color:var(--muted)}.trace-strip{display:flex;align-items:center;gap:12px;padding:16px 20px;background:var(--jade-dark);color:#fff}.trace-strip .el-icon{font-size:25px;color:#e9c276}.trace-strip b,.trace-strip small{display:block}.trace-strip small{margin-top:3px;color:#9fb9b3}.trace-strip .el-button{margin-left:auto;color:#e9c276}.detail-copy{padding:16px 0}.detail-copy h1{margin:12px 0;font-size:36px}.detail-copy>p{color:var(--muted);line-height:1.8}.price-panel{display:flex;align-items:end;gap:14px;margin:24px 0;padding:20px;background:#efe8da}.price-panel>span{align-self:center;color:var(--muted)}dl{display:grid;grid-template-columns:80px 1fr;margin:0}dt,dd{margin:0;padding:10px 0;border-bottom:1px dashed var(--line);font-size:14px}dt{color:var(--muted)}.quantity{display:flex;align-items:center;gap:20px;margin:22px 0}.detail-actions{display:flex;gap:12px}.detail-actions .el-button{min-width:150px}.promises{display:flex;gap:20px;margin-top:20px;color:var(--muted);font-size:12px}.promises span{display:flex;align-items:center;gap:5px}.promises svg{width:13px;color:var(--jade)}.detail-info{margin-top:60px;padding:24px;background:#fff;border:1px solid var(--line)}.content{padding:20px}.content h2{font-size:28px}.content>p{max-width:850px;color:var(--muted);line-height:1.9}.route{display:grid;grid-template-columns:repeat(3,1fr);margin-top:30px;border:1px solid var(--line)}.route article{display:flex;flex-direction:column;align-items:center;gap:8px;padding:28px;border-right:1px solid var(--line)}.route article:last-child{border:0}.route .el-icon{font-size:27px;color:var(--cinnabar)}.route span{color:var(--muted);font-size:12px}@media(max-width:850px){.detail-grid{grid-template-columns:1fr}.main-visual{height:320px}}@media(max-width:550px){.route{grid-template-columns:1fr}.route article{border-right:0;border-bottom:1px solid var(--line)}.promises{flex-wrap:wrap}}</style>
