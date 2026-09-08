// @vitest-environment jsdom
import { test, expect } from "vitest";
import { readFileSync, existsSync } from "node:fs";
import { parseWord } from "../src/word";
import { parseGeometry } from "../src/pdf-geometry";
import {
  weeks,
  parsePayload,
  parseTable,
  weekOf,
  lessons,
  lastWeek,
} from "../src/model";
test("week ranges and date boundaries", () => {
  expect(weeks("1-7单周")).toEqual([1, 3, 5, 7]);
  expect(() => weeks("9-2")).toThrow();
  expect(() => parsePayload("{}", "2026-09-07")).toThrow();
  expect(() =>
    parseTable({ title: "", rows: [], allWeeks: false }, "2026-09-07"),
  ).toThrow();
});
const root = "../private-fixtures/";
test.skipIf(!existsSync(root + "schedule.doc"))(
  "private Word and PDF geometry match 29 rules / 17 weeks",
  () => {
    const word = parseWord(
      new Uint8Array(readFileSync(root + "schedule.doc")),
      "2026-09-07",
    ).schedule;
    expect(word.rules).toHaveLength(29);
    expect(lastWeek(word)).toBe(17);
    expect(weekOf(word, "2026-11-02")).toBe(9);
    expect(lessons(word, "2026-09-07")[0].start).toBe("08:00");
    const pages = JSON.parse(
      readFileSync(root + "pdf-geometry.json", "utf8"),
    ).map((p: any) => ({
      glyphs: p.chars.map((c: any) => ({
        text: c.text,
        x: c.x0,
        y: (c.top + c.bottom) / 2,
      })),
      lines: p.lines.map((l: any) => ({
        x0: l.x0,
        y0: l.top,
        x1: l.x1,
        y1: l.bottom,
      })),
    }));
    const pdf = parseGeometry(pages, "2026-09-07").schedule;
    expect(pdf.rules).toHaveLength(29);
    const normalize = (s: any) =>
      JSON.stringify(
        s.rules
          .map((r: any) => ({
            ...r,
            id: "",
            title: r.title.replace(/\s/g, ""),
            variants: r.variants.map((v: any) => ({
              ...v,
              candidates: v.candidates
                .map((c: any) => ({
                  teacher: c.teacher.replace(/\s/g, ""),
                  location: c.location.replace(/\s/g, ""),
                }))
                .sort((a: any, b: any) =>
                  JSON.stringify(a).localeCompare(JSON.stringify(b)),
                ),
            })),
          }))
          .sort(
            (a: any, b: any) =>
              a.weekday - b.weekday ||
              a.startPeriod - b.startPeriod ||
              a.title.localeCompare(b.title),
          ),
      );
    expect(normalize(pdf)).toBe(normalize(word));
    expect(() =>
      parseWord(new TextEncoder().encode("broken"), "2026-09-07"),
    ).toThrow();
    expect(() => parseGeometry(pages.slice(0, -1), "2026-09-07")).toThrow();
  },
);
