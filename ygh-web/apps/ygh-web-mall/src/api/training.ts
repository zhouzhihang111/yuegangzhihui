import { apiData } from "@ygh/web-shared";
import { useHttp } from "./client";
export interface Course {
    id: string;
    title: string;
    description: string;
    status: string;
    passScore: number;
    estimatedMinutes: number;
    version: number;
}
export interface Chapter {
    id: string;
    courseId: string;
    title: string;
    sequenceNo: number;
    minimumActiveSeconds: number;
    version: number;
}
export interface Gate {
    id: string;
    chapterId: string;
    title: string;
    passScore: number;
    maximumAttempts?: number;
}
export interface TrainingDocument {
    id: string;
    chapterId: string;
    fileName: string;
    mediaType: string;
    sizeBytes: number;
    status: string;
}
export interface DocumentProgress {
    assignmentId: string;
    documentId: string;
    chapterId: string;
    fileName: string;
    status: "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED";
    openedAt?: string;
    completedAt?: string;
    version: number;
}
export interface Assignment {
    assignmentId: string;
    userId: string;
    courseId: string;
    status: string;
    dueAt?: string;
}
export interface Progress {
    assignmentId: string;
    courseId: string;
    userId: string;
    progressPercent: string;
    status: string;
    currentChapterId?: string;
    bestScore?: number;
    version: number;
}
export interface Question {
    id: string;
    gateId: string;
    type: string;
    stem: string;
    options: string[];
    explanation?: string;
    score: number;
}
export interface QuizAttempt {
    attemptId: string;
    assignmentId: string;
    gateId: string;
    score: number;
    passed: boolean;
}
export interface ChapterProgress {
    chapterId: string;
    activeSeconds: number;
    lastPosition?: string;
    completed: boolean;
    completedAt?: string;
    version: number;
}
export interface QuizAttemptDetail {
    attemptId: string;
    assignmentId: string;
    gateId: string;
    score: number;
    passed: boolean;
    answers: Record<string, string>;
    submittedAt: string;
}
export const listCourses = async (): Promise<Course[]> =>
    apiData(await useHttp().get("/api/v1/training/courses"));
export const listChapters = async (courseId: string): Promise<Chapter[]> =>
    apiData(
        await useHttp().get(`/api/v1/training/courses/${courseId}/chapters`),
    );
export const listGates = async (courseId: string): Promise<Gate[]> =>
    apiData(await useHttp().get(`/api/v1/training/courses/${courseId}/gates`));
export const listChapterDocuments = async (
    chapterId: string,
): Promise<TrainingDocument[]> =>
    apiData(
        await useHttp().get(`/api/v1/training/chapters/${chapterId}/documents`),
    );
export const getTrainingDocumentContent = async (
    documentId: string,
    assignmentId: string,
): Promise<Blob> =>
    (
        await useHttp().get<Blob>(
            `/api/v1/training/documents/${documentId}/content`,
            { params: { inline: true, assignmentId }, responseType: "blob" },
        )
    ).data;
export const listDocumentProgress = async (
    assignmentId: string,
    chapterId: string,
): Promise<DocumentProgress[]> =>
    apiData(await useHttp().get(`/api/v1/training/learning/assignments/${assignmentId}/documents`, {
        params: { chapterId },
    }));
export const completeDocument = async (
    assignmentId: string,
    documentId: string,
): Promise<DocumentProgress> =>
    apiData(await useHttp().post(`/api/v1/training/learning/assignments/${assignmentId}/documents/${documentId}/complete`));
export const listMyAssignments = async (): Promise<Assignment[]> =>
    apiData(await useHttp().get("/api/v1/training/assignments/mine"));
export const getProgress = async (assignmentId: string): Promise<Progress> =>
    apiData(await useHttp().get(`/api/v1/training/progress/${assignmentId}`));
export const listChapterProgress = async (
    assignmentId: string,
): Promise<ChapterProgress[]> =>
    apiData(
        await useHttp().get(
            `/api/v1/training/learning/assignments/${assignmentId}/chapters`,
        ),
    );
export const listQuestions = async (gateId: string): Promise<Question[]> =>
    apiData(await useHttp().get(`/api/v1/training/gates/${gateId}/questions`));
export const listAttempts = async (
    assignmentId: string,
): Promise<QuizAttemptDetail[]> =>
    apiData(
        await useHttp().get(
            `/api/v1/training/learning/assignments/${assignmentId}/attempts`,
        ),
    );
export const heartbeat = async (
    assignmentId: string,
    chapterId: string,
    activeSeconds: number,
): Promise<Progress> =>
    apiData(
        await useHttp().post("/api/v1/training/progress/heartbeat", {
            assignmentId,
            chapterId,
            activeSeconds,
            nonce: crypto.randomUUID(),
        }),
    );
export async function recordPosition(
    assignmentId: string,
    chapterId: string,
    position: string,
): Promise<void> {
    await useHttp().put("/api/v1/training/learning/position", {
        assignmentId,
        chapterId,
        position,
        nonce: crypto.randomUUID(),
    });
}
export const submitQuiz = async (
    assignmentId: string,
    gateId: string,
    answers: Record<string, string>,
): Promise<QuizAttempt> =>
    apiData(
        await useHttp().post("/api/v1/training/quizzes/attempts", {
            assignmentId,
            gateId,
            answers,
            requestId: crypto.randomUUID(),
        }),
    );
