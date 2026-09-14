<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import PageHeader from "@/components/PageHeader.vue";
import { listCourses, type Course } from "@/api/training";
const keyword = ref("");
const loading = ref(true);
const courses = ref<Course[]>([]);
const visible = computed(() =>
    courses.value.filter((x) =>
        (x.title + x.description).includes(keyword.value),
    ),
);
onMounted(async () => {
    try {
        courses.value = await listCourses();
    } catch {
        ElMessage.error("课程目录加载失败");
    } finally {
        loading.value = false;
    }
});
</script>
<template>
    <PageHeader
        title="课程中心"
        description="课程按岗位学习路径组织，章节完成与关卡通过后逐步解锁。"
    />
    <div class="catalog-filter paper-card">
        <b>已发布课程</b
        ><el-input
            v-model="keyword"
            placeholder="搜索课程"
            clearable
            style="width: 240px"
        />
    </div>
    <div v-loading="loading" class="course-grid">
        <article v-for="course in visible" :key="course.id" class="paper-card">
            <div class="cover">
                <span>{{ course.title.slice(0, 2) }}</span
                ><el-tag>{{ course.passScore }} 分通过</el-tag>
            </div>
            <div class="copy">
                <small>{{ course.status }}</small>
                <h2 class="serif">{{ course.title }}</h2>
                <p>{{ course.description }}</p>
                <div>
                    <span>{{ course.estimatedMinutes }} 分钟</span
                    ><span>版本 {{ course.version }}</span>
                </div>
                <el-button
                    type="primary"
                    plain
                    @click="
                        $router.push(`/workspace/training/courses/${course.id}`)
                    "
                    >查看课程</el-button
                >
            </div>
        </article>
        <el-empty v-if="!loading && !visible.length" description="暂无课程" />
    </div>
</template>
<style scoped>
.catalog-filter {
    display: flex;
    justify-content: space-between;
    padding: 16px;
    margin-bottom: 20px;
}
.course-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 17px;
    min-height: 200px;
}
.course-grid article {
    overflow: hidden;
}
.cover {
    height: 145px;
    position: relative;
    display: grid;
    place-items: center;
    background: linear-gradient(145deg, #d9e7df, #e9dfca);
}
.cover > span {
    font: 900 38px serif;
    color: var(--jade);
}
.cover .el-tag {
    position: absolute;
    right: 12px;
    top: 12px;
}
.copy {
    padding: 18px;
}
.copy > small {
    color: var(--cinnabar);
}
.copy h2 {
    height: 54px;
    margin: 7px 0;
    font-size: 20px;
}
.copy p {
    height: 64px;
    color: var(--muted);
    font-size: 13px;
    line-height: 1.7;
}
.copy > div {
    display: flex;
    justify-content: space-between;
    padding-top: 12px;
    border-top: 1px solid var(--line);
    color: var(--muted);
    font-size: 11px;
}
.copy .el-button {
    width: 100%;
    margin-top: 15px;
}
@media (max-width: 900px) {
    .course-grid {
        grid-template-columns: repeat(2, 1fr);
    }
}
@media (max-width: 600px) {
    .course-grid {
        grid-template-columns: 1fr;
    }
}
</style>
