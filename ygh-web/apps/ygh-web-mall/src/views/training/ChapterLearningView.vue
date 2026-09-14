<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import {
    ArrowLeft,
    ArrowRight,
    Clock,
    Document,
} from "@element-plus/icons-vue";
import {
    heartbeat,
    completeDocument,
    getTrainingDocumentContent,
    listChapterDocuments,
    listDocumentProgress,
    recordPosition,
    type Progress,
    type DocumentProgress,
    type TrainingDocument,
} from "@/api/training";
import { useBinaryDocumentPreview } from "@/composables/useBinaryDocumentPreview";
const route = useRoute();
const router = useRouter();
const chapterId = String(route.params.id);
const assignmentId = String(route.query.assignment || "");
const gateId = String(route.query.gate || "");
const seconds = ref(0);
const unsaved = ref(0);
const documents = ref<TrainingDocument[]>([]);
const documentProgress = ref<DocumentProgress[]>([]);
const progress = ref<Progress>();
const saving = ref(false);
const saveBlocked = ref(false);
const preview = useBinaryDocumentPreview();
const allDocumentsCompleted = computed(() =>
    documents.value.every((item) => state(item.id) === "COMPLETED"),
);
let timer = 0;
function isBusinessConflict(error: unknown) {
    const response = (error as {
        response?: { status?: number; data?: { code?: string } };
    }).response;
    return response?.status === 409 || response?.data?.code === "BUSINESS_CONFLICT";
}
function blockSaving(message: string) {
    if (!saveBlocked.value) ElMessage.error(message);
    saveBlocked.value = true;
    clearInterval(timer);
}
async function openDocument(item: TrainingDocument) {
    try {
        await preview.open({
            fileName: item.fileName,
            mediaType: item.mediaType,
            load: () => getTrainingDocumentContent(item.id, assignmentId),
        });
        documentProgress.value = await listDocumentProgress(assignmentId, chapterId);
    } catch {
        ElMessage.error("培训文档读取失败或当前账号无权访问");
    }
}
function state(documentId: string) {
    return documentProgress.value.find((item) => item.documentId === documentId)?.status || "NOT_STARTED";
}
function stateText(status: string) {
    const labels: Record<string, string> = { NOT_STARTED: "未开始", IN_PROGRESS: "未完成", COMPLETED: "已完成" };
    return labels[status] || status;
}
async function markCompleted(item: TrainingDocument) {
    try {
        if (!(await save())) return;
        await completeDocument(assignmentId, item.id);
        documentProgress.value = await listDocumentProgress(assignmentId, chapterId);
        progress.value = await heartbeat(assignmentId, chapterId, 1);
        ElMessage.success("文档任务点已完成");
    } catch {
        ElMessage.error("请先打开并阅读该文档，再标记完成");
    }
}
async function save() {
    if (saveBlocked.value) return false;
    if (!assignmentId || unsaved.value <= 0) return true;
    saving.value = true;
    const active = Math.min(unsaved.value, 300);
    try {
        progress.value = await heartbeat(assignmentId, chapterId, active);
        await recordPosition(
            assignmentId,
            chapterId,
            `active-seconds:${seconds.value}`,
        );
        unsaved.value -= active;
        return true;
    } catch (error) {
        if (isBusinessConflict(error)) {
            blockSaving("当前章节尚未解锁，请先完成前置章节和闯关");
        } else {
            ElMessage.error("学习进度保存失败，请保持页面打开后重试");
        }
        return false;
    } finally {
        saving.value = false;
    }
}
async function finish() {
    if (!allDocumentsCompleted.value) {
        ElMessage.warning("请先阅读并完成本章全部文档任务点");
        return;
    }
    if (!(await save())) return;
    if (gateId)
        await router.push({
            path: `/workspace/training/quiz/${gateId}`,
            query: { assignment: assignmentId },
        });
    else {
        ElMessage.success("本章没有闯关，进度已由服务端计算");
        router.back();
    }
}
onMounted(async () => {
    if (!assignmentId) {
        ElMessage.error("缺少学习任务参数");
        return;
    }
    try {
        [documents.value, documentProgress.value] = await Promise.all([
            listChapterDocuments(chapterId),
            listDocumentProgress(assignmentId, chapterId),
        ]);
    } catch {
        ElMessage.error("培训文档加载失败");
    }
    timer = window.setInterval(() => {
        if (document.visibilityState === "visible") {
            seconds.value++;
            unsaved.value++;
            if (unsaved.value >= 60) void save();
        }
    }, 1000);
});
onBeforeUnmount(() => {
    clearInterval(timer);
    if (!saveBlocked.value) void save();
});
</script>
<template>
    <div class="learning">
        <header>
            <el-button text :icon="ArrowLeft" @click="$router.back()"
                >返回课程</el-button
            ><b class="serif">章节学习</b>
            <div>
                <el-icon><Clock /></el-icon>{{ Math.floor(seconds / 60) }}:{{
                    String(seconds % 60).padStart(2, "0")
                }}
                本次有效学习
            </div>
        </header>
        <main>
            <section class="paper">
                <span class="label">企业培训文档</span>
                <h1 class="serif">受控课程资料</h1>
                <p class="lead">
                    系统仅在页面可见期间累计学习时间，每 60
                    秒向服务端发送一次带唯一 nonce
                    的心跳。关闭页面前会尝试保存剩余时长。
                </p>
                <el-alert
                    title="完成状态由服务端计算"
                    description="请逐一打开文档阅读并标记完成。服务端会同时校验文档任务点、有效学习时长和闯关结果。"
                    type="warning"
                    :closable="false"
                />
                <div class="documents">
                    <article v-for="item in documents" :key="item.id">
                        <el-icon><Document /></el-icon>
                        <div>
                            <b>{{ item.fileName }}</b
                            ><small
                                >{{ item.mediaType }} ·
                                {{
                                    (item.sizeBytes / 1024).toFixed(1)
                                }}
                                KB</small
                            >
                        </div>
                        <div class="document-actions">
                            <el-tag :type="state(item.id) === 'COMPLETED' ? 'success' : state(item.id) === 'IN_PROGRESS' ? 'warning' : 'info'">{{ stateText(state(item.id)) }}</el-tag>
                            <el-button size="small" @click="openDocument(item)"
                                >查看</el-button
                            >
                            <el-button size="small" type="primary" :disabled="state(item.id) !== 'IN_PROGRESS'" @click="markCompleted(item)">标记已完成</el-button>
                        </div>
                    </article>
                    <el-empty
                        v-if="!documents.length"
                        description="本章暂未上传培训文档"
                    />
                </div>
                <div v-if="preview.mode.value !== 'empty'" class="document-preview">
                    <header>
                        <div>
                            <b>{{ preview.fileName.value }}</b>
                            <small>{{ preview.mediaType.value }}</small>
                        </div>
                        <el-button
                            v-if="preview.mode.value !== 'loading'"
                            size="small"
                            @click="preview.download"
                            >下载原文件</el-button
                        >
                    </header>
                    <el-skeleton v-if="preview.mode.value === 'loading'" :rows="6" animated />
                    <iframe
                        v-else-if="preview.mode.value === 'pdf'"
                        :src="preview.objectUrl.value"
                        :title="preview.fileName.value"
                    />
                    <pre v-else-if="preview.mode.value === 'text'">{{ preview.text.value }}</pre>
                    <el-empty
                        v-else
                        description="该格式需下载后使用本机办公软件查看"
                    >
                        <el-button type="primary" @click="preview.download">下载文档</el-button>
                    </el-empty>
                </div>
            </section>
        </main>
        <footer>
            <div>
                <span
                    >本次已记录 {{ seconds - unsaved }} 秒，待保存
                    {{ unsaved }} 秒</span
                ><el-progress
                    :percentage="
                        Math.min(100, Number(progress?.progressPercent || 0))
                    "
                    :show-text="false"
                />
            </div>
            <el-button :loading="saving" @click="save">保存进度</el-button
            ><el-button
                type="primary"
                :icon="ArrowRight"
                :disabled="!assignmentId || !allDocumentsCompleted"
                @click="finish"
                >保存并进入闯关</el-button
            >
        </footer>
    </div>
</template>
<style scoped>
.learning {
    height: 100vh;
    display: grid;
    grid-template-rows: 62px 1fr 70px;
    background: #eceee9;
}
.learning > header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 24px;
    background: #0a403c;
    color: #fff;
}
.learning > header .el-button {
    color: #d0dfdb;
}
.learning > header > div {
    display: flex;
    align-items: center;
    gap: 7px;
    color: #a9c1bb;
    font-size: 12px;
}
.learning > main {
    overflow: auto;
    padding: 35px;
}
.paper {
    max-width: 850px;
    min-height: 600px;
    margin: 0 auto;
    padding: 48px 60px;
    background: #fff;
    box-shadow: 0 8px 40px rgba(30, 50, 45, 0.08);
}
.label {
    color: var(--cinnabar);
    font-size: 11px;
    letter-spacing: 0.15em;
}
.paper h1 {
    font-size: 36px;
}
.lead {
    color: var(--muted);
    line-height: 1.9;
}
.documents {
    display: grid;
    gap: 10px;
    margin-top: 30px;
}
.documents article {
    display: grid;
    grid-template-columns: 35px 1fr auto;
    align-items: center;
    padding: 16px;
    border: 1px solid var(--line);
}
.document-actions {
    display: flex;
    align-items: center;
    gap: 8px;
}
.document-preview {
    margin-top: 22px;
    border: 1px solid var(--line);
    background: #f9f8f3;
}
.document-preview > header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 20px;
    padding: 12px 16px;
    border-bottom: 1px solid var(--line);
}
.document-preview > header b,
.document-preview > header small {
    display: block;
}
.document-preview > header small {
    color: var(--muted);
    font-size: 11px;
}
.document-preview iframe {
    width: 100%;
    height: 520px;
    border: 0;
}
.document-preview pre {
    max-height: 520px;
    margin: 0;
    padding: 24px;
    overflow: auto;
    white-space: pre-wrap;
    word-break: break-word;
    font: 14px/1.8 "Noto Sans SC", sans-serif;
}
.documents b,
.documents small {
    display: block;
}
.documents small {
    margin-top: 4px;
    color: var(--muted);
}
.learning > footer {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 10px;
    padding: 10px 24px;
    background: #fff;
    border-top: 1px solid var(--line);
}
.learning > footer > div {
    width: 300px;
    margin-right: auto;
    font-size: 11px;
}
@media (max-width: 700px) {
    .learning > main {
        padding: 10px;
    }
    .paper {
        padding: 30px 22px;
    }
    .learning > footer > div {
        display: none;
    }
}
</style>
