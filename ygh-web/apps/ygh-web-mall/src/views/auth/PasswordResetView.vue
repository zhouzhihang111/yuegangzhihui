<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import {
    confirmPasswordReset,
    fetchCaptcha,
    requestPasswordReset,
    type CaptchaChallenge,
} from "@ygh/web-shared";
import { useHttp } from "@/api/client";
const step = ref<"request" | "confirm">("request"),
    loading = ref(false);
const captcha = ref<CaptchaChallenge>();
const form = reactive({
    principal: "",
    captchaAnswer: "",
    resetToken: "",
    newPassword: "",
    confirmPassword: "",
});
const captchaSource = computed(() =>
    captcha.value
        ? `data:${captcha.value.mimeType};base64,${captcha.value.imageBase64}`
        : "",
);
async function loadCaptcha() {
    try {
        captcha.value = await fetchCaptcha(useHttp());
        form.captchaAnswer = "";
    } catch {
        ElMessage.error("验证码加载失败");
    }
}
async function requestReset() {
    if (!captcha.value) return;
    loading.value = true;
    try {
        await requestPasswordReset(useHttp(), {
            principal: form.principal,
            captchaChallengeId: captcha.value.challengeId,
            captchaAnswer: form.captchaAnswer,
        });
        step.value = "confirm";
        ElMessage.success("如果账号存在，重置凭据已发送到站内通知");
    } catch {
        ElMessage.error("申请失败，请刷新验证码后重试");
        await loadCaptcha();
    } finally {
        loading.value = false;
    }
}
async function confirmReset() {
    loading.value = true;
    try {
        await confirmPasswordReset(useHttp(), {
            resetToken: form.resetToken,
            newPassword: form.newPassword,
            confirmPassword: form.confirmPassword,
        });
        ElMessage.success("密码已重置，请使用新密码登录");
        location.assign("/login");
    } catch {
        ElMessage.error("重置凭据无效、已过期或新密码不符合安全策略");
    } finally {
        loading.value = false;
    }
}
onMounted(loadCaptcha);
</script>
<template>
    <div class="reset-page">
        <section class="paper-card">
            <RouterLink to="/login">← 返回登录</RouterLink>
            <h1 class="serif">重置登录密码</h1>
            <p>系统不会公开账号是否存在。重置凭据只通过企业站内通知发送。</p>
            <el-form
                v-if="step === 'request'"
                label-position="top"
                @submit.prevent="requestReset"
                ><el-form-item label="账号"
                    ><el-input
                        v-model="form.principal"
                        autocomplete="username" /></el-form-item
                ><el-form-item label="图形验证码"
                    ><div class="captcha-row">
                        <el-input v-model="form.captchaAnswer" /><button
                            type="button"
                            @click="loadCaptcha"
                        >
                            <img
                                v-if="captchaSource"
                                :src="captchaSource"
                                alt="图形验证码"
                            />
                        </button></div></el-form-item
                ><el-button
                    native-type="submit"
                    type="primary"
                    :loading="loading"
                    >申请重置</el-button
                ></el-form
            ><el-form v-else label-position="top" @submit.prevent="confirmReset"
                ><el-form-item label="重置凭据"
                    ><el-input
                        v-model="form.resetToken"
                        type="textarea"
                        autocomplete="one-time-code" /></el-form-item
                ><el-form-item label="新密码"
                    ><el-input
                        v-model="form.newPassword"
                        type="password"
                        show-password
                        autocomplete="new-password" /></el-form-item
                ><el-form-item label="确认新密码"
                    ><el-input
                        v-model="form.confirmPassword"
                        type="password"
                        show-password
                        autocomplete="new-password" /></el-form-item
                ><el-button
                    native-type="submit"
                    type="primary"
                    :loading="loading"
                    >确认重置</el-button
                ></el-form
            >
        </section>
    </div>
</template>
<style scoped>
.reset-page {
    min-height: 100vh;
    display: grid;
    place-items: center;
    padding: 30px;
    background: linear-gradient(135deg, #d9e7df, #f1eadc);
}
section {
    width: min(520px, 100%);
    padding: 38px;
}
h1 {
    margin: 24px 0 8px;
}
p {
    margin-bottom: 24px;
    color: var(--muted);
    line-height: 1.7;
}
.captcha-row {
    display: grid;
    grid-template-columns: 1fr 150px;
    gap: 10px;
    width: 100%;
}
.captcha-row button {
    height: 40px;
    border: 1px solid var(--line);
    background: #fff;
}
.captcha-row img {
    width: 100%;
    height: 100%;
    object-fit: contain;
}
.el-button {
    width: 100%;
}
</style>
