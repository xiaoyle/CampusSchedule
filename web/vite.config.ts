import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { VitePWA } from "vite-plugin-pwa";
export default defineConfig({
  base: "/CampusSchedule/",
  plugins: [
    react(),
    VitePWA({
      registerType: "prompt",
      includeAssets: ["icons/*.png", "shortcut.js", "shortcut-guide.html", "push-sw.js"],
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
        shortcuts: [
          { name: "查看今天", short_name: "今天", url: "/CampusSchedule/#/today", icons: [{ src: "icons/icon-192.png", sizes: "192x192" }] },
          { name: "添加待办", short_name: "待办", url: "/CampusSchedule/#/task/new", icons: [{ src: "icons/icon-192.png", sizes: "192x192" }] },
        ],
      },
      workbox: {
        globPatterns: ["**/*.{js,mjs,css,html,png,svg,woff2,bcmap,pfb,ttf}"],
        maximumFileSizeToCacheInBytes: 6000000,
        clientsClaim: true,
        cleanupOutdatedCaches: true,
        navigateFallback: "index.html",
        importScripts: ["push-sw.js"],
        runtimeCaching: [
          {
            urlPattern: /^https:\/\/(cdn\.jsdelivr\.net|tessdata\.projectnaptha\.com)\//,
            handler: "CacheFirst",
            options: { cacheName: "ocr-models-v1", expiration: { maxEntries: 12, maxAgeSeconds: 60 * 60 * 24 * 365 } },
          },
        ],
      },
    }),
  ],
});
