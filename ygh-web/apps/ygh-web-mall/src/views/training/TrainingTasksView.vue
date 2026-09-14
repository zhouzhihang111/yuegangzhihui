<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import PageHeader from "@/components/PageHeader.vue";
import {
    getProgress,
    listCourses,
    listMyAssignments,
    type Assignment,
    type Course,
    type Progress,
} from "@/api/training";
type Task = { assignment: Assignment; course?: Course; progress?: Progress };
const statusText = (status: string) => {
    const labels: Record<string, string> = { ASSIGNED: "未开始", IN_PROGRESS: "未完成", COMPLETED: "已完成" };
    return labels[status] || status;
};
const loading = ref(true);
const tasks = ref<Task[]>([]);
const pending = computed(
    () => tasks.value.filter((x) => x.assignment.status !== "COMPLETED").length,
);
const completion = computed(() =>
    tasks.value.length
        ? Math.round(
              ((tasks.value.length - pending.value) / tasks.value.length) * 100,
          )
        : 0,
);
onMounted(async () => {
    try {
        const [assignments, courses] = await Promise.all([
            listMyAssignments(),
            listCourses(),
        ]);
        const progress = await Promise.all(
            assignments.map((x) => getProgress(x.assignmentId)),
        );
        tasks.value = assignments.map((assignment, index) => ({
            assignment,
            course: courses.find((x) => x.id === assignment.courseId),
            progress: progress[index],
        }));
    } catch {
        ElMessage.error("学习任务加载失败");
    } finally {
        loading.value = false;
    }
});
</script>
<template>
    <PageHeader
        title="我的学习任务"
        description="学习进度与章节解锁由服务端根据有效阅读时长和闯关结果计算。"
        ><el-button @click="$router.push('/workspace/training/courses')"
            >浏览课程中心</el-button
        ></PageHeader
    >
    <div class="metric-grid">
        <div class="metric paper-card">
            <span>待完成任务</span><b>{{ pending }}</b>
        </div>
        <div class="metric paper-card">
            <span>课程完成率</span><b>{{ completion }}%</b>
        </div>
        <div class="metric paper-card">
            <span>最好成绩</span
            ><b>{{
                Math.max(0, ...tasks.map((x) => x.progress?.bestScore || 0))
            }}</b>
        </div>
        <div class="metric paper-card">
            <span>任务总数</span><b>{{ tasks.length }}</b>
        </div>
    </div>
    <section v-loading="loading" class="task-list">
        <article
            v-for="task in tasks"
            :key="task.assignment.assignmentId"
            class="paper-card"
        >
            <div class="course-index">
                {{ task.course?.title.slice(0, 1) || "课" }}
            </div>
            <div class="task-copy">
                <div>
                    <el-tag size="small" effect="plain">{{
                        task.course?.status || "课程"
                    }}</el-tag
                    ><el-tag
                        size="small"
                        :type="
                            task.assignment.status === 'COMPLETED'
                                ? 'success'
                                : task.assignment.status === 'IN_PROGRESS'
                                  ? 'warning'
                                  : 'info'
                        "
                        >{{ statusText(task.assignment.status) }}</el-tag
                    >
                </div>
                <h2 class="serif">
                    {{
                        task.course?.title || `课程 ${task.assignment.courseId}`
                    }}
                </h2>
                <p>
                    截止
                    {{
                        task.assignment.dueAt
                            ? new Date(task.assignment.dueAt).toLocaleString(
                                  "zh-CN",
                              )
                            : "未设置"
                    }}
                </p>
                <el-progress
                    :percentage="Number(task.progress?.progressPercent || 0)"
                    :stroke-width="9"
                />
            </div>
            <el-button
                :type="
                    task.assignment.status === 'IN_PROGRESS'
                        ? 'primary'
                        : 'default'
                "
                @click="
                    $router.push({
                        path: `/workspace/training/courses/${task.assignment.courseId}`,
                        query: { assignment: task.assignment.assignmentId },
                    })
                "
                >{{
                    task.assignment.status === "COMPLETED"
                        ? "查看档案"
                        : task.assignment.status === "IN_PROGRESS"
                          ? "继续学习"
                          : "开始学习"
                }}</el-button
            >
        </article>
        <el-empty
            v-if="!loading && !tasks.length"
            description="暂无岗位学习任务"
        />
    </section>
</template>
<style scoped>
.task-list {
    display: grid;
    gap: 14px;
    margin-top: 22px;
    min-height: 180px;
}
.task-list article {
    display: grid;
    grid-template-columns: 80px 1fr 110px;
    align-items: center;
    gap: 20px;
    padding: 20px;
}
.course-index {
    height: 80px;
    display: grid;
    place-items: center;
    background: linear-gradient(135deg, var(--jade), var(--jade-dark));
    color: #e9ce8c;
    font: 900 32px serif;
}
.task-copy h2 {
    margin: 10px 0 5px;
    font-size: 20px;
}
.task-copy p {
    margin: 0 0 12px;
    color: var(--muted);
    font-size: 12px;
}
.task-copy .el-tag {
    margin-right: 7px;
}
@media (max-width: 600px) {
    .task-list article {
        grid-template-columns: 55px 1fr;
    }
    .course-index {
        height: 55px;
    }
}
</style>
