<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { Search } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import EnterpriseTable from "@/components/EnterpriseTable.vue";
import {
    changeAccountStatus,
    advanceSimulatedFulfillment,
    changeProductStatus,
    createEmployee,
    createProduct,
    createProductBatch,
    createProductBrand,
    createProductCategory,
    createProductTraceEvent,
    assignUserRoles,
    getUserAuthorities,
    listAccounts,
    listDepartments,
    listEmployees,
    listAdminInventory,
    listAdminOrders,
    listAdminProducts,
    listProductBrands,
    listProductCategories,
    listPositions,
    listWalletTransactions,
    listWalletAccounts,
    runCommerceReconciliation,
    updateProduct,
    type AdminAccount,
    type Department,
    type Employee,
    type Position,
    type Product,
    type ProductBrand,
    type ProductCategory,
    type Order,
    type CommerceReconciliation,
    type WalletAccount,
} from "@/api/operations";
type Row = Record<string, unknown>;
const route = useRoute();
const keyword = ref("");
const status = ref("");
const loading = ref(true);
const rows = ref<Row[]>([]);
const pageNo = ref(1);
const pageSize = ref(20);
const unsupported = ref("");
const reconciliation = ref<CommerceReconciliation>();
const reconciliationDialog = ref(false);
const walletAccounts = ref<WalletAccount[]>([]);
const walletAccountsDialog = ref(false);
const employeeDialog = ref(false);
const savingEmployee = ref(false);
const selectedAccount = ref<AdminAccount>();
const departments = ref<Department[]>([]);
const positions = ref<Position[]>([]);
const employees = ref<Employee[]>([]);
const employeeForm = reactive({
    employeeNo: "",
    departmentId: "",
    positionIds: [] as string[],
    hiredOn: "",
});
const productDialog = ref(false);
const savingProduct = ref(false);
const editingProduct = ref<Product>();
const traceDialog = ref(false);
const catalogDialog = ref<"category" | "brand" | "">("");
const traceProduct = ref<Product>();
const traceTab = ref("batch");
const categories = ref<ProductCategory[]>([]);
const brands = ref<ProductBrand[]>([]);
const productForm = reactive({
    categoryId: "",
    brandId: "",
    name: "",
    skuCode: "",
    price: "0.00",
    currency: "CNY",
    imagesText: "",
    traceabilityCode: "",
    specificationsText: "{}",
});
const batchForm = reactive({
    batchNo: "",
    origin: "",
    proofUrl: "",
    producedOn: "",
    expiresOn: "",
    traceDescription: "",
});
const traceForm = reactive({
    type: "CUSTOMS_CLEARED",
    location: "",
    occurredAt: "",
    detailsText: "{}",
});
const categoryForm = reactive({
    parentId: "",
    code: "",
    name: "",
    sortOrder: 0,
});
const brandForm = reactive({ code: "", name: "", logoUrl: "" });
const configs: Record<
    string,
    { description: string; columns: Array<{ key: string; label: string }> }
> = {
    user: {
        description: "管理消费者账号和账号状态。",
        columns: [
            { key: "userId", label: "用户 ID" },
            { key: "principal", label: "登录账号" },
            { key: "accountType", label: "账号类型" },
            { key: "employeeStatus", label: "员工身份" },
            { key: "status", label: "状态" },
            { key: "failedLoginCount", label: "失败次数" },
            { key: "lastLoginAt", label: "最近登录" },
        ],
    },
    product: {
        description: "维护商品、价格、溯源与上下架。",
        columns: [
            { key: "skuCode", label: "SKU" },
            { key: "name", label: "商品名称" },
            { key: "categoryId", label: "类目 ID" },
            { key: "price", label: "售价" },
            { key: "currency", label: "币种" },
            { key: "status", label: "状态" },
        ],
    },
    order: {
        description: "查看订单状态机与模拟支付结果。",
        columns: [
            { key: "orderNo", label: "订单号" },
            { key: "userId", label: "用户 ID" },
            { key: "totalAmount", label: "金额" },
            { key: "currency", label: "币种" },
            { key: "status", label: "状态" },
            { key: "createdAt", label: "创建时间" },
        ],
    },
    inventory: {
        description:
            "查看库存领域服务公开的只读管理视图；调整仍必须形成库存业务流水。",
        columns: [
            { key: "skuId", label: "SKU ID" },
            { key: "available", label: "可用" },
            { key: "locked", label: "锁定" },
            { key: "sold", label: "已售" },
            { key: "version", label: "版本" },
        ],
    },
    wallet: {
        description: "审计虚拟充值、支付和退款流水；不接入任何真实支付渠道。",
        columns: [
            { key: "transactionId", label: "流水 ID" },
            { key: "userId", label: "用户 ID" },
            { key: "type", label: "类型" },
            { key: "amount", label: "金额" },
            { key: "status", label: "状态" },
            { key: "createdAt", label: "发生时间" },
        ],
    },
};
const entity = computed(() => String(route.meta.entity));
const config = computed(() => configs[entity.value] || configs.user!);
const visible = computed(() =>
    rows.value.filter((row) =>
        Object.values(row)
            .join(" ")
            .toLowerCase()
            .includes(keyword.value.toLowerCase()),
    ),
);
const pagedRows = computed(() =>
    visible.value.slice((pageNo.value - 1) * pageSize.value, pageNo.value * pageSize.value),
);
async function load() {
    pageNo.value = 1;
    loading.value = true;
    unsupported.value = "";
    try {
        if (entity.value === "user") {
            const [accounts, employeeRecords] = await Promise.all([
                listAccounts(keyword.value, status.value),
                listEmployees(),
            ]);
            employees.value = employeeRecords;
            const employeesByUserId = new Map(
                employeeRecords.map((employee) => [employee.userId, employee]),
            );
            rows.value = accounts.map((account) => ({
                ...account,
                employeeStatus:
                    employeesByUserId.get(account.userId)?.status ?? "非员工",
            })) as unknown as Row[];
        }
        else if (entity.value === "product") {
            const [products, categoryOptions, brandOptions] = await Promise.all(
                [
                    listAdminProducts(keyword.value, status.value),
                    listProductCategories(),
                    listProductBrands(),
                ],
            );
            rows.value = products as unknown as Row[];
            categories.value = categoryOptions.filter((item) => item.enabled);
            brands.value = brandOptions.filter((item) => item.enabled);
        } else if (entity.value === "order")
            rows.value = (await listAdminOrders(
                status.value,
            )) as unknown as Row[];
        else if (entity.value === "inventory")
            rows.value = (await listAdminInventory()) as unknown as Row[];
        else if (entity.value === "wallet")
            rows.value = (await listWalletTransactions()) as unknown as Row[];
        else {
            rows.value = [];
            unsupported.value = config.value.description;
        }
    } catch {
        ElMessage.error(`${String(route.meta.title)}加载失败`);
    } finally {
        loading.value = false;
    }
}
function openProduct(product?: Product) {
    editingProduct.value = product;
    Object.assign(productForm, {
        categoryId: product?.categoryId ?? "",
        brandId: product?.brandId ?? "",
        name: product?.name ?? "",
        skuCode: product?.skuCode ?? "",
        price: product?.price ?? "0.00",
        currency: product?.currency ?? "CNY",
        imagesText: product?.images?.join("\n") ?? "",
        traceabilityCode: product?.traceabilityCode ?? "",
        specificationsText: JSON.stringify(
            product?.specifications ?? {},
            null,
            2,
        ),
    });
    productDialog.value = true;
}
async function saveProduct() {
    if (!productForm.categoryId || !productForm.name || !productForm.skuCode) {
        ElMessage.warning("请完整填写类目、商品名称和 SKU");
        return;
    }
    let specifications: Record<string, string>;
    try {
        specifications = JSON.parse(productForm.specificationsText);
    } catch {
        ElMessage.warning("规格必须是合法的 JSON 对象");
        return;
    }
    const command = {
        categoryId: productForm.categoryId,
        brandId: productForm.brandId || undefined,
        name: productForm.name.trim(),
        skuCode: productForm.skuCode.trim(),
        price: productForm.price,
        currency: productForm.currency,
        images: productForm.imagesText
            .split("\n")
            .map((item) => item.trim())
            .filter(Boolean),
        traceabilityCode: productForm.traceabilityCode.trim() || undefined,
        specifications,
    };
    savingProduct.value = true;
    try {
        if (editingProduct.value)
            await updateProduct(editingProduct.value, command);
        else await createProduct({ ...command, version: 0 });
        ElMessage.success(editingProduct.value ? "商品已更新" : "商品已创建");
        productDialog.value = false;
        await load();
    } catch {
        ElMessage.error("商品保存失败，请检查输入或数据版本");
    } finally {
        savingProduct.value = false;
    }
}
function openTrace(product: Product) {
    traceProduct.value = product;
    traceDialog.value = true;
}
async function saveBatch() {
    if (!traceProduct.value || !batchForm.batchNo.trim()) return;
    savingProduct.value = true;
    try {
        await createProductBatch(traceProduct.value.skuId, {
            ...batchForm,
            producedOn: batchForm.producedOn || undefined,
            expiresOn: batchForm.expiresOn || undefined,
        });
        Object.assign(batchForm, {
            batchNo: "",
            origin: "",
            proofUrl: "",
            producedOn: "",
            expiresOn: "",
            traceDescription: "",
        });
        ElMessage.success("商品批次已创建");
    } catch {
        ElMessage.error("批次创建失败，请核对批次号和日期");
    } finally {
        savingProduct.value = false;
    }
}
async function saveTraceEvent() {
    if (!traceProduct.value || !traceForm.type.trim() || !traceForm.occurredAt)
        return;
    let details: Record<string, unknown>;
    try {
        details = JSON.parse(traceForm.detailsText);
    } catch {
        ElMessage.warning("溯源详情必须是合法 JSON 对象");
        return;
    }
    savingProduct.value = true;
    try {
        await createProductTraceEvent(traceProduct.value.skuId, {
            type: traceForm.type,
            location: traceForm.location || undefined,
            occurredAt: new Date(traceForm.occurredAt).toISOString(),
            details,
        });
        ElMessage.success("溯源事件已记录");
    } catch {
        ElMessage.error("溯源事件保存失败");
    } finally {
        savingProduct.value = false;
    }
}
async function saveCatalog() {
    savingProduct.value = true;
    try {
        if (catalogDialog.value === "category")
            await createProductCategory({
                ...categoryForm,
                parentId: categoryForm.parentId || undefined,
            });
        if (catalogDialog.value === "brand")
            await createProductBrand({
                ...brandForm,
                logoUrl: brandForm.logoUrl || undefined,
            });
        catalogDialog.value = "";
        ElMessage.success("商品目录已更新");
        await load();
    } catch {
        ElMessage.error("目录保存失败，请检查编码唯一性");
    } finally {
        savingProduct.value = false;
    }
}
function updateCatalogDialogVisibility(visible: boolean) {
    if (!visible) catalogDialog.value = "";
}
function format(value: unknown) {
    if (typeof value === "string" && /^\d{4}-\d\d-\d\dT/.test(value))
        return new Date(value).toLocaleString("zh-CN");
    return value ?? "--";
}
async function changeState(row: Row) {
    try {
        if (entity.value === "user") {
            const account = row as unknown as AdminAccount;
            const next = account.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
            const { value } = await ElMessageBox.prompt(
                `确认将账号状态改为 ${next}？请输入原因`,
                "账号状态变更",
                {
                    inputValidator: (value) =>
                        Boolean(value?.trim()) || "原因不能为空",
                },
            );
            await changeAccountStatus(account, next, value);
        } else if (entity.value === "product") {
            const product = row as unknown as Product;
            const next =
                product.status === "PUBLISHED" ? "OFF_SHELF" : "PUBLISHED";
            await ElMessageBox.confirm(
                `确认将商品状态改为 ${next}？`,
                "商品状态变更",
            );
            await changeProductStatus(product, next);
        }
        ElMessage.success("状态已更新");
        await load();
    } catch (error) {
        if (error !== "cancel" && error !== "close")
            ElMessage.error("状态更新失败，版本可能已变化");
    }
}
async function fulfill(row: Row) {
    const order = row as unknown as Order;
    const label = order.status === "PAID" ? "开始模拟处理" : "标记模拟完成";
    await ElMessageBox.confirm(
        `确认${label}订单 ${order.orderNo}？`,
        "模拟履约",
    );
    try {
        await advanceSimulatedFulfillment(order);
        ElMessage.success("订单履约状态已更新");
        await load();
    } catch {
        ElMessage.error("履约状态更新失败，订单状态或版本可能已变化");
    }
}
async function reconcileCommerce() {
    loading.value = true;
    try {
        reconciliation.value = await runCommerceReconciliation();
        reconciliationDialog.value = true;
        ElMessage.success("交易对账已完成");
    } catch {
        ElMessage.error("交易对账执行失败，请检查钱包和库存服务");
    } finally {
        loading.value = false;
    }
}
async function openWalletAccounts() {
    loading.value = true;
    try {
        walletAccounts.value = await listWalletAccounts();
        walletAccountsDialog.value = true;
    } catch {
        ElMessage.error("钱包账户加载失败");
    } finally {
        loading.value = false;
    }
}
async function openEmployeePromotion(account: AdminAccount) {
    selectedAccount.value = account;
    savingEmployee.value = true;
    try {
        const [departmentOptions, positionOptions, employeeRecords] =
            await Promise.all([
                listDepartments(),
                listPositions(),
                listEmployees(),
            ]);
        departments.value = departmentOptions.filter((item) => item.enabled);
        positions.value = positionOptions.filter((item) => item.enabled);
        employees.value = employeeRecords;
        const existing = employeeRecords.find(
            (employee) => employee.userId === account.userId,
        );
        Object.assign(employeeForm, {
            employeeNo:
                existing?.employeeNo ?? `YG${account.userId.slice(-10)}`,
            departmentId: existing?.departmentId ?? "",
            positionIds: existing?.positionIds ?? [],
            hiredOn: existing?.hiredOn ?? new Date().toISOString().slice(0, 10),
        });
        employeeDialog.value = true;
    } catch {
        ElMessage.error("员工转换信息加载失败，请检查组织服务状态");
    } finally {
        savingEmployee.value = false;
    }
}
async function promoteToEmployee() {
    const account = selectedAccount.value;
    if (!account || !employeeForm.employeeNo.trim()) {
        ElMessage.warning("请填写唯一工号");
        return;
    }
    savingEmployee.value = true;
    let employeeCreated = false;
    try {
        let employee = employees.value.find(
            (item) => item.userId === account.userId,
        );
        if (!employee) {
            employee = await createEmployee({
                userId: account.userId,
                employeeNo: employeeForm.employeeNo.trim(),
                departmentId: employeeForm.departmentId || undefined,
                positionIds: employeeForm.positionIds,
                hiredOn: employeeForm.hiredOn || undefined,
            });
            employees.value.push(employee);
            employeeCreated = true;
        }
        const authority = await getUserAuthorities(account.userId);
        if (!authority.roles.includes("EMPLOYEE")) {
            await assignUserRoles(
                authority,
                [...new Set([...authority.roles, "EMPLOYEE"])],
                `管理员将账号 ${account.principal} 转为内部员工`,
            );
        }
        employeeDialog.value = false;
        ElMessage.success("已转为内部员工；该用户重新登录后可访问培训功能");
        await load();
    } catch {
        ElMessage.error(
            employeeCreated
                ? "员工档案已创建，但角色授权失败；请再次点击“员工设置”重试"
                : "转为员工失败，请检查工号、部门、岗位及角色版本",
        );
    } finally {
        savingEmployee.value = false;
    }
}
watch(() => route.fullPath, load, { immediate: true });
</script>
<template>
    <div class="page-head">
        <div>
            <h1>{{ route.meta.title }}</h1>
            <p>{{ config.description }}</p>
        </div>
        <div v-if="entity === 'product'">
            <el-button @click="catalogDialog = 'category'">新增类目</el-button>
            <el-button @click="catalogDialog = 'brand'">新增品牌</el-button>
            <el-button type="primary" @click="openProduct()"
                >新增商品</el-button
            >
        </div>
        <el-button
            v-if="entity === 'order'"
            type="primary"
            @click="reconcileCommerce"
            >执行交易对账</el-button
        >
        <el-button
            v-if="entity === 'wallet'"
            type="primary"
            @click="openWalletAccounts"
            >查看钱包账户</el-button
        >
    </div>
    <div class="panel filter-row">
        <el-input
            v-model="keyword"
            :prefix-icon="Search"
            placeholder="输入关键字查询"
            clearable
            style="width: 280px"
            @keyup.enter="load"
        /><el-input
            v-model="status"
            placeholder="状态编码（可选）"
            clearable
            style="width: 180px"
        /><el-button type="primary" @click="load">查询</el-button
        ><el-button
            @click="
                keyword = '';
                status = '';
                load();
            "
            >重置</el-button
        >
    </div>
    <el-alert
        v-if="unsupported"
        :title="unsupported"
        description="为避免 Admin 跨库或绕过服务边界，页面不会用演示数据伪装此能力。需要先由对应领域服务提供经过权限控制的管理查询契约。"
        type="warning"
        :closable="false"
        style="margin-bottom: 14px"
    />
    <section v-loading="loading" class="panel table-panel">
        <el-table :data="pagedRows" stripe empty-text="暂无真实数据"
            ><el-table-column
                v-for="col in config.columns"
                :key="col.key"
                :prop="col.key"
                :label="col.label"
                min-width="130"
                ><template #default="scope"
                    ><el-tag
                        v-if="col.key === 'status'"
                        size="small"
                        effect="plain"
                        >{{ format(scope.row[col.key]) }}</el-tag
                    ><span v-else>{{
                        format(scope.row[col.key])
                    }}</span></template
                ></el-table-column
            ><el-table-column
                v-if="['user', 'product', 'order'].includes(entity)"
                label="操作"
                width="270"
                ><template #default="scope"
                    ><el-button
                        v-if="
                            entity === 'order' &&
                            ['PAID', 'PROCESSING'].includes(scope.row.status)
                        "
                        link
                        type="primary"
                        @click="fulfill(scope.row)"
                        >{{
                            scope.row.status === "PAID"
                                ? "开始处理"
                                : "模拟完成"
                        }}</el-button
                    >
                    ><el-button
                        v-if="entity === 'product'"
                        link
                        type="primary"
                        @click="openProduct(scope.row as Product)"
                        >编辑</el-button
                    >
                    ><el-button
                        v-if="entity === 'product'"
                        link
                        type="primary"
                        @click="openTrace(scope.row as Product)"
                        >批次/溯源</el-button
                    >
                    ><el-button
                        v-if="entity === 'user'"
                        link
                        type="success"
                        :loading="savingEmployee"
                        @click="openEmployeePromotion(scope.row as AdminAccount)"
                        >{{
                            scope.row.employeeStatus === "非员工"
                                ? "转为员工"
                                : "员工设置"
                        }}</el-button
                    ><el-button
                        v-if="entity !== 'order'"
                        link
                        type="primary"
                        @click="changeState(scope.row)"
                        >{{
                            entity === "user"
                                ? scope.row.status === "ACTIVE"
                                    ? "停用"
                                    : "启用"
                                : scope.row.status === "PUBLISHED"
                                  ? "下架"
                                  : "上架"
                        }}</el-button
                    ></template
                ></el-table-column
            ></el-table
        ><el-pagination
            background
            v-model:current-page="pageNo"
            v-model:page-size="pageSize"
            layout="total, sizes, prev, pager, next"
            :page-sizes="[10, 20, 50, 100]"
            :total="visible.length"
            style="margin-top: 16px; justify-content: flex-end"
        />
    </section>
    <el-dialog
        v-model="employeeDialog"
        title="转为内部员工"
        width="600px"
        destroy-on-close
    >
        <el-alert
            title="完成后将同时建立员工档案并授予 EMPLOYEE 角色"
            description="该用户需要重新登录，培训课程、学习任务和学习档案才会显示。重复操作会检查已有档案，可用于补全中断的授权步骤。"
            type="info"
            :closable="false"
            style="margin-bottom: 18px"
        />
        <el-descriptions :column="1" border style="margin-bottom: 18px">
            <el-descriptions-item label="登录账号">{{
                selectedAccount?.principal
            }}</el-descriptions-item>
            <el-descriptions-item label="用户 ID">{{
                selectedAccount?.userId
            }}</el-descriptions-item>
        </el-descriptions>
        <el-form label-position="top">
            <el-form-item label="员工工号" required>
                <el-input v-model="employeeForm.employeeNo" maxlength="32" />
            </el-form-item>
            <el-form-item label="所属部门">
                <el-select v-model="employeeForm.departmentId" clearable filterable>
                    <el-option
                        v-for="item in departments"
                        :key="item.id"
                        :label="item.name"
                        :value="item.id"
                    />
                </el-select>
            </el-form-item>
            <el-form-item label="岗位">
                <el-select v-model="employeeForm.positionIds" multiple filterable>
                    <el-option
                        v-for="item in positions"
                        :key="item.id"
                        :label="item.name"
                        :value="item.id"
                    />
                </el-select>
            </el-form-item>
            <el-form-item label="入职日期">
                <el-date-picker
                    v-model="employeeForm.hiredOn"
                    type="date"
                    value-format="YYYY-MM-DD"
                />
            </el-form-item>
        </el-form>
        <template #footer>
            <el-button @click="employeeDialog = false">取消</el-button>
            <el-button
                type="primary"
                :loading="savingEmployee"
                @click="promoteToEmployee"
                >确认转为员工</el-button
            >
        </template>
    </el-dialog>
    <el-dialog
        v-model="productDialog"
        :title="editingProduct ? '编辑商品' : '新增商品'"
        width="720px"
        destroy-on-close
    >
        <el-form label-position="top" class="product-form">
            <el-form-item label="类目" required>
                <el-select v-model="productForm.categoryId" filterable>
                    <el-option
                        v-for="item in categories"
                        :key="item.id"
                        :label="item.name"
                        :value="item.id"
                    />
                </el-select>
            </el-form-item>
            <el-form-item label="品牌">
                <el-select v-model="productForm.brandId" clearable filterable>
                    <el-option
                        v-for="item in brands"
                        :key="item.id"
                        :label="item.name"
                        :value="item.id"
                    />
                </el-select>
            </el-form-item>
            <el-form-item label="商品名称" required>
                <el-input v-model="productForm.name" maxlength="120" />
            </el-form-item>
            <el-form-item label="SKU" required>
                <el-input
                    v-model="productForm.skuCode"
                    :disabled="Boolean(editingProduct)"
                    maxlength="64"
                />
            </el-form-item>
            <el-form-item label="售价" required>
                <el-input v-model="productForm.price" />
            </el-form-item>
            <el-form-item label="币种">
                <el-input v-model="productForm.currency" maxlength="3" />
            </el-form-item>
            <el-form-item label="溯源码">
                <el-input v-model="productForm.traceabilityCode" />
            </el-form-item>
            <el-form-item label="图片 URL（每行一个）" class="full-row">
                <el-input
                    v-model="productForm.imagesText"
                    type="textarea"
                    :rows="3"
                />
            </el-form-item>
            <el-form-item label="规格 JSON" class="full-row">
                <el-input
                    v-model="productForm.specificationsText"
                    type="textarea"
                    :rows="5"
                />
            </el-form-item>
        </el-form>
        <template #footer>
            <el-button @click="productDialog = false">取消</el-button>
            <el-button
                type="primary"
                :loading="savingProduct"
                @click="saveProduct"
                >保存</el-button
            >
        </template>
    </el-dialog>
    <el-dialog
        v-model="traceDialog"
        :title="`${traceProduct?.name || ''} · 批次与溯源`"
        width="660"
    >
        <el-tabs v-model="traceTab">
            <el-tab-pane label="新增批次" name="batch"
                ><el-form label-position="top" class="product-form"
                    ><el-form-item label="批次号"
                        ><el-input v-model="batchForm.batchNo" /></el-form-item
                    ><el-form-item label="原产地"
                        ><el-input v-model="batchForm.origin" /></el-form-item
                    ><el-form-item label="证明材料 URL" class="full-row"
                        ><el-input v-model="batchForm.proofUrl" /></el-form-item
                    ><el-form-item label="生产日期"
                        ><el-date-picker
                            v-model="batchForm.producedOn"
                            type="date"
                            value-format="YYYY-MM-DD" /></el-form-item
                    ><el-form-item label="有效期至"
                        ><el-date-picker
                            v-model="batchForm.expiresOn"
                            type="date"
                            value-format="YYYY-MM-DD" /></el-form-item
                    ><el-form-item label="溯源说明" class="full-row"
                        ><el-input
                            v-model="batchForm.traceDescription"
                            type="textarea" /></el-form-item></el-form
                ><el-button
                    type="primary"
                    :loading="savingProduct"
                    @click="saveBatch"
                    >保存批次</el-button
                ></el-tab-pane
            >
            <el-tab-pane label="记录溯源事件" name="event"
                ><el-form label-position="top"
                    ><el-form-item label="事件类型"
                        ><el-input v-model="traceForm.type" /></el-form-item
                    ><el-form-item label="发生地点"
                        ><el-input v-model="traceForm.location" /></el-form-item
                    ><el-form-item label="发生时间"
                        ><el-date-picker
                            v-model="traceForm.occurredAt"
                            type="datetime"
                            value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item
                    ><el-form-item label="详情 JSON"
                        ><el-input
                            v-model="traceForm.detailsText"
                            type="textarea"
                            :rows="5" /></el-form-item></el-form
                ><el-button
                    type="primary"
                    :loading="savingProduct"
                    @click="saveTraceEvent"
                    >保存事件</el-button
                ></el-tab-pane
            >
        </el-tabs>
    </el-dialog>
    <el-dialog
        :model-value="Boolean(catalogDialog)"
        @update:model-value="updateCatalogDialogVisibility"
        :title="catalogDialog === 'category' ? '新增商品类目' : '新增商品品牌'"
        width="520"
    >
        <el-form v-if="catalogDialog === 'category'" label-position="top"
            ><el-form-item label="上级类目"
                ><el-select
                    v-model="categoryForm.parentId"
                    clearable
                    style="width: 100%"
                    ><el-option
                        v-for="x in categories"
                        :key="x.id"
                        :label="x.name"
                        :value="x.id" /></el-select></el-form-item
            ><el-form-item label="类目编码"
                ><el-input v-model="categoryForm.code" /></el-form-item
            ><el-form-item label="类目名称"
                ><el-input v-model="categoryForm.name" /></el-form-item
            ><el-form-item label="排序"
                ><el-input-number
                    v-model="categoryForm.sortOrder" /></el-form-item
        ></el-form>
        <el-form v-else label-position="top"
            ><el-form-item label="品牌编码"
                ><el-input v-model="brandForm.code" /></el-form-item
            ><el-form-item label="品牌名称"
                ><el-input v-model="brandForm.name" /></el-form-item
            ><el-form-item label="Logo URL"
                ><el-input v-model="brandForm.logoUrl" /></el-form-item
        ></el-form>
        <template #footer
            ><el-button @click="catalogDialog = ''">取消</el-button
            ><el-button
                type="primary"
                :loading="savingProduct"
                @click="saveCatalog"
                >保存</el-button
            ></template
        >
    </el-dialog>
    <el-dialog
        v-model="reconciliationDialog"
        title="订单、钱包与库存对账结果"
        width="820"
        ><div v-if="reconciliation" class="metric-grid">
            <div class="metric">
                <span>检查订单</span><b>{{ reconciliation.checkedOrders }}</b>
            </div>
            <div class="metric">
                <span>一致订单</span
                ><b>{{ reconciliation.consistentOrders }}</b>
            </div>
            <div class="metric">
                <span>差异数量</span
                ><b>{{ reconciliation.discrepancies.length }}</b>
            </div>
        </div>
        <EnterpriseTable
            :items="reconciliation?.discrepancies || []"
            :search-fields="['orderId', 'orderStatus', 'dimension', 'expected', 'actual']"
            search-placeholder="检索订单或差异维度"
            v-slot="{ rows: dialogRows, emptyText }"
        ><el-table :data="dialogRows" :empty-text="emptyText"
            ><el-table-column prop="orderId" label="订单 ID" /><el-table-column
                prop="orderStatus"
                label="订单状态" /><el-table-column
                prop="dimension"
                label="差异维度" /><el-table-column
                prop="expected"
                label="预期" /><el-table-column
                prop="actual"
                label="实际" /></el-table></EnterpriseTable
    ></el-dialog>
    <el-dialog v-model="walletAccountsDialog" title="虚拟钱包账户" width="820"
        ><el-alert
            title="全部金额均为模拟资金，不产生真实交易"
            type="warning"
            :closable="false" /><EnterpriseTable
            :items="walletAccounts"
            :search-fields="['userId', 'currency', 'status']"
            status-field="status"
            search-placeholder="检索用户、币种或状态"
            v-slot="{ rows: dialogRows, emptyText }"
        ><el-table :data="dialogRows" :empty-text="emptyText"
            ><el-table-column prop="userId" label="用户 ID" /><el-table-column
                prop="availableBalance"
                label="可用余额" /><el-table-column
                prop="frozenBalance"
                label="冻结余额" /><el-table-column
                prop="currency"
                label="币种" /><el-table-column
                prop="status"
                label="状态" /><el-table-column
                prop="version"
                label="版本" /></el-table></EnterpriseTable
    ></el-dialog>
</template>

<style scoped>
.product-form {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 0 20px;
}
.full-row {
    grid-column: 1 / -1;
}
@media (max-width: 760px) {
    .product-form {
        grid-template-columns: 1fr;
    }
    .full-row {
        grid-column: auto;
    }
}
</style>
