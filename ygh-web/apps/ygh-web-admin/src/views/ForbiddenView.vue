<script setup lang="ts">
import { useRouter } from "vue-router";
import { useSessionStore } from "@ygh/web-shared";

const router = useRouter();
const session = useSessionStore();

async function relogin() {
    session.clear();
    await router.replace("/login");
}
</script>

<template>
    <div class="forbidden">
        <b class="serif">403</b>
        <h1>权限不足</h1>
        <p>当前浏览器保存的后台会话没有运营模块权限，可能仍是升级前的旧登录状态。</p>
        <div class="actions">
            <el-button type="primary" @click="$router.push('/dashboard')">刷新权限并返回工作台</el-button>
            <el-button @click="relogin">清除旧会话重新登录</el-button>
        </div>
    </div>
</template>

<style scoped>
.forbidden{min-height:100vh;display:grid;place-content:center;text-align:center}.forbidden>b{font-size:86px;color:var(--accent)}.forbidden h1{margin:0}.forbidden p{max-width:420px;color:var(--muted);line-height:1.8}.actions{display:flex;justify-content:center;gap:10px;margin-top:18px}
</style>
