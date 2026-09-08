import { test, expect } from "vitest";
import { JSDOM } from "jsdom";
import { readFileSync } from "node:fs";
const script = readFileSync("public/shortcut.js", "utf8");
function run(url: string, body: string) {
  const dom = new JSDOM(body, { url, runScripts: "outside-only" });
  let value = "";
  (dom.window as any).completion = (s: string) => (value = s);
  dom.window.eval(script);
  dom.window.close();
  return JSON.parse(value);
}
test("shortcut rejects login, external origin and mobile single-week route", () => {
  for (const url of [
    "https://cas.sysu.edu.cn/login",
    "https://example.com/",
    "https://jwxt.sysu.edu.cn/jwxt/#/mobile",
  ])
    expect(run(url, '<input value="SECRET">').error).toBeTruthy();
});
test("shortcut reads only timetable and sanitized term", () => {
  const result = run(
    "https://jwxt.sysu.edu.cn/jwxt/mk/schedule-web/#/studentTimeTabPrint",
    '<input value="SECRET"><p>学生身份PRIVATE</p><h2>2026学年度第一学期PRIVATE课程表</h2><div class="ant-tabs-tab-active">全部</div><div id="table-bot"><table><tr><td>星期一</td></tr><tr><td>课程</td></tr></table></div>',
  );
  expect(result.snapshot.title).toBe("2026学年度第一学期");
  expect(JSON.stringify(result)).not.toMatch(/SECRET|PRIVATE/);
});
