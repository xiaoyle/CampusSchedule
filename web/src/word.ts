import { unzipSync } from "fflate";
import { parseTable, fail, term, type Cell, type Result } from "./model";
const ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
export function parseWord(bytes: Uint8Array, date: string): Result {
  if (bytes.length > 8 * 1024 * 1024) fail("文件超过 8 MB");
  let data = bytes;
  if (bytes[0] === 80 && bytes[1] === 75) {
    data = unzipSync(bytes, {
      filter: (e) => {
        if (e.name === "word/document.xml" && e.originalSize > 8 * 1024 * 1024)
          fail("解压后的文件过大");
        return e.name === "word/document.xml";
      },
    })["word/document.xml"];
    if (!data) fail("未找到 Word 文档");
  }
  const xml = new TextDecoder().decode(data);
  if (/<!DOCTYPE|<!ENTITY/i.test(xml)) fail("不支持含实体声明的文件");
  const doc = new DOMParser().parseFromString(xml, "application/xml");
  if (doc.querySelector("parsererror"))
    fail("无法读取 Word；请选择学校导出的原始文件");
  const children = (e: Element, n: string) =>
    [...e.children].filter((c) => c.localName === n && c.namespaceURI === ns);
  const txt = (e: Element) =>
    [...e.getElementsByTagNameNS(ns, "t")]
      .map((t) => t.textContent)
      .join("")
      .trim();
  const table = [...doc.getElementsByTagNameNS(ns, "tbl")].find((t) =>
    txt(children(t, "tr")[0] || t).includes("星期一"),
  );
  if (!table) fail("没有找到完整课表");
  const rows: Cell[][] = [];
  let prev = new Map<number, Cell>();
  for (const row of children(table!, "tr")) {
    const out: Cell[] = [];
    let col = 0;
    const current = new Map<number, Cell>();
    for (const c of children(row, "tc")) {
      const props = children(c, "tcPr").flatMap((p) => [...p.children]);
      const prop = (n: string) => props.filter((p) => p.localName === n).at(-1);
      const span = +(prop("gridSpan")?.getAttributeNS(ns, "val") || 1);
      if (!Number.isInteger(span) || span < 1 || span > 256)
        fail("单元格跨度异常");
      const merge = prop("vMerge");
      if (merge && merge.getAttributeNS(ns, "val") !== "restart") {
        const old = prev.get(col);
        if (!old) fail("纵向合并单元格异常");
        old.rowSpan++;
        for (let j = col; j < col + span; j++) current.set(j, old);
      } else {
        const cell = { text: txt(c), rowSpan: 1, colSpan: span };
        out.push(cell);
        if (merge) for (let j = col; j < col + span; j++) current.set(j, cell);
      }
      col += span;
    }
    rows.push(out);
    prev = current;
  }
  const body = doc.getElementsByTagNameNS(ns, "body")[0];
  return parseTable(
    {
      title: term(body ? children(body, "p").map(txt).join("") : ""),
      allWeeks: true,
      rows,
    },
    date,
  );
}
