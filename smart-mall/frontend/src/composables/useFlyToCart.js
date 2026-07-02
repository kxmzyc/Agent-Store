export function flyToCart(sourceEl, options = {}) {
  const cartLink = document.querySelector('[data-cart-target]')
  if (!cartLink) return null

  const originRect = options.origin?.getBoundingClientRect?.()
  const sourceRect = sourceEl?.getBoundingClientRect?.() || originRect
  const targetRect = cartLink.getBoundingClientRect()
  if (!sourceRect) {
    cartLink.classList.add('cart-target-pop')
    window.setTimeout(() => cartLink.classList.remove('cart-target-pop'), 780)
    return null
  }

  const startSize = sourceEl ? Math.min(Math.max(sourceRect.width, 72), 130) : 34
  const clone = document.createElement('div')
  clone.className = sourceEl ? 'fly-cart-image' : 'fly-cart-image fly-cart-dot'
  if (sourceEl?.src) {
    clone.style.backgroundImage = `url("${sourceEl.src}")`
  } else {
    clone.innerHTML = '<span>+</span>'
  }
  const startLeft = sourceRect.left + sourceRect.width / 2 - startSize / 2
  const startTop = sourceRect.top + sourceRect.height / 2 - startSize / 2
  const targetX = targetRect.left + targetRect.width / 2 - sourceRect.left - sourceRect.width / 2
  const targetY = targetRect.top + targetRect.height / 2 - sourceRect.top - sourceRect.height / 2
  clone.style.left = `${startLeft}px`
  clone.style.top = `${startTop}px`
  clone.style.width = `${startSize}px`
  clone.style.height = `${startSize}px`
  document.body.appendChild(clone)

  sourceEl?.closest('.product-card, .detail-layout, .favorites-page')?.classList.add('cart-added-pulse')
  cartLink.classList.add('cart-target-pop')
  const animation = clone.animate([
    { transform: 'translate3d(0, 0, 0) scale(1) rotate(0deg)', opacity: 1, filter: 'saturate(1)' },
    { transform: `translate3d(${targetX * 0.26}px, ${targetY * 0.08 - 84}px, 0) scale(0.92) rotate(-7deg)`, opacity: 1, filter: 'saturate(1.15)', offset: 0.25 },
    { transform: `translate3d(${targetX * 0.7}px, ${targetY * 0.56 - 38}px, 0) scale(0.48) rotate(10deg)`, opacity: 0.96, filter: 'saturate(1.08)', offset: 0.68 },
    { transform: `translate3d(${targetX}px, ${targetY}px, 0) scale(0.1) rotate(18deg)`, opacity: 0, filter: 'saturate(1)' }
  ], {
    duration: 1180,
    easing: 'cubic-bezier(0.16, 1, 0.3, 1)',
    fill: 'forwards'
  })

  animation.finished.finally(() => clone.remove())
  window.setTimeout(() => cartLink.classList.remove('cart-target-pop'), 980)
  window.setTimeout(() => sourceEl?.closest('.product-card, .detail-layout, .favorites-page')?.classList.remove('cart-added-pulse'), 760)
  return animation
}
