import { chromium, expect } from "@playwright/test";
import { readFileSync, writeFileSync } from "node:fs";
const browser = await chromium.launch({ channel: "msedge", headless: true });
const context = await browser.newContext({
  viewport: { width: 390, height: 844 },
});
const page = await context.newPage();
const errors = [];
page.on("pageerror", (e) => errors.push(e.message));
await page.goto("http://127.0.0.1:4173/CampusSchedule/");
await page.getByRole("button", { name: "导入我的课表" }).waitFor();
await page.screenshot({ path: "../.tools/web-empty.png", fullPage: true });
await page.getByRole("button", { name: "先添加到主屏幕" }).click();
await expect(page.getByRole("dialog")).toContainText("Safari");
await page.getByRole("button", { name: "关闭", exact: true }).click();
await page.getByRole("button", { name: "导入我的课表" }).click();
await page
  .getByLabel("选择课表文件")
  .setInputFiles("../private-fixtures/schedule.pdf");
await expect(page.getByRole("dialog")).toContainText("29 项安排 · 17 周");
await page.getByRole("button", { name: "确认保存课表" }).click();
await page.getByText("课表已保存到本机。").waitFor();
await page.reload();
await page.getByRole("button", { name: "课表", exact: true }).click();
await page.screenshot({ path: "../.tools/web-schedule.png", fullPage: true });
await expect(page.locator(".lesson").first()).toBeVisible();
await page.getByRole("button", { name: "导入与设置", exact: true }).click();
await page
  .getByLabel("选择课表文件")
  .setInputFiles({
    name: "broken.doc",
    mimeType: "application/msword",
    buffer: Buffer.from("broken"),
  });
await expect(page.getByRole("alert")).toContainText("无法读取");
await page.getByRole("button", { name: "课表", exact: true }).click();
await expect(page.locator(".lesson").first()).toBeVisible();
await page.waitForFunction(() => navigator.serviceWorker.controller !== null);
await expect(page.getByText("已可离线使用")).toBeVisible();
await context.setOffline(true);
await page.reload();
await page.getByRole("button", { name: "课表", exact: true }).click();
await expect(page.locator(".lesson").first()).toBeVisible();

await page.getByRole("button", { name: "导入与设置", exact: true }).click();
await page
  .getByLabel("选择课表文件")
  .setInputFiles("../private-fixtures/schedule.pdf");
await expect(page.getByRole("dialog")).toContainText("29 项安排 · 17 周");
await page.getByRole("button", { name: "关闭", exact: true }).click();
await context.setOffline(false);
await page.emulateMedia({ colorScheme: "dark" });
await page.screenshot({
  path: "../.tools/web-settings-dark.png",
  fullPage: true,
});
for (const width of [320, 390, 768, 1280]) {
  await page.setViewportSize({ width, height: 844 });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
}
await page.setViewportSize({ width: 390, height: 844 });
await page.evaluate(() => (document.documentElement.style.fontSize = "24px"));
expect(
  await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
).toBe(true);
expect(errors).toEqual([]);
console.log(
  "PASS: PDF 29/17, preview, save/reopen, failed import preservation, offline shell & PDF, widths 320–1280, installation guide, no runtime errors",
);
await browser.close();
