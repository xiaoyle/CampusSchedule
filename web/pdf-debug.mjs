import { chromium } from "@playwright/test";
import { readFileSync, writeFileSync } from "node:fs";
const b = await chromium.launch({ channel: "msedge" }),
  p = await b.newPage();
await p.goto("http://127.0.0.1:4174/CampusSchedule/");
const pages = await p.evaluate(
  async (data) => {
    const { readPdf } = await import("/CampusSchedule/src/pdf.ts");
    return readPdf(new Uint8Array(data));
  },
  [...readFileSync("../private-fixtures/schedule.pdf")],
);
writeFileSync("../.tools/web-pdf-geometry.json", JSON.stringify(pages));
console.log(
  pages.map((p) => ({ glyphs: p.glyphs.length, lines: p.lines.length })),
);
await b.close();
