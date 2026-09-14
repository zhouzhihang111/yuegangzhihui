<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
    getAiSummary,
    getAiProviderConfig,
    createPrompt,
    createEvaluationCase,
    listEvaluationCases,
    listPrompts,
    runAiEvaluations,
    saveAiProviderConfig,
    setEvaluationCaseEnabled,
    type AiSummary,
    type AiProviderConfig,
    type EvaluationCase,
    type EvaluationRun,
    type PromptConfig,
} from "@/api/operations";
import EnterpriseTable from "@/components/EnterpriseTable.vue";
const tab = ref("prompt");
const loading = ref(true);
const running = ref(false);
const prompts = ref<PromptConfig[]>([]);
const cases = ref<EvaluationCase[]>([]);
const summary = ref<AiSummary>();
const runs = ref<EvaluationRun[]>([]);
const promptDialog = ref(false);
const caseDialog = ref(false);
const providerDialog = ref(false);
const saving = ref(false);
const providerLoading = ref(false);
const providerConfig = ref<AiProviderConfig>();
const providerForm = reactive({
    provider: "DOUBAO_ARK" as const,
    baseUrl: "https://ark.cn-beijing.volces.com/api/v3",
    apiKey: "",
    chatModel: "doubao-seed-2-0-lite-260215",
    embeddingModel: "doubao-embedding-text-240515",
    webSearchEnabled: false,
    version: 0,
});
const chatModels = ["doubao-seed-2-0-lite-260215"];
const embeddingModels = ["doubao-embedding-text-240515"];
const promptForm = reactive({
    code: "CUSTOMS_ASSISTANT",
    systemPrompt: "",
    modelName: "doubao-pro-32k",
    temperature: 0.2,
    knowledgeScope: "POLICY,CUSTOMS,PRODUCT",
    sensitiveWords: "",
    enabled: true,
    version: 0,
});
const caseForm = reactive({
    category: "POLICY",
    question: "",
    expectedEvidence: "",
    forbiddenAnswer: "",
    expectedRefusal: false,
    enabled: true,
});
async function load() {
    loading.value = true;
    try {
        [prompts.value, cases.value, summary.value] = await Promise.all([
            listPrompts(),
            listEvaluationCases(),
            getAiSummary(),
        ]);
    } catch {
        ElMessage.error("AI 治理数据加载失败");
    } finally {
        loading.value = false;
    }
}
async function run() {
    running.value = true;
    try {
        runs.value = await runAiEvaluations();
        ElMessage.success("离线评测完成");
    } catch {
        ElMessage.error("离线评测失败");
    } finally {
        running.value = false;
    }
}
async function changeCaseStatus(item: EvaluationCase) {
    try {
        await setEvaluationCaseEnabled(item.id, item.enabled);
        ElMessage.success(item.enabled ? "评测用例已启用" : "评测用例已停用");
        summary.value = await getAiSummary();
    } catch {
        item.enabled = !item.enabled;
        ElMessage.error("评测用例状态更新失败");
    }
}
async function savePrompt() {
    saving.value = true;
    try {
        await createPrompt(promptForm);
        promptDialog.value = false;
        ElMessage.success("提示词新版本已保存");
        await load();
    } catch {
        ElMessage.error("提示词保存失败，请检查编码与字段长度");
    } finally {
        saving.value = false;
    }
}
async function saveCase() {
    saving.value = true;
    try {
        await createEvaluationCase(caseForm);
        caseDialog.value = false;
        ElMessage.success("评测用例已新增");
        await load();
    } catch {
        ElMessage.error("评测用例保存失败");
    } finally {
        saving.value = false;
    }
}
async function openProviderConfig() {
    providerDialog.value = true;
    providerLoading.value = true;
    try {
        const value = await getAiProviderConfig();
        providerConfig.value = value;
        Object.assign(providerForm, {
            provider: value.provider,
            baseUrl: value.baseUrl,
            apiKey: "",
            chatModel: value.chatModel,
            embeddingModel: value.embeddingModel,
            webSearchEnabled: value.webSearchEnabled,
            version: value.version,
        });
    } catch {
        ElMessage.error("模型配置加载失败");
    } finally {
        providerLoading.value = false;
    }
}
async function saveProviderConfig() {
    if (!providerForm.apiKey && !providerConfig.value?.apiKeyConfigured) {
        ElMessage.warning("首次配置必须填写 API Key");
        return;
    }
    if (providerForm.webSearchEnabled && !providerConfig.value?.webSearchEnabled) {
        try {
            await ElMessageBox.confirm(
                "火山方舟联网搜索属于按量计费插件，启用后每次模型判断需要联网时可能产生搜索和额外 Token 费用。确认启用吗？",
                "确认启用联网搜索",
                { confirmButtonText: "确认启用", cancelButtonText: "暂不启用", type: "warning" },
            );
        } catch {
            return;
        }
    }
    saving.value = true;
    try {
        const value = await saveAiProviderConfig({
            provider: providerForm.provider,
            baseUrl: providerForm.baseUrl.trim(),
            chatModel: providerForm.chatModel.trim(),
            embeddingModel: providerForm.embeddingModel.trim(),
            webSearchEnabled: providerForm.webSearchEnabled,
            apiKey: providerForm.apiKey.trim() || undefined,
            version: providerForm.version,
        });
        providerConfig.value = value;
        providerForm.version = value.version;
        providerForm.apiKey = "";
        providerDialog.value = false;
        ElMessage.success("模型配置已加密保存，AI 与向量检索将在下一次请求自动使用新配置");
    } catch {
        ElMessage.error("模型配置保存失败，请检查字段或刷新配置后重试");
    } finally {
        saving.value = false;
    }
}
onMounted(load);
</script>
<template>
    <div v-loading="loading">
        <div class="page-head">
            <div>
                <h1>AI 客服治理</h1>
                <p>管理提示词版本、评测集与离线评测结果。</p>
            </div>
            <div>
                <el-button @click="openProviderConfig">模型 API 配置</el-button
                ><el-button @click="caseDialog = true">新增评测用例</el-button
                ><el-button type="primary" @click="promptDialog = true"
                    >新建提示词版本</el-button
                ><el-tag :type="summary?.activePrompt ? 'success' : 'danger'">{{
                    summary?.activePrompt ? "存在启用提示词" : "无启用提示词"
                }}</el-tag>
            </div>
        </div>
        <div class="metric-grid">
            <div class="metric panel">
                <span>会话</span><b>{{ summary?.conversations || 0 }}</b>
            </div>
            <div class="metric panel">
                <span>消息</span><b>{{ summary?.messages || 0 }}</b>
            </div>
            <div class="metric panel">
                <span>拒答</span><b>{{ summary?.refusals || 0 }}</b>
            </div>
            <div class="metric panel">
                <span>有效评测用例</span
                ><b>{{ summary?.enabledEvaluationCases || 0 }}</b>
            </div>
        </div>
        <el-tabs v-model="tab" class="panel ai-config"
            ><el-tab-pane label="提示词版本" name="prompt"
                ><EnterpriseTable
                    :items="prompts"
                    :search-fields="['code', 'modelName', 'knowledgeScope']"
                    status-field="enabled"
                    search-placeholder="检索编码、模型或知识范围"
                    v-slot="{ rows, emptyText }"
                ><el-table :data="rows" :empty-text="emptyText"
                    ><el-table-column
                        prop="code"
                        label="编码"
                    /><el-table-column
                        prop="modelName"
                        label="模型"
                    /><el-table-column
                        prop="temperature"
                        label="温度"
                    /><el-table-column
                        prop="knowledgeScope"
                        label="知识范围"
                    /><el-table-column
                        prop="version"
                        label="版本"
                    /><el-table-column label="状态"
                        ><template #default="scope"
                            ><el-tag
                                :type="scope.row.enabled ? 'success' : 'info'"
                                >{{
                                    scope.row.enabled ? "启用" : "停用"
                                }}</el-tag
                            ></template
                        ></el-table-column
                    ></el-table></EnterpriseTable
                ></el-tab-pane
            ><el-tab-pane label="评测集" name="evaluation"
                ><EnterpriseTable
                    :items="cases"
                    :search-fields="['category', 'question', 'expectedEvidence']"
                    status-field="enabled"
                    search-placeholder="检索分类、问题或预期证据"
                    v-slot="{ rows, emptyText }"
                ><el-table :data="rows" :empty-text="emptyText"
                    ><el-table-column
                        prop="category"
                        label="分类"
                    /><el-table-column
                        prop="question"
                        label="问题"
                        min-width="280"
                    /><el-table-column
                        prop="expectedEvidence"
                        label="预期证据"
                        min-width="240"
                    /><el-table-column label="预期结果"
                        ><template #default="scope"
                            ><el-tag :type="scope.row.expectedRefusal ? 'warning' : 'success'">{{
                                scope.row.expectedRefusal ? "应拒答" : "应回答"
                            }}</el-tag></template
                        ></el-table-column
                    ><el-table-column label="状态" width="100"
                        ><template #default="scope"
                            ><el-switch
                                v-model="scope.row.enabled"
                                @change="changeCaseStatus(scope.row)" /></template
                        ></el-table-column
                    ></el-table></EnterpriseTable
                ><el-button
                    type="primary"
                    :loading="running"
                    style="margin-top: 15px"
                    @click="run"
                    >运行全量评测</el-button
                ></el-tab-pane
            ><el-tab-pane label="最近运行" name="runs"
                ><EnterpriseTable
                    :items="runs"
                    :search-fields="['caseId', 'failureReason']"
                    status-field="passed"
                    search-placeholder="检索用例 ID 或失败原因"
                    v-slot="{ rows, emptyText }"
                ><el-table :data="rows" :empty-text="emptyText"
                    ><el-table-column
                        prop="caseId"
                        label="用例 ID" /><el-table-column
                        prop="score"
                        label="得分" /><el-table-column
                        prop="citationCount"
                        label="引用数" /><el-table-column
                        prop="durationMs"
                        label="耗时 ms" /><el-table-column label="结果"
                        ><template #default="scope"
                            ><el-tag
                                :type="scope.row.passed ? 'success' : 'danger'"
                                >{{
                                    scope.row.passed ? "通过" : "失败"
                                }}</el-tag
                            ></template
                        ></el-table-column
                    ><el-table-column
                        prop="failureReason"
                        label="失败原因" /></el-table></EnterpriseTable></el-tab-pane></el-tabs
        ><el-dialog
            v-model="providerDialog"
            title="豆包模型 API 配置"
            width="680"
        >
            <el-alert
                type="warning"
                :closable="false"
                show-icon
                title="密钥只在这里录入一次"
                description="API Key 会由 System 服务使用 AES-256-GCM 加密保存，页面和接口都不会回显明文。留空表示继续使用原密钥；保存后无需修改后端文件或重启服务。"
            />
            <el-form v-loading="providerLoading" class="provider-form" label-position="top">
                <el-form-item label="模型服务商"><el-select v-model="providerForm.provider" disabled><el-option label="豆包 Ark" value="DOUBAO_ARK" /></el-select></el-form-item>
                <el-form-item label="API 地址"><el-input v-model="providerForm.baseUrl" /></el-form-item>
                <el-form-item :label="providerConfig?.apiKeyConfigured ? 'API Key（已配置，留空则不修改）' : 'API Key（首次配置必填）'">
                    <el-input v-model="providerForm.apiKey" type="password" show-password autocomplete="new-password" placeholder="请输入豆包 Ark API Key" />
                </el-form-item>
                <el-form-item label="对话模型 / Endpoint ID">
                    <el-select v-model="providerForm.chatModel" filterable allow-create default-first-option placeholder="选择预设或输入自定义 ID">
                        <el-option v-for="model in chatModels" :key="model" :label="model" :value="model" />
                    </el-select>
                </el-form-item>
                <el-form-item label="向量模型 / Endpoint ID">
                    <el-select v-model="providerForm.embeddingModel" filterable allow-create default-first-option placeholder="选择预设或输入自定义 ID">
                        <el-option v-for="model in embeddingModels" :key="model" :label="model" :value="model" />
                    </el-select>
                </el-form-item>
                <el-form-item label="互联网知识补充（Web Search）">
                    <div>
                        <el-switch
                            v-model="providerForm.webSearchEnabled"
                            active-text="允许模型按需联网"
                            inactive-text="仅使用企业知识库与业务工具"
                        />
                        <el-alert
                            v-if="providerForm.webSearchEnabled"
                            class="web-search-warning"
                            type="warning"
                            :closable="false"
                            show-icon
                            title="联网搜索会产生额外费用"
                            description="回答将优先使用企业知识库；资料不足或问题具有时效性时，模型可检索公开互联网，并在回答下方展示可点击来源。"
                        />
                    </div>
                </el-form-item>
                <el-text type="info">如果火山引擎控制台为账号分配的是 Endpoint ID，请直接输入。对话模型与向量模型必须在当前 Ark 账号中已开通；向量模型不可用时系统会自动降级为全文检索，知识问答仍可继续。</el-text>
            </el-form>
            <template #footer>
                <el-button @click="providerDialog = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="saveProviderConfig">保存并同步</el-button>
            </template>
        </el-dialog
        ><el-dialog v-model="promptDialog" title="新建提示词版本" width="700"
            ><el-form label-position="top"
                ><div class="form-grid">
                    <el-form-item label="配置编码"
                        ><el-input v-model="promptForm.code" /></el-form-item
                    ><el-form-item label="模型名称"
                        ><el-input v-model="promptForm.modelName"
                    /></el-form-item>
                </div>
                <el-form-item label="系统提示词"
                    ><el-input
                        v-model="promptForm.systemPrompt"
                        type="textarea"
                        :rows="8"
                        maxlength="12000"
                        show-word-limit
                /></el-form-item>
                <div class="form-grid">
                    <el-form-item label="温度"
                        ><el-input-number
                            v-model="promptForm.temperature"
                            :min="0"
                            :max="2"
                            :step="0.1" /></el-form-item
                    ><el-form-item label="启用"
                        ><el-switch v-model="promptForm.enabled"
                    /></el-form-item>
                </div>
                <el-form-item label="知识范围"
                    ><el-input
                        v-model="promptForm.knowledgeScope" /></el-form-item
                ><el-form-item label="敏感词规则"
                    ><el-input
                        v-model="promptForm.sensitiveWords"
                        type="textarea" /></el-form-item></el-form
            ><template #footer
                ><el-button @click="promptDialog = false">取消</el-button
                ><el-button type="primary" :loading="saving" @click="savePrompt"
                    >保存版本</el-button
                ></template
            ></el-dialog
        ><el-dialog v-model="caseDialog" title="新增离线评测用例" width="620"
            ><el-form label-position="top"
                ><el-form-item label="分类"
                    ><el-select v-model="caseForm.category" style="width: 100%"
                        ><el-option
                            v-for="value in [
                                'POLICY',
                                'CUSTOMS',
                                'TRACEABILITY',
                                'RECOMMENDATION',
                            ]"
                            :key="value"
                            :label="value"
                            :value="value" /></el-select></el-form-item
                ><el-form-item label="问题"
                    ><el-input
                        v-model="caseForm.question"
                        type="textarea" /></el-form-item
                ><el-form-item label="预期证据"
                    ><el-input
                        v-model="caseForm.expectedEvidence"
                        type="textarea" /></el-form-item
                ><el-form-item label="禁止回答"
                    ><el-input
                        v-model="caseForm.forbiddenAnswer"
                        type="textarea" /></el-form-item
                ><el-form-item label="预期拒答"
                    ><el-switch v-model="caseForm.expectedRefusal" />
                    <span class="form-hint">仅用于已审核知识不足时必须拒答的用例</span></el-form-item
                ><el-form-item label="启用"
                    ><el-switch
                        v-model="caseForm.enabled" /></el-form-item></el-form
            ><template #footer
                ><el-button @click="caseDialog = false">取消</el-button
                ><el-button type="primary" :loading="saving" @click="saveCase"
                    >保存用例</el-button
                ></template
            ></el-dialog
        >
    </div>
</template>
<style scoped>
.ai-config {
    margin-top: 14px;
    padding: 20px;
}
.form-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px;
}
.form-hint { margin-left: 10px; color: var(--muted); font-size: 12px; }
.provider-details {
    margin-top: 18px;
}
.web-search-warning { margin-top: 12px; }
.provider-details code,
.configuration-steps code {
    color: var(--jade);
    font-family: Consolas, "Courier New", monospace;
}
.configuration-steps {
    margin-top: 18px;
    color: var(--muted);
    line-height: 1.8;
}
.configuration-steps b {
    color: var(--ink);
}
.configuration-steps pre {
    overflow-x: auto;
    padding: 12px;
    border-radius: 6px;
    background: #102d2a;
    color: #f7f3ea;
    font: 12px/1.6 Consolas, "Courier New", monospace;
}
</style>
