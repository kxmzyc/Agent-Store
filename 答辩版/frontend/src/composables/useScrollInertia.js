import { onBeforeUnmount, onMounted } from 'vue'

export function useScrollInertia(heroRef, dockRef) {
  let frame = 0
  let docked = false

  function writeProgress() {
    frame = 0
    const hero = heroRef.value
    const dock = dockRef.value
    if (!hero || !dock) return

    const progress = Math.min(window.scrollY / 220, 1)
    const value = progress.toFixed(3)
    hero.style.setProperty('--scroll-progress', value)
    hero.style.setProperty('--hero-opacity', (1 - progress * 0.1).toFixed(3))
    hero.style.setProperty('--hero-shift', `${(-18 * progress).toFixed(2)}px`)
    dock.style.setProperty('--scroll-progress', value)
    dock.style.setProperty('--dock-shift', `${(-8 * progress).toFixed(2)}px`)

    const nextDocked = progress > 0.62
    if (nextDocked !== docked) {
      docked = nextDocked
      dock.classList.toggle('is-docked', docked)
    }
  }

  function onScroll() {
    if (!frame) frame = window.requestAnimationFrame(writeProgress)
  }

  onMounted(() => {
    // Diagnostic-only switch, populated by dev/fxDiagnostics.js for isolated perf runs.
    if (document.documentElement.classList.contains('fx-off-scroll-inertia')) return
    writeProgress()
    window.addEventListener('scroll', onScroll, { passive: true })
  })

  onBeforeUnmount(() => {
    window.removeEventListener('scroll', onScroll)
    if (frame) window.cancelAnimationFrame(frame)
  })
}
