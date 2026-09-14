<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
    changeEmployeeStatus,
    createDepartment,
    createEmployee,
    createPosition,
    listAccounts,
    listDepartments,
    listEmployees,
    listPositions,
    replaceEmployeePositions,
    type Department,
    type Employee,
    type AdminAccount,
    type Position,
} from "@/api/operations";
import EnterpriseTable from "@/components/EnterpriseTable.vue";

const loading = ref(true),
    saving = ref(false),
    tab = ref("employees");
const dialog = ref<"department" | "position" | "employee" | "">("");
const departments = ref<Department[]>([]),
    positions = ref<Position[]>([]),
    employees = ref<Employee[]>([]),
    accounts = ref<AdminAccount[]>([]);
const availableAccounts = computed(() => {
    const employeeUserIds = new Set(employees.value.map((item) => item.userId));
    return accounts.value.filter(
        (account) =>
            account.status === "ACTIVE" && !employeeUserIds.has(account.userId),
    );
});
const departmentForm = reactive({
    parentId: "",
    code: "",
    name: "",
    sortOrder: 0,
});
const positionForm = reactive({ code: "", name: "", description: "" });
const employeeForm = reactive({
    userId: "",
    employeeNo: "",
    departmentId: "",
    positionIds: [] as string[],
    hiredOn: "",
});
const departmentNames = computed(() =>
    Object.fromEntries(departments.value.map((x) => [x.id, x.name])),
);
const positionNames = computed(() =>
    Object.fromEntries(positions.value.map((x) => [x.id, x.name])),
);
function updateDialogVisibility(visible: boolean) {
    if (!visible) dialog.value = "";
}

async function load() {
    loading.value = true;
    try {
        [departments.value, positions.value, employees.value, accounts.value] =
            await Promise.all([
                listDepartments(),
                listPositions(),
                listEmployees(),
                listAccounts(undefined, "ACTIVE"),
            ]);
    } catch {
        ElMessage.error("组织数据加载失败");
    } finally {
        loading.value = false;
    }
}
async function save() {
    saving.value = true;
    try {
        if (dialog.value === "department")
            await createDepartment({
                ...departmentForm,
                parentId: departmentForm.parentId || undefined,
            });
        if (dialog.value === "position") await createPosition(positionForm);
        if (dialog.value === "employee")
            await createEmployee({
                ...employeeForm,
                departmentId: employeeForm.departmentId || undefined,
                hiredOn: employeeForm.hiredOn || undefined,
            });
        dialog.value = "";
        ElMessage.success("组织数据已保存");
        await load();
    } catch {
        ElMessage.error("保存失败，请检查编码唯一性和关联 ID");
    } finally {
        saving.value = false;
    }
}
async function editPositions(employee: Employee) {
    try {
        const { value } = await ElMessageBox.prompt(
            "请输入岗位 ID，多个岗位使用英文逗号分隔",
            `调整 ${employee.employeeNo} 的岗位`,
            { inputValue: employee.positionIds.join(",") },
        );
        await replaceEmployeePositions(
            employee.id,
            value
                .split(",")
                .map((x) => x.trim())
                .filter(Boolean),
        );
        ElMessage.success("员工岗位已更新");
        await load();
    } catch (error) {
        if (error !== "cancel" && error !== "close")
            ElMessage.error("岗位更新失败");
    }
}
async function setStatus(
    employee: Employee,
    status: "ACTIVE" | "SUSPENDED" | "LEFT",
) {
    await ElMessageBox.confirm(
        `确认将员工状态改为 ${status}？`,
        "员工状态变更",
    );
    try {
        await changeEmployeeStatus(employee.id, status);
        ElMessage.success("员工状态已更新");
        await load();
    } catch {
        ElMessage.error("员工状态更新失败");
    }
}
onMounted(load);
</script>

<template>
    <div v-loading="loading">
        <div class="page-head">
            <div>
                <h1>组织与员工</h1>
                <p>
                    维护部门树、岗位和员工任职关系，培训任务按这些事实数据分配。
                </p>
            </div>
            <div>
                <el-button @click="dialog = 'department'">新增部门</el-button
                ><el-button @click="dialog = 'position'">新增岗位</el-button
                ><el-button type="primary" @click="dialog = 'employee'"
                    >新增员工</el-button
                >
            </div>
        </div>
        <el-tabs v-model="tab" class="panel organization">
            <el-tab-pane label="员工" name="employees"
                ><EnterpriseTable
                    :items="employees"
                    :search-fields="['employeeNo', 'userId', 'departmentId', 'status']"
                    status-field="status"
                    search-placeholder="检索工号、用户、部门或状态"
                    v-slot="{ rows, emptyText }"
                ><el-table :data="rows" :empty-text="emptyText" stripe
                    ><el-table-column
                        prop="employeeNo"
                        label="工号"
                    /><el-table-column
                        prop="userId"
                        label="用户 ID"
                    /><el-table-column label="部门"
                        ><template #default="s">{{
                            departmentNames[s.row.departmentId] || "--"
                        }}</template></el-table-column
                    ><el-table-column label="岗位" min-width="180"
                        ><template #default="s">{{
                            s.row.positionIds
                                .map((id: string) => positionNames[id] || id)
                                .join("、") || "--"
                        }}</template></el-table-column
                    ><el-table-column
                        prop="status"
                        label="状态"
                    /><el-table-column
                        prop="hiredOn"
                        label="入职日期"
                    /><el-table-column label="操作" width="240"
                        ><template #default="s"
                            ><el-button
                                link
                                type="primary"
                                @click="editPositions(s.row)"
                                >调整岗位</el-button
                            ><el-button link @click="setStatus(s.row, 'ACTIVE')"
                                >启用</el-button
                            ><el-button
                                link
                                @click="setStatus(s.row, 'SUSPENDED')"
                                >停职</el-button
                            ><el-button
                                link
                                type="danger"
                                @click="setStatus(s.row, 'LEFT')"
                                >离职</el-button
                            ></template
                        ></el-table-column
                    ></el-table></EnterpriseTable
                ></el-tab-pane
            >
            <el-tab-pane label="部门" name="departments"
                ><EnterpriseTable
                    :items="departments"
                    :search-fields="['code', 'name', 'parentId']"
                    status-field="enabled"
                    search-placeholder="检索部门编码、名称或上级部门"
                    v-slot="{ rows, emptyText }"
                ><el-table :data="rows" :empty-text="emptyText" stripe
                    ><el-table-column
                        prop="code"
                        label="编码" /><el-table-column
                        prop="name"
                        label="名称" /><el-table-column label="上级部门"
                        ><template #default="s">{{
                            departmentNames[s.row.parentId] || "根部门"
                        }}</template></el-table-column
                    ><el-table-column
                        prop="sortOrder"
                        label="排序" /><el-table-column
                        prop="enabled"
                        label="启用" /></el-table></EnterpriseTable
            ></el-tab-pane>
            <el-tab-pane label="岗位" name="positions"
                ><EnterpriseTable
                    :items="positions"
                    :search-fields="['code', 'name', 'description']"
                    status-field="enabled"
                    search-placeholder="检索岗位编码、名称或说明"
                    v-slot="{ rows, emptyText }"
                ><el-table :data="rows" :empty-text="emptyText" stripe
                    ><el-table-column
                        prop="code"
                        label="编码" /><el-table-column
                        prop="name"
                        label="名称" /><el-table-column
                        prop="description"
                        label="说明" /><el-table-column
                        prop="enabled"
                        label="启用" /></el-table></EnterpriseTable
            ></el-tab-pane>
        </el-tabs>
        <el-dialog
            :model-value="Boolean(dialog)"
            @update:model-value="updateDialogVisibility"
            :title="
                dialog === 'department'
                    ? '新增部门'
                    : dialog === 'position'
                      ? '新增岗位'
                      : '新增员工'
            "
            width="560"
        >
            <el-form v-if="dialog === 'department'" label-position="top"
                ><el-form-item label="上级部门"
                    ><el-select v-model="departmentForm.parentId" clearable
                        ><el-option
                            v-for="x in departments"
                            :key="x.id"
                            :label="x.name"
                            :value="x.id" /></el-select></el-form-item
                ><el-form-item label="部门编码"
                    ><el-input v-model="departmentForm.code" /></el-form-item
                ><el-form-item label="部门名称"
                    ><el-input v-model="departmentForm.name" /></el-form-item
                ><el-form-item label="排序"
                    ><el-input-number
                        v-model="departmentForm.sortOrder" /></el-form-item
            ></el-form>
            <el-form v-else-if="dialog === 'position'" label-position="top"
                ><el-form-item label="岗位编码"
                    ><el-input v-model="positionForm.code" /></el-form-item
                ><el-form-item label="岗位名称"
                    ><el-input v-model="positionForm.name" /></el-form-item
                ><el-form-item label="岗位说明"
                    ><el-input
                        v-model="positionForm.description"
                        type="textarea" /></el-form-item
            ></el-form>
            <el-form v-else label-position="top"
                ><el-form-item label="平台账号"
                    ><el-select
                        v-model="employeeForm.userId"
                        filterable
                        placeholder="请选择尚未转为员工的真实账号"
                        ><el-option
                            v-for="account in availableAccounts"
                            :key="account.userId"
                            :label="`${account.principal}（${account.userId}）`"
                            :value="account.userId" /></el-select></el-form-item
                ><el-form-item label="工号"
                    ><el-input
                        v-model="employeeForm.employeeNo" /></el-form-item
                ><el-form-item label="部门"
                    ><el-select v-model="employeeForm.departmentId" clearable
                        ><el-option
                            v-for="x in departments"
                            :key="x.id"
                            :label="x.name"
                            :value="x.id" /></el-select></el-form-item
                ><el-form-item label="岗位"
                    ><el-select v-model="employeeForm.positionIds" multiple
                        ><el-option
                            v-for="x in positions"
                            :key="x.id"
                            :label="x.name"
                            :value="x.id" /></el-select></el-form-item
                ><el-form-item label="入职日期"
                    ><el-date-picker
                        v-model="employeeForm.hiredOn"
                        type="date"
                        value-format="YYYY-MM-DD" /></el-form-item
            ></el-form>
            <template #footer
                ><el-button @click="dialog = ''">取消</el-button
                ><el-button type="primary" :loading="saving" @click="save"
                    >保存</el-button
                ></template
            >
        </el-dialog>
    </div>
</template>
<style scoped>
.organization {
    padding: 18px;
}
.el-select {
    width: 100%;
}
</style>
