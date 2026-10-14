// File: research/spikes/S2-pdf-extraction/scripts/print.js
// Purpose: Render S2 HTML sources to PDF with Chromium (Skia PDF backend), with running header + page-number footer.
const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const path = require('path');
(async () => {
  const [, , outDir, ...files] = process.argv;
  const browser = await chromium.launch();
  for (const f of files) {
    const page = await browser.newPage();
    await page.goto('file://' + path.resolve(f), { waitUntil: 'load', timeout: 0 });
    const name = path.basename(f, '.html');
    await page.pdf({
      path: path.join(outDir, name + '.pdf'), format: 'A4', printBackground: false,
      displayHeaderFooter: true,
      headerTemplate: `<div style="font-size:8px;width:100%;text-align:center">Audora S2 test corpus - ${name}</div>`,
      footerTemplate: '<div style="font-size:9px;width:100%;text-align:center"><span class="pageNumber"></span></div>',
      margin: { top: '22mm', bottom: '22mm', left: '18mm', right: '18mm' },
    });
    console.log('printed', name);
    await page.close();
  }
  await browser.close();
})();
