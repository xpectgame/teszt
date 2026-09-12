import { chromium } from 'playwright'
import { resolve } from 'node:path'

// [fájl, kiválasztó, kimenet, szélesség, magasság]
const jobs = JSON.parse(process.argv[2])
const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome' })

for (const [src, selector, out, w, h] of jobs) {
  const page = await browser.newPage({ viewport: { width: w, height: h }, deviceScaleFactor: 1 })
  await page.goto('file://' + resolve(src))
  const el = await page.locator(selector)
  const box = await el.boundingBox()
  console.log(`${out}: az elem ${Math.round(box.width)}x${Math.round(box.height)}`)
  // Túlcsordulás-ellenőrzés: ha a tartalom kilóg, a kép szép lesz, de a szöveg levágva.
  const overflow = await el.evaluate((node) => {
    const r = []
    for (const child of node.querySelectorAll('*')) {
      if (child.scrollHeight > child.clientHeight + 1 && getComputedStyle(child).overflow !== 'visible') {
        r.push(child.className + ' ' + child.scrollHeight + '>' + child.clientHeight)
      }
    }
    return { pageOverflow: node.scrollHeight > node.clientHeight + 1, inner: r }
  })
  if (overflow.pageOverflow || overflow.inner.length) console.log('  FIGYELEM, túlcsordulás:', JSON.stringify(overflow))
  await el.screenshot({ path: out })
  await page.close()
}
await browser.close()
