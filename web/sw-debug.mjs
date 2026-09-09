import { chromium } from "@playwright/test";
const b = await chromium.launch({ channel: "msedge" }),
  p = await b.newPage();
p.on("console", (m) => console.log(m.type(), m.text()));
p.on("requestfailed", (r) => console.log("FAILED", r.url(), r.failure()));
await p.goto("http://127.0.0.1:4173/CampusSchedule/");
await p.waitForTimeout(8000);
console.log(
  await p.evaluate(async () => ({
    regs: (await navigator.serviceWorker.getRegistrations()).map((r) => ({
      scope: r.scope,
      active: r.active?.state,
      waiting: r.waiting?.state,
      install: r.installing?.state,
    })),
    controller: !!navigator.serviceWorker.controller,
    caches: await caches.keys(),
  })),
);
await b.close();
