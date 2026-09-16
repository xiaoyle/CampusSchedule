import { test, expect } from "vitest";
import { JSDOM } from "jsdom";
import { readFileSync } from "node:fs";
import { parsePayload } from "../src/model";
const script = readFileSync("public/shortcut.js", "utf8");

async function run(url: string, body: string, fetchImpl?: (input: string, init?: RequestInit) => Promise<unknown>) {
  const dom = new JSDOM(body, { url, runScripts: "outside-only" });
  (dom.window as any).fetch = fetchImpl;
  const value = await new Promise<string>((resolve) => {
    (dom.window as any).completion = resolve;
    dom.window.eval(script);
  });
  dom.window.close();
  return JSON.parse(value);
}

test("shortcut rejects login and external pages without reading inputs", async () => {
  for (const url of ["https://cas.sysu.edu.cn/login", "https://example.com/", "https://jwxt.sysu.edu.cn/jwxt/#/mobile"])
    expect((await run(url, '<input value="SECRET">')).error).toBeTruthy();
});

test("shortcut reads a selected desktop all-weeks table", async () => {
  const result = await run(
    "https://jwxt.sysu.edu.cn/jwxt/mk/schedule-web/#/studentTimeTabPrint",
    '<input value="SECRET"><p>学生身份PRIVATE</p><h2>2026学年度第一学期PRIVATE课程表</h2><div class="ant-tabs-tab-active">全部</div><div id="table-bot"><table><tr><td>星期一</td></tr><tr><td>课程</td></tr></table></div>',
  );
  expect(result.snapshot.title).toBe("2026学年度第一学期");
  expect(JSON.stringify(result)).not.toMatch(/SECRET|PRIVATE/);
});

test("shortcut requests the full term when iPhone only shows one week", async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = [];
  const fetchImpl = async (input: string, init?: RequestInit) => {
    calls.push({ url: input, init });
    if (input.includes("minorName")) return {
      json: async () => ({ code: 200, data: [
        { sectionNumber: 1, startTime: "08:00", endTime: "08:45" },
        { sectionNumber: 2, startTime: "08:55", endTime: "09:40" },
      ] }),
    };
    return {
      ok: true, status: 200,
      json: async () => ({ code: 200, data: { timetable: {
        "11": [{ rowSpan: 2, emptyFlag: 0, timeDetail: "1-17每周/本(专必)", courseName: "高等数学/", teachingStaffName: "教师甲/", classPlace: "教学楼101/" }],
        "61": [{ rowSpan: 2, emptyFlag: 0, timeDetail: "1-5单周/本(公选)", courseName: "周末课程/", teachingStaffName: "教师乙/", classPlace: "/" }],
      } } }),
    };
  };
  const result = await run(
    "https://jwxt.sysu.edu.cn/jwxt/mk/schedule-web/#/studentweekTimeTabPrint?code=jwxsd_xskbcx",
    "<main><b>2026-1学期</b><span>第1周</span></main>",
    fetchImpl,
  );
  expect(calls[0].url).toContain("studentQuery");
  expect(JSON.parse(String(calls[0].init?.body))).toMatchObject({ acadYear: "2026-1", week: "99" });
  expect(result.schedule.rules).toHaveLength(2);
  expect(result.schedule.rules[0]).toMatchObject({ title: "高等数学", weekday: 1, startPeriod: 1, endPeriod: 2 });
  expect(result.schedule.rules[1].variants[0].weeks).toEqual([1, 3, 5]);
  expect(parsePayload(JSON.stringify(result), "2026-09-07").schedule.rules).toHaveLength(2);
  expect(JSON.stringify(result)).not.toMatch(/SECRET|PRIVATE/);
});
