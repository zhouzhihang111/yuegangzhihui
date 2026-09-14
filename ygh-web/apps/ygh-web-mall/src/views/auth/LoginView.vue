<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import {
    login,
    sessionUserFromAuthentication,
    useSessionStore,
} from "@ygh/web-shared";
import { useHttp } from "@/api/client";

const router = useRouter();
const route = useRoute();
const session = useSessionStore();
const loading = ref(false);
const form = reactive({ username: "", password: "" });

async function submit() {
    loading.value = true;
    try {
        const result = await login(useHttp(), {
            principal: form.username,
            password: form.password,
        });
        session.establish(
            result.tokens,
            sessionUserFromAuthentication(result, form.username),
        );
        ElMessage.success("登录成功");
        await router.replace(
            String(route.query.redirect || "/workspace/profile"),
        );
    } catch {
        ElMessage.error("登录失败，请核对账号、密码或验证码要求");
    } finally {
        loading.value = false;
    }
}
</script>
<template>
    <div class="auth-page">
        <RouterLink to="/" class="auth-brand"
            ><span>粤</span>
            <div>
                <b class="serif">粤港甄选</b><small>跨境智汇 AI 平台</small>
            </div></RouterLink
        >
        <div class="auth-story">
            <span class="section-label">ONE ACCOUNT · FOUR SERVICES</span>
            <h1 class="serif">一套身份，连接<br />消费、知识与成长。</h1>
            <ul>
                <li>跨境商城与模拟钱包</li>
                <li>政策知识与可信 AI 客服</li>
                <li>员工岗位培训与闯关进度</li>
                <li>角色权限与企业数据隔离</li>
            </ul>
            <blockquote>“业务事实留在系统，AI 负责把事实讲清楚。”</blockquote>
        </div>
        <div class="auth-panel">
            <div class="paper-card login-card">
                <h2 class="serif">欢迎回来</h2>
                <p>登录后继续您的订单、咨询与学习任务。</p>
                <el-form label-position="top" @submit.prevent="submit"
                    ><el-form-item label="账号"
                        ><el-input
                            v-model="form.username"
                            size="large"
                            autocomplete="username" /></el-form-item
                    ><el-form-item label="密码"
                        ><el-input
                            v-model="form.password"
                            size="large"
                            type="password"
                            show-password
                            autocomplete="current-password"
                    /></el-form-item>
                    <div class="form-row">
                        <el-checkbox>记住设备</el-checkbox
                        ><RouterLink to="/password-reset"
                            >忘记密码？</RouterLink
                        >
                    </div>
                    <el-button
                        native-type="submit"
                        type="primary"
                        size="large"
                        :loading="loading"
                        class="submit"
                        >登录平台</el-button
                    ></el-form
                >
                <div class="register">
                    还没有账号？<RouterLink to="/register">立即注册</RouterLink>
                </div>
                <el-alert
                    title="平台不提供默认账号或默认密码，请使用已分配的个人账号。"
                    type="info"
                    :closable="false"
                />
            </div>
        </div>
    </div>
</template>
<style scoped>
.auth-page {
    min-height: 100vh;
    display: grid;
    grid-template-columns: 1.05fr 0.95fr;
    position: relative;
    background: linear-gradient(115deg, #0a4541 0 52%, #f4f0e6 52%);
}
.auth-brand {
    position: absolute;
    left: 42px;
    top: 32px;
    display: flex;
    align-items: center;
    gap: 11px;
    color: #fff;
}
.auth-brand > span {
    display: grid;
    place-items: center;
    width: 38px;
    height: 38px;
    background: var(--cinnabar);
    outline: 1px solid #fff;
    outline-offset: -4px;
}
.auth-brand b,
.auth-brand small {
    display: block;
}
.auth-brand small {
    color: #a8bfba;
    font-size: 10px;
}
.auth-story {
    align-self: center;
    padding: 120px max(50px, 10vw);
    color: #fff;
}
.auth-story h1 {
    font-size: 48px;
    line-height: 1.35;
}
.auth-story ul {
    padding: 0;
    list-style: none;
    color: #bad0cb;
    line-height: 2.2;
}
.auth-story li:before {
    content: "◇";
    margin-right: 10px;
    color: #dfbe71;
}
.auth-story blockquote {
    margin: 28px 0 0;
    padding: 18px;
    border-left: 3px solid var(--cinnabar);
    background: rgba(255, 255, 255, 0.05);
    color: #d4e1dd;
}
.auth-panel {
    display: grid;
    place-items: center;
    padding: 80px 40px;
}
.login-card {
    width: min(430px, 100%);
    padding: 38px;
}
.login-card h2 {
    margin: 0;
    font-size: 30px;
}
.login-card > p {
    margin-bottom: 28px;
    color: var(--muted);
}
.form-row {
    display: flex;
    justify-content: space-between;
    margin: -3px 0 20px;
    font-size: 13px;
}
.form-row a,
.register a {
    color: var(--jade);
    font-weight: 600;
}
.submit {
    width: 100%;
}
.register {
    margin: 22px 0;
    text-align: center;
    color: var(--muted);
    font-size: 13px;
}
@media (max-width: 800px) {
    .auth-page {
        grid-template-columns: 1fr;
        background: #f4f0e6;
    }
    .auth-story {
        display: none;
    }
    .auth-brand {
        color: var(--ink);
    }
    .auth-brand small {
        color: var(--muted);
    }
    .auth-panel {
        padding-top: 120px;
    }
}
</style>
