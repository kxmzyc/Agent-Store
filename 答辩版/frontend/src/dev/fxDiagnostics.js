// Diagnostic-only effect isolation switches. Loaded only in dev or VITE_PERF_HUD builds.
export const knownFx = [
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

function parseFxList(value) {
  return String(value || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

export function mountFxDiagnostics() {
  const params = new URLSearchParams(window.location.search)
  const fxOff = parseFxList(params.get('fx-off'))
  const fxOnly = parseFxList(params.get('fx-only'))
  const off = new Set(fxOff.includes('all') || fxOnly.length ? knownFx : fxOff)

  for (const name of fxOnly) off.delete(name)
  for (const name of off) document.documentElement.classList.add(`fx-off-${name}`)

  window.__fxDiagnostics = {
    knownFx,
    off: [...off],
    only: fxOnly
  }
}
