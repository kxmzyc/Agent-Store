export function flyToCart(event, options = {}) {
  const cartLink = document.querySelector('[data-cart-target]')
  if (!cartLink) return null
  const cartIcon = cartLink.querySelector('.cart-icon')
  if (!cartIcon) return null
  const fxOff = document.documentElement.classList

  const motionReduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  // Diagnostic-only switches, populated by dev/fxDiagnostics.js for isolated perf runs.
  const canFly = !motionReduced && !fxOff.contains('fx-off-cart-fly')
  const canBurst = !motionReduced && !fxOff.contains('fx-off-cart-burst')
  const shouldBurstOnly = canBurst && fxOff.contains('fx-off-cart-fly')

  if (canFly || shouldBurstOnly) {
    const targetRect = cartIcon.getBoundingClientRect()
    const endX = targetRect.left + targetRect.width / 2
    const endY = targetRect.top + targetRect.height / 2

    if (canFly) {
      const sourceRect = event?.currentTarget?.getBoundingClientRect()
      const startX = sourceRect ? sourceRect.left + sourceRect.width / 2 : window.innerWidth / 2
      const startY = sourceRect ? sourceRect.top + sourceRect.height / 2 : window.innerHeight / 2
      const orb = document.createElement('span')
      orb.className = 'cart-fly-orb'
      if (options.imageUrl) {
        orb.classList.add('cart-fly-orb--thumb')
        orb.style.backgroundImage = `url(${options.imageUrl})`
      }
      orb.style.setProperty('--start-x', `${startX}px`)
      orb.style.setProperty('--start-y', `${startY}px`)
      orb.style.setProperty('--fly-x', `${endX - startX}px`)
      orb.style.setProperty('--fly-y', `${endY - startY}px`)
      orb.style.setProperty('--arc-x', `${(endX - startX) * 0.48}px`)
      orb.style.setProperty('--arc-y', `${(endY - startY) * 0.48 - 62}px`)
      document.body.appendChild(orb)
      orb.addEventListener('animationend', () => {
        orb.remove()
        spawnCartBurst(endX, endY, fxOff)
      }, { once: true })
    } else {
      spawnCartBurst(endX, endY, fxOff)
    }
  }

  if (!fxOff.contains('fx-off-cart-pulse')) {
    cartIcon.classList.remove('is-added')
    window.requestAnimationFrame(() => {
      cartIcon.classList.add('is-added')
      cartIcon.addEventListener('animationend', () => cartIcon.classList.remove('is-added'), { once: true })
    })
  }
  return null
}

function spawnCartBurst(x, y, fxOff) {
  if (fxOff.contains('fx-off-cart-burst')) return
  for (let i = 0; i < 4; i += 1) {
    const spark = document.createElement('span')
    spark.className = 'cart-burst-spark'
    const angle = (Math.PI * 2 * i) / 4 + Math.random() * 0.6
    const dist = 18 + Math.random() * 8
    spark.style.setProperty('--start-x', `${x}px`)
    spark.style.setProperty('--start-y', `${y}px`)
    spark.style.setProperty('--dx', `${Math.cos(angle) * dist}px`)
    spark.style.setProperty('--dy', `${Math.sin(angle) * dist}px`)
    document.body.appendChild(spark)
    spark.addEventListener('animationend', () => spark.remove(), { once: true })
  }
}
