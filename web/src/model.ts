export interface Period {
  number: number;
  start: string;
  end: string;
}
export interface Candidate {
  teacher: string;
  location: string;
}
export interface Rule {
  id: string;
  title: string;
  weekday: number;
  startPeriod: number;
  endPeriod: number;
  variants: { weeks: number[]; candidates: Candidate[] }[];
}
export interface Schedule {
  term: string;
  firstMonday: string;
  periods: Period[];
  rules: Rule[];
}
export interface Result {
  schedule: Schedule;
  warnings: string[];
}
export interface Cell {
  text: string;
  rowSpan: number;
  colSpan: number;
}
export interface Snapshot {
  title: string;
  allWeeks: boolean;
  rows: Cell[][];
}
export interface Raw {
  weekday: number;
  start: number;
  end: number;
  text: string;
}
export function fail(s: string): never {
  throw new Error(s);
}
export const term = (s: string) =>
  s.replace(/\s/g, "").match(/\d{4}学年度第[一二三123]学期/)?.[0] || "导入学期";
export function monday(s: string) {
  if (
    !/^\d{4}-\d{2}-\d{2}$/.test(s) ||
    !Number.isFinite(Date.parse(s)) ||
    new Date(s + "T00:00:00Z").toISOString().slice(0, 10) !== s ||
    new Date(s + "T00:00:00Z").getUTCDay() !== 1
  )
    fail("第 1 教学周起始日期必须是有效的周一");
  return s;
}
export function weeks(s: string) {
  const m = s
    .replace(/\s/g, "")
    .replace(/[，、]/g, ",")
    .replace(/[－～]/g, "-")
    .match(/^([\d,-]+)(?:周)?(每周|单周|双周|单|双)?$/);
  if (!m) return fail("无法识别周次");
  const out = new Set<number>();
  for (const p of m[1].split(",")) {
    const a = p.split("-").map(Number);
    if (
      a.length > 2 ||
      a.some((x) => x < 1 || x > 60) ||
      a[0] > a[a.length - 1]
    )
      fail("周次范围异常");
    for (let n = a[0]; n <= a[a.length - 1]; n++)
      if (
        (!m[2]?.startsWith("单") && !m[2]?.startsWith("双")) ||
        (m[2].startsWith("单") ? n % 2 === 1 : n % 2 === 0)
      )
        out.add(n);
  }
  if (!out.size) fail("周次为空");
  return [...out].sort((a, b) => a - b);
}
export function assemble(
  title: string,
  firstMonday: string,
  periods: Period[],
  raws: Raw[],
): Result {
  monday(firstMonday);
  if (!raws.length) fail("课表中没有课程");
  const groups = new Map<
    string,
    { raw: Raw; title: string; byWeek: Map<number, Candidate[]> }
  >();
  for (const raw of raws) {
    const f = raw.text.split("/");
    if (f.length < 4) fail("课程信息不完整，请使用全部周次课表");
    const title = f[1].replace(/^\s*本\s*[（(][^）)]*[）)]/, "").trim();
    if (!title) fail("课程名称为空");
    const id = [raw.weekday, raw.start, raw.end, title.replace(/\s/g, "")].join(
      "|",
    );
    const g = groups.get(id) || {
      raw,
      title,
      byWeek: new Map<number, Candidate[]>(),
    };
    for (const w of weeks(f[0])) {
      const cs = g.byWeek.get(w) || [];
      const c = { teacher: f[2].trim(), location: f[3].trim() };
      if (
        !cs.some(
          (x) =>
            JSON.stringify(x).replace(/\s/g, "") ===
            JSON.stringify(c).replace(/\s/g, ""),
        )
      )
        cs.push(c);
      g.byWeek.set(w, cs);
    }
    groups.set(id, g);
  }
  const rules: Rule[] = [...groups]
    .map(([id, g]) => {
      const vs = new Map<
        string,
        { weeks: number[]; candidates: Candidate[] }
      >();
      for (const [w, cs] of [...g.byWeek].sort((a, b) => a[0] - b[0])) {
        cs.sort((a, b) =>
          (a.teacher + a.location).localeCompare(b.teacher + b.location),
        );
        const key = JSON.stringify(cs).replace(/\s/g, ""),
          v = vs.get(key) || { weeks: [], candidates: cs };
        v.weeks.push(w);
        vs.set(key, v);
      }
      return {
        id,
        title: g.title,
        weekday: g.raw.weekday,
        startPeriod: g.raw.start,
        endPeriod: g.raw.end,
        variants: [...vs.values()],
      };
    })
    .sort(
      (a, b) =>
        a.weekday - b.weekday ||
        a.startPeriod - b.startPeriod ||
        a.title.localeCompare(b.title),
    );
  return {
    schedule: { term: term(title), firstMonday, periods, rules },
    warnings: [
      ...(rules.some((r) => r.variants.some((v) => v.candidates.length > 1))
        ? ["部分课程有多个教师或教室，已全部保留，请核对。"]
        : []),
      ...(rules.some((r) =>
        r.variants.some((v) => v.candidates.some((c) => !c.location)),
      )
        ? ["部分课程未提供地点。"]
        : []),
      "课表不会自动更新，节假日安排请以学校通知为准。",
    ],
  };
}
const time = /第(\d+)节\s*(\d{2}:\d{2})[~～—-](\d{2}:\d{2})/;
export function period(text: string): Period {
  const m = text.match(time);
  if (
    !m ||
    !/^([01]\d|2[0-3]):[0-5]\d$/.test(m[2]) ||
    !/^([01]\d|2[0-3]):[0-5]\d$/.test(m[3]) ||
    m[2] >= m[3]
  )
    return fail("无法识别节次时间");
  return { number: +m[1], start: m[2], end: m[3] };
}
export function parseTable(s: Snapshot, firstMonday: string): Result {
  if (
    !s ||
    s.allWeeks !== true ||
    !Array.isArray(s.rows) ||
    s.rows.length < 2 ||
    s.rows.length > 80
  )
    fail("请选择完整课表及全部周次");
  let count = 0;
  const grid: ({ cell: Cell; origin: boolean } | undefined)[][] = [];
  s.rows.forEach((row, y) => {
    if (!Array.isArray(row)) fail("课表结构异常");
    grid[y] ??= [];
    let x = 0;
    for (const c of row) {
      if (
        ++count > 6000 ||
        typeof c.text !== "string" ||
        c.text.length > 12000 ||
        ![c.rowSpan, c.colSpan].every(
          (n) => Number.isInteger(n) && n > 0 && n <= 256,
        ) ||
        y + c.rowSpan > s.rows.length
      )
        fail("课表单元格异常");
      while (grid[y][x]) x++;
      if (x + c.colSpan > 256) fail("课表过宽");
      for (let dy = 0; dy < c.rowSpan; dy++) {
        grid[y + dy] ??= [];
        for (let dx = 0; dx < c.colSpan; dx++) {
          if (grid[y + dy][x + dx]) fail("课表合并单元格重叠");
          grid[y + dy][x + dx] = { cell: c, origin: dx === 0 && dy === 0 };
        }
      }
      x += c.colSpan;
    }
  });
  const days = grid[0].map((c) => {
    const m = c?.cell.text.trim().match(/^星期([一二三四五六日天])$/);
    return m ? Math.min("一二三四五六日天".indexOf(m[1]) + 1, 7) : 0;
  });
  if (new Set(days.filter(Boolean)).size !== 7) fail("星期表头不完整");
  const ps = grid.slice(1).map((row) => period(row[0]?.cell.text || ""));
  if (ps.some((p, i) => p.number !== i + 1)) fail("节次不连续");
  const raws: Raw[] = [];
  grid.slice(1).forEach((row, i) =>
    row.forEach((g, x) => {
      if (x && g?.origin && g.cell.text.trim()) {
        const ds = new Set(days.slice(x, x + g.cell.colSpan));
        if (ds.size !== 1 || !days[x]) fail("课程跨越星期边界");
        const end = ps[i + g.cell.rowSpan - 1];
        if (!end) fail("课程节次超出表格");
        raws.push({
          weekday: days[x],
          start: ps[i].number,
          end: end.number,
          text: g.cell.text.trim(),
        });
      }
    }),
  );
  return assemble(s.title, firstMonday, ps, raws);
}
export function parsePayload(text: string, date: string): Result {
  if (text.length > 2_000_000) fail("导入数据过大");
  let o;
  try {
    o = JSON.parse(text);
  } catch {
    return fail("不是有效的课表数据，请重新运行快捷指令或选择原始文件");
  }
  if (o.format !== "campus-schedule" || o.version !== 1)
    fail("课表数据格式或版本不支持");
  if (o.snapshot) return parseTable(o.snapshot, date);
  return {
    schedule: validateSchedule(o.schedule, date),
    warnings: ["确认后替换本机课表。"],
  };
}
export function validateSchedule(s: Schedule, date: string): Schedule {
  monday(date);
  if (
    !s ||
    !Array.isArray(s.periods) ||
    !Array.isArray(s.rules) ||
    !s.rules.length ||
    s.rules.length > 1000 ||
    s.periods.length > 40
  )
    fail("课表数据不完整");
  const text = (v: unknown) => {
    if (typeof v !== "string" || v.length > 12000) fail("课表字段异常");
    return v as string;
  };
  const ps = s.periods.map((p, i) => {
    if (p.number !== i + 1) fail("节次不连续");
    return period(`第${p.number}节${p.start}~${p.end}`);
  });
  const rules = s.rules.map((r) => {
    if (
      !Number.isInteger(r.weekday) ||
      r.weekday < 1 ||
      r.weekday > 7 ||
      !Number.isInteger(r.startPeriod) ||
      !Number.isInteger(r.endPeriod) ||
      r.startPeriod < 1 ||
      r.endPeriod < r.startPeriod ||
      r.endPeriod > ps.length ||
      !Array.isArray(r.variants) ||
      !r.variants.length ||
      r.variants.length > 60
    )
      fail("课程范围异常");
    return {
      id: text(r.id),
      title: text(r.title),
      weekday: r.weekday,
      startPeriod: r.startPeriod,
      endPeriod: r.endPeriod,
      variants: r.variants.map((v) => {
        if (
          !Array.isArray(v.weeks) ||
          !v.weeks.length ||
          v.weeks.length > 60 ||
          v.weeks.some((w) => !Number.isInteger(w) || w < 1 || w > 60) ||
          !Array.isArray(v.candidates) ||
          !v.candidates.length ||
          v.candidates.length > 100
        )
          fail("候选安排异常");
        return {
          weeks: [...new Set(v.weeks)].sort((a, b) => a - b),
          candidates: v.candidates.map((c) => ({
            teacher: text(c.teacher),
            location: text(c.location),
          })),
        };
      }),
    };
  });
  return { term: term(text(s.term)), firstMonday: date, periods: ps, rules };
}
export const today = () =>
  new Intl.DateTimeFormat("sv-SE", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
export const addDays = (d: string, n: number) =>
  new Date(Date.parse(d + "T00:00:00Z") + n * 86400000)
    .toISOString()
    .slice(0, 10);
export const weekOf = (s: Schedule, d: string) =>
  Math.floor((Date.parse(d) - Date.parse(s.firstMonday)) / 604800000) + 1;
export const lastWeek = (s: Schedule) =>
  Math.max(...s.rules.flatMap((r) => r.variants.flatMap((v) => v.weeks)));
export function lessons(s: Schedule, d: string) {
  const day = new Date(d + "T00:00:00Z").getUTCDay() || 7,
    week = weekOf(s, d);
  return s.rules
    .filter((r) => r.weekday === day)
    .flatMap((r) => {
      const v = r.variants.find((v) => v.weeks.includes(week));
      return v
        ? [
            {
              ...r,
              candidates: v.candidates,
              start: s.periods[r.startPeriod - 1].start,
              end: s.periods[r.endPeriod - 1].end,
            },
          ]
        : [];
    })
    .sort((a, b) => a.start.localeCompare(b.start));
}
