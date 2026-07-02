# 任务书:Smart Mall 前端视觉与交互升级(去后台化改造)

## 角色
你是本项目的前端负责人,精通 Vue3 + 现代C端电商视觉设计。本次任务只涉及**视觉层与交互层**,严禁改动业务逻辑、接口调用、状态管理结构(Pinia/Vuex store 的字段与方法签名保持不变)。

## 背景
当前商城首页(商品中心页)视觉上过于接近后台管理系统风格:大量 1px 描边、8px 以下小圆角、扁平投影、图片贴边无留白、信息等权重堆砌。目标是改造成 Apple Store / 无印良品式的极简高级零售视觉,同时补齐微交互动效。

## 设计基调(Design Tokens)——请在全局样式文件中新增或替换

```css
:root {
  --bg: #FAFAF9;
  --surface: #FFFFFF;
  --ink-900: #17171A;
  --ink-500: #8B8B8F;
  --line: #EDEDEA;
  --accent: #5F6F52;
  --accent-soft: #EEF1EA;

  --radius-card: 22px;
  --radius-input: 999px;
  --radius-image: 14px;

  --shadow-rest: 0 1px 2px rgba(0,0,0,0.04), 0 12px 24px rgba(0,0,0,0.06);
  --shadow-hover: 0 4px 8px rgba(0,0,0,0.06), 0 24px 48px rgba(0,0,0,0.10);

  --ease-out-soft: cubic-bezier(0.22, 1, 0.36, 1);
  --ease-spring: cubic-bezier(0.68, -0.55, 0.27, 1.55);

  --space-page-x: 72px;
  --space-card-gap: 36px;
}
```

请先在代码库中定位现有的全局样式入口(通常是 `src/assets/styles` 或 `src/styles` 下的 `variables.css` / `theme.css`,或 `src/App.vue` 的全局 style),将现有硬编码颜色值统一替换为上述变量。如果项目已有一套 token 系统,请合并而不是重复定义。

## 任务范围与执行顺序

请严格按以下顺序执行,每完成一步做一次自检(如环境支持,截图对比),再进入下一步:

### Step 1 — 全局样式基建
- 引入上方 design tokens
- 定位现有商品中心页面对应的组件文件(可能是 `src/views/Product*.vue` / `src/views/Home*.vue`,请自行搜索包含"商品中心"字样或对应路由的文件)
- 全局背景色替换为 `var(--bg)`,页面左右内边距扩大到 `var(--space-page-x)`

### Step 2 — 侧边导航组件
- 移除当前激活态的纯色矩形背景填充
- 改为:非激活态图标/文字使用 `var(--ink-500)`;激活态左侧加 3px 竖线指示条(`var(--ink-900)` 或 `var(--accent)`),文字加粗,不使用整块背景色
- hover 态背景轻染 `var(--accent-soft)`,圆角 12px,过渡 200ms ease

### Step 3 — 搜索栏与分类标签
- 搜索输入框圆角改为 `var(--radius-input)`(胶囊形),边框改为 1px `var(--line)`
- 聚焦态:边框替换为 `box-shadow: 0 0 0 4px rgba(95,111,82,0.12)`(accent色低透明度光晕),不使用加粗边框表达聚焦
- 排序下拉与搜索按钮视觉上整合进搜索胶囊,减少三个独立矩形并排的割裂感
- 分类标签(chips):默认展示区域限定高度,超出的分类改为横向可滑动容器,右侧添加渐隐遮罩(`linear-gradient` mask)提示可滑动
- 选中态分类:用 `var(--accent-soft)` 底色 + `var(--accent)` 文字,替代当前纯黑实心块

### Step 4 — 商品卡片组件(本任务核心,优先级最高)
定位商品卡片组件文件(可能是 `src/components/ProductCard.vue` 或内联在列表页中,若是内联请抽成独立组件)。按以下规则重写:

- 去除卡片的 `border: 1px solid` 描边,改用 `box-shadow: var(--shadow-rest)`
- 圆角从现有值改为 `var(--radius-card)`
- 图片外层新增容器,`padding: 20px`,背景 `var(--bg)`,图片本身圆角 `var(--radius-image)`,不再贴满卡片边缘
- HOT / 热销角标:改为胶囊形,半透明白底 + `backdrop-filter: blur(6px)`,不用纯黑直角矩形
- 卡片内文字层级重排:
  - 标题:15px, font-weight 600, `var(--ink-900)`
  - 描述:13px, `var(--ink-500)`, line-height 1.6
  - 价格:单独一行,26–28px, font-weight 700, `var(--ink-900)`
  - 库存/销量:12px, `var(--ink-500)`,与价格分行,不再与价格同行左右对齐
- hover 交互:`transform: translateY(-6px) scale(1.01)`,`box-shadow` 切换到 `var(--shadow-hover)`,`transition: 380ms var(--ease-out-soft)`
- 图片加载:实现骨架屏占位(shimmer 扫光效果)+ 加载完成后 `opacity 0→1` 且 `scale 1.05→1` 的渐显动画,600ms ease-out,避免布局跳动(CLS)

### Step 5 — 列表入场与分类切换动效
- 商品网格首次渲染 / 分类切换后重新渲染时,卡片按索引做 stagger 入场(每张延迟约 40ms,`opacity 0→1` + `translateY 16px→0`)
- 优先使用 `@vueuse/motion`(如项目未安装,执行 `npm install @vueuse/motion` 并在 `main.ts` 中按官方文档接入);仅在需要更复杂编排(如滚动触发分批加载)时才引入 GSAP + ScrollTrigger,避免过度增加依赖体积

### Step 6(可选,若时间允许)— 路由级过渡
- 若项目使用 Vue Router 4 且目标浏览器支持 View Transitions API,可在路由守卫中包裹 `document.startViewTransition`,实现列表页→详情页的图片共享元素过渡
- 若不确定浏览器兼容性影响范围,此步骤可跳过,不影响验收

## 硬性约束(请务必遵守)
1. 不修改任何接口请求逻辑、Pinia/Vuex store 结构、路由 path、组件对外暴露的 props/emits
2. 所有动画使用 `transform` 和 `opacity` 实现,不要用引起重排的属性(如 `width`/`height`/`top`/`left`)做动画,保证性能
3. 保留原有功能完整性:搜索、排序、分类筛选、加入购物车、收藏必须在改造后正常工作
4. 响应式:改造后的样式需要在移动端宽度(如 375px)下正常显示,不出现横向溢出
5. 尊重 `prefers-reduced-motion`:为所有新增的 transform/opacity 动画添加媒体查询降级,减少动效的用户不应看到位移/缩放动画,保留透明度渐变即可

```css
@media (prefers-reduced-motion: reduce) {
  * { transition-duration: 0.01ms !important; animation-duration: 0.01ms !important; }
}
```

## 验收标准(Acceptance Criteria)
- [ ] 商品卡片、搜索框上不再有可见的 `1px solid` 灰色描边(装饰性 `var(--line)` 分割线除外)
- [ ] 卡片圆角、搜索框圆角均 ≥ 16px
- [ ] 商品卡片 hover 时同时具备位移 + 阴影两种反馈,过渡时长在 300–450ms 之间
- [ ] 商品图片加载具备骨架屏占位,无明显布局跳动
- [ ] 分类切换或列表渲染时卡片有 stagger 入场效果,不是瞬间跳变
- [ ] 侧边导航激活态不再是整块纯黑背景填充
- [ ] 页面整体左右留白、卡片间距较改造前明显增大
- [ ] 原有全部交互功能(搜索/排序/分类/购物车/收藏)回归测试通过
- [ ] 移动端(375px)下无横向滚动、无元素重叠

## 交付方式
请在每个 Step 完成后输出一个简要的 diff 摘要(改了哪些文件、核心改动点),全部完成后给出一份改造前后的对比说明,方便我快速 review。如果环境支持截图,请在 Step 4 和全部完成后各截一次图用于自检。
