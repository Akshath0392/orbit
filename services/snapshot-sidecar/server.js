// Orbit snapshot sidecar — Playwright + Express.
// Single endpoint: POST /render { targetUrl, jwt, viewport:{w,h}, formats:[png,pdf],
//                                  waitForSelector, timeoutMs }
//   → 200 { png: base64, pdf: base64, renderMs }
//
// The contract with the page: the frontend sets data-snapshot-ready="true" on the
// outermost div once data is loaded (see RadarPage.tsx). We never capture before
// that selector resolves.

import express from 'express'
import { chromium } from 'playwright'

const PORT = parseInt(process.env.PORT || '3001', 10)
const MAX_CONCURRENT = parseInt(process.env.MAX_CONCURRENT || '2', 10)
const DEFAULT_TIMEOUT = parseInt(process.env.DEFAULT_TIMEOUT_MS || '20000', 10)
const DEFAULT_VIEWPORT_W = parseInt(process.env.VIEWPORT_W || '1440', 10)
const DEFAULT_VIEWPORT_H = parseInt(process.env.VIEWPORT_H || '900',  10)

// Security (audit H4):
// - SHARED_SECRET: required header from the backend; blocks unauthenticated callers.
// - ALLOWED_TARGET_ORIGINS: allowlist of origins page.goto() may navigate to (anti-SSRF).
// - NO_SANDBOX: the Chromium sandbox is ON by default; only disabled when explicitly
//   requested (e.g. a root container that cannot use it).
const SHARED_SECRET = process.env.SNAPSHOT_SHARED_SECRET || ''
const ALLOWED_TARGET_ORIGINS = (process.env.ALLOWED_TARGET_ORIGINS || '')
  .split(',').map(s => s.trim()).filter(Boolean)
const NO_SANDBOX = /^(1|true|yes)$/i.test(process.env.CHROMIUM_NO_SANDBOX || '')

if (!SHARED_SECRET) console.warn('[security] SNAPSHOT_SHARED_SECRET is unset — /render is not authenticated. Set it in production.')
if (!ALLOWED_TARGET_ORIGINS.length) console.warn('[security] ALLOWED_TARGET_ORIGINS is unset — targetUrl origin is not restricted. Set it in production.')

const app = express()
app.use(express.json({ limit: '256kb' }))

// shared browser, one context per render so cookies don't leak between requests
let browserPromise = null
function getBrowser() {
  if (!browserPromise) {
    const args = ['--disable-dev-shm-usage']
    if (NO_SANDBOX) args.push('--no-sandbox')
    browserPromise = chromium.launch({ args })
  }
  return browserPromise
}

// Constant-time-ish comparison for the shared secret.
function secretMatches(provided) {
  if (!SHARED_SECRET) return true            // not configured — allow (dev), warned at boot
  if (typeof provided !== 'string' || provided.length !== SHARED_SECRET.length) return false
  let diff = 0
  for (let i = 0; i < provided.length; i++) diff |= provided.charCodeAt(i) ^ SHARED_SECRET.charCodeAt(i)
  return diff === 0
}

// Reject any targetUrl whose origin is not on the allowlist (anti-SSRF).
function targetOriginAllowed(targetUrl) {
  if (!ALLOWED_TARGET_ORIGINS.length) return true   // not configured — allow (dev), warned at boot
  try {
    const origin = new URL(targetUrl).origin
    return ALLOWED_TARGET_ORIGINS.includes(origin)
  } catch {
    return false
  }
}

let active = 0
const waiters = []
async function acquire() {
  if (active < MAX_CONCURRENT) { active++; return }
  await new Promise(r => waiters.push(r))
  active++
}
function release() {
  active--
  const next = waiters.shift()
  if (next) next()
}

app.get('/healthz', (_req, res) => res.json({ ok: true, active }))

app.post('/render', async (req, res) => {
  const started = Date.now()

  if (!secretMatches(req.get('X-Snapshot-Secret'))) {
    return res.status(401).json({ error: 'unauthorized' })
  }

  const {
    targetUrl,
    jwt,
    viewport = { w: DEFAULT_VIEWPORT_W, h: DEFAULT_VIEWPORT_H },
    formats = ['png', 'pdf'],
    waitForSelector = '[data-snapshot-ready="true"]',
    timeoutMs = DEFAULT_TIMEOUT,
  } = req.body || {}

  if (!targetUrl || typeof targetUrl !== 'string') {
    return res.status(400).json({ error: 'targetUrl required' })
  }
  if (!targetOriginAllowed(targetUrl)) {
    return res.status(403).json({ error: 'targetUrl origin not allowed' })
  }
  if (!jwt || typeof jwt !== 'string') {
    return res.status(400).json({ error: 'jwt required' })
  }

  // The frontend (api/client) reads the JWT from `?token=` when `?snapshot=1` is set,
  // so we just append it to the URL — no localStorage seeding, no cookies, no reload.
  const url = appendQuery(targetUrl, { token: jwt })

  await acquire()
  let context = null
  let page = null
  // Diagnostics buffer — surfaced when waitForSelector times out so the user
  // can see why the page never reached data-snapshot-ready (auth failure,
  // API 404, JS error, etc.) instead of just "Timeout 20000ms exceeded".
  const consoleMsgs = []
  const pageErrors = []
  const failedRequests = []
  const allRequests = []
  try {
    const browser = await getBrowser()
    context = await browser.newContext({
      viewport: { width: viewport.w, height: viewport.h },
      deviceScaleFactor: 2,
    })
    page = await context.newPage()
    page.on('console', m => {
      const t = m.type()
      if (t === 'error' || t === 'warning') consoleMsgs.push(`[${t}] ${m.text()}`)
    })
    page.on('pageerror', e => pageErrors.push(String(e.message || e)))
    page.on('requestfailed', r => failedRequests.push(`${r.method()} ${r.url()} — ${r.failure()?.errorText}`))
    page.on('response', r => {
      const s = r.status()
      const u = r.url()
      // Track every API call we make to the backend (filter out HMR / static asset noise)
      if (u.includes('/api/')) allRequests.push(`${r.request().method()} ${u} → HTTP ${s}`)
      if (s >= 400) failedRequests.push(`${r.request().method()} ${u} → HTTP ${s}`)
    })

    console.log(`[render] navigating: ${url.replace(/token=[^&]+/, 'token=…')}`)
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: timeoutMs })
    await page.waitForSelector(waitForSelector, { timeout: timeoutMs })

    const out = { renderMs: 0 }
    if (formats.includes('png')) {
      const png = await page.screenshot({ fullPage: true, type: 'png' })
      out.png = png.toString('base64')
    }
    if (formats.includes('pdf')) {
      const pdf = await page.pdf({
        printBackground: true,
        format: 'A3',
        landscape: true,
        margin: { top: '12mm', bottom: '12mm', left: '10mm', right: '10mm' },
      })
      out.pdf = pdf.toString('base64')
    }
    out.renderMs = Date.now() - started
    res.json(out)
  } catch (err) {
    console.error('render failed:', err.message)
    if (consoleMsgs.length) console.error('  page console:', consoleMsgs.slice(0, 10))
    if (pageErrors.length) console.error('  page errors:', pageErrors.slice(0, 5))
    if (failedRequests.length) console.error('  failed requests:', failedRequests.slice(0, 20))
    if (allRequests.length) console.error('  api calls (all):', allRequests.slice(0, 30))
    let debugScreenshot = null
    let pageProbe = null
    try {
      if (page) debugScreenshot = (await page.screenshot({ fullPage: false, type: 'png' })).toString('base64')
    } catch {}
    try {
      // Probe the page from the inside: does the outermost div carry the ready
      // attribute? What's the document title? Which root-level data attrs exist?
      // This tells us whether snapshot-mode JS ran at all and whether the gate
      // condition was ever satisfied.
      if (page) pageProbe = await page.evaluate(() => {
        const root = document.querySelector('[data-snapshot-ready]')
        const allReady = Array.from(document.querySelectorAll('[data-snapshot-ready]'))
          .map(el => el.getAttribute('data-snapshot-ready'))
        return {
          title: document.title,
          url: window.location.href,
          hasReadyAttr: !!root,
          readyAttrValue: root ? root.getAttribute('data-snapshot-ready') : null,
          allReadyValues: allReady,
          bodyTextLen: document.body ? document.body.innerText.length : 0,
          bodyTextSample: document.body ? document.body.innerText.slice(0, 200) : null,
          localStorageKeys: Object.keys(window.localStorage || {}),
          hasOrbitSession: !!window.localStorage?.getItem('orbit-session'),
        }
      })
    } catch (e) { pageProbe = { error: String(e.message || e) } }
    res.status(500).json({
      error: err.message,
      diagnostics: {
        url: url.replace(/token=[^&]+/, 'token=…'),
        consoleMsgs: consoleMsgs.slice(0, 20),
        pageErrors: pageErrors.slice(0, 10),
        failedRequests: failedRequests.slice(0, 30),
        allApiRequests: allRequests.slice(0, 30),
        pageProbe,
        debugScreenshotPng: debugScreenshot,   // base64; render whatever the page looked like at timeout
      }
    })
  } finally {
    if (context) await context.close().catch(() => {})
    release()
  }
})

function appendQuery(targetUrl, extra) {
  const u = new URL(targetUrl)
  for (const [k, v] of Object.entries(extra)) u.searchParams.set(k, v)
  return u.toString()
}

process.on('SIGTERM', async () => {
  try { const b = await browserPromise; if (b) await b.close() } catch {}
  process.exit(0)
})

app.listen(PORT, () => {
  console.log(`snapshot-sidecar listening on :${PORT} (maxConcurrent=${MAX_CONCURRENT})`)
})
