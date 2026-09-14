import { fileURLToPath, URL } from "node:url";
import vue from "@vitejs/plugin-vue";
import { defineConfig, loadEnv } from "vite";

function vendorChunk(id: string) {
  if (!id.includes("node_modules")) return undefined;
  if (id.includes("element-plus") || id.includes("@element-plus")) return "element-plus";
  if (id.includes("vue") || id.includes("pinia")) return "vue-runtime";
  if (id.includes("axios")) return "http-client";
  return "vendor";
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  return {
    plugins: [vue()],
    resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
    server: {
      strictPort: true,
      proxy: {
        "/api": {
          target: env.VITE_GATEWAY_URL || "http://127.0.0.1:8080",
          changeOrigin: true,
        },
      },
    },
    build: {
      target: "es2022",
      sourcemap: mode !== "production",
      rollupOptions: { output: { manualChunks: vendorChunk } },
    },
  };
});
