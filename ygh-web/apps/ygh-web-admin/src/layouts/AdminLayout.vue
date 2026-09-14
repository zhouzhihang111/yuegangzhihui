<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { logout as revokeSession, useSessionStore } from "@ygh/web-shared";
import {
    Bell,
    Box,
    ChatDotRound,
    Collection,
    Document,
    Goods,
    Grid,
    Key,
    Management,
    Menu,
    Message,
    Money,
    Reading,
    SwitchButton,
    User,
    Warning,
} from "@element-plus/icons-vue";
import { getDashboard } from "@/api/operations";
import { useHttp } from "@/api/client";
const route = useRoute();
const router = useRouter();
const session = useSessionStore();
const collapsed = ref(false);
const title = computed(() => String(route.meta.title || "运营后台"));
const serviceHealth = ref<{ healthy: number; total: number }>();
const pendingCount = ref(0);
const noticeIcon = computed(() => (pendingCount.value > 0 ? Warning : Bell));
const noticeTip = computed(() =>
    pendingCount.value > 0
        ? "核心服务异常，点击查看运营总览"
        : "通知与补偿",
);
const openNotice = () => {
    router.push(pendingCount.value > 0 ? "/dashboard" : "/notifications");
};
const groups = [
    {
        name: "工作台",
        items: [{ to: "/dashboard", label: "运营总览", icon: Grid }],
    },
    {
        name: "交易运营",
        items: [
            { to: "/products", label: "商品中心", icon: Goods },
            { to: "/inventory", label: "库存中心", icon: Box },
            { to: "/orders", label: "订单管理", icon: Document },
            { to: "/wallet", label: "钱包与流水", icon: Money },
        ],
    },
    {
        name: "知识与成长",
        items: [
            { to: "/knowledge", label: "知识库治理", icon: Collection },
            { to: "/ai", label: "AI 客服治理", icon: ChatDotRound },
            { to: "/training", label: "培训运营", icon: Reading },
            { to: "/notifications", label: "通知与补偿", icon: Message },
        ],
    },
    {
        name: "组织与系统",
        items: [
            { to: "/users", label: "用户账号", icon: User },
            { to: "/organization", label: "组织与员工", icon: Management },
            { to: "/roles", label: "角色与权限", icon: Key },
        ],
    },
];
async function logout() {
    const refreshToken = session.renewal;
    try {
        if (refreshToken) await revokeSession(useHttp(), refreshToken);
    } finally {
        session.clear();
        await router.replace("/login");
    }
}
onMounted(async () => {
    try {
        const dashboard = await getDashboard();
        serviceHealth.value = {
            healthy: dashboard.summary.healthyServices,
            total: dashboard.summary.totalServices,
        };
        pendingCount.value = dashboard.pending.reduce(
            (total, item) => total + item.count,
            0,
        );
    } catch {
        serviceHealth.value = undefined;
    }
});
</script>
<template>
    <div class="admin-shell" :class="{ collapsed }">
        <aside>
            <div class="brand">
                <span>粤</span>
                <div>
                    <b class="serif">跨境智汇</b><small>ENTERPRISE OPS</small>
                </div>
            </div>
            <nav>
                <template v-for="group in groups" :key="group.name"
                    ><p>{{ group.name }}</p>
                    <RouterLink
                        v-for="item in group.items"
                        :key="item.to"
                        :to="item.to"
                        :class="{ active: route.path === item.to }"
                        :title="item.label"
                        ><el-icon><component :is="item.icon" /></el-icon
                        ><span>{{ item.label }}</span></RouterLink
                    ></template
                >
            </nav>
            <div class="system-health">
                <i :class="{ unavailable: !serviceHealth }" /><span
                    >核心服务可用</span
                ><b>{{
                    serviceHealth
                        ? `${serviceHealth.healthy} / ${serviceHealth.total}`
                        : "-- / --"
                }}</b>
            </div>
        </aside>
        <section>
            <header>
                <el-button
                    text
                    circle
                    :icon="Menu"
                    @click="collapsed = !collapsed"
                />
                <div>
                    <small>粤港甄选 /</small><b>{{ title }}</b>
                </div>
                <div class="header-actions">
                    <el-tooltip :content="noticeTip" placement="bottom">
                        <el-badge
                            :value="pendingCount"
                            :hidden="pendingCount === 0"
                            ><el-button
                                text
                                circle
                                :icon="noticeIcon"
                                @click="openNotice"
                        /></el-badge>
                    </el-tooltip>
                    <span class="operator"
                        ><el-avatar :size="32">管</el-avatar
                        ><b>{{
                            session.user?.displayName || "运营管理员"
                        }}</b></span
                    ><el-button text :icon="SwitchButton" @click="logout"
                        >退出</el-button
                    >
                </div>
            </header>
            <main><RouterView :key="route.fullPath" /></main>
        </section>
    </div>
</template>
<style scoped>
.admin-shell {
    min-height: 100vh;
    display: grid;
    grid-template-columns: 238px 1fr;
}
.admin-shell > aside {
    height: 100vh;
    position: sticky;
    top: 0;
    display: flex;
    flex-direction: column;
    background: var(--admin-deep);
    color: #dce8e4;
    transition: 0.2s;
}
.brand {
    height: 68px;
    display: flex;
    align-items: center;
    gap: 11px;
    padding: 0 20px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.brand > span {
    display: grid;
    place-items: center;
    width: 34px;
    height: 34px;
    background: var(--accent);
    color: #fff;
    outline: 1px solid rgba(255, 255, 255, 0.7);
    outline-offset: -4px;
}
.brand b,
.brand small {
    display: block;
}
.brand small {
    margin-top: 2px;
    color: #78938d;
    font-size: 8px;
    letter-spacing: 0.15em;
}
.admin-shell nav {
    flex: 1;
    overflow: auto;
    scrollbar-width: none;
    padding: 12px;
}
.admin-shell nav::-webkit-scrollbar {
    width: 0;
    height: 0;
}
.admin-shell nav p {
    margin: 16px 11px 7px;
    color: #66847d;
    font-size: 10px;
    letter-spacing: 0.15em;
}
.admin-shell nav a {
    display: flex;
    align-items: center;
    gap: 11px;
    margin: 3px 0;
    padding: 10px 12px;
    border-radius: 6px;
    color: #abc1bc;
    font-size: 13px;
}
.admin-shell nav a:hover,
.admin-shell nav a.active {
    background: rgba(255, 255, 255, 0.08);
    color: #fff;
}
.admin-shell nav a.active {
    box-shadow: inset 3px 0 var(--gold);
}
.system-health {
    display: grid;
    grid-template-columns: 8px 1fr auto;
    align-items: center;
    gap: 8px;
    margin: 12px;
    padding: 12px;
    background: rgba(255, 255, 255, 0.05);
    font-size: 10px;
}
.system-health i {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: #50ba8b;
}
.system-health i.unavailable {
    background: #d28b49;
}
.system-health b {
    color: #d8b86d;
}
.admin-shell > section {
    min-width: 0;
}
.admin-shell > section > header {
    height: 68px;
    position: sticky;
    top: 0;
    z-index: 20;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 0 22px;
    background: rgba(255, 255, 255, 0.93);
    border-bottom: 1px solid var(--line);
    backdrop-filter: blur(12px);
}
.admin-shell > section > header > div:nth-child(2) {
    display: flex;
    gap: 7px;
    font-size: 13px;
}
.admin-shell > section > header small {
    color: var(--muted);
}
.header-actions {
    display: flex;
    align-items: center;
    gap: 15px;
    margin-left: auto;
}
.operator {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;
}
.admin-shell main {
    padding: 22px;
}
.admin-shell.collapsed {
    grid-template-columns: 68px 1fr;
}
.collapsed .brand {
    padding: 0 17px;
}
.collapsed .brand > div,
.collapsed nav p,
.collapsed nav a span,
.collapsed .system-health span,
.collapsed .system-health b {
    display: none;
}
.collapsed nav a {
    justify-content: center;
}
.collapsed .system-health {
    display: flex;
    justify-content: center;
}
@media (max-width: 750px) {
    .admin-shell,
    .admin-shell.collapsed {
        grid-template-columns: 1fr;
    }
    .admin-shell > aside {
        display: none;
    }
    .operator b {
        display: none;
    }
}
</style>
