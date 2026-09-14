<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import VChart from "vue-echarts";
import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { BarChart } from "echarts/charts";
import { GridComponent, TooltipComponent } from "echarts/components";
import { ElMessage } from "element-plus";
import { getDashboard, type Dashboard } from "@/api/operations";
use([CanvasRenderer, BarChart, GridComponent, TooltipComponent]);
const loading = ref(true);
const data = ref<Dashboard>();
const chart = computed(() => ({
    tooltip: { trigger: "axis" },
    grid: { left: 45, right: 20, top: 20, bottom: 45 },
    xAxis: {
        type: "category",
        axisLabel: { rotate: 25 },
        data: data.value?.services.map((x) => x.service) || [],
    },
    yAxis: { type: "value", name: "ms" },
    series: [
        {
            type: "bar",
            data: data.value?.services.map((x) => x.latencyMs) || [],
            itemStyle: { color: "#0b625b" },
        },
    ],
}));
async function load() {
    loading.value = true;
    try {
        data.value = await getDashboard();
    } catch {
        ElMessage.error("运营总览加载失败");
    } finally {
        loading.value = false;
    }
}
onMounted(load);
</script>
<template>
    <div v-loading="loading">
        <div class="page-head">
            <div>
                <h1>运营总览</h1>
                <p>跨境商城、知识、AI 与培训的实时聚合视图，不跨库直接查询。</p>
            </div>
            <el-button @click="load">刷新数据</el-button>
        </div>
        <div class="metric-grid">
            <div class="metric panel">
                <span>服务总数</span
                ><b>{{ data?.summary.totalServices || 0 }}</b>
            </div>
            <div class="metric panel">
                <span>健康服务</span
                ><b>{{ data?.summary.healthyServices || 0 }}</b>
            </div>
            <div class="metric panel">
                <span>不可用服务</span
                ><b>{{ data?.summary.unavailableServices || 0 }}</b>
            </div>
            <div class="metric panel">
                <span>服务异常数</span
                ><b>{{
                    data?.pending.reduce((sum, x) => sum + x.count, 0) || 0
                }}</b
                ><small>{{
                    data
                        ? new Date(data.generatedAt).toLocaleString("zh-CN")
                        : ""
                }}</small>
            </div>
        </div>
        <div class="bottom-grid">
            <section class="panel chart">
                <header>
                    <b class="serif">服务响应延迟</b><span>聚合健康探测</span>
                </header>
                <VChart :option="chart" autoresize />
            </section>
            <section class="panel todo">
                <header>
                    <b class="serif">服务异常</b
                    ><el-tag type="danger"
                        >{{ data?.pending.length || 0 }} 类</el-tag
                    >
                </header>
                <article v-for="item in data?.pending || []" :key="item.type">
                    <span>{{ item.type.slice(0, 1) }}</span>
                    <div>
                        <b>{{ item.type }}</b
                        ><small>来源：{{ item.source }}</small>
                    </div>
                    <el-badge :value="item.count" />
                </article>
                <el-empty v-if="!data?.pending.length" description="暂无服务异常" />
            </section>
        </div>
        <section class="panel services">
            <header><b class="serif">系统状态</b><span>只读聚合</span></header>
            <div v-for="item in data?.services || []" :key="item.service">
                <b>{{ item.service }}</b
                ><el-tag
                    size="small"
                    :type="item.status === 'UP' ? 'success' : 'danger'"
                    >{{ item.status }}</el-tag
                ><span>{{ item.latencyMs }} ms</span>
            </div>
        </section>
    </div>
</template>
<style scoped>
.bottom-grid {
    display: grid;
    grid-template-columns: 1.2fr 1fr;
    gap: 14px;
    margin-top: 14px;
}
.bottom-grid section,
.services {
    padding: 16px;
}
.bottom-grid header,
.services header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    height: 30px;
}
.chart {
    height: 340px;
}
.chart .echarts {
    height: 280px;
}
.todo article {
    display: grid;
    grid-template-columns: 35px 1fr 30px;
    align-items: center;
    gap: 10px;
    padding: 12px 0;
    border-top: 1px solid var(--line);
}
.todo article > span {
    width: 30px;
    height: 30px;
    display: grid;
    place-items: center;
    background: #edf3ef;
}
.todo b,
.todo small {
    display: block;
}
.todo small {
    color: var(--muted);
}
.services {
    margin-top: 14px;
}
.services > div {
    display: grid;
    grid-template-columns: 1fr 120px 80px;
    align-items: center;
    padding: 11px 0;
    border-top: 1px solid var(--line);
}
@media (max-width: 900px) {
    .bottom-grid {
        grid-template-columns: 1fr;
    }
}
</style>
