import { assemble, fail, period, term, type Raw } from "./model";
export interface Glyph {
  text: string;
  x: number;
  y: number;
}
export interface Line {
  x0: number;
  y0: number;
  x1: number;
  y1: number;
}
export interface Page {
  glyphs: Glyph[];
  lines: Line[];
}
type Rect = { left: number; right: number; top: number; bottom: number };
type Fragment = Rect & { text: string };
const cluster = (v: number[]) => {
  const gs: number[][] = [];
  for (const n of v.sort((a, b) => a - b)) {
    if (!gs.length || n - gs.at(-1)!.at(-1)! > 1) gs.push([n]);
    else gs.at(-1)!.push(n);
  }
  return gs.map((g) => g.reduce((a, b) => a + b) / g.length);
};
function cells(lines: Line[]): Rect[] {
  const vs = lines.filter(
      (l) => Math.abs(l.x0 - l.x1) < 0.8 && Math.abs(l.y1 - l.y0) > 4,
    ),
    hs = lines.filter(
      (l) => Math.abs(l.y0 - l.y1) < 0.8 && Math.abs(l.x1 - l.x0) > 4,
    );
  const ys = cluster(hs.map((l) => (l.y0 + l.y1) / 2));
  if (ys.length > 200 || vs.length > 2000) fail("PDF 表格过于复杂");
  const result = new Map<string, Rect>();
  for (let i = 0; i < ys.length - 1; i++) {
    const mid = (ys[i] + ys[i + 1]) / 2,
      xs = cluster(
        vs
          .filter(
            (l) => Math.min(l.y0, l.y1) <= mid && Math.max(l.y0, l.y1) >= mid,
          )
          .map((l) => (l.x0 + l.x1) / 2),
      );
    for (let j = 0; j < xs.length - 1; j++) {
      const left = xs[j],
        right = xs[j + 1];
      const bs = hs
        .filter(
          (l) =>
            Math.min(l.x0, l.x1) <= left + 1 &&
            Math.max(l.x0, l.x1) >= right - 1,
        )
        .map((l) =>
          ys.reduce((a, b) =>
            Math.abs(a - l.y0) < Math.abs(b - l.y0) ? a : b,
          ),
        );
      const top = Math.max(...bs.filter((y) => y <= mid)),
        bottom = Math.min(...bs.filter((y) => y >= mid));
      if (Number.isFinite(top) && Number.isFinite(bottom) && right - left > 4) {
        const c = { left, right, top, bottom };
        result.set(JSON.stringify(c), c);
      }
    }
  }
  return [...result.values()];
}
const contains = (c: Rect, g: Glyph) =>
  g.x >= c.left && g.x < c.right && g.y >= c.top && g.y < c.bottom;
export function parseGeometry(pages: Page[], date: string) {
  if (!pages.length || pages.length > 40) fail("PDF 最多支持 40 页");
  const fragments: Fragment[] = [],
    leftGlyphs: Glyph[] = [];
  let columns: (Rect & { day: number })[] = [],
    previous: Fragment[] = [],
    offset = 0,
    title = "";
  pages.forEach((page, index) => {
    const cs = cells(page.lines);
    if (!cs.length) fail("PDF 缺少表格边框，不支持扫描件");
    const top = Math.min(...cs.map((c) => c.top)),
      bottom = Math.max(...cs.map((c) => c.bottom));
    if (!index) {
      const indexed = page.glyphs.flatMap((g) =>
          [...g.text].map((t) => ({ t, g })),
        ),
        text = indexed.map((i) => i.t).join("");
      title = term(text);
      columns = [...text.matchAll(/星期([一二三四五六日天])/g)].map((m) => {
        const anchor = indexed[m.index! + m[0].length - 1].g;
        const c = cs.find((c) => contains(c, anchor));
        if (!c) return fail("PDF 星期表头位置不清晰");
        return { ...c, day: Math.min("一二三四五六日天".indexOf(m[1]) + 1, 7) };
      });
      if (columns.length !== 7 || new Set(columns.map((c) => c.day)).size !== 7)
        fail("PDF 星期表头不完整");
    }
    const left = Math.min(...columns.map((c) => c.left));
    leftGlyphs.push(
      ...page.glyphs
        .filter((g) => g.x < left && g.y >= top && g.y < bottom)
        .map((g) => ({ ...g, y: offset + g.y - top })),
    );
    const current: Fragment[] = [];
    for (const c of cs.filter((c) => c.left >= left - 1)) {
      const text = page.glyphs
        .filter((g) => contains(c, g))
        .map((g) => g.text)
        .join("")
        .trim();
      let next: Fragment = {
        ...c,
        top: offset + c.top - top,
        bottom: offset + c.bottom - top,
        text,
      };
      const before =
        Math.abs(c.top - top) < 1
          ? previous.find(
              (p) =>
                Math.abs(p.left - c.left) < 1 &&
                Math.abs(p.right - c.right) < 1,
            )
          : undefined;
      const starts = /^[\d,、，-]+(?:周)?(?:每周|单周|双周|单|双)?\//.test(
        text.replace(/\s/g, ""),
      );
      if (
        before &&
        !(/\d+人\s*$/.test(before.text.replace(/\s/g, "")) && starts)
      ) {
        before.text += text;
        before.bottom = next.bottom;
        next = before;
      } else fragments.push(next);
      if (Math.abs(c.bottom - bottom) < 1) current.push(next);
    }
    previous = current;
    offset += bottom - top;
  });
  const indexed = leftGlyphs
    .flatMap((g) => [...g.text].map((t) => ({ t, y: g.y })))
    .filter((i) => !/\s/.test(i.t));
  const markers = [
    ...indexed
      .map((i) => i.t)
      .join("")
      .matchAll(/第\d+节\s*\d{2}:\d{2}[~～—-]\d{2}:\d{2}/g),
  ].map((m) => ({ p: period(m[0]), y: indexed[m.index!].y }));
  if (!markers.length || markers.some((m, i) => m.p.number !== i + 1))
    fail("PDF 节次不连续或缺页");
  const raws: Raw[] = fragments
    .filter((f) => f.text.includes("/"))
    .map((f) => {
      if (
        !/^[\d,、，-]+(?:周)?(?:每周|单周|双周|单|双)?\//.test(
          f.text.replace(/\s/g, ""),
        )
      )
        fail("PDF 跨页课程不完整");
      const day = columns.find(
        (c) => f.left >= c.left - 1 && f.right <= c.right + 1,
      );
      const covered = markers.filter((m) => m.y >= f.top - 1 && m.y < f.bottom);
      if (!day || !covered.length) fail("PDF 课程无法定位");
      return {
        weekday: day!.day,
        start: covered[0].p.number,
        end: covered.at(-1)!.p.number,
        text: f.text,
      };
    });
  if (
    fragments.some(
      (f) => f.top > markers[0].y && f.text.trim() && !f.text.includes("/"),
    )
  )
    fail("PDF 存在不完整课程片段");
  return assemble(
    title,
    date,
    markers.map((m) => m.p),
    raws,
  );
}
