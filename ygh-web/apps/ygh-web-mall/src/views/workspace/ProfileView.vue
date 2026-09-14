<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { changePassword, useSessionStore } from "@ygh/web-shared";
import PageHeader from "@/components/PageHeader.vue";
import { getMyProfile, updateMyProfile } from "@/api/user";
import { useHttp } from "@/api/client";

const session = useSessionStore();
const loading = ref(true);
const saving = ref(false);
const passwordDialog = ref(false);
const passwordForm = reactive({
    currentPassword: "",
    newPassword: "",
    confirmPassword: "",
});
const profile = reactive({
    displayName: "",
    avatarUrl: "",
    phone: "",
    email: "",
    locale: "zh-CN",
    timezone: "Asia/Shanghai",
    version: 0,
});

async function load() {
    loading.value = true;
    try {
        const result = await getMyProfile();
        Object.assign(profile, result);
    } catch {
        ElMessage.error("个人资料加载失败");
    } finally {
        loading.value = false;
    }
}

async function save() {
    saving.value = true;
    try {
        const result = await updateMyProfile(profile);
        Object.assign(profile, result);
        if (session.user) session.user.displayName = result.displayName;
        ElMessage.success("个人资料已保存");
    } catch {
        ElMessage.error("保存失败，资料可能已被其他会话修改，请刷新后重试");
    } finally {
        saving.value = false;
    }
}
async function savePassword() {
    saving.value = true;
    try {
        await changePassword(useHttp(), passwordForm);
        passwordDialog.value = false;
        session.clear();
        ElMessage.success("密码已修改，所有会话已撤销，请重新登录");
        location.assign("/login");
    } catch {
        ElMessage.error("密码修改失败，请检查原密码和新密码安全策略");
    } finally {
        saving.value = false;
    }
}

onMounted(load);
</script>
<template>
    <PageHeader
        title="个人资料"
        description="管理基础信息与账户安全。用户名和员工岗位关系由管理员维护。"
    />
    <div v-loading="loading" class="profile-grid">
        <section class="paper-card form">
            <h3 class="serif">基础信息</h3>
            <div class="avatar-row">
                <el-avatar :size="72" :src="profile.avatarUrl">{{
                    profile.displayName.slice(0, 1)
                }}</el-avatar>
                <div>
                    <small
                        >头像地址由受控文件服务提供，不能填写本地文件路径。</small
                    >
                </div>
            </div>
            <el-form label-position="top"
                ><el-form-item label="登录账号"
                    ><el-input
                        :model-value="session.user?.username"
                        disabled /></el-form-item
                ><el-form-item label="显示名称"
                    ><el-input
                        v-model="profile.displayName"
                        maxlength="80"
                        show-word-limit /></el-form-item
                ><el-form-item label="头像 URL"
                    ><el-input v-model="profile.avatarUrl" maxlength="512"
                /></el-form-item>
                <div class="two">
                    <el-form-item label="联系手机">
                        <el-input
                            v-model="profile.phone"
                            maxlength="32"
                            autocomplete="tel"
                            placeholder="例如 +86 13800138000"
                        />
                    </el-form-item>
                    <el-form-item label="联系邮箱">
                        <el-input
                            v-model="profile.email"
                            maxlength="254"
                            autocomplete="email"
                            placeholder="name@example.com"
                        />
                    </el-form-item>
                </div>
                <div class="two">
                    <el-form-item label="界面语言"
                        ><el-select v-model="profile.locale"
                            ><el-option
                                label="简体中文"
                                value="zh-CN" /><el-option
                                label="English"
                                value="en-US" /></el-select></el-form-item
                    ><el-form-item label="时区"
                        ><el-select v-model="profile.timezone"
                            ><el-option
                                label="亚洲/上海"
                                value="Asia/Shanghai" /><el-option
                                label="UTC"
                                value="UTC" /></el-select
                    ></el-form-item>
                </div>
                <el-button type="primary" :loading="saving" @click="save"
                    >保存修改</el-button
                ></el-form
            >
        </section>
        <aside>
            <section class="paper-card security">
                <h3 class="serif">账户安全</h3>
                <p>
                    <span>登录密码</span><b>已设置</b
                    ><el-button text @click="passwordDialog = true"
                        >修改</el-button
                    >
                </p>
                <p>
                    <span>当前角色</span
                    ><b>{{ session.user?.roles.join("、") || "普通用户" }}</b>
                </p>
                <p><span>账号状态</span><el-tag type="success">正常</el-tag></p>
            </section>
            <section class="paper-card employee">
                <h3 class="serif">权限信息</h3>
                <el-descriptions :column="1"
                    ><el-descriptions-item label="用户 ID">{{
                        session.user?.id
                    }}</el-descriptions-item
                    ><el-descriptions-item label="权限数量">{{
                        session.user?.permissions.length || 0
                    }}</el-descriptions-item
                    ><el-descriptions-item label="资料版本">{{
                        profile.version
                    }}</el-descriptions-item></el-descriptions
                >
            </section>
        </aside>
    </div>
    <el-dialog v-model="passwordDialog" title="修改登录密码" width="520"
        ><el-form label-position="top"
            ><el-form-item label="当前密码"
                ><el-input
                    v-model="passwordForm.currentPassword"
                    type="password"
                    show-password
                    autocomplete="current-password" /></el-form-item
            ><el-form-item label="新密码"
                ><el-input
                    v-model="passwordForm.newPassword"
                    type="password"
                    show-password
                    autocomplete="new-password" /></el-form-item
            ><el-form-item label="确认新密码"
                ><el-input
                    v-model="passwordForm.confirmPassword"
                    type="password"
                    show-password
                    autocomplete="new-password" /></el-form-item></el-form
        ><template #footer
            ><el-button @click="passwordDialog = false">取消</el-button
            ><el-button type="primary" :loading="saving" @click="savePassword"
                >修改并退出</el-button
            ></template
        ></el-dialog
    >
</template>
<style scoped>
.profile-grid {
    display: grid;
    grid-template-columns: 1fr 330px;
    gap: 18px;
}
.form,
.security,
.employee {
    padding: 24px;
}
.form h3,
.security h3,
.employee h3 {
    margin-top: 0;
}
.avatar-row {
    display: flex;
    align-items: center;
    gap: 15px;
    margin-bottom: 20px;
}
.avatar-row small {
    display: block;
    margin-top: 6px;
    color: var(--muted);
}
.two {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 14px;
}
.security p {
    display: grid;
    grid-template-columns: 1fr auto auto;
    align-items: center;
    padding: 12px 0;
    border-top: 1px solid var(--line);
    font-size: 13px;
}
.security p span {
    color: var(--muted);
}
.employee {
    margin-top: 18px;
}
@media (max-width: 750px) {
    .profile-grid,
    .two {
        grid-template-columns: 1fr;
    }
}
</style>
