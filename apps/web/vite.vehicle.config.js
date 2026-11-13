import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";
import { fileURLToPath } from "node:url";
export default defineConfig({
  plugins: [react()],
  server: { proxy: { "/api": "http://127.0.0.1:8000" } },
  preview: { proxy: { "/api": "http://127.0.0.1:8000" } },
  build: {
    outDir: "dist-vehicle",
    rollupOptions: {
      input: fileURLToPath(new URL("./vehicle.html", import.meta.url)),
    },
  },
});
