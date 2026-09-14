<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { Search, Reading } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import PageHeader from "@/components/PageHeader.vue";
import {
    listKnowledge,
    searchKnowledge,
    type KnowledgeDocument,
    type KnowledgeSearchHit,
} from "@/api/knowledge";
const keyword = ref("");
const category = ref("");
const loading = ref(true);
const documents = ref<KnowledgeDocument[]>([]);
const searchResults = ref<KnowledgeSearchHit[]>([]);
const categories = ["政策法规", "通关流程", "商品知识"];
const searching = computed(() => keyword.value.trim().length > 0);
const resultCount = computed(() =>
    searching.value ? searchResults.value.length : documents.value.length,
);
async function load() {
    loading.value = true;
    try {
        documents.value = await listKnowledge(category.value);
    } catch {
        ElMessage.error("知识目录加载失败");
    } finally {
        loading.value = false;
    }
}
async function runSearch() {
    const query = keyword.value.trim();
    if (!query) {
        searchResults.value = [];
        await load();
        return;
    }
    loading.value = true;
    try {
        searchResults.value = await searchKnowledge(query, category.value);
    } catch {
        ElMessage.error("知识检索失败，请稍后重试");
    } finally {
        loading.value = false;
    }
}
async function selectCategory(value: string) {
    category.value = value;
    await (searching.value ? runSearch() : load());
}
onMounted(load);
</script>
<template>
    <div class="knowledge-hero">
        <div class="container">
            <span class="section-label">ENTERPRISE KNOWLEDGE BASE</span>
            <h1 class="serif">跨境知识，有版本、有审核、有出处。</h1>
            <p>
                聚合商品知识、政策法规与通关流程。只有审核发布且在有效期内的内容进入检索与
                AI 问答。
            </p>
            <el-input
                v-model="keyword"
                size="large"
                :prefix-icon="Search"
                placeholder="搜索政策名称、通关环节、商品知识……"
                clearable
                @keyup.enter="runSearch"
                @clear="runSearch"
            />
            <el-button type="warning" size="large" @click="runSearch">检索知识</el-button>
        </div>
    </div>
    <div class="page-shell">
        <div class="container knowledge-layout">
            <aside class="paper-card">
                <b>知识分类</b
                ><button
                    :class="{ active: !category }"
                    @click="
                        selectCategory('');
                    "
                >
                    全部<span>{{ documents.length }}</span></button
                ><button
                    v-for="c in categories"
                    :key="c"
                    :class="{ active: category === c }"
                    @click="
                        selectCategory(c);
                    "
                >
                    {{ c }}
                </button>
                <div class="ai-entry">
                    <el-icon><Reading /></el-icon><b>找不到答案？</b>
                    <p>让 AI 在已发布知识中检索并标注引用。</p>
                    <el-button
                        type="primary"
                        size="small"
                        @click="$router.push('/ai-service')"
                        >咨询 AI</el-button
                    >
                </div>
            </aside>
            <main v-loading="loading">
                <PageHeader
                    eyebrow="PUBLISHED KNOWLEDGE"
                    :title="category || '全部已发布知识'"
                    :description="`共 ${resultCount} 条有效内容，${searching ? '按混合检索相关度' : '按最近更新'}排序`"
                />
                <el-alert
                    class="publish-notice"
                    type="info"
                    :closable="false"
                    show-icon
                    title="这里只展示已审核发布的知识"
                    description="刚上传的文件会先进行安全校验和内容解析，然后进入运营后台待审核；管理员审核通过后，才会在此处显示。"
                />
                <el-empty
                    v-if="!loading && !resultCount"
                    description="没有符合条件的已发布知识"
                />
                <div class="article-list">
                    <template v-if="!searching">
                        <RouterLink
                            v-for="article in documents"
                            :key="article.id"
                            :to="`/knowledge/${article.id}`"
                            ><div class="article-mark">
                                {{ article.category.slice(0, 1) }}
                            </div>
                            <div>
                                <div class="meta">
                                    <el-tag size="small" effect="plain">{{
                                        article.category
                                    }}</el-tag
                                    ><span>版本 {{ article.version }}</span
                                    ><span>{{ article.mediaType }}</span>
                                </div>
                                <h2 class="serif">{{ article.title }}</h2>
                                <p>
                                    来源文件：{{ article.fileName }} · 校验摘要
                                    {{ article.sha256.slice(0, 12) }}…
                                </p>
                                <small
                                    >更新于
                                    {{
                                        new Date(article.updatedAt).toLocaleString(
                                            "zh-CN",
                                        )
                                    }}
                                    · 已审核发布</small
                                >
                            </div></RouterLink
                        >
                    </template>
                    <template v-else>
                        <RouterLink
                            v-for="hit in searchResults"
                            :key="hit.chunkId"
                            :to="`/knowledge/${hit.documentId}`"
                        >
                            <div class="article-mark">检</div>
                            <div>
                                <div class="meta">
                                    <el-tag size="small" effect="plain">{{ category || "相关知识" }}</el-tag>
                                    <span>版本 {{ hit.documentVersion }}</span>
                                    <span>相关度 {{ hit.finalScore.toFixed(3) }}</span>
                                </div>
                                <h2 class="serif">{{ hit.title }}</h2>
                                <p>{{ hit.excerpt }}</p>
                                <small>
                                    {{ hit.sourceUpdatedAt ? `来源更新于 ${new Date(hit.sourceUpdatedAt).toLocaleString("zh-CN")}` : "已审核发布" }}
                                </small>
                            </div>
                        </RouterLink>
                    </template>
                </div>
            </main>
        </div>
    </div>
</template>
<style scoped>
.knowledge-hero {
    padding: 70px 0;
    background: linear-gradient(120deg, #083d39, #0c5b54);
    color: #fff;
}
.knowledge-hero h1 {
    margin: 12px 0;
    font-size: 43px;
}
.knowledge-hero p {
    color: #b7cbc5;
}
.knowledge-hero .el-input {
    width: min(680px, 100%);
    margin-top: 22px;
}
.knowledge-hero .el-button {
    margin: 22px 0 0 10px;
}
.knowledge-layout {
    display: grid;
    grid-template-columns: 230px 1fr;
    gap: 34px;
}
.knowledge-layout aside {
    height: max-content;
    padding: 20px;
    position: sticky;
    top: 120px;
}
.knowledge-layout aside > b {
    display: block;
    margin: 5px 8px 14px;
}
.knowledge-layout aside > button {
    width: 100%;
    display: flex;
    justify-content: space-between;
    padding: 10px 12px;
    border: 0;
    border-radius: 7px;
    background: transparent;
    color: var(--muted);
    cursor: pointer;
}
.knowledge-layout aside > button.active {
    background: var(--jade-soft);
    color: var(--jade);
    font-weight: 700;
}
.ai-entry {
    margin-top: 22px;
    padding: 18px;
    background: #f1ecdf;
    border-top: 3px solid var(--gold);
}
.ai-entry .el-icon {
    color: var(--cinnabar);
    font-size: 24px;
}
.ai-entry b {
    display: block;
    margin-top: 8px;
}
.ai-entry p {
    color: var(--muted);
    font-size: 12px;
    line-height: 1.7;
}
.article-list {
    display: grid;
    gap: 12px;
}
.publish-notice {
    margin-bottom: 16px;
}
.article-list > a {
    display: grid;
    grid-template-columns: 55px 1fr;
    gap: 18px;
    padding: 22px;
    background: #fff;
    border: 1px solid var(--line);
}
.article-mark {
    width: 52px;
    height: 58px;
    display: grid;
    place-items: center;
    background: var(--jade);
    color: #fff;
    font: 700 24px serif;
}
.meta {
    display: flex;
    gap: 13px;
    color: var(--muted);
    font-size: 11px;
}
.article-list h2 {
    margin: 10px 0 7px;
    font-size: 19px;
}
.article-list p,
.article-list small {
    color: var(--muted);
}
@media (max-width: 750px) {
    .knowledge-layout {
        grid-template-columns: 1fr;
    }
    .knowledge-layout aside {
        position: static;
    }
}
</style>
