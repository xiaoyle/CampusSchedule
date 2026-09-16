import { createWorker } from "tesseract.js";
import { assemble, type Period, type Raw, type Result } from "./model";

export type ImageImportKind = "AUTO" | "FULL_TERM_TABLE" | "WEEKLY_MOBILE";
export interface ImageOcrToken { pageIndex: number; text: string; left: number; top: number; right: number; bottom: number; confidence: number }
export interface ImageOcrPage { index: number; width: number; height: number; tokens: ImageOcrToken[] }
export interface ImageImportRow { id: string; weekExpression: string; weekday: number; startPeriod: number; endPeriod: number; title: string; teacher: string; location: string; confidence: number; issues: string[] }
export interface ImageImportDraft { kind: Exclude<ImageImportKind, "AUTO">; term: string; firstMonday: string; periods: Period[]; rows: ImageImportRow[]; capturedWeeks: number[]; warnings: string[] }

export const defaultPeriods: Period[] = [
  [1,"08:00","08:45"],[2,"08:55","09:40"],[3,"10:10","10:55"],[4,"11:05","11:50"],[5,"14:20","15:05"],
  [6,"15:15","16:00"],[7,"16:30","17:15"],[8,"17:25","18:10"],[9,"19:00","19:45"],[10,"19:55","20:40"],
].map(([number,start,end]) => ({ number: Number(number), start: String(start), end: String(end) }));

export async function recognizeImages(files: File[], firstMonday: string, kind: ImageImportKind, progress: (message: string, value: number) => void): Promise<ImageImportDraft> {
  if (!files.length || files.length > 20) throw new Error("请选择 1 至 20 张课表图片");
  if (files.some((x) => x.size > 18_000_000)) throw new Error("单张图片不能超过 18 MB");
  progress("正在准备中文识别模型，首次使用需要联网下载", .02);
  const worker = await createWorker(["chi_sim", "eng"], undefined, { logger: (m) => progress(translateStatus(m.status), Math.max(.03, m.progress * .75)) });
  try {
    const pages: ImageOcrPage[] = [];
    for (let index = 0; index < files.length; index++) {
      progress(`正在识别第 ${index + 1}/${files.length} 张`, index / files.length);
      const image = await createImageBitmap(files[index]);
      const result = await worker.recognize(files[index], { rotateAuto: true }, { blocks: true, text: true });
      const blocks = result.data.blocks ?? [];
      const tokens = blocks.flatMap((block) => {
        const lines = block.paragraphs?.flatMap((p) => p.lines ?? []) ?? [];
        return (lines.length ? lines : [block as any]).map((line: any) => ({
          pageIndex: index, text: normalize(line.text ?? ""), left: line.bbox?.x0 ?? block.bbox.x0,
          top: line.bbox?.y0 ?? block.bbox.y0, right: line.bbox?.x1 ?? block.bbox.x1,
          bottom: line.bbox?.y1 ?? block.bbox.y1, confidence: Math.max(0, Math.min(1, Number(line.confidence ?? block.confidence) / 100)),
        })).filter((x) => x.text);
      });
      if (!tokens.length && result.data.text.trim()) tokens.push({ pageIndex: index, text: normalize(result.data.text), left: 0, top: 0, right: image.width, bottom: image.height, confidence: .5 });
      pages.push({ index, width: image.width, height: image.height, tokens }); image.close();
    }
    progress("正在还原课表结构", .9);
    return parseImagePages(pages, firstMonday, kind);
  } finally { await worker.terminate(); }
}

const weekPattern = /([0-9]{1,2}(?:[-－—~～][0-9]{1,2})?(?:[、,，][0-9]{1,2}(?:[-－—~～][0-9]{1,2})?)*)\s*(?:周)?\s*(每周|单周|双周|单|双)?/;
const semesterPattern = /\d{4}\s*学年度?\s*第[一二三123]学期/;
const weekNumberPattern = /第\s*([0-9]{1,2})\s*周/;
const dayNames = ["星期一","星期二","星期三","星期四","星期五","星期六","星期日"];
const centerX = (x: ImageOcrToken) => (x.left + x.right) / 2;
const centerY = (x: ImageOcrToken) => (x.top + x.bottom) / 2;

export function parseImagePages(pages: ImageOcrPage[], firstMonday: string, requested: ImageImportKind = "AUTO"): ImageImportDraft {
  const term = pages.flatMap((p) => p.tokens).map((x) => x.text.match(semesterPattern)?.[0]?.replace(/\s/g, "")).find(Boolean) ?? "图片导入学期";
  const detected = pages.flatMap((p) => p.tokens).filter((x) => dayIndex(x.text)).length >= 5 ? "FULL_TERM_TABLE" : "WEEKLY_MOBILE";
  const kind = requested === "AUTO" ? detected : requested;
  const rows = pages.flatMap((p) => kind === "FULL_TERM_TABLE" ? parseFullPage(p) : parseWeeklyPage(p));
  const unique = [...new Map(rows.map((x) => [[x.weekExpression,x.weekday,x.startPeriod,x.endPeriod,x.title,x.teacher,x.location].join("|"), x])).values()];
  if (!unique.length) throw new Error(kind === "WEEKLY_MOBILE" ? "没有识别到课程卡片，请选择包含周次、星期和节次的完整截图" : "没有识别到完整课表，请确认图片包含星期表头、节次和课程周次");
  const capturedWeeks = [...new Set(unique.flatMap((x) => expandWeeks(x.weekExpression)))].sort((a,b) => a-b);
  return { kind, term, firstMonday, periods: defaultPeriods, rows: unique, capturedWeeks, warnings: [
    ...(kind === "WEEKLY_MOBILE" ? [`手机单周图片只生成已识别周次：${capturedWeeks.join("、")}`] : []),
    ...(unique.some((x) => x.confidence < .72) ? ["部分文字可信度较低，请逐项校对"] : []),
    ...(unique.some((x) => !x.location) ? ["部分课程没有识别到地点，可在校对页补充"] : []), "图片识别可能出现错字，保存前请核对课程、周次和节次",
  ] };
}

function parseFullPage(page: ImageOcrPage) {
  const headers = new Map<number, ImageOcrToken>();
  for (const token of page.tokens) { const day = dayIndex(token.text); if (day && (!headers.has(day) || token.top < headers.get(day)!.top)) headers.set(day, token); }
  if (headers.size < 5) throw new Error(`第 ${page.index + 1} 张图片缺少星期表头`);
  const xs = [...headers].map(([day,x]) => [day, centerX(x)] as [number,number]); const headerBottom = Math.max(...[...headers.values()].map((x) => x.bottom)); const anchors = periodAnchors(page, headerBottom);
  return page.tokens.flatMap((token): ImageImportRow[] => {
    const match = token.text.match(weekPattern); if (!match || token.top <= headerBottom || (!token.text.includes("周") && !/(每周|单周|双周)/.test(token.text))) return [];
    const weekday = nearestDay(centerX(token), xs, page.width); if (!weekday) return [];
    const [startPeriod,endPeriod] = periodRange(token, anchors); const details = courseDetails(token.text); if (!details[0]) return [];
    return [{ id: `image-${page.index}-${token.left}-${token.top}`, weekExpression: normalizeWeeks(match[0]), weekday, startPeriod, endPeriod, title: details[0], teacher: details[1], location: details[2], confidence: token.confidence, issues: [...(token.confidence < .72 ? ["文字可信度较低"] : []), ...(!details[2] ? ["地点未识别"] : [])] }];
  });
}
function parseWeeklyPage(page: ImageOcrPage) {
  const all = page.tokens.map((x) => x.text).join(" "); const week = Number(all.match(weekNumberPattern)?.[1]); if (!(week >= 1 && week <= 60)) throw new Error(`第 ${page.index + 1} 张图片没有识别到“第几周”`);
  let xs = page.tokens.flatMap((x) => { const day = shortDay(x.text); return day ? [[day,centerX(x)] as [number,number]] : []; });
  if (new Set(xs.map((x) => x[0])).size < 5) {
    const candidates = page.tokens.filter((x) => /^(0?[1-9]|[12][0-9]|3[01])$/.test(x.text) && x.top < page.height * .45).sort((a,b) => a.left-b.left);
    xs = candidates.slice(0,7).map((x,i) => [i+1,centerX(x)]);
  }
  if (xs.length < 5) throw new Error(`第 ${page.index + 1} 张图片没有识别到完整星期栏`);
  const headerBottom = Math.min(page.height * .4, Math.max(...page.tokens.filter((x) => shortDay(x.text)).map((x) => x.bottom), page.height * .18)); const anchors = periodAnchors(page, headerBottom);
  return page.tokens.flatMap((token): ImageImportRow[] => {
    const text = token.text.trim().replace(/^\/+|\/+$/g, ""); if (token.top <= headerBottom || text.length < 2 || /上一周|下一周|更多|学期|课表|返回|导入/.test(text) || /^\d{1,2}(?::\d{2})?$/.test(text)) return [];
    const weekday = nearestDay(centerX(token), xs, page.width); if (!weekday) return []; const [startPeriod,endPeriod] = periodRange(token, anchors);
    const parts = text.split("/").map((x) => x.trim()).filter(Boolean); const title = (parts[0] ?? "").replace(/^本[（(][^）)]*[）)]/, ""); if (title.length < 2) return [];
    const location = parts.slice(1).find(locationHint) ?? ""; const teacher = parts.slice(1).find((x) => x !== location) ?? "";
    return [{ id: `image-${page.index}-${token.left}-${token.top}`, weekExpression: `${week}周每周`, weekday, startPeriod, endPeriod, title, teacher, location, confidence: token.confidence, issues: [...(token.confidence < .72 ? ["文字可信度较低"] : []), ...(!location ? ["地点未识别"] : [])] }];
  });
}
function periodAnchors(page: ImageOcrPage, headerBottom: number): [number,number][] {
  const explicit = page.tokens.flatMap((x) => [...x.text.matchAll(/第?\s*([0-9]{1,2})\s*节/g)].map((m) => [Number(m[1]),centerY(x)] as [number,number])).filter((x) => x[0] >= 1 && x[0] <= 10);
  if (new Set(explicit.map((x) => x[0])).size >= 4) return explicit;
  const times = new Map(defaultPeriods.map((x) => [x.start,x.number])); const byTime = page.tokens.flatMap((x) => { const time=x.text.match(/(?:0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]/)?.[0]; return time && times.has(time) ? [[times.get(time)!,centerY(x)] as [number,number]] : []; });
  if (new Set(byTime.map((x) => x[0])).size >= 4) return byTime;
  const top = headerBottom + page.height * .025, height = Math.max(100,page.height-top); return defaultPeriods.map((x,i) => [x.number, top + height*(i+.5)/defaultPeriods.length]);
}
function periodRange(token: ImageOcrToken, anchors: [number,number][]): [number,number] { const h=Math.max(1,token.bottom-token.top); const a=anchors.reduce((p,c)=>Math.abs(c[1]-(token.top+h*.18))<Math.abs(p[1]-(token.top+h*.18))?c:p); const b=anchors.reduce((p,c)=>Math.abs(c[1]-(token.bottom-h*.18))<Math.abs(p[1]-(token.bottom-h*.18))?c:p); return [Math.min(a[0],b[0]),Math.max(a[0],b[0])]; }
function nearestDay(x: number, anchors: [number,number][], width: number) { const best=anchors.reduce((p,c)=>Math.abs(c[1]-x)<Math.abs(p[1]-x)?c:p); const sorted=[...anchors].sort((a,b)=>a[1]-b[1]); const tolerance=sorted.length>1?sorted.slice(1).reduce((n,c,i)=>n+c[1]-sorted[i][1],0)/(sorted.length-1)*.78:width/5; return Math.abs(best[1]-x)<=Math.max(tolerance,width*.07)?best[0]:undefined; }
function dayIndex(value: string) { const i=dayNames.findIndex((x)=>value.includes(x)); return i>=0?i+1:value.includes("星期天")?7:undefined; }
function shortDay(value: string) { const text=value.trim(); const map: Record<string,number>={一:1,二:2,三:3,四:4,五:5,六:6,日:7,天:7}; return map[text] ?? dayIndex(text); }
function locationHint(value: string) { return /楼|室|馆|场|校区|园|中心|教室|体育/.test(value); }
function normalize(value: string) { return value.replace(/[\n\r]/g,"/").replace(/／/g,"/").replace(/\s+/g," ").trim(); }
function normalizeWeeks(value: string) { const x=value.replace(/\s/g,"").replace(/[－—~～]/g,"-").replace(/[，、]/g,","); return x.includes("周")?x:x.replace(/(每周|单周|双周|单|双)$/, "周$1"); }
function courseDetails(text: string): [string,string,string] { const match=text.match(weekPattern); if(!match)return ["","",""]; const parts=text.slice((match.index??0)+match[0].length).replace(/^\/+/,"").split("/").map((x)=>x.trim()).filter(Boolean); const title=(parts[0]??"").replace(/^本[（(][^）)]*[）)]/,""); const location=parts.slice(1).find(locationHint)??parts[2]??""; const teacher=parts.slice(1).find((x)=>x!==location)??""; return [title,teacher,location]; }
function expandWeeks(value: string) { const clean=value.replace(/\s|周|每周/g,"").replace(/[－—~～]/g,"-").replace(/[，、]/g,","); const odd=/单/.test(value), even=/双/.test(value); return clean.replace(/[单双]/g,"").split(",").flatMap((part)=>{const [a,b]=part.split("-").map(Number); return Array.from({length:(b||a)-a+1},(_,i)=>a+i)}).filter((x)=>x>=1&&x<=60&&(!odd||x%2===1)&&(!even||x%2===0)); }
function translateStatus(value: string) { return value.includes("loading") ? "正在下载识别模型" : value.includes("recognizing") ? "正在识别图片文字" : "正在准备图片识别"; }

export function imageDraftToResult(draft: ImageImportDraft): Result {
  const raws: Raw[] = draft.rows.map((x) => ({ weekday:x.weekday,start:x.startPeriod,end:x.endPeriod,text:`${x.weekExpression}/${x.title}/${x.teacher}/${x.location}` }));
  const result=assemble(draft.term,draft.firstMonday,draft.periods,raws); return {...result,warnings:[...draft.warnings,...result.warnings]};
}
