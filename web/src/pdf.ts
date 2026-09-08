import { getDocument, GlobalWorkerOptions, OPS, Util } from "pdfjs-dist";
import workerUrl from "pdfjs-dist/build/pdf.worker.min.mjs?url";
import { parseGeometry, type Page, type Line } from "./pdf-geometry";
import { fail } from "./model";
GlobalWorkerOptions.workerSrc = workerUrl;
export async function readPdf(data: Uint8Array) {
  const task = getDocument({
    data,
    cMapUrl: import.meta.env.BASE_URL + "cmaps/",
    cMapPacked: true,
    standardFontDataUrl: import.meta.env.BASE_URL + "standard_fonts/",
    isEvalSupported: false,
  });
  try {
    const pdf = await task.promise;
    if (pdf.numPages > 40) fail("PDF 超过 40 页");
    const pages: Page[] = [];
    for (let n = 1; n <= pdf.numPages; n++) {
      const p = await pdf.getPage(n);
      if (p.rotate) fail("暂不支持旋转的 PDF");
      const viewport = p.getViewport({ scale: 1 }),
        content = await p.getTextContent(),
        glyphs: Page["glyphs"] = [];
      for (const it of content.items) {
        if (!("str" in it)) continue;
        const tr = Util.transform(viewport.transform, it.transform);
        const chars = [...it.str];
        chars.forEach((text, i) =>
          glyphs.push({
            text,
            x: tr[4] + (it.width * i) / Math.max(1, chars.length),
            y: tr[5] - Math.abs(tr[3]) * 0.25,
          }),
        );
      }
      if (glyphs.length > 100000) fail("PDF 文字过多");
      const ops = await p.getOperatorList(),
        lines: Line[] = [];
      let pending: Line[] = [];
      let matrix = [1, 0, 0, 1, 0, 0],
        stack: number[][] = [];
      const point = (x: number, y: number) =>
        Util.applyTransform(
          Util.applyTransform([x, y], matrix),
          viewport.transform,
        );
      for (let i = 0; i < ops.fnArray.length; i++) {
        const op = ops.fnArray[i],
          args = ops.argsArray[i];
        if (
          [
            OPS.stroke,
            OPS.closeStroke,
            OPS.fillStroke,
            OPS.eoFillStroke,
            OPS.closeFillStroke,
            OPS.closeEOFillStroke,
          ].includes(op)
        ) {
          lines.push(...pending);
          pending = [];
        } else if ([OPS.endPath, OPS.fill, OPS.eoFill].includes(op)) {
          pending = [];
        } else if (op === OPS.save) stack.push([...matrix]);
        else if (op === OPS.restore) matrix = stack.pop() || [1, 0, 0, 1, 0, 0];
        else if (op === OPS.transform) matrix = Util.transform(matrix, args);
        else if (op === OPS.constructPath) {
          const [cmds, coords] = args;
          let k = 0,
            x = 0,
            y = 0,
            sx = 0,
            sy = 0;
          const line = (a: number, b: number, c: number, d: number) => {
            const s = point(a, b),
              e = point(c, d);
            pending.push({ x0: s[0], y0: s[1], x1: e[0], y1: e[1] });
          };
          for (const cmd of cmds) {
            if (cmd === OPS.moveTo) {
              x = coords[k++];
              y = coords[k++];
              sx = x;
              sy = y;
            } else if (cmd === OPS.lineTo) {
              const nx = coords[k++],
                ny = coords[k++];
              line(x, y, nx, ny);
              x = nx;
              y = ny;
            } else if (cmd === OPS.rectangle) {
              const a = coords[k++],
                b = coords[k++],
                w = coords[k++],
                h = coords[k++];
              line(a, b, a + w, b);
              line(a + w, b, a + w, b + h);
              line(a + w, b + h, a, b + h);
              line(a, b + h, a, b);
            } else if (cmd === OPS.closePath) {
              line(x, y, sx, sy);
              x = sx;
              y = sy;
            } else if (cmd === OPS.curveTo) {
              k += 6;
            } else if (cmd === OPS.curveTo2 || cmd === OPS.curveTo3) {
              k += 4;
            }
          }
        }
      }
      pages.push({ glyphs, lines });
    }
    return pages;
  } finally {
    await task.destroy();
  }
}

export async function parsePdf(data: Uint8Array, date: string) {
  return parseGeometry(await readPdf(data), date);
}
