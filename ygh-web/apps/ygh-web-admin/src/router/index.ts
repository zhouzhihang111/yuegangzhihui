import {
    createRouter,
    createWebHistory,
    type RouteRecordRaw,
} from "vue-router";
import {
    createHttpClient,
    refresh,
    useSessionStore,
    type TokenPair,
} from "@ygh/web-shared";
import AdminLayout from "@/layouts/AdminLayout.vue";
const routes: RouteRecordRaw[] = [
    {
        path: "/login",
        component: () => import("@/views/LoginView.vue"),
        meta: { guest: true },
    },
    {
        path: "/",
        component: AdminLayout,
        meta: { requiresAuth: true, requiresAdmin: true },
        children: [
            { path: "", redirect: "/dashboard" },
            {
                path: "dashboard",
                component: () => import("@/views/DashboardView.vue"),
                meta: { title: "运营总览" },
            },
            {
                path: "users",
                component: () => import("@/views/EntityManagementView.vue"),
                meta: {
                    title: "用户与员工",
                    entity: "user",
                    permission: "user:read",
                },
            },
            {
                path: "organization",
                component: () =>
                    import("@/views/OrganizationManagementView.vue"),
                meta: { title: "组织与员工", permission: "organization:read" },
            },
            {
                path: "roles",
                component: () => import("@/views/RbacView.vue"),
                meta: { title: "角色与权限", permission: "system:role:read" },
            },
            {
                path: "products",
                component: () => import("@/views/EntityManagementView.vue"),
                meta: {
                    title: "商品中心",
                    entity: "product",
                    permission: "product:read",
                },
            },
            {
                path: "inventory",
                component: () => import("@/views/EntityManagementView.vue"),
                meta: {
                    title: "库存中心",
                    entity: "inventory",
                    permission: "inventory:read",
                },
            },
            {
                path: "orders",
                component: () => import("@/views/EntityManagementView.vue"),
                meta: {
                    title: "订单管理",
                    entity: "order",
                    permission: "order:read",
                },
            },
            {
                path: "wallet",
                component: () => import("@/views/EntityManagementView.vue"),
                meta: {
                    title: "钱包与流水",
                    entity: "wallet",
                    permission: "wallet:read",
                },
            },
            {
                path: "knowledge",
                component: () => import("@/views/KnowledgeGovernanceView.vue"),
                meta: { title: "知识库治理", permission: "knowledge:read" },
            },
            {
                path: "ai",
                component: () => import("@/views/AiGovernanceView.vue"),
                meta: { title: "AI 客服治理", permission: "ai:config:read" },
            },
            {
                path: "training",
                component: () => import("@/views/TrainingManagementView.vue"),
                meta: {
                    title: "培训运营",
                    permission: "training:statistics:read",
                },
            },
            {
                path: "notifications",
                component: () => import("@/views/NotificationOpsView.vue"),
                meta: {
                    title: "通知与补偿",
                    permission: "notification:compensate",
                },
            },
        ],
    },
    { path: "/403", component: () => import("@/views/ForbiddenView.vue") },
    { path: "/:pathMatch(.*)*", redirect: "/dashboard" },
];
const router = createRouter({ history: createWebHistory(), routes });

let authorityRefreshHttp: ReturnType<typeof createHttpClient> | undefined;

function useAuthorityRefreshHttp() {
    const session = useSessionStore();
    authorityRefreshHttp ??= createHttpClient(
        import.meta.env.VITE_GATEWAY_URL || "",
        {
            accessToken: () => session.bearer,
            refreshToken: () => session.renewal,
            updateTokens: (tokens: TokenPair) => session.rotate(tokens),
            clearSession: () => session.clear(),
        },
    );
    return authorityRefreshHttp;
}

async function refreshSessionOnce() {
    const session = useSessionStore();
    if (!session.renewal) return false;
    try {
        session.restore(await refresh(useAuthorityRefreshHttp(), session.renewal));
        return true;
    } catch {
        session.clear();
        return false;
    }
}

router.beforeEach(async (to) => {
    const session = useSessionStore();
    if (to.path === "/403") {
        if (!session.renewal) return "/login";
        if (await refreshSessionOnce()) {
            const updated = useSessionStore();
            if (updated.isAdmin) return "/dashboard";
            return true;
        }
        return "/login";
    }
    if (to.meta.requiresAuth && !session.authenticated) {
        if (!(await refreshSessionOnce()))
            return { path: "/login", query: { redirect: to.fullPath } };
    }
    if (to.meta.requiresAdmin && !session.isAdmin) {
        if (await refreshSessionOnce()) {
            const updated = useSessionStore();
            if (updated.isAdmin) return true;
        }
        return "/403";
    }
    const permission = to.meta.permission as string | undefined;
    if (permission && !session.can(permission)) {
        if (await refreshSessionOnce()) {
            const updated = useSessionStore();
            if (updated.can(permission)) return true;
        }
        return "/403";
    }
    if (to.meta.guest && session.authenticated) return "/dashboard";
    return true;
});
export default router;
