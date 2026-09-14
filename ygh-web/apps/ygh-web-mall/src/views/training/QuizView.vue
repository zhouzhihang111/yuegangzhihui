<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { Clock } from "@element-plus/icons-vue";
import {
    listQuestions,
    submitQuiz,
    type Question,
    type QuizAttempt,
} from "@/api/training";
const route = useRoute();
const gateId = String(route.params.gateId);
const assignmentId = String(route.query.assignment || "");
const loading = ref(true);
const submitting = ref(false);
const result = ref<QuizAttempt>();
const answers = reactive<Record<string, string>>({});
const questions = ref<Question[]>([]);
const answered = computed(() => Object.keys(answers).length);
async function submit() {
    if (answered.value < questions.value.length)
        return ElMessage.warning("请完成全部题目");
    submitting.value = true;
    try {
        result.value = await submitQuiz(assignmentId, gateId, answers);
    } catch {
        ElMessage.error("测验提交失败，可能未达到阅读时长或已超过重试次数");
    } finally {
        submitting.value = false;
    }
}
onMounted(async () => {
    if (!assignmentId) {
        ElMessage.error("缺少学习任务参数");
        loading.value = false;
        return;
    }
    try {
        questions.value = await listQuestions(gateId);
    } catch {
        ElMessage.error("关卡题目加载失败");
    } finally {
        loading.value = false;
    }
});
</script>
<template>
    <div class="quiz-page">
        <header>
            <div><span>岗位闯关</span><b class="serif">服务端评分测验</b></div>
            <div>
                <el-icon><Clock /></el-icon>答案提交后不可修改
            </div>
        </header>
        <main v-loading="loading">
            <section class="quiz-card paper-card">
                <div class="quiz-meta">
                    <span
                        >共 {{ questions.length }} 题 ·
                        成绩与解锁由服务端判定</span
                    ><el-progress
                        :percentage="
                            questions.length
                                ? (answered / questions.length) * 100
                                : 0
                        "
                        :show-text="false"
                    />
                </div>
                <article v-for="(q, i) in questions" :key="q.id">
                    <h2>
                        <span>{{ i + 1 }}</span
                        >{{ q.stem }}
                    </h2>
                    <el-radio-group v-model="answers[q.id]" class="options"
                        ><el-radio
                            v-for="(option, index) in q.options"
                            :key="option"
                            :value="String.fromCharCode(65 + index)"
                            border
                            ><i>{{ String.fromCharCode(65 + index) }}</i
                            >{{ option }}</el-radio
                        ></el-radio-group
                    >
                </article>
                <el-empty
                    v-if="!loading && !questions.length"
                    description="本关暂无题目"
                /><el-button
                    type="primary"
                    size="large"
                    :loading="submitting"
                    :disabled="!questions.length"
                    @click="submit"
                    >提交答案</el-button
                >
            </section>
        </main>
        <el-dialog
            :model-value="Boolean(result)"
            title="闯关结果"
            width="460"
            :show-close="false"
            ><div class="result">
                <div class="score serif">
                    {{ result?.score }}<small>分</small>
                </div>
                <h2 class="serif">
                    {{ result?.passed ? "恭喜通过本章闯关" : "本次未通过" }}
                </h2>
                <p>
                    {{
                        result?.passed
                            ? "下一章节将由服务端解锁。"
                            : "请根据课程规则复习后再次尝试。"
                    }}
                </p>
                <el-progress
                    :percentage="result?.score || 0"
                    :status="result?.passed ? 'success' : 'exception'"
                />
            </div>
            <template #footer
                ><el-button
                    @click="$router.push('/workspace/training/progress')"
                    >查看学习档案</el-button
                ><el-button type="primary" @click="$router.back()"
                    >返回课程</el-button
                ></template
            ></el-dialog
        >
    </div>
</template>
<style scoped>
.quiz-page {
    min-height: 100vh;
    background: #edf0eb;
}
.quiz-page > header {
    height: 70px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 max(24px, calc((100% - 900px) / 2));
    background: #0a403c;
    color: #fff;
}
.quiz-page > header span,
.quiz-page > header b {
    display: block;
}
.quiz-page > header span {
    color: #d3b86f;
    font-size: 10px;
}
.quiz-page > header > div:last-child {
    display: flex;
    gap: 7px;
}
.quiz-page main {
    padding: 30px 20px;
}
.quiz-card {
    max-width: 900px;
    margin: 0 auto;
    padding: 30px 40px;
}
.quiz-meta {
    display: grid;
    grid-template-columns: 1fr 260px;
    align-items: center;
    padding-bottom: 20px;
    border-bottom: 1px solid var(--line);
}
.quiz-card article {
    padding: 24px 0;
    border-bottom: 1px solid var(--line);
}
.quiz-card h2 {
    display: flex;
    gap: 10px;
    font-size: 17px;
}
.quiz-card h2 span {
    display: grid;
    place-items: center;
    flex: 0 0 28px;
    height: 28px;
    background: var(--jade);
    color: white;
}
.options {
    width: 100%;
    display: grid;
    gap: 9px;
    padding-left: 38px;
}
.options .el-radio {
    height: auto;
    margin: 0;
    padding: 12px;
}
.options i {
    display: inline-grid;
    place-items: center;
    width: 23px;
    height: 23px;
    margin-right: 10px;
    background: #edf1ed;
    font-style: normal;
}
.quiz-card > .el-button {
    display: block;
    margin: 25px auto 0;
    min-width: 160px;
}
.result {
    text-align: center;
}
.score {
    color: #2b8d68;
    font-size: 68px;
}
.score small {
    font-size: 18px;
}
@media (max-width: 600px) {
    .quiz-card {
        padding: 22px 16px;
    }
    .quiz-meta {
        grid-template-columns: 1fr;
    }
    .options {
        padding-left: 0;
    }
}
</style>
