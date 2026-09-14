<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import {
    getKnowledge,
    getKnowledgeContent,
    getKnowledgeMetadata,
    type KnowledgeDocument,
    type KnowledgeMetadata,
} from "@/api/knowledge";
import { useBinaryDocumentPreview } from "@/composables/useBinaryDocumentPreview";
const route = useRoute();
const loading = ref(true);
const article = ref<KnowledgeDocument>();
const metadata = ref<KnowledgeMetadata>();
const preview = useBinaryDocumentPreview();
onMounted(async () => {
    try {
        [article.value, metadata.value] = await Promise.all([
            getKnowledge(String(route.params.id)),
            getKnowledgeMetadata(String(route.params.id)),
        ]);
        if (article.value) {
            await preview.open({
                fileName: article.value.fileName,
                mediaType: article.value.mediaType,
                load: () => getKnowledgeContent(article.value!.id),
            });
        }
    } catch {
        ElMessage.error("知识文档加载失败");
    } finally {
        loading.value = false;
    }
});
</script>
<template>
    <div v-loading="loading" class="page-shell">
        <div class="container article-layout">
            <article class="paper-card article">
                <el-breadcrumb separator="/"
                    ><el-breadcrumb-item to="/knowledge"
                        >知识中心</el-breadcrumb-item
                    ><el-breadcrumb-item>{{
                        article?.category
                    }}</el-breadcrumb-item></el-breadcrumb
                >
                <header>
                    <el-tag effect="plain">{{ article?.category }}</el-tag>
                    <h1 class="serif">{{ article?.title }}</h1>
                    <div>
                        {{
                            metadata?.issuingAuthority ||
                            metadata?.sourceName ||
                            "企业知识库"
                        }}
                        · 更新于
                        {{
                            article
                                ? new Date(article.updatedAt).toLocaleString(
                                      "zh-CN",
                                  )
                                : ""
                        }}
                        · 当前版本 {{ article?.version }}
                    </div>
                </header>
                <el-alert
                    title="发布状态：有效"
                    type="success"
                    show-icon
                    :closable="false"
                    description="本文已通过审核并进入全文与向量检索索引。引用时请以当前版本为准。"
                />
                <section>
                    <h2>文档档案</h2>
                    <el-descriptions :column="2" border
                        ><el-descriptions-item label="文件名">{{
                            article?.fileName
                        }}</el-descriptions-item
                        ><el-descriptions-item label="媒体类型">{{
                            article?.mediaType
                        }}</el-descriptions-item
                        ><el-descriptions-item label="发布机构">{{
                            metadata?.issuingAuthority || "--"
                        }}</el-descriptions-item
                        ><el-descriptions-item label="适用地区">{{
                            metadata?.region || "--"
                        }}</el-descriptions-item
                        ><el-descriptions-item label="生效日期">{{
                            metadata?.effectiveFrom || "--"
                        }}</el-descriptions-item
                        ><el-descriptions-item label="失效时间">{{
                            metadata?.expiresAt || "长期有效"
                        }}</el-descriptions-item
                        ><el-descriptions-item label="密级">{{
                            metadata?.classification
                        }}</el-descriptions-item
                        ><el-descriptions-item label="来源">{{
                            metadata?.sourceName || "--"
                        }}</el-descriptions-item></el-descriptions
                    >
                    <h2>内容使用说明</h2>
                    <p>
                        下方正文来自受控附件服务，不根据标题虚构政策条款。PDF、TXT 和 Markdown
                        可在页面内查看，DOCX 等格式使用原文件下载。
                    </p>
                    <div class="document-viewer">
                        <div class="viewer-head">
                            <div>
                                <b>{{ preview.fileName.value || article?.fileName }}</b>
                                <small>{{ preview.mediaType.value || article?.mediaType }}</small>
                            </div>
                            <el-button
                                v-if="preview.mode.value !== 'empty' && preview.mode.value !== 'loading'"
                                @click="preview.download"
                                >下载原文件</el-button
                            >
                        </div>
                        <el-skeleton v-if="preview.mode.value === 'loading'" :rows="8" animated />
                        <iframe
                            v-else-if="preview.mode.value === 'pdf'"
                            :src="preview.objectUrl.value"
                            :title="preview.fileName.value"
                        />
                        <pre v-else-if="preview.mode.value === 'text'">{{ preview.text.value }}</pre>
                        <el-empty
                            v-else-if="preview.mode.value === 'download'"
                            description="该格式需下载后使用本机办公软件查看"
                        >
                            <el-button type="primary" @click="preview.download">下载文档</el-button>
                        </el-empty>
                    </div>
                    <blockquote>
                        未审核、已驳回、已下线、失效或超出当前用户权限的知识不会进入有效检索结果。
                    </blockquote>
                </section>
            </article>
            <aside>
                <div class="paper-card toc">
                    <b>文档标签</b
                    ><el-tag
                        v-for="tag in metadata?.tags || []"
                        :key="tag"
                        effect="plain"
                        >{{ tag }}</el-tag
                    ><span v-if="!metadata?.tags?.length">暂无标签</span>
                </div>
                <div class="paper-card ask">
                    <b>需要结合业务提问？</b>
                    <p>AI 客服会在回答中附上知识引用。</p>
                    <el-button
                        type="primary"
                        @click="
                            $router.push({
                                path: '/ai-service',
                                query: { q: article?.title },
                            })
                        "
                        >基于本文提问</el-button
                    >
                </div>
            </aside>
        </div>
    </div>
</template>
<style scoped>
.article-layout {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 260px;
    gap: 26px;
}
.article {
    padding: 36px 44px;
}
.article header {
    padding: 28px 0;
    border-bottom: 1px solid var(--line);
    margin-bottom: 20px;
}
.article h1 {
    margin: 14px 0;
    font-size: 36px;
}
.article header div {
    color: var(--muted);
    font-size: 13px;
}
.article section {
    font-size: 15px;
    line-height: 2;
}
.article section h2 {
    margin-top: 38px;
    font: 700 22px serif;
}
.article blockquote {
    margin: 25px 0 0;
    padding: 18px 22px;
    background: #f3eee2;
    border-left: 4px solid var(--cinnabar);
}
.document-viewer {
    min-height: 280px;
    margin-top: 18px;
    border: 1px solid var(--line);
    background: #fbfaf6;
}
.viewer-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 20px;
    padding: 14px 18px;
    border-bottom: 1px solid var(--line);
}
.viewer-head b,
.viewer-head small {
    display: block;
}
.viewer-head small {
    color: var(--muted);
    font-size: 11px;
}
.document-viewer iframe {
    width: 100%;
    height: 680px;
    border: 0;
}
.document-viewer pre {
    max-height: 680px;
    margin: 0;
    padding: 28px;
    overflow: auto;
    white-space: pre-wrap;
    word-break: break-word;
    font: 14px/1.9 "Noto Sans SC", sans-serif;
}
aside > div {
    padding: 22px;
    margin-bottom: 18px;
}
.toc {
    display: flex;
    flex-direction: column;
    align-items: start;
    gap: 10px;
}
.ask {
    background: var(--jade-dark);
    color: white;
}
.ask p {
    color: #aac1bb;
    font-size: 13px;
    line-height: 1.7;
}
@media (max-width: 800px) {
    .article-layout {
        grid-template-columns: 1fr;
    }
    .article {
        padding: 24px;
    }
}
</style>
