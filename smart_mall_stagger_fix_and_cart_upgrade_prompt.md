# 任务书:修复交错入场 bug + 把加购效果做得更高级 + 最终确认

## 背景

环境问题(日常浏览器扩展/多标签页干扰)排查清楚后,页面本身在干净环境下是流畅的,说明这几轮的性能修复方向是对的,代码底子没问题。现在可以从"保守地一点点加回效果"切换到"放心地把效果做得更好看",不需要再像之前那样每加一点就停下来怕出问题。

这次任务包含三件事:修掉上次审查发现的一个真实 bug(分类切换的卡片交错入场被覆盖掉了)、把加购效果从"一个小圆点飞过去"升级成更有质感的版本、最后跑一次完整确认。

---

## Part A — 修复:分类切换时卡片交错入场被吞掉

### 问题原因

`.product-card` 自身有:
```css
transition:
  transform 220ms var(--ease-out-soft),
  border-color 180ms var(--ease-out-soft);
```
`.product-list-enter-active` 也用 `transition` 简写声明了 `opacity`/`transform` 加上 `transition-delay` 做交错。CSS 里 `transition` 是简写属性,两条规则谁在层叠里赢,就是整条覆盖对方,不会按属性智能合并——这就是交错入场效果被吃掉的原因。

### 修复方式

把交错入场从"靠 transition 类名切换"改成"用 @keyframes + animation-delay",这样它和 `.product-card` 自身的 hover `transition` 是两套独立机制,不会互相覆盖:

```css
/* 删除 .product-list-enter-active 里原来的 transition 写法,改成: */
.product-list-enter-active {
  animation: product-card-in 420ms var(--ease-out-soft) both;
  animation-delay: calc(var(--card-index, 0) * 26ms);
}

@keyframes product-card-in {
  from {
    opacity: 0;
    transform: translate3d(0, 18px, 0) scale(0.985);
  }
  to {
    opacity: 1;
    transform: translate3d(0, 0, 0) scale(1);
  }
}

/* enter-from 不再需要单独声明起始状态,因为 animation 的 from 已经包含 */
```

`.product-list-leave-active` 和 `.product-list-move` 暂时不用动(问题报告里只确认了 enter 阶段被吃,move/leave 没有报告异常),但请顺手确认一下 `.product-list-move` 会不会有同样的简写冲突——如果 `.product-card` 的 hover transition 和 move 的 transition 同时命中同一个元素,道理是一样的。如果发现 move 阶段也有类似问题,用同样的思路处理(拆成不冲突的独立声明,或者也换成 animation)。

修完后请实际点击切换一次分类,确认卡片是按索引依次"错开"浮现,而不是所有卡片同时出现。

---

## Part B — 升级加购效果:从"小圆点"到"带产品缩略图 + 落地小火花"

当前 `flyToCart` 用一个纯色小圆点飞向购物车图标,视觉上能用但比较素。现在环境确认有性能余量,可以做得更有质感,同时保持原来的性能纪律(依然只在 click 这个离散事件里读一次布局,不常驻、不循环)。

### B.1 飞行元素改用商品缩略图

需要找到调用 `flyToCart` 的地方(`ProductCard.vue` emit `add-cart` 事件、`ProductListView.vue` 里的 `addCart` 处理函数),把商品的 `imageUrl` 一并传进去:

```js
// composables/useFlyToCart.js
export function flyToCart(event, options = {}) {
  const cartLink = document.querySelector('[data-cart-target]')
  if (!cartLink) return null
  const cartIcon = cartLink.querySelector('.cart-icon')
  if (!cartIcon) return null
  const fxOff = document.documentElement.classList

  const motionReduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  if (!motionReduced && !fxOff.contains('fx-off-cart-fly')) {
    const sourceRect = event?.currentTarget?.getBoundingClientRect()
    const targetRect = cartIcon.getBoundingClientRect()
    const startX = sourceRect ? sourceRect.left + sourceRect.width / 2 : window.innerWidth / 2
    const startY = sourceRect ? sourceRect.top + sourceRect.height / 2 : window.innerHeight / 2
    const endX = targetRect.left + targetRect.width / 2
    const endY = targetRect.top + targetRect.height / 2

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
      spawnCartBurst(cartIcon, fxOff)
    }, { once: true })
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

function spawnCartBurst(cartIcon, fxOff) {
  if (fxOff.contains('fx-off-cart-burst')) return
  const rect = cartIcon.getBoundingClientRect()
  const cx = rect.left + rect.width / 2
  const cy = rect.top + rect.height / 2
  for (let i = 0; i < 4; i += 1) {
    const spark = document.createElement('span')
    spark.className = 'cart-burst-spark'
    const angle = (Math.PI * 2 * i) / 4 + Math.random() * 0.6
    const dist = 18 + Math.random() * 8
    spark.style.setProperty('--start-x', `${cx}px`)
    spark.style.setProperty('--start-y', `${cy}px`)
    spark.style.setProperty('--dx', `${Math.cos(angle) * dist}px`)
    spark.style.setProperty('--dy', `${Math.sin(angle) * dist}px`)
    document.body.appendChild(spark)
    spark.addEventListener('animationend', () => spark.remove(), { once: true })
  }
}
```

调用方(找到实际的 `addCart(product)` 或类似函数,补上 event 和 imageUrl):
```js
function addCart(product, event) {
  flyToCart(event, { imageUrl: product.imageUrl })
  // ...原有的加购逻辑不变
}
```

### B.2 对应 CSS

```css
.cart-fly-orb--thumb {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background-size: cover;
  background-position: center;
  box-shadow: 0 10px 28px rgba(17, 19, 24, 0.28);
}

@keyframes cart-burst {
  0% { opacity: 1; transform: translate3d(-50%, -50%, 0) scale(1); }
  100% { opacity: 0; transform: translate3d(calc(var(--dx) - 50%), calc(var(--dy) - 50%), 0) scale(0.4); }
}

.cart-burst-spark {
  position: fixed;
  left: var(--start-x);
  top: var(--start-y);
  z-index: 81;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--klein, var(--color-accent));
  pointer-events: none;
  animation: cart-burst 380ms var(--ease-out-soft) both;
}
```

### B.3 诊断开关同步更新

在 `dev/fxDiagnostics.js` 的 `knownFx` 数组里加一项 `'cart-burst'`,方便以后单独测这一项的性能成本(虽然预期它非常便宜:只有 4 个 5px 小元素,只在一次 click 后短暂存在)。

### B.4 保持的性能纪律(不要在升级时破坏)

- 依然只在 `click` 这个离散事件里读一次 `getBoundingClientRect`(源商品卡 + 购物车图标各一次),不要变成常驻或循环读取
- 缩略图用 `background-image`,不要真的克隆整个 `<img>` DOM 节点或读取图片的自然尺寸
- 火花元素动画结束后必须 `remove()`,不能让它们堆积在 `document.body` 里
- 全部效果只用 `transform`/`opacity`/`background-image`,不要引入 `filter`/`box-shadow` 动画

如果 `prefers-reduced-motion: reduce` 生效,现有的全局降级规则(`*, *::before, *::after { animation-duration: 0.01ms !important }`)会自动让新加的 `cart-burst` 关键帧瞬间完成,不需要额外处理,但请实际切一下这个系统设置确认火花效果确实被压下去了,不会在降级模式下还很显眼地飞一圈。

---

## Part C — 最终确认(这次不用拆两次会话,CDP 链路上次已经跑通)

按之前跑通的流程,人工确认 Chrome 已经用调试端口开着、`http://localhost:9222/json/version` 有正常响应之后,直接跑:

```powershell
cd "G:\claudeproject\Agent Store\smart-mall\frontend"
$env:VITE_PERF_HUD='1'; npm run build
npm run preview -- --port 4174
```

另一个窗口:
```powershell
cd "G:\claudeproject\Agent Store\smart-mall\frontend"
$env:FX_MATRIX='1'; $env:PAGE_URL='http://127.0.0.1:4174/'; $env:CDP_URL='http://localhost:9222'
node scripts/perf-real-browser.mjs
```

重点确认:
1. 分类切换场景:交错入场修好之后,这个场景的 fps/longtask 有没有因为多了 animation 而变差(预期不会,只是把 transition 换成了 animation,成本量级一样)
2. 加购场景:升级后的缩略图飞行 + 落地火花,fps/longtask 是否依然干净(预期几乎无变化,新增元素很小很短暂)
3. 手动实际操作一遍确认:分类切换时卡片确实按顺序错开浮现;加购时能看到商品缩略图飞向购物车、落地有几个小火花、购物车图标 pulse

---

## 硬性约束
1. Part A 的修复不改变现有 move/leave 的视觉表现,只解决 enter 阶段的交错被吞问题
2. Part B 新增的效果全部遵守"离散触发、transform/opacity为主、动画结束即清理 DOM"的原则
3. 不改变业务逻辑、接口调用、store 结构
4. `fxDiagnostics.js`/`perfHud.js` 相关改动只做诊断层面的增补,不影响普通生产构建

## 交付要求
1. Part A 的具体 diff,以及分类切换实测交错入场是否恢复正常
2. Part B 的具体 diff,以及加购效果实测截图或文字描述(飞行轨迹、落地火花、购物车 pulse 是否都能看到)
3. Part C 的真实浏览器数据,尤其是分类切换和加购这两个场景改动前后的对比
