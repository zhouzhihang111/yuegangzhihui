<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { Search } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import ProductCard from "@/components/ProductCard.vue";
import { listCategories, listProducts, productSummary } from "@/api/product";
import { addCartItem } from "@/api/cart";

const keyword = ref("");
const category = ref("");
const origin = ref("");
const minimumPrice = ref<number>();
const maximumPrice = ref<number>();
const sort = ref("综合排序");
const loading = ref(true);
const categoryOptions = ref<Array<{ id: string; name: string }>>([]);
const raw = ref<Awaited<ReturnType<typeof listProducts>>>([]);
const categoryNames = computed(() =>
    Object.fromEntries(categoryOptions.value.map((item) => [item.id, item.name])),
);
const filtered = computed(() =>
    raw.value
        .map((product) =>
            productSummary(
                product,
                categoryNames.value[product.categoryId] || "跨境甄选",
            ),
        )
        .sort((left, right) =>
            sort.value === "价格从低到高"
                ? Number(left.price) - Number(right.price)
                : sort.value === "价格从高到低"
                  ? Number(right.price) - Number(left.price)
                  : 0,
        ),
);

async function load() {
    if (
        minimumPrice.value !== undefined &&
        maximumPrice.value !== undefined &&
        minimumPrice.value > maximumPrice.value
    ) {
        ElMessage.warning("最低价格不能高于最高价格");
        return;
    }
    loading.value = true;
    try {
        const [products, categories] = await Promise.all([
            listProducts({
                category: category.value || undefined,
                keyword: keyword.value || undefined,
                origin: origin.value || undefined,
                minPrice: minimumPrice.value,
                maxPrice: maximumPrice.value,
                limit: 100,
            }),
            listCategories(),
        ]);
        raw.value = products;
        categoryOptions.value = categories;
    } catch {
        ElMessage.error("商品目录加载失败");
    } finally {
        loading.value = false;
    }
}

async function reset() {
    keyword.value = "";
    category.value = "";
    origin.value = "";
    minimumPrice.value = undefined;
    maximumPrice.value = undefined;
    await load();
}

async function add(skuId: string) {
    try {
        await addCartItem(skuId, 1);
        ElMessage.success("已加入购物车");
    } catch {
        ElMessage.error("加入购物车失败，请先登录或检查商品状态");
    }
}

onMounted(load);
</script>
<template>
    <div class="page-shell">
        <div class="container">
            <div class="market-head">
                <div><span class="section-label">CROSS-BORDER MARKET</span><h1 class="page-title">跨境甄选商城</h1><p class="page-lead">供港生鲜、岭南特产、港澳手信与跨境零食。商品展示溯源信息，结算使用模拟钱包。</p></div>
                <div class="search"><el-input v-model="keyword" size="large" placeholder="搜索商品或规格" :prefix-icon="Search" clearable @keyup.enter="load"><template #append><el-button @click="load">搜索</el-button></template></el-input></div>
            </div>
            <div class="filter-bar paper-card">
                <div class="category-filter"><span>商品分类</span><button :class="{ active: !category }" @click="category = ''; load()">全部</button><button v-for="item in categoryOptions" :key="item.id" :class="{ active: category === item.id }" @click="category = item.id; load()">{{ item.name }}</button></div>
                <div class="advanced-filter">
                    <el-input v-model="origin" clearable placeholder="批次产地（精确）" @keyup.enter="load" />
                    <el-input-number v-model="minimumPrice" :min="0" :precision="2" :controls="false" placeholder="最低价" />
                    <span>—</span>
                    <el-input-number v-model="maximumPrice" :min="0" :precision="2" :controls="false" placeholder="最高价" />
                    <el-select v-model="sort"><el-option v-for="item in ['综合排序', '价格从低到高', '价格从高到低']" :key="item" :label="item" :value="item" /></el-select>
                    <el-button type="primary" @click="load">应用筛选</el-button>
                    <el-button @click="reset">重置</el-button>
                </div>
            </div>
            <div class="result-line"><span>共找到 <b>{{ filtered.length }}</b> 件在售商品</span><small>价格、商品状态与库存以结算预览为准</small></div>
            <div v-loading="loading" class="product-grid"><ProductCard v-for="product in filtered" :key="product.id" :product="product" @add="add(product.id)" /></div>
            <div v-if="!loading && !filtered.length" class="empty-state paper-card">没有符合当前条件的商品，请调整筛选条件。</div>
        </div>
    </div>
</template>
<style scoped>
.page-shell .filter-bar { display: grid; gap: 16px; }
.filter-bar > .category-filter { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.filter-bar > .advanced-filter { display: grid; grid-template-columns: minmax(170px, 1fr) 110px auto 110px 160px auto auto; align-items: center; gap: 8px; width: 100%; }
.filter-bar > .advanced-filter .el-select { width: 160px; }
@media (max-width: 900px) { .filter-bar > .advanced-filter { grid-template-columns: 1fr 1fr; } .filter-bar > .advanced-filter .el-select { width: 100%; } }
@media (max-width: 550px) { .filter-bar > .advanced-filter { grid-template-columns: 1fr; } .filter-bar > .advanced-filter > span { display: none; } }
</style>
<style scoped>.market-head{display:flex;align-items:end;justify-content:space-between;margin-bottom:28px}.search{width:380px}.filter-bar{display:flex;align-items:center;justify-content:space-between;padding:18px 20px}.filter-bar>div{display:flex;align-items:center;gap:8px}.filter-bar span{margin-right:10px;color:var(--muted);font-size:13px}.filter-bar button{padding:7px 12px;border:0;border-radius:999px;background:transparent;color:#53635f;cursor:pointer}.filter-bar button.active{background:var(--jade);color:white}.result-line{display:flex;justify-content:space-between;margin:24px 2px 14px;color:var(--muted);font-size:13px}.result-line b{color:var(--cinnabar)}.product-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:18px}@media(max-width:900px){.market-head{align-items:start;flex-direction:column;gap:20px}.search{width:100%}.product-grid{grid-template-columns:repeat(2,1fr)}.filter-bar{align-items:start;flex-direction:column;gap:14px}.filter-bar>div{flex-wrap:wrap}}@media(max-width:550px){.product-grid{grid-template-columns:1fr}}</style>
