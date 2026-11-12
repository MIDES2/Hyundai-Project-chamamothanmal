import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";
import { fileURLToPath } from "node:url";
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: "dist-vehicle",
    rollupOptions: {
      input: fileURLToPath(new URL("./vehicle.html", import.meta.url)),
    },
  },
});
