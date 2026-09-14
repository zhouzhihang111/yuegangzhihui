<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import {
    ElMessage,
    type FormInstance,
    type FormRules,
} from "element-plus";
import {
    fetchCaptcha,
    register,
    sessionUserFromAuthentication,
    useSessionStore,
    type ApiResponse,
    type CaptchaChallenge,
} from "@ygh/web-shared";
import { useHttp } from "@/api/client";

interface RegisterForm {
    principal: string;
    password: string;
    confirmPassword: string;
    captchaAnswer: string;
    agreementAccepted: boolean;
}

interface FieldValidationError {
    field?: string;
    message?: string;
}

interface ApiClientError {
    response?: {
        status?: number;
        data?: ApiResponse<unknown>;
    };
}

const router = useRouter();
const session = useSessionStore();
const formRef = ref<FormInstance>();
const loading = ref(false);
const captchaLoading = ref(false);
const captcha = ref<CaptchaChallenge>();
const submitError = ref("");
const form = reactive<RegisterForm>({
    principal: "",
    password: "",
    confirmPassword: "",
    captchaAnswer: "",
    agreementAccepted: false,
});

const captchaSource = computed(() =>
    captcha.value
        ? `data:${captcha.value.mimeType};base64,${captcha.value.imageBase64}`
        : "",
);

const rules: FormRules<RegisterForm> = {
    principal: [
        { required: true, message: "请输入用户名、手机号或邮箱", trigger: "blur" },
        { max: 190, message: "账号长度不能超过 190 个字符", trigger: "blur" },
    ],
    password: [
        { required: true, message: "请输入登录密码", trigger: "blur" },
        { min: 15, message: "密码至少需要 15 个字符", trigger: "blur" },
        { max: 128, message: "密码不能超过 128 个字符", trigger: "blur" },
    ],
    confirmPassword: [
        { required: true, message: "请再次输入密码", trigger: "blur" },
        {
            validator: (_rule, value, callback) => {
                if (value !== form.password) callback(new Error("两次输入的密码不一致"));
                else callback();
            },
            trigger: ["blur", "change"],
        },
    ],
    captchaAnswer: [
        { required: true, message: "请输入图片中的 6 位验证码", trigger: "blur" },
        {
            pattern: /^[A-Z0-9]{6}$/i,
            message: "验证码必须是 6 位数字或英文字母",
            trigger: "blur",
        },
    ],
    agreementAccepted: [
        {
            validator: (_rule, value, callback) => {
                if (value !== true) callback(new Error("请先阅读并同意服务条款与隐私说明"));
                else callback();
            },
            trigger: "change",
        },
    ],
};

async function loadCaptcha(clearSubmitError = true) {
    captchaLoading.value = true;
    form.captchaAnswer = "";
    if (clearSubmitError) submitError.value = "";
    try {
        captcha.value = await fetchCaptcha(useHttp());
        await formRef.value?.clearValidate("captchaAnswer");
    } catch {
        captcha.value = undefined;
        submitError.value = "验证码加载失败，请检查网络连接后重试。";
        ElMessage.error(submitError.value);
    } finally {
        captchaLoading.value = false;
    }
}

async function submit() {
    submitError.value = "";
    const valid = await formRef.value?.validate().catch(() => false);
    if (!valid) {
        submitError.value = "资料尚未填写完整，请按字段下方的红色提示修改。";
        return;
    }
    if (!captcha.value) {
        submitError.value = "验证码尚未加载，请点击验证码区域重新获取。";
        return;
    }

    loading.value = true;
    try {
        const result = await register(useHttp(), {
            ...form,
            principal: form.principal.trim(),
            captchaAnswer: form.captchaAnswer.trim(),
            captchaChallengeId: captcha.value.challengeId,
        });
        session.establish(
            result.tokens,
            sessionUserFromAuthentication(result, form.principal.trim()),
        );
        ElMessage.success("注册成功");
        await router.replace("/workspace/profile");
    } catch (error) {
        submitError.value = registrationErrorMessage(error);
        ElMessage.error(submitError.value);
        await loadCaptcha(false);
    } finally {
        loading.value = false;
    }
}

function registrationErrorMessage(error: unknown): string {
    if (!isApiClientError(error)) {
        return "注册失败，请稍后重试。";
    }
    const status = error.response?.status;
    const response = error.response?.data;
    if (!error.response) return "无法连接认证服务，请检查网络连接后重试。";
    if (status === 409) return "该账号已经注册，请返回登录或使用其他账号。";
    if (status === 429) return "操作过于频繁，请稍等一分钟后再试。";
    if (status === 503) return "认证服务暂时不可用，请稍后重试。";
    if (status === 400) {
        const fields = Array.isArray(response?.data)
            ? (response.data as FieldValidationError[])
                  .map((item) => item.message?.trim())
                  .filter((message): message is string => Boolean(message))
            : [];
        if (fields.length > 0) return fields.join("；");
        return "验证码不正确或已过期，或者密码不符合安全策略。请使用新验证码重试。";
    }
    return response?.message?.trim() || "注册失败，请稍后重试。";
}

function isApiClientError(error: unknown): error is ApiClientError {
    return typeof error === "object" && error !== null && "response" in error;
}

onMounted(() => loadCaptcha());
</script>

<template>
    <div class="register-page">
        <div class="register-card paper-card">
            <RouterLink to="/" class="brand">
                <span>粤</span><b class="serif">粤港甄选</b>
            </RouterLink>
            <h1 class="serif">创建平台账号</h1>
            <p>用于跨境购物、知识咨询和员工学习。员工岗位由管理员在后台关联。</p>

            <el-alert
                v-if="submitError"
                class="submit-error"
                :title="submitError"
                type="error"
                show-icon
                :closable="false"
            />

            <el-form
                ref="formRef"
                :model="form"
                :rules="rules"
                label-position="top"
                status-icon
                @submit.prevent="submit"
            >
                <el-form-item label="用户名 / 手机号 / 邮箱" prop="principal">
                    <el-input
                        v-model="form.principal"
                        size="large"
                        maxlength="190"
                        autocomplete="username"
                    />
                </el-form-item>
                <el-form-item label="登录密码" prop="password">
                    <el-input
                        v-model="form.password"
                        size="large"
                        type="password"
                        maxlength="128"
                        show-password
                        autocomplete="new-password"
                    />
                    <p class="field-hint">15–128 个字符，请勿使用常见弱密码。</p>
                </el-form-item>
                <el-form-item label="确认密码" prop="confirmPassword">
                    <el-input
                        v-model="form.confirmPassword"
                        size="large"
                        type="password"
                        maxlength="128"
                        show-password
                        autocomplete="new-password"
                    />
                </el-form-item>
                <el-form-item label="图形验证码" prop="captchaAnswer">
                    <div class="captcha-row">
                        <el-input
                            v-model="form.captchaAnswer"
                            size="large"
                            maxlength="6"
                            placeholder="请输入右侧 6 位字符"
                            @keyup.enter="submit"
                        />
                        <button
                            type="button"
                            class="captcha"
                            title="看不清？点击刷新验证码"
                            :disabled="captchaLoading"
                            @click="loadCaptcha()"
                        >
                            <img v-if="captchaSource" :src="captchaSource" alt="图形验证码" />
                            <span v-else>{{ captchaLoading ? "加载中…" : "重新加载" }}</span>
                        </button>
                    </div>
                    <p class="field-hint">验证码 5 分钟内有效；点击图片会刷新并清空旧答案。</p>
                </el-form-item>
                <el-form-item prop="agreementAccepted" class="agreement-item">
                    <el-checkbox v-model="form.agreementAccepted">
                        我已阅读并同意平台服务条款与隐私说明
                    </el-checkbox>
                </el-form-item>
                <el-button
                    native-type="submit"
                    type="primary"
                    size="large"
                    class="submit"
                    :loading="loading"
                    :disabled="captchaLoading"
                >
                    注册账号
                </el-button>
            </el-form>
            <div class="login">已有账号？<RouterLink to="/login">返回登录</RouterLink></div>
        </div>
    </div>
</template>

<style scoped>
.register-page {
    min-height: 100vh;
    display: grid;
    place-items: center;
    padding: 50px 20px;
    background: linear-gradient(135deg, #d9e7df, #f1eadc);
}
.register-card {
    width: min(520px, 100%);
    padding: 38px 44px;
}
.brand {
    display: flex;
    align-items: center;
    gap: 10px;
}
.brand span {
    display: grid;
    place-items: center;
    width: 32px;
    height: 32px;
    background: var(--cinnabar);
    color: #fff;
}
.register-card h1 {
    margin: 24px 0 8px;
}
.register-card > p {
    margin-bottom: 25px;
    color: var(--muted);
    line-height: 1.7;
}
.submit-error {
    margin-bottom: 20px;
}
.captcha-row {
    display: grid;
    grid-template-columns: 1fr 150px;
    gap: 10px;
    width: 100%;
}
.captcha {
    height: 40px;
    border: 1px solid #c8c3b5;
    background: #fff;
    cursor: pointer;
}
.captcha:disabled {
    cursor: wait;
    opacity: 0.65;
}
.captcha img {
    width: 100%;
    height: 100%;
    object-fit: contain;
}
.field-hint {
    width: 100%;
    margin: 6px 0 0;
    color: var(--muted);
    font-size: 12px;
    line-height: 1.5;
}
.agreement-item {
    margin-bottom: 0;
}
.submit {
    width: 100%;
    margin-top: 18px;
}
.login {
    margin-top: 20px;
    text-align: center;
    color: var(--muted);
    font-size: 13px;
}
.login a {
    color: var(--jade);
    font-weight: 600;
}
@media (max-width: 560px) {
    .register-card {
        padding: 30px 24px;
    }
    .captcha-row {
        grid-template-columns: 1fr 126px;
    }
}
</style>
