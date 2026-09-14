<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Lock, VideoPlay } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import PageHeader from "@/components/PageHeader.vue";
import {
    getProgress,
    listChapterProgress,
    listChapters,
    listCourses,
    listGates,
    listAttempts,
    type Chapter,
    type ChapterProgress,
    type Course,
    type Gate,
    type Progress,
    type QuizAttemptDetail,
} from "@/api/training";
const route = useRoute();
const router = useRouter();
const course = ref<Course>();
const chapters = ref<Chapter[]>([]);
const gates = ref<Gate[]>([]);
const records = ref<ChapterProgress[]>([]);
const attempts = ref<QuizAttemptDetail[]>([]);
const progress = ref<Progress>();
const loading = ref(true);
const assignmentId = computed(() => String(route.query.assignment || ""));
const recordMap = computed(() =>
    Object.fromEntries(records.value.map((x) => [x.chapterId, x])),
);
const gateMap = computed(() =>
    Object.fromEntries(gates.value.map((x) => [x.chapterId, x])),
);
const passedGateIds = computed(
    () => new Set(attempts.value.filter((x) => x.passed).map((x) => x.gateId)),
);
function gatePassed(chapter: Chapter) {
    const gate = gateMap.value[chapter.id];
    return !gate || passedGateIds.value.has(gate.id);
}
function completedWithGate(chapter: Chapter) {
    return Boolean(recordMap.value[chapter.id]?.completed) && gatePassed(chapter);
}
function unlocked(chapter: Chapter) {
    if (!assignmentId.value) return false;
    if (chapter.sequenceNo === 1) return true;
    return chapters.value
        .filter((x) => x.sequenceNo < chapter.sequenceNo)
        .every((x) => completedWithGate(x));
}
function openChapter(chapter: Chapter) {
    const gate = gateMap.value[chapter.id];
    if (recordMap.value[chapter.id]?.completed && gate && !gatePassed(chapter)) {
        void router.push({
            path: `/workspace/training/quiz/${gate.id}`,
            query: { assignment: assignmentId.value },
        });
        return;
    }
    void router.push({
        path: `/workspace/training/chapters/${chapter.id}`,
        query: {
            assignment: assignmentId.value,
            gate: gate?.id,
        },
    });
}
onMounted(async () => {
    const id = String(route.params.id);
    try {
        const courses = await listCourses();
        course.value = courses.find((x) => x.id === id);
        if (!course.value) ElMessage.warning("当前账号暂无该课程访问权限");
    } catch {
        ElMessage.error("课程详情加载失败");
    }
    try {
        chapters.value = await listChapters(id);
    } catch {
        ElMessage.error("课程章节加载失败");
    }
    try {
        gates.value = await listGates(id);
    } catch {
        ElMessage.warning("课程关卡加载失败，章节仍可查看");
    }
    if (assignmentId.value) {
        try {
            [progress.value, records.value, attempts.value] = await Promise.all([
                getProgress(assignmentId.value),
                listChapterProgress(assignmentId.value),
                listAttempts(assignmentId.value),
            ]);
        } catch {
            ElMessage.warning("学习进度加载失败，已显示课程基础内容");
        }
    }
    loading.value = false;
});
</script>
<template>
    <div v-loading="loading">
        <PageHeader
            eyebrow="POSITION LEARNING PATH"
            :title="course?.title || '课程详情'"
            :description="`${course?.status || ''} · 约 ${course?.estimatedMinutes || 0} 分钟`"
        />
        <section class="course-banner">
            <div>
                <el-tag effect="dark">{{
                    progress?.status || "课程浏览"
                }}</el-tag>
                <h2 class="serif">{{ course?.description }}</h2>
                <p>
                    本课程包含文档阅读、有效学习时长与章节闯关。不能通过刷新页面或直接提交状态完成任务。
                </p>
                <el-progress
                    :percentage="Number(progress?.progressPercent || 0)"
                    color="#e6bd68"
                />
            </div>
            <div class="seal">岗位<br />必修</div>
        </section>
        <div class="course-layout">
            <main class="paper-card chapters">
                <h3 class="serif">课程章节</h3>
                <article
                    v-for="ch in chapters"
                    :key="ch.id"
                    :class="{ locked: !unlocked(ch) }"
                >
                    <span>{{ String(ch.sequenceNo).padStart(2, "0") }}</span>
                    <div>
                        <b>{{ ch.title }}</b
                        ><small
                            >最少有效学习
                            {{ Math.ceil(ch.minimumActiveSeconds / 60) }} 分钟 ·
                            {{
                                recordMap[ch.id]?.completed
                                    ? gatePassed(ch)
                                        ? "已完成"
                                        : "待闯关"
                                    : unlocked(ch)
                                      ? "可学习"
                                      : "完成前置章节后解锁"
                            }}<template v-if="gateMap[ch.id]">
                                · 闯关
                                {{ gateMap[ch.id]?.passScore }} 分通过</template
                            ></small
                        >
                    </div>
                    <el-icon v-if="!unlocked(ch)"><Lock /></el-icon
                    ><el-button
                        v-else
                        :type="
                            progress?.currentChapterId === ch.id
                                ? 'primary'
                                : 'default'
                        "
                        size="small"
                        :icon="VideoPlay"
                        @click="openChapter(ch)"
                        >{{
                            recordMap[ch.id]?.completed && !gatePassed(ch)
                                ? "闯关"
                                : recordMap[ch.id]?.completed
                                  ? "复习"
                                  : "学习"
                        }}</el-button
                    >
                </article>
            </main>
            <aside>
                <section class="paper-card">
                    <h3 class="serif">学习规则</h3>
                    <ul>
                        <li>阅读心跳每次最多累计 300 秒</li>
                        <li>重复 nonce 不重复计时</li>
                        <li>章节有效时长达标后可闯关</li>
                        <li>通过关卡才解锁下一章</li>
                    </ul>
                </section>
                <section class="paper-card">
                    <h3 class="serif">课程成绩</h3>
                    <p>
                        <span>当前进度</span
                        ><b>{{ progress?.progressPercent || 0 }}%</b>
                    </p>
                    <p>
                        <span>最好成绩</span
                        ><b>{{ progress?.bestScore ?? "--" }}</b>
                    </p>
                    <p>
                        <span>课程版本</span><b>{{ course?.version }}</b>
                    </p>
                </section>
            </aside>
        </div>
    </div>
</template>
<style scoped>
.course-banner {
    display: grid;
    grid-template-columns: 1fr 170px;
    align-items: center;
    padding: 35px 42px;
    background: linear-gradient(120deg, #0a4843, #0e6860);
    color: #fff;
}
.course-banner h2 {
    max-width: 700px;
    font-size: 29px;
}
.course-banner p {
    color: #b9ceca;
}
.seal {
    justify-self: end;
    width: 120px;
    height: 120px;
    display: grid;
    place-items: center;
    border: 2px solid #e6bd68;
    border-radius: 50%;
    color: #e6bd68;
    text-align: center;
    font: 700 24px serif;
}
.course-layout {
    display: grid;
    grid-template-columns: 1fr 280px;
    gap: 18px;
    margin-top: 20px;
}
.chapters {
    padding: 24px;
}
.chapters article {
    display: grid;
    grid-template-columns: 45px 1fr 80px;
    align-items: center;
    gap: 15px;
    padding: 17px 10px;
    border-top: 1px solid var(--line);
}
.chapters article > span {
    color: var(--cinnabar);
    font: 700 19px serif;
}
.chapters b,
.chapters small {
    display: block;
}
.chapters small {
    margin-top: 5px;
    color: var(--muted);
}
.chapters article.locked {
    opacity: 0.55;
}
.course-layout aside section {
    padding: 22px;
    margin-bottom: 16px;
}
.course-layout aside li {
    margin: 9px 0;
    color: var(--muted);
    font-size: 12px;
}
.course-layout aside p {
    display: flex;
    justify-content: space-between;
}
@media (max-width: 750px) {
    .course-layout {
        grid-template-columns: 1fr;
    }
    .seal {
        display: none;
    }
    .course-banner {
        grid-template-columns: 1fr;
    }
}
</style>
