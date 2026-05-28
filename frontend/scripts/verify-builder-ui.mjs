// End-to-end check for the custom-scenario builder UI.
//
// Drives the running app via puppeteer-core + the system Chrome:
//   1. Opens the "+ Create Scenario" modal.
//   2. Names the scenario and adds a child to the root.
//   3. Switches the child's builder to Async, ticks "Throws exception",
//      sets a timing.
//   4. Clicks "Generate & Play" and lets the timeline render.
//   5. Presses Space to play the scenario through to the end.
//   6. Asserts no page/console errors, the scenario name is visible,
//      and the canvas mounts with the rendered tree.
//
// Run from the frontend dir with the dev servers already running:
//
//   npm run verify:builder
//
// Configurable via env:
//   APP_URL     (default http://localhost:5173)
//   BACKEND_URL (default http://localhost:8080)
//   CHROME_PATH (default macOS Google Chrome)
//   SHOTS_DIR   (default /tmp/coroutines-verify/builder)
//
// Exits 0 on success; non-zero with a list of findings on failure.

import puppeteer from 'puppeteer-core';
import { mkdirSync } from 'node:fs';

const APP_URL = process.env.APP_URL ?? 'http://localhost:5173';
const BACKEND_URL = process.env.BACKEND_URL ?? 'http://localhost:8080';
const CHROME_PATH = process.env.CHROME_PATH ?? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const SHOTS_DIR = process.env.SHOTS_DIR ?? '/tmp/coroutines-verify/builder';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// Preflight: confirm the dev servers are reachable before opening Chrome.
async function preflight() {
  const failures = [];
  for (const url of [APP_URL, `${BACKEND_URL}/api/scenarios`]) {
    try {
      const res = await fetch(url, { signal: AbortSignal.timeout(3000) });
      if (!res.ok) failures.push(`${url} → HTTP ${res.status}`);
    } catch (e) {
      failures.push(`${url} → ${e.message}`);
    }
  }
  if (failures.length) {
    console.error('Preflight failed. Start the dev servers first:');
    console.error('  backend:  cd backend && ./gradlew run');
    console.error('  frontend: cd frontend && npm run dev');
    for (const f of failures) console.error('  • ' + f);
    process.exit(2);
  }
}

await preflight();
mkdirSync(SHOTS_DIR, { recursive: true });

const browser = await puppeteer.launch({
  executablePath: CHROME_PATH,
  headless: 'new',
  defaultViewport: { width: 1600, height: 1000 },
  args: ['--no-sandbox'],
});
const page = await browser.newPage();

const errs = [];
page.on('pageerror', (e) => errs.push('PAGE: ' + e.message));
page.on('console', (m) => { if (m.type() === 'error') errs.push('CONSOLE: ' + m.text()); });

const findings = [];
const shot = (name) => page.screenshot({ path: `${SHOTS_DIR}/${name}.png` });

await page.goto(APP_URL, { waitUntil: 'networkidle0', timeout: 30000 });
await page.waitForFunction(() => document.body.innerText.includes('Happy Path'), { timeout: 15000 });
await sleep(400);

// 1 — open the builder modal
console.log('1. opening builder');
errs.length = 0;
const opened = await page.evaluate(() => {
  const btn = Array.from(document.querySelectorAll('button'))
    .find((b) => b.textContent?.includes('Create Scenario'));
  if (!btn) return false;
  btn.click();
  return true;
});
if (!opened) findings.push('❌ "+ Create Scenario" button not found in sidebar');
await sleep(500);
if (errs.length) findings.push(`❌ open-builder errors: ${errs.slice(0, 2).join(' | ')}`);
await shot('01-opened');

// 2 — set the scenario name
console.log('2. setting scenario name');
await page.evaluate(() => {
  const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
  const inputs = Array.from(document.querySelectorAll('input'));
  // The scenario-name input is the first one shown, with the default "My Scenario".
  const name = inputs.find((i) => (i.value || '').includes('My Scenario')) ?? inputs[0];
  if (!name) return;
  setter.call(name, 'Probe: failing async');
  name.dispatchEvent(new Event('input', { bubbles: true }));
});
await sleep(300);

// 3 — add a child to the root
console.log('3. adding a child');
errs.length = 0;
const childAdded = await page.evaluate(() => {
  const btn = Array.from(document.querySelectorAll('button'))
    .find((b) => b.textContent?.trim().toLowerCase().includes('add child'));
  if (!btn) return false;
  btn.click();
  return true;
});
if (!childAdded) findings.push('❌ "Add child" button not found');
await sleep(400);
if (errs.length) findings.push(`❌ add-child errors: ${errs.slice(0, 2).join(' | ')}`);
await shot('02-child-added');

// 4 — switch the child's builder to Async
console.log('4. switching child to Async');
await page.evaluate(() => {
  const builderSelects = Array.from(document.querySelectorAll('select')).filter((s) =>
    Array.from(s.options).some((o) => o.value === 'Launch' || o.value === 'Async')
  );
  const childBuilder = builderSelects[1]; // [0] is root, [1] is first child
  if (!childBuilder) return;
  const setter = Object.getOwnPropertyDescriptor(window.HTMLSelectElement.prototype, 'value').set;
  setter.call(childBuilder, 'Async');
  childBuilder.dispatchEvent(new Event('change', { bubbles: true }));
});
await sleep(400);

// 5 — enable failure on the child
console.log('5. enabling failure on child');
const failureToggled = await page.evaluate(() => {
  const checkboxes = Array.from(document.querySelectorAll('input[type="checkbox"]'));
  if (checkboxes.length === 0) return false;
  const cb = checkboxes[checkboxes.length - 1]; // last checkbox = child's "Throws exception"
  if (!cb.checked) cb.click();
  return true;
});
if (!failureToggled) findings.push('❌ "Throws exception" checkbox not found');
await sleep(400);
await shot('03-failure-set');

// 6 — set the failure timing
console.log('6. setting failure timing');
await page.evaluate(() => {
  const numInputs = Array.from(document.querySelectorAll('input[type="number"]'));
  if (numInputs.length === 0) return;
  const target = numInputs[numInputs.length - 1];
  const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
  setter.call(target, '600');
  target.dispatchEvent(new Event('input', { bubbles: true }));
  target.dispatchEvent(new Event('change', { bubbles: true }));
});
await sleep(300);

// 7 — click "Generate & Play"
console.log('7. clicking Generate & Play');
errs.length = 0;
const generated = await page.evaluate(() => {
  const btn = Array.from(document.querySelectorAll('button'))
    .find((b) => b.textContent?.toLowerCase().includes('generate & play'));
  if (!btn) return false;
  btn.click();
  return true;
});
if (!generated) findings.push('❌ "Generate & Play" button not found');
await sleep(1500);
if (errs.length) findings.push(`❌ generate errors: ${errs.slice(0, 2).join(' | ')}`);
await shot('04-generated');

// 8 — confirm the new scenario is now active
console.log('8. verifying scenario loaded');
const loaded = await page.evaluate(() => ({
  nameVisible: document.body.innerText.includes('Probe: failing async'),
  hasCanvas: !!document.querySelector('canvas'),
  eventLogPresent: document.body.innerText.toUpperCase().includes('EVENT LOG'),
}));
if (!loaded.nameVisible) findings.push('❌ custom scenario name not visible after Generate');
if (!loaded.hasCanvas) findings.push('❌ no canvas after Generate');
if (!loaded.eventLogPresent) findings.push('❌ event log section not found');

// 9 — play the scenario through
console.log('9. playing the custom scenario');
errs.length = 0;
await page.keyboard.press('Space');
await sleep(2500);
await shot('05-midplay');
await sleep(3000);
await shot('06-end');
if (errs.length) findings.push(`❌ playback errors: ${errs.slice(0, 2).join(' | ')}`);

await browser.close();

console.log('\n=== findings ===');
if (findings.length === 0) {
  console.log('No issues. Screenshots at', SHOTS_DIR);
  process.exit(0);
} else {
  findings.forEach((f) => console.log(f));
  console.log('Screenshots at', SHOTS_DIR);
  process.exit(1);
}
