<script setup lang="ts">
import { onMounted, ref } from "vue";
import {
    ArrowRight,
    ChatLineRound,
    Connection,
    DocumentChecked,
    Goods,
    Reading,
} from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import ProductCard from "@/components/ProductCard.vue";
import { addCartItem } from "@/api/cart";
import { listKnowledge, type KnowledgeDocument } from "@/api/knowledge";
import { listCategories, listProducts, productSummary } from "@/api/product";
import type { ProductSummary } from "@ygh/web-shared";
import { useSessionStore } from "@ygh/web-shared";

const products = ref<ProductSummary[]>([]);
const knowledgeArticles = ref<KnowledgeDocument[]>([]);
const session = useSessionStore();
async function load() {
    try {
        const [rawProducts, categories] = await Promise.all([
            listProducts({ limit: 4 }),
            listCategories(),
        ]);
        const names = Object.fromEntries(
            categories.map((category) => [category.id, category.name]),
        );
        products.value = rawProducts.map((product) =>
            productSummary(product, names[product.categoryId] || "跨境甄选"),
        );
    } catch {
        ElMessage.error("首页推荐内容加载失败");
    }
    if (!session.authenticated) {
        knowledgeArticles.value = [];
        return;
    }
    try {
        knowledgeArticles.value = await listKnowledge(undefined, 3);
    } catch {
        knowledgeArticles.value = [];
    }
}
async function add(product: ProductSummary) {
    try {
        await addCartItem(product.id, 1);
        ElMessage.success("已加入购物车");
    } catch {
        ElMessage.error("加入购物车失败，请先登录");
    }
}
onMounted(load);
</script>
<template>
    <section class="hero">
        <div class="container hero-grid">
            <div class="hero-copy">
                <div class="overline">
                    <span class="status-dot" />企业可信跨境服务平台
                </div>
                <h1 class="serif">一件好物，<br /><em>一条可信来路。</em></h1>
                <p>
                    把岭南风物、港澳手信和全球甄选带到消费者面前，也把商品溯源、通关政策与岗位知识放在同一个可信入口。
                </p>
                <div class="hero-actions">
                    <el-button
                        size="large"
                        type="primary"
                        @click="$router.push('/products')"
                        >进入跨境商城<el-icon
                            ><ArrowRight /></el-icon></el-button
                    ><el-button
                        size="large"
                        @click="$router.push('/ai-service')"
                        >咨询 AI 客服</el-button
                    >
                </div>
                <div class="hero-trust">
                    <span><b>3</b> 大知识域</span
                    ><span><b>100%</b> 商品溯源展示</span
                    ><span><b>7×24h</b> 专业问答</span>
                </div>
            </div>
            <div class="hero-art">
                <div class="harbor-lines" />
                <div class="cargo-stamp">
                    <small>TRACEABLE</small><b>粤港澳</b><span>跨境甄选</span>
                </div>
                <div class="route-card">
                    <span>广东高州</span><i /><span>平台质检</span><i /><span
                        >模拟交付</span
                    >
                </div>
                <div class="hero-note">
                    本平台支付、发货均为教学模拟<br />业务知识来自审核发布版本
                </div>
            </div>
        </div>
    </section>
    <section class="category-strip">
        <div class="container">
            <article
                v-for="item in [
                    { i: Goods, t: '岭南特产', s: '荔枝、红茶与地标农品' },
                    { i: Connection, t: '跨境甄选', s: '港澳手信与全球零食' },
                    {
                        i: DocumentChecked,
                        t: '可信溯源',
                        s: '批次、产地与检验信息',
                    },
                    { i: Reading, t: '政策知识', s: '法规、通关与商品知识' },
                    {
                        i: ChatLineRound,
                        t: 'AI 专业客服',
                        s: '回答有引用，边界可解释',
                    },
                ]"
                :key="item.t"
            >
                <el-icon><component :is="item.i" /></el-icon>
                <div>
                    <b>{{ item.t }}</b
                    ><small>{{ item.s }}</small>
                </div>
            </article>
        </div>
    </section>
    <section class="container home-section">
        <div class="section-head">
            <div>
                <span class="section-label">CURATED GOODS</span>
                <h2>湾区甄选 · 今日好物</h2>
            </div>
            <el-button text @click="$router.push('/products')"
                >查看全部商品 <el-icon><ArrowRight /></el-icon
            ></el-button>
        </div>
        <div class="product-grid">
            <ProductCard
                v-for="p in products"
                :key="p.id"
                :product="p"
                @add="add"
            />
        </div>
        <el-empty v-if="!products.length" description="暂无在售推荐商品" />
    </section>
    <section class="knowledge-band">
        <div class="container knowledge-grid">
            <div class="knowledge-intro">
                <span class="section-label">KNOWLEDGE DESK</span>
                <h2 class="serif">跨境业务，<br />先问依据，再做判断。</h2>
                <p>
                    政策法规、通关流程、商品知识经过上传、解析、审核和发布后进入检索。AI
                    回答展示引用，不用模型猜测代替企业事实。
                </p>
                <el-button
                    type="primary"
                    plain
                    @click="$router.push('/knowledge')"
                    >进入知识中心</el-button
                >
            </div>
            <div class="article-stack">
                <RouterLink
                    v-for="article in knowledgeArticles"
                    :key="article.id"
                    :to="`/knowledge/${article.id}`"
                    ><span>{{ article.category }}</span>
                    <div>
                        <h3>{{ article.title }}</h3>
                        <small
                            >版本 {{ article.version }} ·
                            {{
                                new Date(article.updatedAt).toLocaleDateString(
                                    "zh-CN",
                                )
                            }}</small
                        >
                    </div>
                    <el-icon><ArrowRight /></el-icon></RouterLink
                ><el-empty
                    v-if="!knowledgeArticles.length"
                    description="暂无已发布知识"
                />
            </div>
        </div>
    </section>
    <section class="container home-section ai-preview">
        <div>
            <span class="section-label">TRUSTED AI SERVICE</span>
            <h2 class="serif">把复杂政策，讲得有根有据。</h2>
            <p>
                咨询进口限值、通关材料、商品批次或本人订单。AI
                只读授权数据，不执行 SQL，不修改订单和余额。
            </p>
            <div class="prompt-list">
                <button
                    v-for="q in [
                        '跨境电商零售进口单次限值是多少？',
                        '这盒杏仁饼的原产地和溯源码是什么？',
                        '我的订单为什么还是待支付？',
                    ]"
                    :key="q"
                    @click="$router.push({ path: '/ai-service', query: { q } })"
                >
                    {{ q }}<ArrowRight />
                </button>
            </div>
        </div>
        <div class="ai-window paper-card">
            <header>
                <span class="status-dot" /><b>跨境智汇 AI 客服</b
                ><small>依据已发布知识回答</small>
            </header>
            <div class="bubble user">进口冷链商品怎么看溯源信息？</div>
            <div class="bubble ai">
                建议依次核对原产地、入境检验检疫证明、批次号和冷链温控记录。平台商品详情的“可信溯源”区域会展示可用信息。
                <div class="citation">
                    引用 2 条 ·《进口冷链水产溯源信息识别指南》
                </div>
            </div>
        </div>
    </section>
</template>
<style scoped>
.hero {
    min-height: 600px;
    display: flex;
    align-items: center;
    overflow: hidden;
    background:
        linear-gradient(
            110deg,
            rgba(247, 244, 236, 0.98) 45%,
            rgba(210, 226, 217, 0.78)
        ),
        repeating-linear-gradient(
            135deg,
            transparent 0 28px,
            rgba(12, 91, 84, 0.04) 29px 30px
        );
}
.hero-grid {
    display: grid;
    grid-template-columns: 1.06fr 0.94fr;
    gap: 72px;
    align-items: center;
}
.overline {
    display: flex;
    align-items: center;
    gap: 13px;
    color: var(--jade);
    font-size: 13px;
    font-weight: 700;
    letter-spacing: 0.13em;
}
.hero h1 {
    margin: 22px 0;
    font-size: clamp(48px, 6vw, 76px);
    line-height: 1.16;
    letter-spacing: 0.04em;
}
.hero h1 em {
    color: var(--cinnabar);
    font-style: normal;
}
.hero-copy > p {
    max-width: 620px;
    color: var(--muted);
    font-size: 17px;
    line-height: 1.95;
}
.hero-actions {
    display: flex;
    gap: 12px;
    margin: 30px 0;
}
.hero-trust {
    display: flex;
    gap: 28px;
    padding-top: 25px;
    border-top: 1px solid var(--line);
    color: var(--muted);
    font-size: 12px;
}
.hero-trust b {
    display: block;
    color: var(--jade);
    font:
        700 20px "Noto Serif SC",
        serif;
}
.hero-art {
    height: 430px;
    position: relative;
    border: 1px solid rgba(12, 91, 84, 0.18);
    background: linear-gradient(160deg, #0c5b54, #063c39);
    box-shadow: 28px 28px 0 rgba(181, 72, 45, 0.12);
}
.harbor-lines {
    position: absolute;
    inset: 0;
    opacity: 0.2;
    background: linear-gradient(
            25deg,
            transparent 47%,
            #fff 48% 49%,
            transparent 50%
        )
        0 0/92px 92px;
}
.cargo-stamp {
    position: absolute;
    left: 50%;
    top: 45%;
    width: 225px;
    height: 225px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    transform: translate(-50%, -50%) rotate(-5deg);
    border: 2px solid #e0c589;
    outline: 1px solid #e0c589;
    outline-offset: -10px;
    border-radius: 50%;
    color: #f5e6bd;
}
.cargo-stamp small {
    letter-spacing: 0.35em;
}
.cargo-stamp b {
    font:
        900 43px "Noto Serif SC",
        serif;
    letter-spacing: 0.12em;
}
.cargo-stamp span {
    letter-spacing: 0.3em;
}
.route-card {
    position: absolute;
    left: 28px;
    right: 28px;
    bottom: 35px;
    display: flex;
    align-items: center;
    padding: 16px;
    background: rgba(247, 244, 236, 0.92);
    color: var(--ink);
    font-size: 11px;
}
.route-card i {
    flex: 1;
    height: 1px;
    margin: 0 10px;
    background: var(--gold);
}
.hero-note {
    position: absolute;
    right: -20px;
    top: 20px;
    padding: 12px 15px;
    background: var(--cinnabar);
    color: #fff;
    font-size: 10px;
    line-height: 1.6;
}
.category-strip {
    background: #fff;
    border-block: 1px solid var(--line);
}
.category-strip .container {
    display: grid;
    grid-template-columns: repeat(5, 1fr);
}
.category-strip article {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 24px 16px;
    border-right: 1px solid var(--line);
}
.category-strip article:first-child {
    border-left: 1px solid var(--line);
}
.category-strip .el-icon {
    font-size: 25px;
    color: var(--cinnabar);
}
.category-strip b,
.category-strip small {
    display: block;
}
.category-strip small {
    margin-top: 4px;
    color: var(--muted);
    font-size: 10px;
}
.home-section {
    padding: 76px 0;
}
.product-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 18px;
}
.knowledge-band {
    padding: 80px 0;
    background: var(--jade-dark);
    color: #fff;
}
.knowledge-grid {
    display: grid;
    grid-template-columns: 0.8fr 1.2fr;
    gap: 90px;
}
.knowledge-intro h2 {
    margin: 12px 0 20px;
    font-size: 38px;
}
.knowledge-intro p {
    color: #b8cbc6;
    line-height: 1.9;
}
.article-stack {
    border-top: 1px solid rgba(255, 255, 255, 0.15);
}
.article-stack a {
    display: grid;
    grid-template-columns: 85px 1fr 20px;
    gap: 20px;
    align-items: center;
    padding: 23px 0;
    border-bottom: 1px solid rgba(255, 255, 255, 0.15);
}
.article-stack > a > span {
    color: #e9c276;
    font-size: 12px;
}
.article-stack h3 {
    margin: 0 0 7px;
    font:
        600 17px "Noto Serif SC",
        serif;
}
.article-stack small {
    color: #91aaa4;
}
.ai-preview {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 80px;
    align-items: center;
}
.ai-preview h2 {
    font-size: 38px;
}
.ai-preview > div > p {
    color: var(--muted);
    line-height: 1.9;
}
.prompt-list {
    display: grid;
    gap: 8px;
    margin-top: 25px;
}
.prompt-list button {
    display: flex;
    justify-content: space-between;
    padding: 13px;
    border: 1px solid var(--line);
    background: rgba(255, 255, 255, 0.65);
    color: var(--ink);
    cursor: pointer;
}
.prompt-list svg {
    width: 16px;
}
.ai-window {
    padding: 20px;
}
.ai-window header {
    display: flex;
    align-items: center;
    gap: 10px;
    padding-bottom: 16px;
    border-bottom: 1px solid var(--line);
}
.ai-window header small {
    margin-left: auto;
    color: var(--muted);
}
.bubble {
    max-width: 88%;
    margin-top: 18px;
    padding: 13px 15px;
    border-radius: 12px;
    line-height: 1.7;
    font-size: 14px;
}
.bubble.user {
    margin-left: auto;
    background: var(--jade);
    color: white;
    border-bottom-right-radius: 2px;
}
.bubble.ai {
    background: #f1eee5;
    border-bottom-left-radius: 2px;
}
.citation {
    margin-top: 11px;
    padding-top: 9px;
    border-top: 1px solid var(--line);
    color: var(--jade);
    font-size: 11px;
}
@media (max-width: 900px) {
    .hero-grid,
    .knowledge-grid,
    .ai-preview {
        grid-template-columns: 1fr;
    }
    .hero {
        padding: 70px 0;
    }
    .hero-art {
        display: none;
    }
    .category-strip .container {
        grid-template-columns: repeat(2, 1fr);
    }
    .product-grid {
        grid-template-columns: repeat(2, 1fr);
    }
}
@media (max-width: 600px) {
    .hero h1 {
        font-size: 42px;
    }
    .hero-trust {
        flex-wrap: wrap;
    }
    .product-grid,
    .category-strip .container {
        grid-template-columns: 1fr;
    }
}
</style>
