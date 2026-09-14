<script setup lang="ts">
import { computed, ref, watch } from "vue";

const props = withDefaults(
    defineProps<{
        items: unknown[];
        searchFields?: string[];
        searchPlaceholder?: string;
        statusField?: string;
        statusLabel?: string;
        pageSize?: number;
        emptyText?: string;
    }>(),
    {
        searchFields: () => [],
        searchPlaceholder: "输入关键字检索",
        statusField: "",
        statusLabel: "状态",
        pageSize: 10,
        emptyText: "暂无符合条件的数据",
    },
);

const keyword = ref("");
const status = ref("");
const pageNo = ref(1);
const currentPageSize = ref(props.pageSize);

const statuses = computed(() => {
    if (!props.statusField) return [];
    return [
        ...new Set(
            props.items
                .map((item) => (item as Record<string, unknown>)[props.statusField])
                .filter((value) => value !== undefined && value !== null)
                .map(String),
        ),
    ].sort();
});

const filteredItems = computed(() => {
    const normalizedKeyword = keyword.value.trim().toLocaleLowerCase("zh-CN");
    return props.items.filter((item) => {
        const record = item as Record<string, unknown>;
        const statusMatches =
            !props.statusField ||
            !status.value ||
            String(record[props.statusField]) === status.value;
        if (!statusMatches || !normalizedKeyword) return statusMatches;
        const values = props.searchFields.length
            ? props.searchFields.map((field) => record[field])
            : Object.values(record);
        return values.some((value) =>
            String(value ?? "")
                .toLocaleLowerCase("zh-CN")
                .includes(normalizedKeyword),
        );
    });
});

const rows = computed(() => {
    const start = (pageNo.value - 1) * currentPageSize.value;
    return filteredItems.value.slice(start, start + currentPageSize.value);
});

watch(
    () => [keyword.value, status.value, props.items.length],
    () => {
        pageNo.value = 1;
    },
);

watch(currentPageSize, () => {
    pageNo.value = 1;
});
</script>

<template>
    <div class="enterprise-table-toolbar">
        <el-input
            v-model="keyword"
            clearable
            :placeholder="searchPlaceholder"
            aria-label="列表关键字检索"
        />
        <el-select
            v-if="statusField && statuses.length"
            v-model="status"
            clearable
            :placeholder="statusLabel"
            aria-label="列表状态筛选"
        >
            <el-option
                v-for="option in statuses"
                :key="option"
                :label="option"
                :value="option"
            />
        </el-select>
        <span>共 {{ filteredItems.length }} 条</span>
    </div>
    <slot :rows="rows" :empty-text="emptyText" />
    <el-pagination
        v-if="filteredItems.length > currentPageSize"
        v-model:current-page="pageNo"
        v-model:page-size="currentPageSize"
        class="enterprise-table-pagination"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :page-sizes="[10, 20, 50, 100]"
        :total="filteredItems.length"
    />
</template>

<style scoped>
.enterprise-table-toolbar {
    display: grid;
    grid-template-columns: minmax(220px, 360px) minmax(140px, 220px) 1fr;
    align-items: center;
    gap: 12px;
    margin-bottom: 14px;
    color: var(--el-text-color-secondary);
    font-size: 13px;
}

.enterprise-table-toolbar > span {
    justify-self: end;
}

.enterprise-table-pagination {
    justify-content: flex-end;
    margin-top: 16px;
}

@media (max-width: 760px) {
    .enterprise-table-toolbar {
        grid-template-columns: 1fr;
    }

    .enterprise-table-toolbar > span {
        justify-self: start;
    }
}
</style>
