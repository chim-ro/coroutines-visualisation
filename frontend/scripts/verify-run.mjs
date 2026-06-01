// Quick post-quiz-removal smoke check: load the app, play a scenario,
// confirm the canvas renders, no Quiz control remains, and no console errors.
import puppeteer from 'puppeteer-core';
import { mkdirSync } from 'node:fs';

const APP_URL = process.env.APP_URL ?? 'http://localhost:5173';
const CHROME_PATH = process.env.CHROME_PATH ?? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const SHOTS_DIR = process.env.SHOTS_DIR ?? '/tmp/coroutines-verify/run';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

mkdirSync(SHOTS_DIR, { recursive: true });
const findings = [];
const consoleErrors = [];

const browser = await puppeteer.launch({ executablePath: CHROME_PATH, headless: 'new', args: ['--no-sandbox'] });
const page = await browser.newPage();
await page.setViewport({ width: 1440, height: 900 });
page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()); });
page.on('pageerror', (e) => consoleErrors.push(String(e)));

await page.goto(APP_URL, { waitUntil: 'networkidle0' });
await sleep(500);

// Click the first scenario in the sidebar.
const clicked = await page.evaluate(() => {
  const el = [...document.querySelectorAll('*')].find((n) => n.textContent?.trim() === 'Happy Path' && n.children.length === 0);
  if (el) { el.closest('div,button,li')?.click?.() ?? el.click(); return true; }
  return false;
});
if (!clicked) findings.push('Could not find "Happy Path" scenario in sidebar');
await sleep(800);

// Canvas present?
const hasCanvas = await page.evaluate(() => !!document.querySelector('canvas'));
if (!hasCanvas) findings.push('No <canvas> mounted after loading scenario');

// Play via Space, let it animate.
await page.keyboard.press('Space');
await sleep(2500);
await page.screenshot({ path: `${SHOTS_DIR}/playing.png` });

// Quiz must be gone.
const hasQuiz = await page.evaluate(() => document.body.innerText.toLowerCase().includes('quiz'));
if (hasQuiz) findings.push('Found "Quiz" text in the UI — should have been removed');

// Compare button should still be present (scenario loaded).
const hasCompare = await page.evaluate(() => document.body.innerText.includes('Compare'));
if (!hasCompare) findings.push('Compare button missing with a scenario loaded');

await browser.close();

if (consoleErrors.length) findings.push(`Console errors: ${consoleErrors.join(' | ')}`);

if (findings.length) {
  console.error('FAIL:\n - ' + findings.join('\n - '));
  process.exit(1);
}
console.log(`PASS — canvas rendered, no Quiz control, Compare present, no console errors. Screenshot: ${SHOTS_DIR}/playing.png`);
