// @vitest-environment jsdom
import { test, expect } from "vitest";
import {
  parseTable,
  parsePayload,
  lastWeek,
  lessons,
  type Cell,
  type Snapshot,
} from "../src/model";
const c = (text = "", rowSpan = 1, colSpan = 1): Cell => ({
  text,
  rowSpan,
  colSpan,
});
const sample = (): Snapshot => ({
  title: "2026学年度第一学期（示例）",
  allWeeks: true,
  rows: [
    [
      c("节次"),
      c("星期一", 1, 2),
      ...["二", "三", "四", "五", "六", "日"].map((d) => c("星期" + d)),
    ],
    [
      c("第1节08:00~08:45"),
      c("1-17每周/本(专必)示例课程/教师甲/教室甲/30人", 2),
      c("1-17每周/本(专必)示例课程/教师乙/教室乙/30人", 2),
      ...Array.from({ length: 5 }, () => c()),
      c("1-3单周/本(公选)周末课程/教师丙//30人", 2),
    ],
    [c("第2节08:55~09:40"), ...Array.from({ length: 5 }, () => c())],
  ],
});
test("web table restores merged periods, weekdays, candidates, weekends and empty locations", () => {
  const s = parseTable(sample(), "2026-09-07").schedule;
  expect(s.rules).toHaveLength(2);
  expect(lastWeek(s)).toBe(17);
  expect(lessons(s, "2026-09-07")[0]).toMatchObject({
    start: "08:00",
    end: "09:40",
    candidates: [
      { teacher: "教师甲", location: "教室甲" },
      { teacher: "教师乙", location: "教室乙" },
    ],
  });
  expect(lessons(s, "2026-09-13")).toHaveLength(1);
  expect(lessons(s, "2026-09-20")).toHaveLength(0);
  expect(
    parsePayload(
      JSON.stringify({ format: "campus-schedule", version: 1, schedule: s }),
      "2026-09-07",
    ).schedule,
  ).toEqual(s);
});
test("single week, invalid span, missing weekday, wrong date and malformed backup rejected", () => {
  const s = sample();
  expect(() => parseTable({ ...s, allWeeks: false }, "2026-09-07")).toThrow();
  s.rows[1][1].rowSpan = 100;
  expect(() => parseTable(s, "2026-09-07")).toThrow();
  expect(() => parseTable(sample(), "2026-09-08")).toThrow();
  const missing = sample();
  missing.rows[0].pop();
  expect(() => parseTable(missing, "2026-09-07")).toThrow();
  expect(() =>
    parsePayload(
      JSON.stringify({ format: "campus-schedule", version: 1, schedule: {} }),
      "2026-09-07",
    ),
  ).toThrow();
  expect(() =>
    parsePayload(JSON.stringify({ error: "学校登录已过期，请重新登录" }), "2026-09-07"),
  ).toThrow("学校登录已过期，请重新登录");
});
