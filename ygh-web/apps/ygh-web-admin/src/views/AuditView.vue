<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { listAuditLogs, type AuditLog } from "@/api/operations";
import EnterpriseTable from "@/components/EnterpriseTable.vue";
const loading = ref(true);
const logs = ref<AuditLog[]>([]);
const filter = reactive({ userId: "", module: "", action: "", result: "" });
async function load() {
    loading.value = true;
    try {
        logs.value = await listAuditLogs(filter);
    } catch {
        ElMessage.error("审计日志加载失败");
    } finally {
        loading.value = false;
    }
}
onMounted(load);
</script>
<template>
    <div class="page-head">
        <div>
            <h1>审计与安全</h1>
            <p>按用户、模块、动作、结果和 traceId 检索关键业务操作。</p>
        </div>
    </div>
    <div class="panel filter-row">
        <el-input v-model="filter.userId" placeholder="用户 ID" /><el-input
            v-model="filter.module"
            placeholder="模块"
        /><el-input v-model="filter.action" placeholder="动作" /><el-select
            v-model="filter.result"
            placeholder="结果"
            clearable
            ><el-option label="成功" value="SUCCESS" /><el-option
                label="失败"
                value="FAILED" /></el-select
        ><el-button type="primary" @click="load">查询</el-button>
    </div>
    <section v-loading="loading" class="panel table-panel">
        <EnterpriseTable
            :items="logs"
            :search-fields="['userId', 'module', 'action', 'traceId', 'message']"
            status-field="result"
            search-placeholder="检索操作人、模块、动作、traceId 或消息"
            v-slot="{ rows, emptyText }"
        >
        <el-table :data="rows" :empty-text="emptyText"
            ><el-table-column label="时间" width="180"
                ><template #default="scope">{{
                    new Date(scope.row.timestamp).toLocaleString("zh-CN")
                }}</template></el-table-column
            ><el-table-column prop="userId" label="操作人" /><el-table-column
                prop="module"
                label="模块" /><el-table-column
                prop="action"
                label="动作" /><el-table-column prop="result" label="结果"
                ><template #default="scope"
                    ><el-tag
                        :type="
                            scope.row.result === 'SUCCESS'
                                ? 'success'
                                : 'danger'
                        "
                        >{{ scope.row.result }}</el-tag
                    ></template
                ></el-table-column
            ><el-table-column
                prop="traceId"
                label="traceId"
                min-width="220" /><el-table-column
                prop="message"
                label="脱敏消息"
                min-width="260"
        /></el-table>
        </EnterpriseTable>
    </section>
    <el-alert
        style="margin-top: 14px"
        title="安全说明"
        description="手机号、Token、Secret 与敏感请求体必须脱敏；审计日志不可由普通运营角色删除。"
        type="info"
        :closable="false"
    />
</template>
