export function mountPerfHud() {
  const el = document.createElement('div')
  el.style.cssText = 'position:fixed;bottom:8px;right:8px;z-index:99999;background:rgba(0,0,0,0.78);color:#7CFC7C;font:11px/1.5 monospace;padding:8px 12px;border-radius:8px;pointer-events:none;white-space:pre;'
  document.body.appendChild(el)

  let frames = 0
  let lastTime = performance.now()
  let fps = 0
  let longTaskCount = 0
  let longTaskMax = 0
  let clsTotal = 0
  let sessionFrames = 0
  let sessionStart = performance.now()
  let sessionLongTasks = 0
  let sessionLongTaskMax = 0

  window.__perfReset = () => {
    sessionFrames = 0
    sessionStart = performance.now()
    sessionLongTasks = 0
    sessionLongTaskMax = 0
  }

  function loop(now) {
    frames += 1
    sessionFrames += 1
    if (now - lastTime >= 1000) {
      fps = frames
      frames = 0
      lastTime = now
    }
    el.textContent = `FPS ${fps}\nlongtask x${longTaskCount} (max ${longTaskMax.toFixed(0)}ms)\nCLS ${clsTotal.toFixed(3)}`
    requestAnimationFrame(loop)
  }
  requestAnimationFrame(loop)

  if ('PerformanceObserver' in window) {
    try {
      new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
          longTaskCount += 1
          longTaskMax = Math.max(longTaskMax, entry.duration)
          sessionLongTasks += 1
          sessionLongTaskMax = Math.max(sessionLongTaskMax, entry.duration)
          console.warn('[longtask]', `${entry.duration.toFixed(1)}ms`, entry.attribution)
        }
      }).observe({ type: 'longtask', buffered: true })
    } catch (e) {}

    try {
      new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
          if (!entry.hadRecentInput) clsTotal += entry.value
        }
      }).observe({ type: 'layout-shift', buffered: true })
    } catch (e) {}
  }

  window.__perfReport = () => {
    const sessionSeconds = (performance.now() - sessionStart) / 1000
    return {
      fps,
      longTaskCount,
      longTaskMax: Number(longTaskMax.toFixed(1)),
      sessionAvgFps: sessionSeconds > 0 ? Number((sessionFrames / sessionSeconds).toFixed(1)) : null,
      sessionLongTaskCount: sessionLongTasks,
      sessionLongTaskMax: Number(sessionLongTaskMax.toFixed(1)),
      cls: Number(clsTotal.toFixed(3))
    }
  }
}
