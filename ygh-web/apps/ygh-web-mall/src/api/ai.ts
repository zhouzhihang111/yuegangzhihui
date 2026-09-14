import { apiData, streamSseEvents, type Citation } from "@ygh/web-shared";
import { useHttp } from "./client";
export interface Conversation {
    id: string;
    title: string;
    status: string;
    updatedAt: string;
    time?: string;
}
export interface AiMessage {
    id: string;
    role: "USER" | "ASSISTANT";
    content: string;
    refused: boolean;
    createdAt: string;
    citations: Array<BackendCitation & { documentVersion: number; sourceUpdatedAt?: string }>;
}
export interface StreamResult {
    conversationId: string;
    messageId: string;
    citations: Citation[];
    refused: boolean;
}
interface BackendCitation {
    sourceType?: "KNOWLEDGE" | "WEB";
    sourceId: string;
    documentId?: string;
    title: string;
    excerpt: string;
    url?: string;
    documentVersion?: number;
    sourceUpdatedAt?: string;
}
export async function listConversations(limit = 20): Promise<Conversation[]> {
    return apiData(
        await useHttp().get("/api/v1/ai/conversations", { params: { limit } }),
    );
}
export async function listConversationMessages(
    id: string,
): Promise<AiMessage[]> {
    return apiData(
        await useHttp().get(`/api/v1/ai/conversations/${id}/messages`),
    );
}
export async function submitAiFeedback(
    messageId: string,
    helpful: boolean,
    comment?: string,
): Promise<void> {
    await useHttp().post("/api/v1/ai/feedback", {
        messageId,
        helpful,
        comment,
    });
}
export async function streamChat(
    command: {
        conversationId?: string;
        message: string;
        category?: string;
        includeOwnOrders: boolean;
    },
    token: string,
    onDelta: (text: string) => void,
    signal?: AbortSignal,
): Promise<StreamResult> {
    const result: StreamResult = {
        conversationId: "",
        messageId: "",
        citations: [],
        refused: false,
    };
    const base = import.meta.env.VITE_GATEWAY_URL || "";
    await streamSseEvents(
        `${base}/api/v1/ai/chat/stream`,
        token,
        command,
        (event) => {
            if (event.event === "delta") onDelta(event.data);
            else if (event.event === "meta") {
                const value = JSON.parse(event.data) as {
                    conversationId: string;
                    messageId: string;
                };
                Object.assign(result, value);
            } else if (event.event === "citations") {
                const values = JSON.parse(event.data) as BackendCitation[];
                result.citations = values.map((x) => ({
                    sourceType: x.sourceType,
                    documentId: x.documentId,
                    chunkId: x.sourceId,
                    title: x.title,
                    excerpt: x.excerpt,
                    url: x.url,
                }));
            } else if (event.event === "done")
                result.refused = Boolean(
                    (JSON.parse(event.data) as { refused: boolean }).refused,
                );
            else if (event.event === "error") {
                const value = JSON.parse(event.data) as {
                    code?: string;
                    message?: string;
                };
                throw new Error(
                    value.message || "AI 服务暂不可用，请稍后重试",
                );
            }
        },
        signal,
    );
    return result;
}
