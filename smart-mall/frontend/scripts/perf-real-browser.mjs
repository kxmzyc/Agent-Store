import { chromium } from 'playwright'

const CDP_URL = process.env.CDP_URL || 'http://localhost:9222'
const PAGE_URL = process.env.PAGE_URL || 'http://127.0.0.1:4174/'
const RUNS_PER_SCENARIO = 3
const FX_MATRIX = process.env.FX_MATRIX === '1'
const FX_ONLY = process.env.FX_ONLY || ''
const FX_OFF = process.env.FX_OFF || ''

// Diagnostic-only effect list. Keep in sync with src/dev/fxDiagnostics.js.
const KNOWN_FX = [
  'nav-hover',
  'nav-active-underline',
  'route-fade',
  'search-focus',
  'scroll-inertia',
  'hero-enter',
  'button-hover',
  'button-active',
  'select-focus',
  'form-focus',
  'product-list',
  'card-hover',
  'card-shine',
  'image-shimmer',
  'image-load',
  'image-hover-zoom',
  'badge-hover',
  'chip-hover',
  'chip-active',
  'swap-controls',
  'add-cart-hover',
  'cart-fly',
  'cart-burst',
  'cart-pulse',
  'table-row-hover',
  'admin-card-hover',
  'cart-item-hover',
  'cart-selection',
  'cart-item-state',
  'checkout-meter',
  'cart-list',
  'amount-pulse',
  'qty-bump',
  'order-card-hover',
  'timeline-state',
  'preference-hover',
  'chat-fab',
  'chat-panel',
  'typing-indicator',
  'bubble-in',
  'quick-action-hover',
  'toast'
]

const SCENARIO_BY_FX = {
  'scroll-inertia': 'scroll',
  'hero-enter': 'load',
  'route-fade': 'route',
  'product-list': 'category',
  'chip-hover': 'category',
  'chip-active': 'category',
  'card-hover': 'hover',
  'card-shine': 'hover',
  'image-shimmer': 'load',
  'image-load': 'load',
  'image-hover-zoom': 'hover',
  'badge-hover': 'hover',
  'add-cart-hover': 'hover',
  'cart-fly': 'add-cart',
  'cart-burst': 'add-cart',
  'cart-pulse': 'add-cart',
  'search-focus': 'search-focus',
  'select-focus': 'search-focus',
  'button-hover': 'hover',
  'button-active': 'hover',
  'nav-hover': 'nav-hover',
  'nav-active-underline': 'route',
  'toast': 'add-cart',
  'chat-fab': 'chat-open',
  'chat-panel': 'chat-open',
  'typing-indicator': 'chat-open',
  'bubble-in': 'chat-open',
  'quick-action-hover': 'chat-open',
  'cart-list': 'cart',
  'cart-item-hover': 'cart',
  'cart-selection': 'cart',
  'cart-item-state': 'cart',
  'checkout-meter': 'cart',
  'amount-pulse': 'cart',
  'qty-bump': 'cart',
  'swap-controls': 'cart',
  'order-card-hover': 'orders',
  'timeline-state': 'order-detail',
  'admin-card-hover': 'admin',
  'table-row-hover': 'admin',
  'form-focus': 'profile',
  'preference-hover': 'profile'
}

function withFxQuery(rawUrl, fx) {
  const url = new URL(rawUrl)
  if (fx.off) url.searchParams.set('fx-off', fx.off)
  if (fx.only) url.searchParams.set('fx-only', fx.only)
  return url.toString()
}

async function runScenario(page, name, action) {
  const runs = []
  for (let i = 0; i < RUNS_PER_SCENARIO; i += 1) {
    await page.evaluate(() => window.__perfReset && window.__perfReset())
    await action(page)
    await page.waitForTimeout(150)
    const report = await page.evaluate(() => window.__perfReport())
    runs.push(report)
  }
  return { scenario: name, runs }
}

async function goto(page, path = '/', fx = {}) {
  const url = new URL(PAGE_URL)
  url.pathname = path
  url.search = ''
  await page.goto(withFxQuery(url.toString(), fx), { waitUntil: 'networkidle' })
  await page.waitForTimeout(500)
}

async function hoverVisibleCards(page) {
  const cards = await page.$$('.product-card')
  for (const card of cards.slice(0, 12)) {
    await card.hover()
    await page.waitForTimeout(120)
  }
}

async function switchCategory(page) {
  const chips = await page.$$('.chip')
  if (chips[1]) {
    await chips[1].click()
  }
  await page.waitForTimeout(600)
}

async function focusSearch(page) {
  const input = await page.$('.catalog-search input')
  if (input) await input.focus()
  const select = await page.$('.catalog-search select')
  if (select) await select.focus()
  await page.waitForTimeout(350)
}

async function hoverNav(page) {
  const links = await page.$$('.topnav a')
  for (const link of links.slice(0, 5)) {
    await link.hover()
    await page.waitForTimeout(120)
  }
}

async function routeOnce(page) {
  const orders = await page.$('.topnav a[href="/orders"]')
  if (orders) await orders.click()
  await page.waitForTimeout(350)
  const home = await page.$('.topnav a[href="/"]')
  if (home) await home.click()
  await page.waitForTimeout(350)
}

async function openChat(page) {
  const fab = await page.$('.chat-fab')
  if (fab) await fab.click()
  await page.waitForTimeout(450)
  const quick = await page.$$('.chat-quick-actions button')
  if (quick[0]) await quick[0].hover()
  await page.waitForTimeout(250)
}

async function runCartScenario(page) {
  await goto(page, '/cart', currentFx)
  const cards = await page.$$('.cart-item-card')
  if (cards[0]) await cards[0].hover()
  const qty = await page.$('.cart-qty button:not([disabled])')
  if (qty) await qty.click()
  await page.waitForTimeout(500)
}

async function runProfileScenario(page) {
  await goto(page, '/profile', currentFx)
  const input = await page.$('.form input:not([disabled])')
  if (input) await input.focus()
  const pref = await page.$('.preference-chip button')
  if (pref) await pref.hover()
  await page.waitForTimeout(350)
}

async function runOrdersScenario(page) {
  await goto(page, '/orders', currentFx)
  const card = await page.$('.order-card')
  if (card) await card.hover()
  await page.waitForTimeout(350)
}

async function runAdminScenario(page) {
  await goto(page, '/admin', currentFx)
  const row = await page.$('.table tbody tr')
  if (row) await row.hover()
  const card = await page.$('.admin-order-card')
  if (card) await card.hover()
  await page.waitForTimeout(350)
}

async function runOrderDetailScenario(page) {
  await goto(page, '/orders/1', currentFx)
  await page.waitForTimeout(350)
}

const scenarioActions = {
  load: async () => {},
  scroll: async (p) => {
    await p.evaluate(() => window.scrollTo(0, 0))
    await p.waitForTimeout(100)
    for (let i = 0; i < 24; i += 1) {
      await p.mouse.wheel(0, 140)
      await p.waitForTimeout(45)
    }
  },
  hover: hoverVisibleCards,
  category: switchCategory,
  'add-cart': addFirstAvailableProduct,
  'search-focus': focusSearch,
  'nav-hover': hoverNav,
  route: routeOnce,
  'chat-open': openChat,
  cart: runCartScenario,
  profile: runProfileScenario,
  orders: runOrdersScenario,
  admin: runAdminScenario,
  'order-detail': runOrderDetailScenario
}

let currentFx = {}

async function addFirstAvailableProduct(page) {
  const buttons = await page.$$('.add-to-cart-btn:not([disabled])')
  if (buttons[0]) {
    await buttons[0].click()
  }
  await page.waitForTimeout(600)
}

async function main() {
  const browser = await chromium.connectOverCDP(CDP_URL)
  const context = browser.contexts()[0] || await browser.newContext()
  const page = context.pages()[0] || await context.newPage()
  currentFx = FX_ONLY ? { only: FX_ONLY } : FX_OFF ? { off: FX_OFF } : {}
  await goto(page, '/', currentFx)

  const hasPerfHud = await page.evaluate(() => Boolean(window.__perfReport && window.__perfReset))
  if (!hasPerfHud) {
    throw new Error('Perf HUD is not available. Rebuild with VITE_PERF_HUD=1 and reload the page.')
  }

  const results = []

  if (FX_MATRIX) {
    for (const scenarioName of [...new Set(Object.values(SCENARIO_BY_FX))]) {
      currentFx = { off: 'all' }
      await goto(page, '/', currentFx)
      results.push({
        effect: 'baseline-all-off',
        ...(await runScenario(page, scenarioName, scenarioActions[scenarioName]))
      })
    }

    for (const effect of KNOWN_FX) {
      const scenarioName = SCENARIO_BY_FX[effect] || 'load'
      currentFx = { only: effect }
      await goto(page, '/', currentFx)
      results.push({
        effect,
        ...(await runScenario(page, scenarioName, scenarioActions[scenarioName]))
      })
    }
  } else {
    results.push(await runScenario(page, 'scroll', scenarioActions.scroll))
    results.push(await runScenario(page, 'hover', hoverVisibleCards))
    results.push(await runScenario(page, 'category', switchCategory))
    results.push(await runScenario(page, 'add-cart', addFirstAvailableProduct))
  }

  console.log(JSON.stringify(results, null, 2))
  await browser.close()
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
