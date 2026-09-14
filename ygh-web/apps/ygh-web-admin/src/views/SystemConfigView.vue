<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
    listAdminDictionaries,
    listFeatureFlags,
    listSystemSettings,
    saveFeatureFlag,
    saveDictionary,
    saveDictionaryItem,
    saveSystemSetting,
    type DictionaryAdmin,
    type FeatureFlag,
    type SystemSetting,
} from "@/api/operations";
import EnterpriseTable from "@/components/EnterpriseTable.vue";
const tab = ref("dictionary");
const loading = ref(true);
const dictionaries = ref<DictionaryAdmin[]>([]);
const flags = ref<FeatureFlag[]>([]);
const settings = ref<SystemSetting[]>([]);
const dictionaryDialog = ref(false);
const itemDialog = ref(false);
const selectedDictionary = ref<DictionaryAdmin>();
const dictionaryForm = reactive({
    code: "",
    name: "",
    enabled: true,
    version: 0,
});
const itemForm = reactive({
    key: "",
    value: "",
    sortOrder: 0,
    enabled: true,
    version: 0,
});
async function toggleFlag(flag: FeatureFlag) {
    try {
        const saved = await saveFeatureFlag(flag);
        Object.assign(flag, saved);
        ElMessage.success("功能开关已更新");
    } catch {
        flag.enabled = !flag.enabled;
        ElMessage.error("功能开关更新失败，版本可能已变化");
    }
}
async function editSetting(setting: SystemSetting) {
    if (setting.secret) {
        ElMessage.warning("Secret 必须通过部署环境变量或 Secret 管理器更新");
        return;
    }
    try {
        const { value } = await ElMessageBox.prompt(
            `请输入 ${setting.key} 的新值（${setting.valueType}）`,
            "更新业务参数",
            {
                inputValue: setting.value,
                inputType: setting.valueType === "JSON" ? "textarea" : "text",
                inputValidator: (input) =>
                    Boolean(input?.trim()) || "参数值不能为空",
                confirmButtonText: "保存",
            },
        );
        Object.assign(setting, await saveSystemSetting(setting, value));
        ElMessage.success("业务参数已更新");
    } catch (error) {
        if (error !== "cancel" && error !== "close")
            ElMessage.error("业务参数更新失败，格式或版本可能不正确");
    }
}
function openDictionary(dictionary?: DictionaryAdmin) {
    Object.assign(
        dictionaryForm,
        dictionary ?? { code: "", name: "", enabled: true, version: 0 },
    );
    dictionaryDialog.value = true;
}
function openItem(dictionary: DictionaryAdmin) {
    selectedDictionary.value = dictionary;
    Object.assign(itemForm, {
        key: "",
        value: "",
        sortOrder: dictionary.items.length,
        enabled: true,
        version: 0,
    });
    itemDialog.value = true;
}
async function persistDictionary() {
    try {
        await saveDictionary(dictionaryForm);
        dictionaries.value = await listAdminDictionaries();
        dictionaryDialog.value = false;
        ElMessage.success("数据字典已保存");
    } catch {
        ElMessage.error("字典保存失败，请检查编码和版本");
    }
}
async function persistItem() {
    if (!selectedDictionary.value) return;
    try {
        await saveDictionaryItem(selectedDictionary.value.code, itemForm);
        dictionaries.value = await listAdminDictionaries();
        itemDialog.value = false;
        ElMessage.success("字典项已保存");
    } catch {
        ElMessage.error("字典项保存失败，请检查键和值");
    }
}
onMounted(async () => {
    try {
        [dictionaries.value, flags.value, settings.value] = await Promise.all([
            listAdminDictionaries(),
            listFeatureFlags(),
            listSystemSettings(),
        ]);
    } catch {
        ElMessage.error("系统配置加载失败");
    } finally {
        loading.value = false;
    }
});
</script>
<template>
    <div class="page-head">
        <div>
            <h1>系统配置</h1>
            <p>维护数据字典、业务参数和功能开关。Secret 不在此页面显示。</p>
        </div>
        <el-button
            v-if="tab === 'dictionary'"
            type="primary"
            @click="openDictionary()"
            >新增字典</el-button
        >
    </div>
    <el-tabs v-model="tab" v-loading="loading" class="panel config"
        ><el-tab-pane label="数据字典" name="dictionary"
            ><EnterpriseTable
                :items="dictionaries"
                :search-fields="['code', 'name']"
                status-field="enabled"
                search-placeholder="检索字典编码或名称"
                v-slot="{ rows, emptyText }"
            ><el-table :data="rows" :empty-text="emptyText"
                ><el-table-column
                    prop="code"
                    label="字典编码"
                /><el-table-column
                    prop="name"
                    label="字典名称"
                /><el-table-column label="字典项"
                    ><template #default="scope">{{
                        scope.row.items.length
                    }}</template></el-table-column
                ><el-table-column label="操作" width="170"
                    ><template #default="scope"
                        ><el-button
                            link
                            type="primary"
                            @click="openDictionary(scope.row)"
                            >编辑</el-button
                        ><el-button
                            link
                            type="primary"
                            @click="openItem(scope.row)"
                            >新增字典项</el-button
                        ></template
                    ></el-table-column
                ></el-table></EnterpriseTable
            ></el-tab-pane
        ><el-tab-pane label="业务参数" name="parameters"
            ><EnterpriseTable
                :items="settings"
                :search-fields="['key', 'valueType']"
                search-placeholder="检索参数键或类型"
                v-slot="{ rows, emptyText }"
            ><el-table :data="rows" :empty-text="emptyText"
                ><el-table-column prop="key" label="参数键" /><el-table-column
                    label="参数值"
                    ><template #default="scope">{{
                        scope.row.secret ? "[REDACTED]" : scope.row.value
                    }}</template></el-table-column
                ><el-table-column
                    prop="valueType"
                    label="类型"
                /><el-table-column
                    prop="version"
                    label="版本"
                /><el-table-column label="操作" width="100"
                    ><template #default="scope"
                        ><el-button
                            link
                            type="primary"
                            :disabled="scope.row.secret"
                            @click="editSetting(scope.row)"
                            >编辑</el-button
                        ></template
                    ></el-table-column
                ></el-table></EnterpriseTable
            ></el-tab-pane
        ><el-tab-pane label="功能开关" name="switches"
            ><div v-for="flag in flags" :key="flag.key" class="switch">
                <div>
                    <b>{{ flag.key }}</b
                    ><small
                        >灰度 {{ flag.rolloutPercent }}% · 版本
                        {{ flag.version }}</small
                    >
                </div>
                <div class="flag-control">
                    <el-input-number
                        v-model="flag.rolloutPercent"
                        :min="0"
                        :max="100"
                        size="small"
                        @change="toggleFlag(flag)"
                    /><el-switch
                        v-model="flag.enabled"
                        @change="toggleFlag(flag)"
                    />
                </div></div></el-tab-pane
    ></el-tabs>
    <el-dialog v-model="dictionaryDialog" title="维护数据字典" width="520">
        <el-form label-position="top"
            ><el-form-item label="字典编码"
                ><el-input
                    v-model="dictionaryForm.code"
                    :disabled="dictionaryForm.version > 0" /></el-form-item
            ><el-form-item label="字典名称"
                ><el-input v-model="dictionaryForm.name" /></el-form-item
            ><el-form-item label="启用"
                ><el-switch v-model="dictionaryForm.enabled" /></el-form-item
        ></el-form>
        <template #footer
            ><el-button @click="dictionaryDialog = false">取消</el-button
            ><el-button type="primary" @click="persistDictionary"
                >保存</el-button
            ></template
        >
    </el-dialog>
    <el-dialog
        v-model="itemDialog"
        :title="`${selectedDictionary?.name || ''} · 新增字典项`"
        width="520"
    >
        <el-form label-position="top"
            ><el-form-item label="键"
                ><el-input v-model="itemForm.key" /></el-form-item
            ><el-form-item label="显示值"
                ><el-input v-model="itemForm.value" /></el-form-item
            ><el-form-item label="排序"
                ><el-input-number v-model="itemForm.sortOrder" /></el-form-item
            ><el-form-item label="启用"
                ><el-switch v-model="itemForm.enabled" /></el-form-item
        ></el-form>
        <template #footer
            ><el-button @click="itemDialog = false">取消</el-button
            ><el-button type="primary" @click="persistItem"
                >保存</el-button
            ></template
        >
    </el-dialog>
</template>
<style scoped>
.config {
    padding: 20px;
}
.switch {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 15px;
    border-bottom: 1px solid var(--line);
}
.switch b,
.switch small {
    display: block;
}
.flag-control {
    display: flex;
    align-items: center;
    gap: 12px;
}
.switch small {
    margin-top: 4px;
    color: var(--muted);
}
</style>
