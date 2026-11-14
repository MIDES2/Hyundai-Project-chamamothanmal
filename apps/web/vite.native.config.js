import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";
import { fileURLToPath } from "node:url";
export default defineConfig({
  base: "./", envDir: false, envPrefix: "NATIVE_VEHICLE_PUBLIC_", plugins: [react()],
  build: { outDir: "../vehicle/android/app/build/generated/vehicleWebAssets/vehicle-ui", emptyOutDir: true,
    rollupOptions: { input: fileURLToPath(new URL("./native-vehicle.html", import.meta.url)) } },
});
