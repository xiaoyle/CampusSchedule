// @vitest-environment jsdom
import { test, expect } from "vitest";
import { readFileSync, existsSync } from "node:fs";
import { parseGeometry } from "../src/pdf-geometry";
import { parseWord } from "../src/word";
test.skipIf(!existsSync("../.tools/web-pdf-geometry.json"))(
  "browser PDF matches Word, including candidates and weeks",
  () => {
    const pdf = parseGeometry(
      JSON.parse(readFileSync("../.tools/web-pdf-geometry.json", "utf8")),
      "2026-09-07",
    ).schedule;
    const word = parseWord(
      new Uint8Array(readFileSync("../private-fixtures/schedule.doc")),
      "2026-09-07",
    ).schedule;
    expect(pdf.rules.length).toBe(29);
    const flatten = (s: any) =>
      s.rules
        .flatMap((r: any) =>
          r.variants.flatMap((v: any) =>
            v.weeks.flatMap((w: any) =>
              v.candidates.map((c: any) =>
                JSON.stringify([
                  r.weekday,
                  r.startPeriod,
                  r.endPeriod,
                  r.title,
                  w,
                  c.teacher,
                  c.location,
                ]).replace(/\s/g, ""),
              ),
            ),
          ),
        )
        .sort();
    expect(flatten(pdf)).toEqual(flatten(word));
  },
);
