# 智能商城前端动效自检报告

生成时间: 2026-07-04  
范围: `smart-mall/frontend/src/style.css`、`views/`、`components/`、`composables/`、`scripts/`  
任务边界: 本次只做诊断与可复测工具补齐；未删除、弱化或修复现有动效。新增代码均标注为诊断用途，并通过 `DEV` 或 `VITE_PERF_HUD=1` 入口启用。

## Part A - 动态效果完整清单

### 顶部导航 hover 位移与颜色变化
- 位置: `smart-mall/frontend/src/style.css:93-116`
- 触发方式: `hover`
- 动画的 CSS 属性: `transform`、`background-color`、`color`、`box-shadow`
- 是否持续运行: 否，只在 hover 状态进入/离开时运行
- 完整代码原文:

```css
.topnav a {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 30px;
  padding: 0 11px;
  border-radius: var(--radius-pill);
  color: var(--ink-500);
  font-size: 12px;
  font-weight: 520;
  white-space: nowrap;
  transition:
    transform 160ms var(--ease-out-soft),
    background-color 120ms linear,
    color 120ms linear,
    box-shadow 160ms var(--ease-out-soft);
}

.topnav a:hover {
  background: var(--accent-soft);
  color: var(--ink-900);
  transform: translate3d(0, -1px, 0);
}
```

### 顶部导航 active 下划线进入动画
- 位置: `smart-mall/frontend/src/style.css:118-146`
- 触发方式: 路由切换后 active class 改变
- 动画的 CSS 属性: `opacity`、`transform`
- 是否持续运行: 否，active 下划线生成时运行一次
- 完整代码原文:

```css
.topnav a.router-link-active {
  background: rgba(255, 255, 255, 0.78);
  color: var(--ink);
  box-shadow: inset 0 0 0 1px rgba(227, 230, 235, 0.76);
}

.topnav a.router-link-active::before {
  content: "";
  position: absolute;
  right: 10px;
  bottom: 5px;
  left: 10px;
  height: 1px;
  border-radius: 999px;
  background: var(--accent);
  transform-origin: center;
  animation: nav-underline-in 240ms var(--ease-out-soft) both;
}

@keyframes nav-underline-in {
  from {
    opacity: 0;
    transform: scaleX(0.2);
  }
  to {
    opacity: 1;
    transform: scaleX(1);
  }
}
```

### 路由页面切换淡入淡出
- 位置: `smart-mall/frontend/src/App.vue:31-34`; `smart-mall/frontend/src/style.css:203-220`
- 触发方式: 路由切换
- 动画的 CSS 属性: `opacity`、`transform`、`filter`
- 是否持续运行: 否，路由切换时运行一次
- 完整代码原文:

```vue
<router-view v-slot="{ Component, route }">
  <transition name="route-fade" mode="out-in">
    <component :is="Component" :key="route.fullPath" />
  </transition>
</router-view>
```

```css
.route-fade-enter-active,
.route-fade-leave-active {
  transition:
    opacity var(--duration-route) var(--ease-out-soft),
    transform var(--duration-route) var(--ease-out-soft),
    filter var(--duration-route) var(--ease-out-soft);
}

.route-fade-enter-from {
  opacity: 0;
  transform: translate3d(0, 14px, 0);
  filter: blur(6px);
}

.route-fade-leave-to {
  opacity: 0;
  transform: translate3d(0, -8px, 0);
  filter: blur(4px);
}
```

### 搜索框 focus 阴影变化
- 位置: `smart-mall/frontend/src/style.css:265-288`
- 触发方式: `focus-within`
- 动画的 CSS 属性: `background-color`; `box-shadow` 有状态变化但当前 transition 未包含 `box-shadow`
- 是否持续运行: 否，focus 进入/离开时状态变化
- 完整代码原文:

```css
.search-box {
  position: relative;
  display: flex;
  align-items: center;
  background: var(--color-surface);
  border: 1px solid var(--line);
  border-radius: var(--radius-input);
  padding: 0 8px 0 44px;
  min-width: min(640px, 100%);
  height: 50px;
  box-shadow: 0 8px 24px rgba(23, 23, 26, 0.04);
  transition: background-color 220ms var(--ease-out-soft);
}

.search-box:focus-within {
  box-shadow: 0 0 0 4px rgba(43, 69, 216, 0.12), 0 16px 34px rgba(17, 19, 24, 0.06);
}
```

### 商品页 hero/dock 滚动联动
- 位置: `smart-mall/frontend/src/views/ProductListView.vue:3-26,77-94`; `smart-mall/frontend/src/composables/useScrollInertia.js:1-41`; `smart-mall/frontend/src/style.css:331-348,426-452`
- 触发方式: `scroll`、组件挂载时
- 动画的 CSS 属性: `opacity`、`transform`、`backdrop-filter`
- 是否持续运行: 是，每次 scroll 事件通过 rAF 合并后计算一次
- 滚动变量写入位置:
  - `--scroll-progress`、`--hero-opacity`、`--hero-shift` 写入 `heroRef.value`，实际 DOM 是 `.commerce-hero`
  - `--scroll-progress`、`--dock-shift` 写入 `dockRef.value`，实际 DOM 是 `.search-dock`
- 更新方式: 通过 `element.style.setProperty` 直接写 DOM；不是 Vue `ref/reactive` 模板绑定
- scroll 监听: `window.addEventListener('scroll', onScroll, { passive: true })`
- 完整代码原文:

```vue
<div ref="heroRef" class="commerce-hero">
  <p class="hero-kicker">SMART MALL</p>
  <h1>把真实商品数据，摆进一个安静的展厅。</h1>
  <p class="hero-copy">搜索、分类、排序和库存状态都来自后端接口，前台只保留浏览与购买的关键动作。</p>
  <div class="hero-stats" aria-label="商品统计">
    <span>{{ total }} 件商品</span>
    <span>{{ categories.length }} 个一级分类</span>
    <span>第 {{ page }} 页</span>
  </div>
</div>

<div ref="dockRef" class="search-dock">
  <form class="search-box catalog-search" @submit.prevent="search">
    <Search size="18" />
    <input v-model="keyword" placeholder="搜索机械键盘、显示器、耳机" />
    <select v-model="sort" aria-label="商品排序" @change="loadProducts(1)">
      <option value="sales_desc">销量优先</option>
      <option value="price_asc">价格升序</option>
      <option value="price_desc">价格降序</option>
      <option value="new_desc">最新上架</option>
    </select>
    <button class="primary search-submit" type="submit">搜索</button>
  </form>
</div>
```

```js
const heroRef = ref(null)
const dockRef = ref(null)

useScrollInertia(heroRef, dockRef)
```

```js
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
```

```css
.commerce-hero {
  --scroll-progress: 0;
  --hero-opacity: 1;
  --hero-shift: 0px;
  min-height: clamp(300px, 44vh, 470px);
  display: grid;
  place-items: center;
  align-content: center;
  gap: 18px;
  text-align: center;
  padding: clamp(58px, 9vh, 92px) clamp(18px, 4vw, 56px) clamp(78px, 12vh, 118px);
  border-bottom: 1px solid rgba(17, 19, 24, 0.08);
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.72), rgba(240, 242, 245, 0.96)),
    repeating-linear-gradient(90deg, rgba(17, 19, 24, 0.045) 0 1px, transparent 1px 56px);
  opacity: var(--hero-opacity);
  transform: translate3d(0, var(--hero-shift), 0);
}

.search-dock {
  --scroll-progress: 0;
  --dock-shift: 0px;
  position: sticky;
  top: 44px;
  z-index: 24;
  display: grid;
  justify-items: center;
  margin-top: clamp(-86px, -10vh, -54px);
  padding: 10px 0 14px;
  transform: translate3d(0, var(--dock-shift), 0);
}

.search-dock.is-docked::before {
  background: rgba(245, 246, 248, 0.9);
  backdrop-filter: blur(14px);
}
```

### 商品页 hero 首屏进入动画
- 位置: `smart-mall/frontend/src/style.css:350-375`
- 触发方式: 加载完成后
- 动画的 CSS 属性: `opacity`、`transform`
- 是否持续运行: 否，每个 hero 子元素进入时运行一次
- 完整代码原文:

```css
.commerce-hero > * {
  animation: hero-rise-in 620ms var(--ease-out-soft) both;
}

.commerce-hero > :nth-child(2) {
  animation-delay: 70ms;
}

.commerce-hero > :nth-child(3) {
  animation-delay: 130ms;
}

.commerce-hero > :nth-child(4) {
  animation-delay: 190ms;
}

@keyframes hero-rise-in {
  from {
    opacity: 0;
    transform: translate3d(0, 18px, 0);
  }
  to {
    opacity: 1;
    transform: translate3d(0, 0, 0);
  }
}
```

### 全局按钮 hover/active 动效
- 位置: `smart-mall/frontend/src/style.css:480-524,542-546`
- 触发方式: `hover`、`active`
- 动画的 CSS 属性: `transform`、`background-color`、`color`、`opacity`、`box-shadow`
- 是否持续运行: 否
- 完整代码原文:

```css
.btn,
button {
  border: 0;
  border-radius: var(--radius-input);
  padding: 10px 14px;
  cursor: pointer;
  background: var(--color-border);
  color: var(--color-text-primary);
  display: inline-flex;
  align-items: center;
  gap: 8px;
  justify-content: center;
  min-height: 40px;
  transition:
    transform 160ms var(--ease-out-soft),
    background-color 120ms linear,
    color 120ms linear,
    opacity 120ms linear,
    box-shadow 160ms var(--ease-out-soft);
}

.btn:hover,
button:hover {
  background: var(--color-border-soft);
  transform: translate3d(0, -1px, 0);
}

.btn:active,
button:active {
  transform: translate3d(0, 0, 0) scale(0.985);
}

.btn.primary:hover,
button.primary:hover,
.btn.dark:hover,
button.dark:hover {
  background: var(--color-accent-hover);
}

button:disabled {
  opacity: 0.3;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}
```

### select 与表单 focus 动效
- 位置: `smart-mall/frontend/src/style.css:549-564,1069-1085`
- 触发方式: `focus`
- 动画的 CSS 属性: `border-color`、`background-color`、`color`; `box-shadow` 有状态变化但未在 transition 中声明
- 是否持续运行: 否
- 完整代码原文:

```css
select {
  height: 44px;
  border: 1px solid var(--color-border-soft);
  border-radius: var(--radius-input);
  padding: 0 36px 0 12px;
  outline: none;
  background: var(--color-surface);
  color: var(--color-text-primary);
  appearance: none;
  transition: border-color var(--duration-base) var(--ease-premium), background-color var(--duration-base) var(--ease-premium), color var(--duration-base) var(--ease-premium);
}

select:focus {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 1px var(--color-accent);
}

.form input,
.form textarea,
.form select {
  border: 1px solid var(--color-border-soft);
  border-radius: var(--radius-input);
  padding: 11px 12px;
  outline: none;
  background: var(--color-surface);
  color: var(--color-text-primary);
  transition: border-color var(--duration-base) var(--ease-premium), background-color var(--duration-base) var(--ease-premium), color var(--duration-base) var(--ease-premium);
}

.form input:focus,
.form textarea:focus,
.form select:focus {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 1px var(--color-accent);
}
```

### 商品列表 TransitionGroup 进出与移动动画
- 位置: `smart-mall/frontend/src/views/ProductListView.vue:44-57,106-145`; `smart-mall/frontend/src/style.css:578-605`
- 触发方式: click 分类、搜索、分页后数据列表变化
- 动画的 CSS 属性: `transform`、`opacity`
- 是否持续运行: 否，列表变更时运行一次
- 完整代码原文:

```vue
<TransitionGroup
  name="product-list"
  tag="div"
  class="grid product-grid"
  :css="categoryTransition"
>
  <ProductCard
    v-for="(p, index) in products"
    :key="p.id"
    :product="p"
    :style="{ '--card-index': index % 12 }"
    @add-cart="addCart"
  />
</TransitionGroup>
```

```js
function selectCategory(id) {
  categoryId.value = id
  searchMode.value = false
  // Diagnostic-only switch, populated by dev/fxDiagnostics.js for isolated perf runs.
  categoryTransition.value = !document.documentElement.classList.contains('fx-off-product-list')
  loadProducts(1)
}

if (categoryTransition.value) {
  window.clearTimeout(categoryTransitionTimer)
  categoryTransitionTimer = window.setTimeout(() => {
    categoryTransition.value = false
  }, 360)
}
```

```css
.product-list-move {
  transition: transform 260ms var(--ease-out-soft);
}

.product-list-enter-active {
  transition:
    opacity 360ms var(--ease-out-soft),
    transform 420ms var(--ease-out-soft);
  transition-delay: calc(var(--card-index, 0) * 26ms);
}

.product-list-leave-active {
  position: absolute;
  width: 100%;
  transition:
    opacity 150ms linear,
    transform 150ms var(--ease-out-soft);
}

.product-list-enter-from {
  opacity: 0;
  transform: translate3d(0, 18px, 0) scale(0.985);
}

.product-list-leave-to {
  opacity: 0;
  transform: scale(0.985);
}
```

### 商品卡片 hover 抬升、边框与阴影伪层
- 位置: `smart-mall/frontend/src/style.css:616-680`
- 触发方式: `hover`
- 动画的 CSS 属性: `transform`、`border-color`、`opacity`
- 是否持续运行: 否
- 完整代码原文:

```css
.product-card {
  position: relative;
  background: #fff;
  border: 1px solid rgba(17, 19, 24, 0.10);
  border-radius: var(--radius-card);
  padding: 10px;
  min-height: 456px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 8px 22px rgba(17, 19, 24, 0.055);
  transform: translate3d(0, 0, 0);
  touch-action: manipulation;
  contain: layout style;
  will-change: transform;
  transition:
    transform 220ms var(--ease-out-soft),
    border-color 180ms var(--ease-out-soft);
}

.product-card::before {
  content: "";
  position: absolute;
  inset: -32% auto -32% -44%;
  z-index: 2;
  width: 38%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.72), transparent);
  opacity: 0;
  pointer-events: none;
  transform: translate3d(-120%, 0, 0) skewX(-16deg);
}

.product-card::after {
  content: "";
  position: absolute;
  inset: 0;
  z-index: 0;
  border-radius: inherit;
  box-shadow: 0 20px 54px rgba(17, 19, 24, 0.12);
  opacity: 0;
  pointer-events: none;
  transition: opacity 180ms var(--ease-out-soft);
}

.product-card:hover {
  border-color: rgba(43, 69, 216, 0.28);
  transform: translate3d(0, -4px, 0);
}

.product-card:hover::after {
  opacity: 1;
}

.product-card:hover::before {
  opacity: 0.75;
  transform: translate3d(440%, 0, 0) skewX(-16deg);
  transition:
    transform 620ms var(--ease-out-soft),
    opacity 180ms linear;
}
```

### 商品图加载 skeleton shimmer 与加载状态
- 位置: `smart-mall/frontend/src/components/ProductCard.vue:3-17,47-55`; `smart-mall/frontend/src/style.css:696-748`
- 触发方式: 加载完成前常驻循环；图片 `load/error` 后切换 class
- 动画的 CSS 属性: `transform`、`opacity`
- 是否持续运行: 是，`.image-shimmer` 在图片未加载时无限循环；图片加载状态切换是一次性
- 完整代码原文:

```vue
<router-link class="product-media" :class="{ 'is-loading': !imageLoaded, 'is-loaded': imageLoaded }" :to="`/products/${product.id}`">
  <span v-if="!imageLoaded" class="image-skeleton" aria-hidden="true">
    <span class="image-shimmer"></span>
  </span>
  <img
    class="product-image"
    :src="product.imageUrl"
    :alt="product.name"
    width="640"
    height="480"
    loading="lazy"
    decoding="async"
    @load="imageLoaded = true"
    @error="imageLoaded = true"
  />
  <span class="product-badge">{{ badgeText }}</span>
</router-link>
```

```js
const imageLoaded = ref(false)

watch(
  () => props.product.imageUrl,
  () => {
    imageLoaded.value = false
  }
)
```

```css
.product-image,
.product-card img {
  position: relative;
  z-index: 1;
  width: 100%;
  display: block;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  border-radius: var(--radius-image);
  background: var(--paper);
  box-shadow: none;
  transform: scale(1);
  transition: opacity 180ms var(--ease-out-soft), transform 260ms var(--ease-out-soft);
}

.image-shimmer {
  position: absolute;
  top: 0;
  bottom: 0;
  left: -64%;
  width: 64%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.68), transparent);
  transform: translateX(-100%);
  animation: image-shimmer-slide 860ms linear infinite;
}

@keyframes image-shimmer-slide {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(360%); }
}

.product-media.is-loading .product-image {
  opacity: 0;
  transform: scale(1);
}

.product-media.is-loaded .product-image {
  opacity: 1;
  transform: scale(1);
}

.product-card:hover .product-media.is-loaded .product-image {
  transform: scale(1.035);
}
```

### 商品 badge hover 位移
- 位置: `smart-mall/frontend/src/style.css:750-767`
- 触发方式: `hover`
- 动画的 CSS 属性: `transform`、`background-color`
- 是否持续运行: 否
- 完整代码原文:

```css
.product-card:hover .product-badge {
  transform: translate3d(0, -1px, 0);
}

.product-badge {
  position: absolute;
  top: 16px;
  left: 16px;
  padding: 5px 9px;
  border-radius: var(--radius-input);
  background: rgba(17, 19, 24, 0.86);
  color: #fff;
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.08em;
  box-shadow: none;
  z-index: 2;
  transition: transform 180ms var(--ease-out-soft), background-color 180ms var(--ease-out-soft);
}
```

### 分类 chip hover 与 active 状态变化
- 位置: `smart-mall/frontend/src/views/ProductListView.vue:28-38`; `smart-mall/frontend/src/style.css:838-868`
- 触发方式: `hover`、`click`
- 动画的 CSS 属性: `transform`、`background-color`、`color`
- 是否持续运行: 否
- 完整代码原文:

```vue
<div class="category-scroll" aria-label="商品分类">
  <div class="category-strip">
    <button class="chip" :class="{ active: !categoryId }" @click="selectCategory(null)">全部</button>
    <template v-for="c in categories" :key="c.id">
      <button class="chip" :class="{ active: categoryId === c.id }" @click="selectCategory(c.id)">{{ c.name }}</button>
      <button v-for="child in c.children" :key="child.id" class="chip" :class="{ active: categoryId === child.id }" @click="selectCategory(child.id)">
        {{ child.name }}
      </button>
    </template>
  </div>
</div>
```

```css
.chip {
  border: 0;
  background: rgba(255, 255, 255, 0.72);
  color: var(--ink-500);
  border-radius: var(--radius-pill);
  padding: 9px 15px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  box-shadow: inset 0 0 0 1px rgba(227, 230, 235, 0.82);
  white-space: nowrap;
  transform: none;
  transition:
    transform 160ms var(--ease-out-soft),
    background-color 160ms var(--ease-out-soft),
    color 160ms var(--ease-out-soft);
}

.chip:hover {
  background: var(--accent-soft);
  color: var(--ink-900);
  transform: translateY(-1px) scale(1.01);
}

.chip.active {
  background: var(--klein);
  color: #fff;
  border-color: transparent;
  transform: translateY(-1px) scale(1.02);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.28), 0 10px 22px rgba(43, 69, 216, 0.18);
}
```

### hover 交换按钮层
- 位置: `smart-mall/frontend/src/style.css:883-948`
- 触发方式: `hover`
- 动画的 CSS 属性: `opacity`、`background-color`、`color`、`transform`
- 是否持续运行: 否
- 完整代码原文:

```css
.swap-container,
.cart-row-swap,
.order-action-swap,
.profile-action-swap {
  position: relative;
  min-height: 44px;
  overflow: hidden;
}

.swap-primary,
.swap-secondary,
.add-to-cart-btn,
.cart-qty-controls,
.cart-delete-btn,
.order-view-action,
.order-danger-action,
.profile-edit-label,
.profile-save-btn {
  position: absolute;
  inset: 0;
  transition: opacity 120ms linear, background-color 120ms linear, color 120ms linear;
}

tr:hover .cart-qty-controls,
.order-card:hover .order-view-action,
.profile-card:hover .profile-edit-label {
  opacity: 0;
}

tr:hover .cart-delete-btn,
.order-card:hover .order-danger-action,
.profile-card:hover .profile-save-btn {
  opacity: 1;
}

.product-card .add-to-cart-btn {
  position: static;
  min-height: 42px;
  margin-top: 4px;
  opacity: 1;
  transform: none;
}

.product-card .add-to-cart-btn:hover {
  background: var(--color-accent-hover);
  transform: translate3d(0, -1px, 0);
}
```

### 加入购物车飞行动画与购物车图标 pulse
- 位置: `smart-mall/frontend/src/composables/useFlyToCart.js:1-37`; `smart-mall/frontend/src/views/ProductListView.vue:150-161`; `smart-mall/frontend/src/style.css:970-1025`
- 触发方式: `click`
- 动画的 CSS 属性: `opacity`、`transform`
- 是否持续运行: 否，点击后飞行 620ms、图标 pulse 420ms
- 完整代码原文:

```js
export function flyToCart(event) {
  const cartLink = document.querySelector('[data-cart-target]')
  if (!cartLink) return null
  const cartIcon = cartLink.querySelector('.cart-icon')
  if (!cartIcon) return null
  const fxOff = document.documentElement.classList

  const motionReduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  // Diagnostic-only switches, populated by dev/fxDiagnostics.js for isolated perf runs.
  if (!motionReduced && !fxOff.contains('fx-off-cart-fly')) {
    const sourceRect = event?.currentTarget?.getBoundingClientRect()
    const targetRect = cartIcon.getBoundingClientRect()
    const startX = sourceRect ? sourceRect.left + sourceRect.width / 2 : window.innerWidth / 2
    const startY = sourceRect ? sourceRect.top + sourceRect.height / 2 : window.innerHeight / 2
    const endX = targetRect.left + targetRect.width / 2
    const endY = targetRect.top + targetRect.height / 2
    const orb = document.createElement('span')
    orb.className = 'cart-fly-orb'
    orb.style.setProperty('--start-x', `${startX}px`)
    orb.style.setProperty('--start-y', `${startY}px`)
    orb.style.setProperty('--fly-x', `${endX - startX}px`)
    orb.style.setProperty('--fly-y', `${endY - startY}px`)
    orb.style.setProperty('--arc-x', `${(endX - startX) * 0.48}px`)
    orb.style.setProperty('--arc-y', `${(endY - startY) * 0.48 - 62}px`)
    document.body.appendChild(orb)
    orb.addEventListener('animationend', () => orb.remove(), { once: true })
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
```

```css
@keyframes cart-pulse {
  0% { transform: scale(1); }
  30% { transform: scale(1.28); }
  55% { transform: scale(0.94); }
  100% { transform: scale(1); }
}

@keyframes cart-fly {
  0% {
    opacity: 0;
    transform: translate3d(-50%, -50%, 0) scale(0.65);
  }
  12% {
    opacity: 1;
  }
  58% {
    opacity: 1;
    transform: translate3d(calc(var(--arc-x) - 50%), calc(var(--arc-y) - 50%), 0) scale(1);
  }
  100% {
    opacity: 0;
    transform: translate3d(calc(var(--fly-x) - 50%), calc(var(--fly-y) - 50%), 0) scale(0.34);
  }
}

.cart-fly-orb {
  position: fixed;
  left: var(--start-x);
  top: var(--start-y);
  z-index: 80;
  width: 13px;
  height: 13px;
  border-radius: 50%;
  pointer-events: none;
  background: var(--color-accent);
  box-shadow:
    0 0 0 6px rgba(43, 69, 216, 0.12),
    0 10px 28px rgba(43, 69, 216, 0.3);
  animation: cart-fly 620ms var(--ease-out-soft) both;
}

.cart-icon.is-added {
  animation: cart-pulse 420ms var(--ease-spring);
}
```

### 表格行 hover 背景
- 位置: `smart-mall/frontend/src/style.css:1162-1168`
- 触发方式: `hover`
- 动画的 CSS 属性: `background-color`
- 是否持续运行: 否
- 完整代码原文:

```css
.table tbody tr {
  transition: background-color var(--duration-base) var(--ease-premium);
}

.table tbody tr:hover {
  background: var(--color-bg);
}
```

### 后台订单卡片 hover
- 位置: `smart-mall/frontend/src/style.css:1414-1422`
- 触发方式: `hover`
- 动画的 CSS 属性: `border-color`、`background-color`
- 是否持续运行: 否
- 完整代码原文:

```css
.admin-order-card {
  display: grid;
  gap: 14px;
  transition: border-color var(--duration-base) var(--ease-premium), background-color var(--duration-base) var(--ease-premium);
}

.admin-order-card:hover {
  border-color: rgba(10, 10, 10, 0.16);
  background: var(--color-surface);
}
```

### 购物车卡片 hover/selected/updating/removing/soldout 状态
- 位置: `smart-mall/frontend/src/views/CartView.vue:37-83,163-212`; `smart-mall/frontend/src/style.css:1560-1647`
- 触发方式: `hover`、click、接口请求前后状态切换
- 动画的 CSS 属性: `transform`、`box-shadow`、`border-color`、`opacity`、`background-color`
- 是否持续运行: 否
- 完整代码原文:

```vue
<TransitionGroup name="cart-list" tag="div" class="cart-list">
  <article
    v-for="item in items"
    :key="item.id"
    class="cart-item-card"
    :class="{
      selected: checked.includes(item.id),
      removing: removingIds.includes(item.id),
      updating: updatingIds.includes(item.id),
      soldout: item.stock <= 0
    }"
  >
    <label class="cart-select">
      <input type="checkbox" v-model="checked" :value="item.id" :disabled="item.stock <= 0" />
      <span></span>
    </label>
  </article>
</TransitionGroup>
```

```css
.cart-item-card {
  position: relative;
  display: grid;
  grid-template-columns: 34px 96px minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  min-height: 128px;
  padding: 16px;
  border: 1px solid var(--color-border-soft);
  border-radius: var(--radius-card);
  background: var(--color-surface);
  overflow: hidden;
  transition:
    transform var(--duration-base) var(--ease-premium),
    box-shadow var(--duration-base) var(--ease-premium),
    border-color var(--duration-base) var(--ease-premium),
    opacity 220ms ease;
}

.cart-item-card::before {
  content: "";
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  background: var(--color-accent);
  opacity: 0;
}

.cart-item-card:hover,
.cart-item-card.selected {
  border-color: rgba(10, 10, 10, 0.16);
  transform: translate3d(0, -2px, 0);
  box-shadow: 0 14px 32px rgba(17, 19, 24, 0.08);
}

.cart-item-card.selected::before {
  opacity: 1;
}

.cart-item-card.updating {
  background: var(--color-bg);
}

.cart-item-card.removing {
  opacity: 0;
}

.cart-item-card.soldout {
  opacity: 0.58;
}

.cart-select span {
  width: 22px;
  height: 22px;
  border: 1px solid var(--color-border-soft);
  border-radius: 50%;
  display: grid;
  place-items: center;
  transition: border-color var(--duration-base) var(--ease-premium), background-color var(--duration-base) var(--ease-premium);
}

.cart-select input:checked + span {
  background: var(--color-accent);
  border-color: var(--color-accent);
}

.cart-select input:checked + span::after {
  opacity: 1;
}

.cart-select input:disabled + span {
  opacity: 0.4;
}
```

### 购物车结算进度条 width 变化
- 位置: `smart-mall/frontend/src/views/CartView.vue:86-94,128-131`; `smart-mall/frontend/src/style.css:1738-1750`
- 触发方式: click 勾选商品
- 动画的 CSS 属性: `width`
- 是否持续运行: 否，勾选状态变化时运行
- 完整代码原文:

```vue
<div class="checkout-meter">
  <span :style="{ width: checkoutProgress + '%' }"></span>
</div>
```

```js
const checkoutProgress = computed(() => {
  if (!availableIds.value.length) return 0
  return Math.round((checked.value.length / availableIds.value.length) * 100)
})
```

```css
.checkout-meter span {
  display: block;
  height: 100%;
  background: var(--color-accent);
  transition: width 260ms var(--ease-out-soft);
}
```

### 购物车 TransitionGroup 列表进出动画
- 位置: `smart-mall/frontend/src/views/CartView.vue:37-84`; `smart-mall/frontend/src/style.css:1842-1859`
- 触发方式: 删除、清空、重新加载列表
- 动画的 CSS 属性: `opacity`、`transform`
- 是否持续运行: 否
- 完整代码原文:

```css
.cart-list-move,
.cart-list-enter-active,
.cart-list-leave-active {
  transition:
    opacity 240ms var(--ease-premium),
    transform 280ms var(--ease-premium);
}

.cart-list-enter-from,
.cart-list-leave-to {
  opacity: 0;
  transform: translate3d(18px, 0, 0) scale(0.985);
}

.cart-list-leave-active {
  position: absolute;
  width: 100%;
}
```

### 金额 pulse 与数量 bump
- 位置: `smart-mall/frontend/src/views/CartView.vue:8-10,70-78,138-178`; `smart-mall/frontend/src/style.css:1861-1879`
- 触发方式: 金额变化、数量按钮 click
- 动画的 CSS 属性: `transform`
- 是否持续运行: 否，触发后一次性运行
- 完整代码原文:

```js
watch(total, () => {
  // Diagnostic-only switch, populated by dev/fxDiagnostics.js for isolated perf runs.
  if (document.documentElement.classList.contains('fx-off-amount-pulse')) return
  totalPulse.value = true
  window.setTimeout(() => { totalPulse.value = false }, 360)
})

async function update(item, quantity) {
  if (quantity < 1 || quantity > item.stock) return
  mark(updatingIds, item.id)
  const qtyBumpOff = document.documentElement.classList.contains('fx-off-qty-bump')
  if (!qtyBumpOff) mark(bumpedIds, item.id)
  try {
    await api.put(`/cart/${item.id}`, { quantity })
    await load()
  } catch (e) {
    toast.show(errorMessage(e))
    await load()
  } finally {
    unmark(updatingIds, item.id)
    if (!qtyBumpOff) window.setTimeout(() => unmark(bumpedIds, item.id), 260)
  }
}
```

```css
@keyframes amount-pop {
  0% { transform: scale(1); }
  38% { transform: scale(1.06); }
  100% { transform: scale(1); }
}

@keyframes qty-bump {
  0% { transform: scale(1); }
  45% { transform: scale(1.08); }
  100% { transform: scale(1); }
}

.cart-total-chip.pulse,
.checkout-head strong.pulse {
  animation: amount-pop 360ms var(--ease-spring);
}

.cart-qty.bump {
  animation: qty-bump 260ms var(--ease-spring);
}
```

### 订单卡片 hover 与状态时间线变化
- 位置: `smart-mall/frontend/src/views/OrdersView.vue:10-20,31-87`; `smart-mall/frontend/src/views/OrderDetailView.vue:24-31`; `smart-mall/frontend/src/style.css:1914-1922,2016-2074`
- 触发方式: `hover`、状态 class 切换
- 动画的 CSS 属性: `border-color`、`background-color`; 时间线使用 `box-shadow`/`opacity` 状态变化，无 transition
- 是否持续运行: 否
- 完整代码原文:

```css
.order-card {
  margin-bottom: 14px;
  transition: border-color var(--duration-base) var(--ease-premium), background-color var(--duration-base) var(--ease-premium);
}

.order-card:hover {
  border-color: rgba(10, 10, 10, 0.16);
  background: var(--color-surface);
}

.timeline-step.current span {
  box-shadow: 0 0 0 6px rgba(10, 10, 10, 0.08);
}

.order-timeline.cancelled .timeline-step:not(:first-child) {
  opacity: 0.45;
}
```

### 偏好标签删除按钮 hover
- 位置: `smart-mall/frontend/src/views/ProfileView.vue:109-114`; `smart-mall/frontend/src/style.css:2229-2239`
- 触发方式: `hover`
- 动画的 CSS 属性: 继承全局按钮 transition，变化 `color`、`background-color`
- 是否持续运行: 否
- 完整代码原文:

```vue
<span v-for="item in preferences" :key="item.tag" class="preference-chip">
  <span>{{ item.tag }}</span>
  <small>{{ item.weight.toFixed(1) }}</small>
  <button type="button" title="删除偏好" @click="removePreference(item.tag)">×</button>
</span>
```

```css
.preference-chip button {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  color: var(--color-text-muted);
}

.preference-chip button:hover {
  color: var(--color-danger);
  background: var(--color-border);
}
```

### AI 聊天悬浮按钮与面板进入动画
- 位置: `smart-mall/frontend/src/components/ChatWidget.vue:2-45`; `smart-mall/frontend/src/style.css:2241-2289`
- 触发方式: `hover`、click 打开
- 动画的 CSS 属性: `transform`、`box-shadow`、`background-color`、`opacity`
- 是否持续运行: 否，面板打开时运行一次
- 完整代码原文:

```vue
<button class="chat-fab" title="AI 导购助手" @click="toggleOpen">
  <Bot size="22" />
</button>
<section v-if="open" class="chat-panel">
```

```css
.chat-fab {
  position: fixed;
  right: 24px;
  bottom: 24px;
  width: 54px;
  height: 54px;
  border-radius: 50%;
  background: var(--color-accent);
  color: var(--color-on-accent);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.15);
  z-index: 20;
  transition:
    transform 180ms var(--ease-out-soft),
    box-shadow 180ms var(--ease-out-soft),
    background-color 120ms linear;
}

.chat-fab:hover {
  transform: translate3d(0, -2px, 0) scale(1.03);
  box-shadow: 0 16px 38px rgba(43, 69, 216, 0.24);
}

.chat-panel {
  position: fixed;
  right: 24px;
  bottom: 90px;
  width: min(380px, calc(100vw - 32px));
  height: 520px;
  background: var(--color-surface);
  border: 1px solid var(--color-border-soft);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card-hover);
  display: grid;
  grid-template-rows: auto auto 1fr auto auto;
  overflow: hidden;
  z-index: 20;
  transform-origin: right bottom;
  animation: chat-panel-in 240ms var(--ease-out-soft) both;
}

@keyframes chat-panel-in {
  from {
    opacity: 0;
    transform: translate3d(10px, 14px, 0) scale(0.96);
  }
  to {
    opacity: 1;
    transform: translate3d(0, 0, 0) scale(1);
  }
}
```

### AI typing 指示器常驻循环
- 位置: `smart-mall/frontend/src/components/ChatWidget.vue:21-26,137-181`; `smart-mall/frontend/src/style.css:2346-2400`
- 触发方式: 请求中 assistant message loading
- 动画的 CSS 属性: `transform`、`opacity`
- 是否持续运行: 是，loading 气泡存在时 `typing-spin` 和 `typing-dot` 无限循环
- 完整代码原文:

```vue
<div v-if="m.loading && !m.content" class="typing-state" aria-live="polite">
  <span class="typing-ring" aria-hidden="true"></span>
  <span>{{ m.status || '正在组织回复' }}</span>
  <span class="typing-dots" aria-hidden="true"><i></i><i></i><i></i></span>
</div>
```

```css
.typing-ring {
  width: 15px;
  height: 15px;
  border: 2px solid rgba(43, 69, 216, 0.2);
  border-top-color: var(--color-accent);
  border-radius: 50%;
  animation: typing-spin 760ms linear infinite;
}

.typing-dots i {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--color-text-muted);
  animation: typing-dot 900ms ease-in-out infinite;
}

.typing-dots i:nth-child(2) {
  animation-delay: 120ms;
}

.typing-dots i:nth-child(3) {
  animation-delay: 240ms;
}

@keyframes typing-spin {
  to { transform: rotate(360deg); }
}

@keyframes typing-dot {
  0%, 100% {
    opacity: 0.35;
    transform: translateY(0);
  }
  45% {
    opacity: 1;
    transform: translateY(-3px);
  }
}
```

### 聊天气泡进入动画与快捷按钮 hover
- 位置: `smart-mall/frontend/src/components/ChatWidget.vue:21-31,33-38`; `smart-mall/frontend/src/style.css:2494-2516,2556-2570`
- 触发方式: 新消息插入、`hover`
- 动画的 CSS 属性: `opacity`、`transform`; 快捷按钮变化 `color`、`border-color`
- 是否持续运行: 否
- 完整代码原文:

```vue
<div v-for="(m, i) in messages" :key="i" class="bubble" :class="m.role">
  <div v-if="m.loading && !m.content" class="typing-state" aria-live="polite">
    <span class="typing-ring" aria-hidden="true"></span>
    <span>{{ m.status || '正在组织回复' }}</span>
    <span class="typing-dots" aria-hidden="true"><i></i><i></i><i></i></span>
  </div>
  <div v-else class="message-content" v-html="renderMarkdown(m.content)"></div>
  <div v-if="m.tools?.length" class="tool-badges">
    <span v-for="tool in m.tools" :key="tool">{{ toolLabel(tool) }}</span>
  </div>
</div>
```

```css
.chat-quick-actions button:hover {
  color: var(--color-text-primary);
  border-color: var(--color-accent);
}

.bubble.user,
.bubble.assistant {
  animation: bubble-in 180ms var(--ease-out-soft) both;
}

@keyframes bubble-in {
  from {
    opacity: 0;
    transform: translate3d(0, 8px, 0) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translate3d(0, 0, 0) scale(1);
  }
}
```

### Toast 进入/离开动画
- 位置: `smart-mall/frontend/src/components/GlobalToast.vue:1-7`; `smart-mall/frontend/src/composables/useToast.js:1-21`; `smart-mall/frontend/src/style.css:2534-2589`
- 触发方式: JS 调用 `toast.show`
- 动画的 CSS 属性: `opacity`、`transform`
- 是否持续运行: 否，显示 2400ms 后离开
- 完整代码原文:

```vue
<Transition name="toast">
  <div v-if="state.visible" class="toast" role="status" aria-live="polite">
    <span class="toast-dot" aria-hidden="true" />
    <span>{{ state.message }}</span>
  </div>
</Transition>
```

```js
import { reactive } from 'vue'

const state = reactive({
  visible: false,
  message: '',
  timer: null
})

export function useToast() {
  function show(message) {
    state.message = message
    state.visible = true
    if (state.timer) window.clearTimeout(state.timer)
    state.timer = window.setTimeout(() => {
      state.visible = false
      state.timer = null
    }, 2400)
  }

  return { state, show }
}
```

```css
.toast-enter-active {
  transition:
    opacity 180ms linear,
    transform 220ms var(--ease-out-soft);
}

.toast-leave-active {
  transition: opacity var(--duration-base) var(--ease-premium);
}

.toast-enter-from {
  opacity: 0;
  transform: translate3d(0, 10px, 0);
}

.toast-leave-to {
  opacity: 0;
  transform: translate3d(0, 6px, 0);
}
```

### prefers-reduced-motion 全局降级
- 位置: `smart-mall/frontend/src/style.css:2935-2986`
- 触发方式: 系统设置 `prefers-reduced-motion: reduce`
- 动画的 CSS 属性: 全局 `animation-duration`、`transition-duration`; 多处 `transform`、`opacity`
- 是否持续运行: 否，媒体查询下强制把动画压缩到一次极短执行
- 完整代码原文:

```css
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }

  .commerce-hero,
  .commerce-hero h1,
  .commerce-hero > *,
  .route-fade-enter-from,
  .route-fade-leave-to,
  .search-dock,
  .chat-panel,
  .chat-fab,
  .bubble,
  .cart-item-card:hover,
  .cart-list-enter-from,
  .cart-list-leave-to,
  .cart-qty.bump,
  .cart-total-chip.pulse,
  .checkout-head strong.pulse,
  .product-page .category-scroll,
  .chip,
  .topnav a:hover,
  .btn:hover,
  button:hover,
  .btn:active,
  button:active,
  .product-card:hover,
  .product-card .add-to-cart-btn:hover,
  .add-to-cart-btn:active,
  .product-card:hover .product-media.is-loaded .product-image,
  .product-card:hover .product-media .product-image,
  .product-card:hover .product-media img {
    transform: none !important;
  }

  .image-shimmer {
    animation: none !important;
  }

  .cart-fly-orb {
    display: none !important;
  }

  .commerce-hero {
    opacity: 1 !important;
  }
}
```

### preventDefault / scrollTop / scrollTo 核查原文
- 位置: `smart-mall/frontend/src/views/ProductListView.vue:15`; `smart-mall/frontend/src/views/CheckoutView.vue:48`; `smart-mall/frontend/src/components/ChatWidget.vue:39`; `smart-mall/frontend/scripts/perf-real-browser.mjs:217-224`
- 触发方式: 表单提交阻止默认行为；诊断脚本滚动页面
- 动画的 CSS 属性: 不适用
- 是否持续运行: 否
- 完整代码原文:

```vue
<form class="search-box catalog-search" @submit.prevent="search">
```

```vue
<form class="form checkout-form" @submit.prevent="submit">
```

```vue
<form @submit.prevent="send">
```

```js
scroll: async (p) => {
  await p.evaluate(() => window.scrollTo(0, 0))
  await p.waitForTimeout(100)
  for (let i = 0; i < 24; i += 1) {
    await p.mouse.wheel(0, 140)
    await p.waitForTimeout(45)
  }
},
```

说明: 应用源码中未发现显式 `preventDefault()` 调用或业务代码对 `scrollTop`/`scrollTo` 赋值；上面的 `window.scrollTo` 位于诊断脚本，不进入生产构建。

## Part B - 逐项隔离的真实浏览器性能测量

### B.1 真实 Chrome CDP 链路状态

项目中已补齐/存在的诊断文件:
- `smart-mall/frontend/scripts/perf-real-browser.mjs`
- `smart-mall/frontend/src/dev/perfHud.js`
- `smart-mall/frontend/src/dev/fxDiagnostics.js`
- `smart-mall/frontend/src/main.js:12-15` 仅在 `import.meta.env.DEV || import.meta.env.VITE_PERF_HUD === '1'` 时加载诊断模块

诊断入口原文:

```js
if (import.meta.env.DEV || import.meta.env.VITE_PERF_HUD === '1') {
  import('./dev/fxDiagnostics').then((module) => module.mountFxDiagnostics())
  import('./dev/perfHud').then((module) => module.mountPerfHud())
}
```

人工需要先在 Windows 上跑一次，这一步由人工完成:

```powershell
& "C:\Program Files\Google\Chrome\Application\chrome.exe" `
  --remote-debugging-port=9222 --remote-allow-origins=* `
  --user-data-dir="C:\temp\chrome-debug-profile" `
  http://127.0.0.1:4174/
```

Codex 当前尝试连接:

```powershell
Invoke-RestMethod -Uri http://localhost:9222/json/version -TimeoutSec 3
```

当前结果: 超时，`localhost:9222` 未连接。因此本次无法产出真实浏览器 fps/longtask 数字。报告不伪造 headless 或 CDP 数据。

### B.2 诊断开关机制

新增文件: `smart-mall/frontend/src/dev/fxDiagnostics.js`  
用途: 仅诊断用途，不进入普通生产构建；仅在开发模式或 `VITE_PERF_HUD=1` 构建中通过动态 import 加载。

使用方式:
- 关闭指定效果: `http://127.0.0.1:4174/?fx-off=hero-scroll,card-hover`
- 关闭全部效果: `http://127.0.0.1:4174/?fx-off=all`
- 只开启某一项，其余全部关闭: `http://127.0.0.1:4174/?fx-only=card-hover`

诊断开关原文:

```js
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
```

CSS 开关层位置: `smart-mall/frontend/src/style.css:2592-2932`。这些规则只在 `<html>` 出现 `fx-off-*` class 时生效，普通访问不改变现有行为。

示例原文:

```css
/* Diagnostic-only effect isolation switches.
   These classes are mounted only by src/dev/fxDiagnostics.js in dev/VITE_PERF_HUD builds. */
html.fx-off-card-hover .product-card,
html.fx-off-card-hover .product-card::after {
  transition: none;
}

html.fx-off-card-hover .product-card:hover {
  border-color: rgba(17, 19, 24, 0.10);
  transform: none;
}

html.fx-off-card-hover .product-card:hover::after {
  opacity: 0;
}
```

### B.3 真实浏览器采样脚本

脚本位置: `smart-mall/frontend/scripts/perf-real-browser.mjs`  
模式:
- 默认模式: 复用原来的 `scroll`、`hover`、`category`、`add-cart` 场景
- 矩阵模式: `FX_MATRIX=1` 时先测各场景 `fx-off=all` 基线，再逐项 `fx-only=<effect>` 测量
- 单项模式: `FX_ONLY=card-hover` 或 `FX_OFF=all`

建议人工执行流程:

```powershell
cd smart-mall/frontend
$env:VITE_PERF_HUD='1'
npm run build
npm run preview -- --port 4174
```

另开 Windows PowerShell 启动 Chrome CDP:

```powershell
& "C:\Program Files\Google\Chrome\Application\chrome.exe" `
  --remote-debugging-port=9222 --remote-allow-origins=* `
  --user-data-dir="C:\temp\chrome-debug-profile" `
  http://127.0.0.1:4174/
```

确认 CDP:

```powershell
Invoke-RestMethod -Uri http://localhost:9222/json/version
```

运行矩阵:

```powershell
$env:FX_MATRIX='1'
$env:PAGE_URL='http://127.0.0.1:4174/'
$env:CDP_URL='http://localhost:9222'
node scripts/perf-real-browser.mjs
```

注意: `cart/profile/orders/admin/order-detail` 场景需要 Chrome profile 已有登录态和测试数据，否则脚本会进入空状态或登录页，数字不能代表目标页面动效成本。

### B.4 当前自动化验证结果

已执行:

```powershell
npm run build
```

结果: 通过，普通生产构建没有输出 `fxDiagnostics`/`perfHud` 独立 chunk。

已执行:

```powershell
$env:VITE_PERF_HUD='1'; npm run build
```

结果: 通过，perf 构建输出 `fxDiagnostics-u1qQeHeE.js` 与 `perfHud-DkaKFdyJ.js`，符合诊断构建预期。

已执行:

```powershell
node --check scripts\perf-real-browser.mjs
```

结果: 通过，无语法错误。

### B.5 逐项隔离性能表

当前状态: 未能连接真实 Chrome CDP，以下所有 fps/longtask 数据均未测。保留表格是为了让人工完成 CDP 后直接填入，不把 headless 或环境上限数据混入真实结论。

| 效果名称 | 触发场景 | 基线 fps(全关) | 单独开启此项 fps | Δ 下降 | longtask | 判定(是否是可疑瓶颈) |
|---|---|---:|---:|---:|---|---|
| nav-hover | nav-hover | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| nav-active-underline | route | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| route-fade | route | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| search-focus | search-focus | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| scroll-inertia | scroll | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| hero-enter | load | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| button-hover | hover | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| button-active | hover | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| select-focus | search-focus | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| form-focus | profile | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| product-list | category | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| card-hover | hover | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| card-shine | hover | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| image-shimmer | load | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| image-load | load | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| image-hover-zoom | hover | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| badge-hover | hover | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| chip-hover | category | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| chip-active | category | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| swap-controls | cart | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| add-cart-hover | hover | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| cart-fly | add-cart | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| cart-pulse | add-cart | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| table-row-hover | admin | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| admin-card-hover | admin | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| cart-item-hover | cart | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| cart-selection | cart | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| cart-item-state | cart | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| checkout-meter | cart | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| cart-list | cart | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| amount-pulse | cart | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| qty-bump | cart | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| order-card-hover | orders | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| timeline-state | order-detail | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| preference-hover | profile | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| chat-fab | chat-open | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| chat-panel | chat-open | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| typing-indicator | chat-open | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| bubble-in | chat-open | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| quick-action-hover | chat-open | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |
| toast | add-cart | 未测(CDP未连接) | 未测 | 未测 | 未测 | 待测 |

## Part C - 环境核查清单

以下需要人工在 Windows 上确认，Codex 无法从当前环境检查。当前状态: 这些项目还没有人工确认。

- [ ] `chrome://gpu` 里 "Graphics Feature Status" 的 Compositing / Rasterization 是否显示 Hardware accelerated(不是 Software only 或 Disabled)
- [ ] Windows 系统设置 → 显示 → 当前显示器的刷新率是否确实设置为 120Hz(有没有被系统或驱动更新悄悄改回 60Hz)
- [ ] NVIDIA 控制面板里有没有设置"最大帧率"限制(部分笔记本预设省电策略会限到 60)
- [ ] 测试时笔记本是接电源还是用电池——很多笔记本在电池模式下会限制独显性能或直接切换到核显
- [ ] Windows 电源模式是否是"最佳性能",而不是"节能"或"平衡"模式下的省电策略
- [ ] 任务管理器里测试期间 GPU 占用曲线大概是什么样(哪怕只是描述"接近满载"或"很低几乎没用上独显",都是有用信息)

## Part D - Git 历史交叉核对

执行命令:

```powershell
git log --oneline --all -- smart-mall/frontend/src/composables/useScrollInertia.js
```

实际输出:

```text

```

执行命令:

```powershell
git log -p --all -- smart-mall/frontend/src/composables/useScrollInertia.js | Select-Object -First 300
```

实际输出:

```text

```

结论: 当前仓库没有 `smart-mall/frontend/src/composables/useScrollInertia.js` 的提交历史记录。该文件在当前工作区显示为未跟踪文件，因此无法从 Git 历史中贴出"最初的教训版本 -> 删除 -> 重新实现只读版本"的实际 diff。此处不做转述或推断。

