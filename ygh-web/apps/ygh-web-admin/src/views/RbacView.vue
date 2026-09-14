<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import {
    listPermissions,
    listRoles,
    getUserAuthorities,
    assignUserRoles,
    saveRole,
    savePermission,
    type Permission,
    type Role,
    type AuthoritySnapshot,
} from "@/api/operations";
import EnterpriseTable from "@/components/EnterpriseTable.vue";
const active = ref("role");
const loading = ref(true);
const roles = ref<Role[]>([]);
const permissions = ref<Permission[]>([]);
const roleDialog = ref(false);
const editingRole = ref<Role>();
const authority = ref<AuthoritySnapshot>();
const authorityUserId = ref("");
const assignedRoles = ref<string[]>([]);
const assignmentReason = ref("");
function editRole(role: Role) {
    editingRole.value = { ...role, permissions: [...role.permissions] };
    roleDialog.value = true;
}
async function persistRole() {
    if (!editingRole.value) return;
    try {
        const saved = await saveRole(editingRole.value);
        const index = roles.value.findIndex((x) => x.code === saved.code);
        roles.value[index] = saved;
        roleDialog.value = false;
        ElMessage.success("角色权限已保存");
    } catch {
        ElMessage.error("角色保存失败，版本可能已变化");
    }
}
async function togglePermission(permission: Permission) {
    try {
        const saved = await savePermission(permission);
        Object.assign(permission, saved);
        ElMessage.success("权限状态已更新");
    } catch {
        permission.enabled = !permission.enabled;
        ElMessage.error("权限状态更新失败");
    }
}
async function loadAuthority() {
    if (!authorityUserId.value.trim()) return;
    try {
        authority.value = await getUserAuthorities(
            authorityUserId.value.trim(),
        );
        assignedRoles.value = [...authority.value.roles];
    } catch {
        authority.value = undefined;
        ElMessage.error("用户授权信息加载失败");
    }
}
async function persistAuthority() {
    if (!authority.value || !assignmentReason.value.trim()) {
        ElMessage.warning("请先加载用户并填写授权变更原因");
        return;
    }
    try {
        authority.value = await assignUserRoles(
            authority.value,
            assignedRoles.value,
            assignmentReason.value.trim(),
        );
        assignedRoles.value = [...authority.value.roles];
        assignmentReason.value = "";
        ElMessage.success("用户角色已更新");
    } catch {
        ElMessage.error("授权失败，角色或授权版本可能已变化");
    }
}
onMounted(async () => {
    try {
        [roles.value, permissions.value] = await Promise.all([
            listRoles(),
            listPermissions(),
        ]);
    } catch {
        ElMessage.error("角色权限目录加载失败");
    } finally {
        loading.value = false;
    }
});
</script>
<template>
    <div class="page-head">
        <div>
            <h1>角色与权限</h1>
            <p>System 服务持有角色、权限与授权关系；Admin 仅呈现运营聚合。</p>
        </div>
    </div>
    <el-tabs v-model="active" v-loading="loading" class="panel rbac"
        ><el-tab-pane label="角色管理" name="role"
            ><EnterpriseTable
                :items="roles"
                :search-fields="['code', 'name']"
                status-field="enabled"
                search-placeholder="检索角色编码或名称"
                v-slot="{ rows, emptyText }"
            ><el-table :data="rows" :empty-text="emptyText"
                ><el-table-column
                    prop="code"
                    label="角色编码"
                /><el-table-column
                    prop="name"
                    label="角色名称"
                /><el-table-column label="权限数"
                    ><template #default="scope">{{
                        scope.row.permissions.length
                    }}</template></el-table-column
                ><el-table-column prop="version" label="版本" /><el-table-column
                    label="状态"
                    ><template #default="scope"
                        ><el-tag
                            :type="scope.row.enabled ? 'success' : 'info'"
                            >{{ scope.row.enabled ? "启用" : "停用" }}</el-tag
                        ></template
                    ></el-table-column
                ><el-table-column label="操作"
                    ><template #default="scope"
                        ><el-button
                            link
                            type="primary"
                            @click="editRole(scope.row)"
                            >配置权限</el-button
                        ></template
                    ></el-table-column
                ></el-table></EnterpriseTable
            ></el-tab-pane
        ><el-tab-pane label="权限目录" name="permission"
            ><EnterpriseTable
                :items="permissions"
                :search-fields="['code', 'name', 'resourceType']"
                status-field="enabled"
                search-placeholder="检索权限编码、名称或资源类型"
                v-slot="{ rows, emptyText }"
            ><el-table :data="rows" :empty-text="emptyText"
                ><el-table-column
                    prop="code"
                    label="权限编码" /><el-table-column
                    prop="name"
                    label="权限名称" /><el-table-column
                    prop="resourceType"
                    label="资源类型" /><el-table-column label="状态"
                    ><template #default="scope"
                        ><el-switch
                            v-model="scope.row.enabled"
                            @change="
                                togglePermission(scope.row)
                            " /></template></el-table-column></el-table></EnterpriseTable></el-tab-pane
        ><el-tab-pane label="用户授权" name="assignment"
            ><div class="assignment">
                <el-input
                    v-model="authorityUserId"
                    placeholder="输入用户 ID"
                    @keyup.enter="loadAuthority"
                    ><template #append
                        ><el-button @click="loadAuthority"
                            >加载</el-button
                        ></template
                    ></el-input
                >
                <el-form v-if="authority" label-position="top">
                    <el-form-item label="角色集合"
                        ><el-select
                            v-model="assignedRoles"
                            multiple
                            filterable
                            style="width: 100%"
                            ><el-option
                                v-for="role in roles.filter((x) => x.enabled)"
                                :key="role.code"
                                :label="`${role.name} (${role.code})`"
                                :value="role.code" /></el-select
                    ></el-form-item>
                    <el-form-item label="变更原因"
                        ><el-input
                            v-model="assignmentReason"
                            type="textarea"
                            maxlength="500"
                    /></el-form-item>
                    <el-alert
                        :title="`当前权限 ${authority.permissions.length} 项 · 授权版本 ${authority.version}`"
                        type="info"
                        :closable="false"
                    />
                    <el-button
                        type="primary"
                        class="save-assignment"
                        @click="persistAuthority"
                        >保存用户角色</el-button
                    >
                </el-form>
            </div></el-tab-pane
        ><el-tab-pane label="权限设计规则" name="rule"
            ><div class="permission">
                <ul>
                    <li>前端隐藏按钮仅改善体验，不能作为授权证据</li>
                    <li>后端接口执行最终权限校验</li>
                    <li>个人资源必须校验数据所有权</li>
                    <li>内部接口必须验证服务 HMAC</li>
                </ul>
            </div></el-tab-pane
        ></el-tabs
    ><el-dialog v-model="roleDialog" title="配置角色权限" width="600"
        ><el-form v-if="editingRole" label-position="top"
            ><el-form-item label="角色编码"
                ><el-input v-model="editingRole.code" disabled /></el-form-item
            ><el-form-item label="角色名称"
                ><el-input
                    v-model="editingRole.name"
                    maxlength="100" /></el-form-item
            ><el-form-item label="权限集合"
                ><el-select
                    v-model="editingRole.permissions"
                    multiple
                    filterable
                    style="width: 100%"
                    ><el-option
                        v-for="permission in permissions.filter(
                            (x) => x.enabled,
                        )"
                        :key="permission.code"
                        :label="`${permission.name} (${permission.code})`"
                        :value="permission.code" /></el-select></el-form-item
            ><el-form-item label="启用状态"
                ><el-switch
                    v-model="editingRole.enabled" /></el-form-item></el-form
        ><template #footer
            ><el-button @click="roleDialog = false">取消</el-button
            ><el-button type="primary" @click="persistRole"
                >保存</el-button
            ></template
        ></el-dialog
    >
</template>
<style scoped>
.rbac {
    padding: 18px;
}
.permission {
    padding: 20px;
    background: #f2f4f0;
}
.permission li {
    margin: 10px 0;
    color: var(--muted);
    font-size: 12px;
}
.assignment {
    max-width: 680px;
    padding: 20px;
}
.assignment form {
    margin-top: 20px;
}
.save-assignment {
    margin-top: 16px;
}
</style>
