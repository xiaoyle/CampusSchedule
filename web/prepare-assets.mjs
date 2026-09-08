import { cpSync } from "node:fs";
for (const name of ["cmaps", "standard_fonts"])
  cpSync("node_modules/pdfjs-dist/" + name, "public/" + name, {
    recursive: true,
  });
