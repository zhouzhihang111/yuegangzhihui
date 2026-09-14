<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import PageHeader from "@/components/PageHeader.vue";
import {
    getProgress,
    listAttempts,
    listCourses,
    listMyAssignments,
    type Course,
    type Progress,
    type QuizAttemptDetail,
} from "@/api/training";
const loading = ref(true);
const courses = ref<Course[]>([]);
const progress = ref<Progress[]>([]);
const attempts = ref<QuizAttemptDetail[]>([]);
const completed = computed(
    () => progress.value.filter((x) => x.status === "COMPLETED").length,
);
const average = computed(() =>
    attempts.value.length
        ? Math.round(
              attempts.value.reduce((s, x) => s + x.score, 0) /
                  attempts.value.length,
          )
        : 0,
);
const passRate = computed(() =>
    attempts.value.length
        ? Math.round(
              (attempts.value.filter((x) => x.passed).length /
                  attempts.value.length) *
                  100,
          )
        : 0,
);
onMounted(async () => {
    try {
        const assignments = await listMyAssignments();
        courses.value = await listCourses();
        progress.value = await Promise.all(
            assignments.map((x) => getProgress(x.assignmentId)),
        );
        attempts.value = (
            await Promise.all(
                assignments.map((x) => listAttempts(x.assignmentId)),
            )
        ).flat();
    } catch {
        ElMessage.error("学习档案加载失败");
    } finally {
        loading.value = false;
    }
});
const title = (courseId: string) =>
    courses.value.find((x) => x.id === courseId)?.title || courseId;
const statusText = (status: string) => {
    const labels: Record<string, string> = { ASSIGNED: "未开始", IN_PROGRESS: "未完成", COMPLETED: "已完成" };
    return labels[status] || status;
};
</script>
<template>
    <div v-loading="loading">
        <PageHeader
            title="学习档案"
            description="由服务端汇总有效阅读时长、章节完成与测验成绩。"
        />
        <div class="metric-grid">
            <div class="metric paper-card">
                <span>学习任务</span><b>{{ progress.length }}</b>
            </div>
            <div class="metric paper-card">
                <span>已完成课程</span><b>{{ completed }}</b>
            </div>
            <div class="metric paper-card">
                <span>闯关通过率</span><b>{{ passRate }}%</b>
            </div>
            <div class="metric paper-card">
                <span>测验平均分</span><b>{{ average }}</b>
            </div>
        </div>
        <section class="paper-card paths">
            <h3 class="serif">课程进度</h3>
            <article v-for="item in progress" :key="item.assignmentId">
                <div>
                    <b>{{ title(item.courseId) }}</b
                    ><small
                        >{{ statusText(item.status) }} · 最好成绩
                        {{ item.bestScore ?? "--" }}</small
                    >
                </div>
                <el-progress
                    :percentage="Number(item.progressPercent)"
                /><el-tag
                    :type="item.status === 'COMPLETED' ? 'success' : 'warning'"
                    >{{ statusText(item.status) }}</el-tag
                >
            </article>
            <el-empty v-if="!progress.length" description="暂无学习记录" />
        </section>
        <section class="paper-card attempts">
            <h3 class="serif">最近测验记录</h3>
            <el-table :data="attempts"
                ><el-table-column
                    prop="gateId"
                    label="关卡 ID"
                /><el-table-column prop="score" label="成绩" /><el-table-column
                    label="结果"
                    ><template #default="scope"
                        ><el-tag
                            :type="scope.row.passed ? 'success' : 'danger'"
                            >{{ scope.row.passed ? "通过" : "未通过" }}</el-tag
                        ></template
                    ></el-table-column
                ><el-table-column label="提交时间"
                    ><template #default="scope">{{
                        new Date(scope.row.submittedAt).toLocaleString("zh-CN")
                    }}</template></el-table-column
                ></el-table
            >
        </section>
    </div>
</template>
<style scoped>
.paths,
.attempts {
    padding: 22px;
    margin-top: 20px;
}
.paths h3,
.attempts h3 {
    margin-top: 0;
}
.paths article {
    display: grid;
    grid-template-columns: 240px 1fr 100px;
    align-items: center;
    gap: 20px;
    padding: 16px 0;
    border-top: 1px solid var(--line);
}
.paths b,
.paths small {
    display: block;
}
.paths small {
    margin-top: 5px;
    color: var(--muted);
}
@media (max-width: 700px) {
    .paths article {
        grid-template-columns: 1fr;
    }
    .paths article > .el-tag {
        width: max-content;
    }
}
</style>
