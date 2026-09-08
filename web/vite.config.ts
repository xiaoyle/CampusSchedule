import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { VitePWA } from "vite-plugin-pwa";
export default defineConfig({
  base: "/CampusSchedule/",
  plugins: [
    react(),
    VitePWA({
      registerType: "prompt",
      includeAssets: ["icons/*.png", "shortcut.js", "shortcut-guide.html"],
      manifest: {
        id: "/CampusSchedule/",
        name: "中大课表",
        short_name: "中大课表",
        lang: "zh-CN",
        start_url: "/CampusSchedule/",
        scope: "/CampusSchedule/",
        display: "standalone",
        background_color: "#F3F7F4",
        theme_color: "#176B52",
        icons: [
          { src: "icons/icon-192.png", sizes: "192x192", type: "image/png" },
          {
            src: "icons/icon-512.png",
            sizes: "512x512",
            type: "image/png",
            purpose: "any",
          },
          {
            src: "icons/maskable-512.png",
            sizes: "512x512",
            type: "image/png",
            purpose: "maskable",
          },
        ],
      },
      workbox: {
        globPatterns: ["**/*.{js,mjs,css,html,png,svg,woff2,bcmap,pfb,ttf}"],
        maximumFileSizeToCacheInBytes: 6000000,
        clientsClaim: true,
        cleanupOutdatedCaches: true,
        navigateFallback: "index.html",
      },
    }),
  ],
});
