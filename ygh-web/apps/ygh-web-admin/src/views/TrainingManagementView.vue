<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { apiData } from "@ygh/web-shared";
import {
    createScopedTrainingAssignment,
    createTrainingChapter,
    createTrainingCourse,
    createTrainingGate,
    createTrainingQuestion,
    createLearningPath,
    addLearningPathCourse,
    getTrainingAnalytics,
    listTrainingCourses,
    listTrainingChapters,
    listTrainingGates,
    listLearningPaths,
    listEmployeeLearningProgress,
    listDepartments,
    listPositions,
    listEmployees,
    publishTrainingCourse,
    uploadTrainingDocument,
    type Department,
    type Position,
    type Employee,
    type TrainingChapter,
    type TrainingAnalytics,
    type TrainingCourse,
    type TrainingGate,
    type LearningPath,
    type EmployeeLearningProgress,
} from "@/api/operations";
import { useHttp } from "@/api/client";
import EnterpriseTable from "@/components/EnterpriseTable.vue";
interface TrainingDocument {
    id: string;
    chapterId: string;
    fileName: string;
    mediaType: string;
    sizeBytes: number;
    status: string;
}
const listTrainingDocuments = async (
    chapterId: string,
): Promise<TrainingDocument[]> =>
    apiData(
        await useHttp().get(
            `/api/v1/training/chapters/${chapterId}/documents`,
        ),
    );
const tab = ref("courses");
const loading = ref(true);
const data = ref<TrainingAnalytics>();
const courses = ref<TrainingCourse[]>([]);
const paths = ref<LearningPath[]>([]);
const employeeProgress = ref<EmployeeLearningProgress[]>([]);
const departments = ref<Department[]>([]);
const positions = ref<Position[]>([]);
const employees = ref<Employee[]>([]);
const courseDialog = ref(false);
const assignmentDialog = ref(false);
const contentDialog = ref(false);
const pathDialog = ref(false);
const pathCourseDialog = ref(false);
const selectedPath = ref<LearningPath>();
const saving = ref(false);
const selectedCourse = ref<TrainingCourse>();
const chapters = ref<TrainingChapter[]>([]);
const gates = ref<TrainingGate[]>([]);
const documents = ref<TrainingDocument[]>([]);
const selectedChapterId = ref("");
const selectedGateId = ref("");
const documentFile = ref<File>();
const documentFileInputKey = ref(0);
const courseForm = reactive({
    title: "",
    description: "",
    estimatedMinutes: 60,
    passScore: 80,
});
const assignmentForm = reactive<{
    targetType: "DEPARTMENT" | "POSITION" | "EMPLOYEE";
    targetId: string;
    courseId: string;
    dueAt: string;
}>({ targetType: "EMPLOYEE", targetId: "", courseId: "", dueAt: "" });
const chapterForm = reactive({
    title: "",
    sequenceNo: 1,
    minimumActiveSeconds: 60,
});
const gateForm = reactive({ title: "", passScore: 80, maximumAttempts: 3 });
const questionForm = reactive({
    type: "SINGLE_CHOICE",
    stem: "",
    optionsText: "",
    correctAnswer: "",
    explanation: "",
    score: 10,
});
const pathForm = reactive({ positionCode: "", name: "" });
const pathCourseForm = reactive({
    courseId: "",
    sequenceNo: 1,
    prerequisiteCourseId: "",
});
const learningStatusText = (status: string) =>
    ({ ASSIGNED: "未开始", IN_PROGRESS: "未完成", COMPLETED: "已完成" } as Record<string, string>)[status] || status;
const departmentName = (id?: string) =>
    departments.value.find((item) => item.id === id)?.name || "未分配部门";
const employeeTargetOptions = computed(() =>
    employees.value
        .filter((employee) => employee.status === "ACTIVE")
        .map((employee) => ({
            label: `${employee.employeeNo} · ${employee.userId} · ${departmentName(employee.departmentId)}`,
            value: employee.id,
        })),
);
const targetOptions = computed(() => {
    if (assignmentForm.targetType === "DEPARTMENT") {
        return departments.value
            .filter((department) => department.enabled)
            .map((department) => ({
                label: `${department.code} · ${department.name}`,
                value: department.id,
            }));
    }
    if (assignmentForm.targetType === "POSITION") {
        return positions.value
            .filter((position) => position.enabled)
            .map((position) => ({
                label: `${position.code} · ${position.name}`,
                value: position.id,
            }));
    }
    return employeeTargetOptions.value;
});
const targetPlaceholder = computed(() => {
    if (assignmentForm.targetType === "DEPARTMENT") return "选择部门";
    if (assignmentForm.targetType === "POSITION") return "选择岗位";
    return "选择员工";
});
watch(
    () => assignmentForm.targetType,
    () => {
        assignmentForm.targetId = "";
    },
);
async function load() {
    loading.value = true;
    try {
        [
            data.value,
            courses.value,
            paths.value,
            employeeProgress.value,
            departments.value,
            positions.value,
            employees.value,
        ] = await Promise.all([
            getTrainingAnalytics(),
            listTrainingCourses(),
            listLearningPaths(),
            listEmployeeLearningProgress(),
            listDepartments(),
            listPositions(),
            listEmployees(),
        ]);
    } catch {
        ElMessage.error("培训运营数据加载失败");
    } finally {
        loading.value = false;
    }
}
async function createCourse() {
    saving.value = true;
    try {
        await createTrainingCourse(courseForm);
        courseDialog.value = false;
        ElMessage.success("课程草稿已创建");
        await load();
    } catch {
        ElMessage.error("课程创建失败");
    } finally {
        saving.value = false;
    }
}
async function publish(course: TrainingCourse) {
    await ElMessageBox.confirm(
        "发布后课程将对已授权员工可见，确认发布？",
        "发布课程",
    );
    try {
        await publishTrainingCourse(course);
        ElMessage.success("课程已发布");
        await load();
    } catch {
        ElMessage.error("课程发布失败，版本或状态可能已变化");
    }
}
async function assign() {
    if (!assignmentForm.targetId || !assignmentForm.courseId) {
        ElMessage.warning("请选择分配目标和课程");
        return;
    }
    saving.value = true;
    try {
        await createScopedTrainingAssignment({
            ...assignmentForm,
            dueAt: assignmentForm.dueAt
                ? new Date(assignmentForm.dueAt).toISOString()
                : undefined,
        });
        assignmentDialog.value = false;
        ElMessage.success("学习任务已分配");
        await load();
    } catch {
        ElMessage.error("任务分配失败，请核对目标和课程");
    } finally {
        saving.value = false;
    }
}
async function openContent(course: TrainingCourse) {
    selectedCourse.value = course;
    chapters.value = await listTrainingChapters(course.id);
    resetChapterForm();
    selectedChapterId.value = chapters.value[0]?.id ?? "";
    await loadChapterResources();
    contentDialog.value = true;
}
function nextChapterSequence() {
    return chapters.value.reduce((max, chapter) => Math.max(max, chapter.sequenceNo), 0) + 1;
}
function resetChapterForm() {
    chapterForm.title = "";
    chapterForm.sequenceNo = nextChapterSequence();
    chapterForm.minimumActiveSeconds = 60;
}
async function loadChapterResources() {
    const courseGates = selectedCourse.value
        ? await listTrainingGates(selectedCourse.value.id)
        : [];
    gates.value = selectedChapterId.value
        ? courseGates.filter(
              (gate) => gate.chapterId === selectedChapterId.value,
          )
        : [];
    selectedGateId.value = gates.value[0]?.id ?? "";
    documents.value = selectedChapterId.value
        ? await listTrainingDocuments(selectedChapterId.value)
        : [];
}
function resetGateForm() {
    gateForm.title = "";
    gateForm.passScore = selectedCourse.value?.passScore ?? 80;
    gateForm.maximumAttempts = 3;
}
async function addChapter() {
    if (!selectedCourse.value || !chapterForm.title.trim()) return;
    saving.value = true;
    try {
        await createTrainingChapter({
            courseId: selectedCourse.value.id,
            ...chapterForm,
        });
        chapters.value = await listTrainingChapters(selectedCourse.value.id);
        selectedChapterId.value = chapters.value[chapters.value.length - 1]?.id ?? "";
        await loadChapterResources();
        resetChapterForm();
        ElMessage.success("章节已创建");
    } catch {
        ElMessage.error("章节创建失败，请检查顺序是否重复");
    } finally {
        saving.value = false;
    }
}
async function addGate() {
    if (!selectedChapterId.value || !gateForm.title.trim()) return;
    if (gates.value.length) {
        ElMessage.warning("当前章节已有关卡，请直接维护题库");
        return;
    }
    saving.value = true;
    try {
        await createTrainingGate({
            chapterId: selectedChapterId.value,
            ...gateForm,
        });
        await loadChapterResources();
        resetGateForm();
        ElMessage.success("关卡已创建");
    } catch {
        ElMessage.error("关卡创建失败，请检查当前章节是否已有关卡");
    } finally {
        saving.value = false;
    }
}
async function addQuestion() {
    if (!selectedGateId.value || !questionForm.stem.trim()) return;
    saving.value = true;
    try {
        await createTrainingQuestion({
            gateId: selectedGateId.value,
            ...questionForm,
            options: questionForm.optionsText
                .split("\n")
                .map((item) => item.trim())
                .filter(Boolean),
        });
        questionForm.stem = "";
        questionForm.optionsText = "";
        questionForm.correctAnswer = "";
        questionForm.explanation = "";
        ElMessage.success("题目已创建");
    } catch {
        ElMessage.error("题目创建失败");
    } finally {
        saving.value = false;
    }
}
async function uploadDocument() {
    if (!selectedChapterId.value || !documentFile.value) return;
    saving.value = true;
    try {
        await uploadTrainingDocument(
            selectedChapterId.value,
            documentFile.value,
        );
        documentFile.value = undefined;
        documentFileInputKey.value++;
        await loadChapterResources();
        ElMessage.success("培训文档已上传");
    } catch {
        ElMessage.error("文档上传失败");
    } finally {
        saving.value = false;
    }
}
async function saveSelectedDocument(event: Event) {
    const input = event.target as HTMLInputElement;
    documentFile.value = input.files?.[0];
    if (!documentFile.value) return;
    if (!selectedChapterId.value) {
        ElMessage.warning("请先选择章节");
        return;
    }
    await uploadDocument();
}
async function savePath() {
    saving.value = true;
    try {
        await createLearningPath(pathForm);
        paths.value = await listLearningPaths();
        pathDialog.value = false;
        ElMessage.success("岗位学习路径已创建");
    } catch {
        ElMessage.error("学习路径创建失败，请检查岗位编码");
    } finally {
        saving.value = false;
    }
}
function openPathCourse(path: LearningPath) {
    selectedPath.value = path;
    pathCourseForm.sequenceNo = path.courses.length + 1;
    pathCourseDialog.value = true;
}
async function savePathCourse() {
    if (!selectedPath.value) return;
    saving.value = true;
    try {
        await addLearningPathCourse(selectedPath.value.id, {
            ...pathCourseForm,
            prerequisiteCourseId:
                pathCourseForm.prerequisiteCourseId || undefined,
        });
        paths.value = await listLearningPaths();
        pathCourseDialog.value = false;
        ElMessage.success("课程已加入学习路径");
    } catch {
        ElMessage.error("路径课程保存失败，请检查顺序和前置课程");
    } finally {
        saving.value = false;
    }
}
onMounted(load);
</script>
<template>
    <div v-loading="loading">
        <div class="page-head">
            <div>
                <h1>培训运营</h1>
                <p>按部门、岗位和员工维护课程、任务与统计。</p>
            </div>
            <div>
                <el-button @click="pathDialog = true">新建学习路径</el-button>
                <el-button @click="assignmentDialog = true">分配任务</el-button
                ><el-button type="primary" @click="courseDialog = true"
                    >新建课程</el-button
                >
            </div>
        </div>
        <div class="metric-grid">
            <div class="metric panel">
                <span>已分配</span><b>{{ data?.assigned || 0 }}</b>
            </div>
            <div class="metric panel">
                <span>已完成</span><b>{{ data?.completed || 0 }}</b>
            </div>
            <div class="metric panel">
                <span>逾期任务</span><b>{{ data?.overdue || 0 }}</b>
            </div>
            <div class="metric panel">
                <span>平均成绩</span><b>{{ data?.averageScore || 0 }}</b>
            </div>
        </div>
        <el-tabs v-model="tab" class="panel training"
            ><el-tab-pane label="课程管理" name="courses"
                ><EnterpriseTable
                    :items="courses"
                    :search-fields="['title', 'status']"
                    status-field="status"
                    search-placeholder="检索课程名称或状态"
                    v-slot="{ rows, emptyText }"
                ><el-table :data="rows" :empty-text="emptyText"
                    ><el-table-column
                        prop="title"
                        label="课程名称"
                        min-width="240"
                    /><el-table-column
                        prop="estimatedMinutes"
                        label="预计分钟"
                    /><el-table-column
                        prop="passScore"
                        label="通过分数"
                    /><el-table-column
                        prop="version"
                        label="版本"
                    /><el-table-column
                        prop="status"
                        label="状态"
                    /><el-table-column label="操作"
                        ><template #default="scope"
                            ><el-button link @click="openContent(scope.row)"
                                >内容编排</el-button
                            >
                            ><el-button
                                v-if="scope.row.status === 'DRAFT'"
                                link
                                type="primary"
                                @click="publish(scope.row)"
                                >发布</el-button
                            ></template
                        ></el-table-column
                    ></el-table></EnterpriseTable
                ></el-tab-pane
            ><el-tab-pane label="岗位学习路径" name="paths"
                ><EnterpriseTable
                    :items="paths"
                    :search-fields="['positionCode', 'name']"
                    search-placeholder="检索岗位编码或路径名称"
                    v-slot="{ rows, emptyText }"
                ><el-table :data="rows" :empty-text="emptyText"
                    ><el-table-column
                        prop="positionCode"
                        label="岗位编码"
                    /><el-table-column
                        prop="name"
                        label="路径名称"
                    /><el-table-column label="课程数"
                        ><template #default="scope">{{
                            scope.row.courses.length
                        }}</template></el-table-column
                    ><el-table-column
                        prop="version"
                        label="版本"
                    /><el-table-column label="操作"
                        ><template #default="scope"
                            ><el-button
                                link
                                type="primary"
                                @click="openPathCourse(scope.row)"
                                >添加课程</el-button
                            ></template
                        ></el-table-column
                    ></el-table></EnterpriseTable
                ></el-tab-pane
            ><el-tab-pane label="员工进度" name="employee-progress"
                ><EnterpriseTable
                    :items="employeeProgress"
                    :search-fields="['userId', 'courseTitle', 'status']"
                    search-placeholder="检索员工 ID、课程或状态"
                    v-slot="{ rows, emptyText }"
                ><el-table :data="rows" :empty-text="emptyText"
                    ><el-table-column prop="userId" label="员工用户 ID" min-width="150" />
                    <el-table-column prop="courseTitle" label="课程" min-width="220" />
                    <el-table-column label="文档任务点" min-width="130"><template #default="scope">{{ scope.row.completedDocuments }}/{{ scope.row.totalDocuments }}</template></el-table-column>
                    <el-table-column label="学习进度" min-width="180"><template #default="scope"><el-progress :percentage="Number(scope.row.progressPercent)" /></template></el-table-column>
                    <el-table-column label="状态"><template #default="scope"><el-tag :type="scope.row.status === 'COMPLETED' ? 'success' : scope.row.status === 'IN_PROGRESS' ? 'warning' : 'info'">{{ learningStatusText(scope.row.status) }}</el-tag></template></el-table-column>
                    <el-table-column prop="bestScore" label="最好成绩" />
                </el-table></EnterpriseTable
                ></el-tab-pane
            ><el-tab-pane label="学习分析" name="analytics"
                ><EnterpriseTable
                    :items="data?.weakKnowledge || []"
                    :search-fields="['knowledgeCode']"
                    search-placeholder="检索知识点编码"
                    v-slot="{ rows, emptyText }"
                ><el-table :data="rows" :empty-text="emptyText"
                    ><el-table-column
                        prop="knowledgeCode"
                        label="知识点编码" /><el-table-column
                        prop="wrongCount"
                        label="错误次数" /></el-table></EnterpriseTable></el-tab-pane></el-tabs
        ><el-dialog v-model="courseDialog" title="新建培训课程" width="540"
            ><el-form label-position="top"
                ><el-form-item label="课程名称"
                    ><el-input
                        v-model="courseForm.title"
                        maxlength="200" /></el-form-item
                ><el-form-item label="课程说明"
                    ><el-input
                        v-model="courseForm.description"
                        type="textarea"
                        maxlength="5000" /></el-form-item
                ><el-form-item label="预计分钟"
                    ><el-input-number
                        v-model="courseForm.estimatedMinutes"
                        :min="0" /></el-form-item
                ><el-form-item label="通过分数"
                    ><el-input-number
                        v-model="courseForm.passScore"
                        :min="0"
                        :max="100" /></el-form-item></el-form
            ><template #footer
                ><el-button @click="courseDialog = false">取消</el-button
                ><el-button
                    type="primary"
                    :loading="saving"
                    @click="createCourse"
                    >创建草稿</el-button
                ></template
            ></el-dialog
        ><el-dialog v-model="pathDialog" title="新建岗位学习路径" width="520"
            ><el-form label-position="top"
                ><el-form-item label="岗位编码"
                    ><el-input v-model="pathForm.positionCode" /></el-form-item
                ><el-form-item label="路径名称"
                    ><el-input
                        v-model="pathForm.name" /></el-form-item></el-form
            ><template #footer
                ><el-button @click="pathDialog = false">取消</el-button
                ><el-button type="primary" :loading="saving" @click="savePath"
                    >保存</el-button
                ></template
            ></el-dialog
        ><el-dialog
            v-model="pathCourseDialog"
            :title="`${selectedPath?.name || ''} · 添加课程`"
            width="560"
            ><el-form label-position="top"
                ><el-form-item label="课程"
                    ><el-select
                        v-model="pathCourseForm.courseId"
                        style="width: 100%"
                        ><el-option
                            v-for="course in courses.filter(
                                (x) => x.status === 'PUBLISHED',
                            )"
                            :key="course.id"
                            :label="course.title"
                            :value="course.id" /></el-select></el-form-item
                ><el-form-item label="顺序"
                    ><el-input-number
                        v-model="pathCourseForm.sequenceNo"
                        :min="1" /></el-form-item
                ><el-form-item label="前置课程"
                    ><el-select
                        v-model="pathCourseForm.prerequisiteCourseId"
                        clearable
                        style="width: 100%"
                        ><el-option
                            v-for="course in courses"
                            :key="course.id"
                            :label="course.title"
                            :value="
                                course.id
                            " /></el-select></el-form-item></el-form
            ><template #footer
                ><el-button @click="pathCourseDialog = false">取消</el-button
                ><el-button
                    type="primary"
                    :loading="saving"
                    @click="savePathCourse"
                    >保存</el-button
                ></template
            ></el-dialog
        ><el-dialog
            v-model="assignmentDialog"
            title="按组织范围分配课程"
            width="540"
            ><el-form label-position="top"
                ><el-form-item label="目标类型"
                    ><el-radio-group v-model="assignmentForm.targetType"
                        ><el-radio-button value="DEPARTMENT"
                            >部门</el-radio-button
                        ><el-radio-button value="POSITION">岗位</el-radio-button
                        ><el-radio-button value="EMPLOYEE"
                            >员工</el-radio-button
                        ></el-radio-group
                    ></el-form-item
                ><el-form-item
                    :label="targetPlaceholder"
                    ><el-select
                        v-model="assignmentForm.targetId"
                        filterable
                        clearable
                        :placeholder="targetPlaceholder"
                        style="width: 100%"
                    >
                        <el-option
                            v-for="option in targetOptions"
                            :key="option.value"
                            :label="option.label"
                            :value="option.value"
                        />
                    </el-select></el-form-item
                ><el-form-item label="课程"
                    ><el-select
                        v-model="assignmentForm.courseId"
                        style="width: 100%"
                        ><el-option
                            v-for="course in courses.filter(
                                (x) => x.status === 'PUBLISHED',
                            )"
                            :key="course.id"
                            :label="course.title"
                            :value="course.id" /></el-select></el-form-item
                ><el-form-item label="截止时间"
                    ><el-date-picker
                        v-model="assignmentForm.dueAt"
                        type="datetime"
                        value-format="YYYY-MM-DDTHH:mm:ss"
                        style="width: 100%" /></el-form-item></el-form
            ><template #footer
                ><el-button @click="assignmentDialog = false">取消</el-button
                ><el-button type="primary" :loading="saving" @click="assign"
                    >确认分配</el-button
                ></template
            ></el-dialog
        ><el-dialog
            v-model="contentDialog"
            :title="`${selectedCourse?.title || ''} · 内容编排`"
            width="880"
        >
            <el-tabs>
                <el-tab-pane label="章节">
                    <el-form label-position="top" class="editor-grid">
                        <el-form-item label="章节标题">
                            <el-input v-model="chapterForm.title" />
                        </el-form-item>
                        <el-form-item label="顺序">
                            <el-input-number
                                v-model="chapterForm.sequenceNo"
                                :min="1"
                            />
                        </el-form-item>
                        <el-form-item label="最少有效学习秒数">
                            <el-input-number
                                v-model="chapterForm.minimumActiveSeconds"
                                :min="0"
                            />
                        </el-form-item>
                    </el-form>
                    <el-button
                        type="primary"
                        :loading="saving"
                        @click="addChapter"
                        >新增章节</el-button
                    >
                </el-tab-pane>
                <el-tab-pane label="文档与关卡">
                    <el-form label-position="top">
                        <el-form-item label="章节">
                            <el-select
                                v-model="selectedChapterId"
                                style="width: 100%"
                                @change="
                                    () => {
                                        resetGateForm();
                                        loadChapterResources();
                                    }
                                "
                            >
                                <el-option
                                    v-for="chapter in chapters"
                                    :key="chapter.id"
                                    :label="`${chapter.sequenceNo}. ${chapter.title}`"
                                    :value="chapter.id"
                                />
                            </el-select>
                        </el-form-item>
                        <el-form-item label="培训文档">
                            <input
                                :key="documentFileInputKey"
                                type="file"
                                accept=".pdf,.docx,.txt,.md"
                                :disabled="saving"
                                @change="saveSelectedDocument"
                            />
                            <el-button
                                :disabled="!documentFile"
                                :loading="saving"
                                @click="uploadDocument"
                                >保存文档</el-button
                            >
                        </el-form-item>
                    </el-form>
                    <div class="document-list">
                        <div class="document-list-head">
                            <b>当前章节文档</b>
                            <span>共 {{ documents.length }} 个</span>
                        </div>
                        <el-empty
                            v-if="!documents.length"
                            description="当前章节暂无培训文档"
                        />
                        <el-table
                            v-else
                            :data="documents"
                            size="small"
                            border
                        >
                            <el-table-column
                                prop="fileName"
                                label="文件名"
                                min-width="220"
                            />
                            <el-table-column
                                prop="mediaType"
                                label="类型"
                                min-width="160"
                            />
                            <el-table-column label="大小">
                                <template #default="scope">
                                    {{
                                        (scope.row.sizeBytes / 1024).toFixed(1)
                                    }}
                                    KB
                                </template>
                            </el-table-column>
                            <el-table-column prop="status" label="状态" />
                        </el-table>
                    </div>
                    <el-divider>新增关卡</el-divider>
                    <el-form label-position="top" class="editor-grid">
                        <el-form-item label="关卡标题">
                            <el-input v-model="gateForm.title" />
                        </el-form-item>
                        <el-form-item label="通过分数">
                            <el-input-number
                                v-model="gateForm.passScore"
                                :min="0"
                                :max="100"
                            />
                        </el-form-item>
                        <el-form-item label="最多尝试次数">
                            <el-input-number
                                v-model="gateForm.maximumAttempts"
                                :min="1"
                            />
                        </el-form-item>
                    </el-form>
                    <el-button
                        v-if="!gates.length"
                        type="primary"
                        :loading="saving"
                        @click="addGate"
                        >新增关卡</el-button
                    >
                    <el-alert
                        v-else
                        type="info"
                        show-icon
                        :closable="false"
                        title="当前章节已有关卡，可在题库页继续新增题目"
                        class="gate-hint"
                    />
                </el-tab-pane>
                <el-tab-pane label="题库">
                    <el-form label-position="top">
                        <el-form-item label="关卡">
                            <el-select
                                v-model="selectedGateId"
                                style="width: 100%"
                            >
                                <el-option
                                    v-for="gate in gates"
                                    :key="gate.id"
                                    :label="gate.title"
                                    :value="gate.id"
                                />
                            </el-select>
                        </el-form-item>
                        <el-form-item label="题干">
                            <el-input
                                v-model="questionForm.stem"
                                type="textarea"
                            />
                        </el-form-item>
                        <el-form-item label="选项（每行一个）">
                            <el-input
                                v-model="questionForm.optionsText"
                                type="textarea"
                                :rows="4"
                            />
                        </el-form-item>
                        <el-form-item label="正确答案">
                            <el-input v-model="questionForm.correctAnswer" />
                        </el-form-item>
                        <el-form-item label="解析">
                            <el-input
                                v-model="questionForm.explanation"
                                type="textarea"
                            />
                        </el-form-item>
                        <el-form-item label="分值">
                            <el-input-number
                                v-model="questionForm.score"
                                :min="1"
                            />
                        </el-form-item>
                    </el-form>
                    <el-button
                        type="primary"
                        :loading="saving"
                        @click="addQuestion"
                        >新增题目</el-button
                    >
                </el-tab-pane>
            </el-tabs>
        </el-dialog>
    </div>
</template>
<style scoped>
.training {
    margin-top: 14px;
    padding: 18px;
}
.editor-grid {
    display: grid;
    grid-template-columns: 2fr 1fr 1fr;
    gap: 0 16px;
}
.gate-hint {
    margin-top: 8px;
}
.document-list {
    margin-top: 12px;
}
.document-list-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
    color: var(--muted);
}
.document-list-head b {
    color: var(--ink);
}
</style>
