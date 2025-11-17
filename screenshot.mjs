#!/usr/bin/env node
import { chromium } from 'playwright';

const url = process.argv[2] || 'http://localhost:1971';
const outputPath = process.argv[3] || 'screenshot.png';

console.log(`📸 Taking screenshot of ${url}...`);

const browser = await chromium.launch();
const page = await browser.newPage({
  viewport: { width: 1280, height: 1024 }
});

await page.goto(url, { waitUntil: 'networkidle' });

// Wait a bit for any animations/renders to complete
await page.waitForTimeout(1000);

await page.screenshot({
  path: outputPath,
  fullPage: true
});

console.log(`✅ Screenshot saved to ${outputPath}`);
await browser.close();
