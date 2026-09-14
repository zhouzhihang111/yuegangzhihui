import { onBeforeUnmount, ref } from "vue";

export type DocumentPreviewMode = "empty" | "loading" | "pdf" | "text" | "download";

export interface BinaryDocumentDescriptor {
    fileName: string;
    mediaType: string;
    load: () => Promise<Blob>;
}

export function useBinaryDocumentPreview() {
    const mode = ref<DocumentPreviewMode>("empty");
    const fileName = ref("");
    const mediaType = ref("");
    const text = ref("");
    const objectUrl = ref("");
    let content: Blob | undefined;

    function clearObjectUrl() {
        if (objectUrl.value) URL.revokeObjectURL(objectUrl.value);
        objectUrl.value = "";
    }

    async function open(descriptor: BinaryDocumentDescriptor) {
        clearObjectUrl();
        mode.value = "loading";
        fileName.value = descriptor.fileName;
        mediaType.value = descriptor.mediaType;
        text.value = "";
        try {
            content = await descriptor.load();
        } catch (error) {
            content = undefined;
            mode.value = "empty";
            throw error;
        }

        const lowerName = descriptor.fileName.toLowerCase();
        const type = (content.type || descriptor.mediaType).toLowerCase();
        if (type.startsWith("text/") || lowerName.endsWith(".txt") || lowerName.endsWith(".md")) {
            text.value = await content.text();
            mode.value = "text";
            return;
        }
        if (type.includes("pdf") || lowerName.endsWith(".pdf")) {
            objectUrl.value = URL.createObjectURL(content);
            mode.value = "pdf";
            return;
        }
        mode.value = "download";
    }

    function download() {
        if (!content || !fileName.value) return;
        const url = URL.createObjectURL(content);
        const anchor = document.createElement("a");
        anchor.href = url;
        anchor.download = fileName.value;
        anchor.rel = "noopener";
        anchor.click();
        window.setTimeout(() => URL.revokeObjectURL(url), 0);
    }

    function clear() {
        clearObjectUrl();
        content = undefined;
        text.value = "";
        fileName.value = "";
        mediaType.value = "";
        mode.value = "empty";
    }

    onBeforeUnmount(clear);
    return { mode, fileName, mediaType, text, objectUrl, open, download, clear };
}
