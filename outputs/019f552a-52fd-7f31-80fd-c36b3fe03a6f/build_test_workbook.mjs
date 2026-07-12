import fs from "node:fs/promises";
import path from "node:path";
import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const workDir = "G:/claudeproject/Agent Store/outputs/019f552a-52fd-7f31-80fd-c36b3fe03a6f";
const templatePath = "F:/软件测试表格模板包.xlsx";

const input = await FileBlob.load(templatePath);
const workbook = await SpreadsheetFile.importXlsx(input);

const overview = await workbook.inspect({
  kind: "workbook,sheet,table,drawing",
  maxChars: 12000,
  tableMaxRows: 8,
  tableMaxCols: 14,
  tableMaxCellChars: 120,
});
console.log(overview.ndjson);

const sheetInfo = await workbook.inspect({ kind: "sheet", include: "id,name", maxChars: 8000 });
console.log(sheetInfo.ndjson);

const previewDir = path.join(workDir, "template-previews");
await fs.mkdir(previewDir, { recursive: true });

for (let index = 0; index < workbook.worksheets.items.length; index += 1) {
  const sheet = workbook.worksheets.getItemAt(index);
  const usedRange = sheet.getUsedRange();
  const usedAddress = usedRange?.address ?? "A1:Z40";
  const region = await workbook.inspect({
    kind: "region,computedStyle,formula",
    sheetId: sheet.name,
    range: usedAddress,
    maxChars: 5000,
    options: { maxResults: 80 },
  });
  console.log(`SHEET_DETAIL ${sheet.name} ${usedAddress}`);
  console.log(region.ndjson);

  const rendered = await workbook.render({
    sheetName: sheet.name,
    autoCrop: "all",
    scale: 1,
    format: "png",
  });
  const safeName = `${String(index + 1).padStart(2, "0")}-${sheet.name.replace(/[\\/:*?"<>|]/g, "_")}.png`;
  await fs.writeFile(path.join(previewDir, safeName), new Uint8Array(await rendered.arrayBuffer()));
}
