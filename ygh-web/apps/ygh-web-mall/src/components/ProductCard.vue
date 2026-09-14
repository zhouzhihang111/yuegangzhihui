<script setup lang="ts">
import type { ProductSummary } from '@ygh/web-shared'
import { ShoppingCart } from '@element-plus/icons-vue'
defineProps<{ product: ProductSummary }>()
defineEmits<{ add: [product: ProductSummary] }>()
;</script>
<template>
  <article class="product-card">
    <RouterLink :to="`/products/${product.id}`" class="visual">
      <img v-if="product.imageUrl" :src="product.imageUrl" :alt="product.name" />
      <div v-else class="product-glyph"><span>{{product.origin.slice(0,2)}}</span><small>{{product.categoryName}}</small></div>
      <span class="origin">产地 · {{product.origin}}</span>
    </RouterLink>
    <div class="product-copy">
      <small>{{product.categoryName}} / {{product.spuCode}}</small>
      <RouterLink :to="`/products/${product.id}`"><h3>{{product.name}}</h3></RouterLink>
      <div class="specs"><span v-for="(v,k) in product.specifications" :key="k">{{k}} {{v}}</span></div>
      <div class="product-foot"><div class="price">¥ <strong>{{product.price}}</strong></div><el-button circle :icon="ShoppingCart" type="primary" aria-label="加入购物车" @click="$emit('add',product)" /></div>
    </div>
  </article>
</template>
<style scoped>
.product-card{overflow:hidden;background:#fff;border:1px solid var(--line);border-radius:12px;transition:.25s ease}.product-card:hover{transform:translateY(-5px);box-shadow:var(--shadow)}.visual{height:210px;position:relative;display:block;overflow:hidden;background:linear-gradient(145deg,#e7eee8,#d5dfd4)}.visual img{width:100%;height:100%;object-fit:cover;transition:transform .4s}.product-card:hover img{transform:scale(1.04)}.product-glyph{height:100%;display:grid;place-content:center;text-align:center;background:radial-gradient(circle,#f7f2df 0 22%,transparent 23%),linear-gradient(135deg,#d9e7df,#e9ddc5)}.product-glyph span{font:900 42px 'Noto Serif SC',serif;color:var(--jade)}.product-glyph small{color:var(--muted);letter-spacing:.2em}.origin{position:absolute;left:12px;top:12px;padding:5px 9px;background:rgba(247,244,236,.9);border:1px solid rgba(255,255,255,.7);font-size:11px}.product-copy{padding:17px}.product-copy>small{color:var(--cinnabar);font-size:11px}.product-copy h3{height:44px;margin:7px 0;font:600 16px/1.45 'Noto Serif SC',serif}.specs{height:23px;display:flex;gap:6px;overflow:hidden}.specs span{padding:3px 6px;background:#f2f4ef;color:var(--muted);font-size:10px;white-space:nowrap}.product-foot{display:flex;align-items:end;justify-content:space-between;margin-top:13px}
</style>
