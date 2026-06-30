# AGENTS.md — 智能商城最终视觉规格（DESIGN_SYSTEM_FINAL）

> **本文档替代之前的 `FRONTEND_RESTYLE_V2_APPLE.md` 和 `LIQUID_GLASS_PATCH.md`，以这两份为参考背景，但执行时只看本文档。**
> 设计来源：用户提供的Tailwind+Vue3 CDN原型demo（`premium_product_center.html`），已验证其动效曲线、配色克制度与此前确认的"丝滑精致"方向完全吻合。
> 实现方式：**用纯CSS（Vue SFC的`<style scoped>`）复刻这份demo的视觉语言，不引入Tailwind、不替换现有Element Plus组件**，除非后续明确要求切换技术栈。
> 前置条件：**乱码bug必须先修复并验证通过**，再执行本文档的视觉改造。
> 范围边界：只改`<template>`结构和`<style>`，不碰任何业务逻辑、API调用、store、路由、props/emit接口。

---

## 1. 设计Token（从demo精确提取）

```css
/* src/styles/tokens.css */
:root {
  /* 中性灰阶，直接对应Tailwind的neutral色板数值，保证还原度 */
  --color-bg: #fafafa;            /* neutral-50 / 页面背景 */
  --color-surface: #ffffff;       /* 卡片、侧边栏、输入框背景 */
  --color-border: #f5f5f5;        /* neutral-100 / 默认边框，极浅 */
  --color-border-soft: rgba(229, 229, 229, 0.8); /* neutral-200/80 / 输入框边框 */
  --color-text-primary: #171717;  /* neutral-900 */
  --color-text-secondary: #737373;/* neutral-500 */
  --color-text-muted: #a3a3a3;    /* neutral-400 */
  --color-accent: #0a0a0a;        /* neutral-950，唯一强调色，纯黑而非彩色 */
  --color-accent-hover: #262626;  /* neutral-800 */

  /* 圆角节奏，与demo一致 */
  --radius-input: 12px;   /* rounded-xl */
  --radius-card: 16px;    /* rounded-2xl */
  --radius-pill: 999px;   /* rounded-full，分类标签/胶囊按钮 */
  --radius-tag: 6px;      /* rounded-md，角标 */

  /* 唯一缓动曲线，全站统一，不允许出现其他曲线 */
  --ease-premium: cubic-bezier(0.16, 1, 0.3, 1);
  --duration-base: 500ms;   /* 对应demo的 .premium-transition */
  --duration-image: 700ms;  /* 对应demo的 .image-transition，图片缩放专用，更慢 */

  /* 卡片悬浮阴影：极浅、大扩散半径，这是"高级感"的关键参数，不要随意改数值 */
  --shadow-card-hover: 0 24px 60px rgba(0, 0, 0, 0.03);
  --shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.04);
}
```

**字体**：demo用的是Inter，如果项目已有中文字体方案，建议写成 `font-family: 'Inter', '苹方-简', 'PingFang SC', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;`——Inter本身不含中文字形，纯英文/数字（价格、库存数字）会用Inter渲染出demo里那种精致的数字字形，中文部分会自动回退到苹方/系统字体，这是预期行为，不是bug。

---

## 2. 全局动效规则

- **全站只允许一条缓动曲线** `var(--ease-premium)`，时长只有两档：`--duration-base`（500ms，给颜色/位移/阴影/透明度过渡用）和`--duration-image`（700ms，专门给图片`scale`用，比其他过渡更慢，这是demo里图片缩放"格外丝滑"的秘密）
- 所有交互态的class统一命名 `.premium-transition`（对应通用过渡）和 `.image-transition`（对应图片专用），不要让组件各自定义transition参数

```css
.premium-transition {
  transition: all var(--duration-base) var(--ease-premium);
}
.image-transition {
  transition: transform var(--duration-image) var(--ease-premium);
}
```

---

## 3. 组件级规格（逐一对应demo的实现，标注还原要点）

### 3.1 侧边栏导航

- 固定宽度容器，白底，右边框用`--color-border`（极浅，几乎看不见，靠这种"几乎没有边框"营造干净感）
- 导航项默认态：灰色文字（`--color-text-secondary`），hover时文字变深+背景出现极浅灰色色块
- 选中态：背景纯黑（`--color-accent`）+ 白色文字 + 轻微阴影（`--shadow-sm`），**不要用任何渐变或彩色**，纯黑块本身就是"强调色"
- 图标用细线条风格（stroke-width 1.5），跟随文字颜色变化

### 3.2 顶部搜索/排序栏

- 输入框白底+极浅边框，**默认没有阴影**，只有focus时边框变黑+1px外环（`focus-visible`效果），不用蓝色系统默认focus环
- 搜索图标放在输入框左侧内嵌位置（`position: absolute`），不要做成搜索框外面再放一个图标按钮的笨拙布局
- 排序下拉框样式与输入框统一（同样的圆角、边框、focus效果），不要用浏览器默认select样式

### 3.3 分类标签（胶囊按钮组）

- 未选中：白底+浅边框+灰色文字，hover时背景变浅灰
- 选中：纯黑底+白字，**圆角是`--radius-pill`（完全圆形两端）**，不是`--radius-card`——这是分类标签和卡片的关键区别，别搞混
- 字号比正文小一档，字间距拉宽（`letter-spacing`），这是demo里"高级感"的细节来源之一，不要用默认字间距

### 3.4 商品卡片（重点，价格⇄购物车按钮切换是核心交互）

**结构**：图片区（4:3比例，裁切）→ 标题+描述 → 价格/购物车切换区，三段式纵向布局。

**Hover整体效果**：
```css
.product-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border-soft);
  border-radius: var(--radius-card);
  padding: 1rem;
  transition: all var(--duration-base) var(--ease-premium);
}
.product-card:hover {
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-6px); /* -translate-y-1.5，约6px，幅度刻意小 */
}
.product-card:hover .product-image {
  transform: scale(1.05);
}
```

**角标**（NEW/HOT/PRO）：黑色半透明背景+白字+`backdrop-filter: blur(4px)`——这是demo里**唯一**用到玻璃模糊的地方，刻意局限在小角标上，不要扩大到整个卡片，原因见此前讨论的性能边界。

**核心交互——价格区与购物车按钮的位置互换**（这是整份demo最值得复刻的细节，必须精确还原）：

```html
<!-- 容器：固定高度，overflow hidden，两层内容绝对定位叠在同一位置 -->
<div class="price-cart-swap">
  <div class="price-info">
    <span class="price">¥{{ product.price }}</span>
    <span class="meta">库存 {{ product.stock }} · 已售 {{ product.sales }}</span>
  </div>
  <button class="add-to-cart-btn" @click="addToCart(product)">
    加入购物车
  </button>
</div>
```

```css
.price-cart-swap {
  position: relative;
  height: 44px;
  overflow: hidden;
  margin-top: 0.5rem;
}
.price-info {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  transition: all var(--duration-base) var(--ease-premium);
}
.product-card:hover .price-info {
  opacity: 0;
  transform: translateY(-16px); /* 向上滑出消失 */
}
.add-to-cart-btn {
  position: absolute;
  inset: 0;
  width: 100%;
  background: var(--color-accent);
  color: white;
  border-radius: var(--radius-input);
  opacity: 0;
  transform: translateY(16px); /* 默认藏在下方 */
  transition: all var(--duration-base) var(--ease-premium);
}
.product-card:hover .add-to-cart-btn {
  opacity: 1;
  transform: translateY(0); /* hover时滑入到位 */
}
```

**这个模式不要只用在商品列表页，推广到**：
- 购物车页的"数量调整↔删除"切换
- 订单卡片的"查看详情↔取消订单"按钮切换
- 个人中心"编辑资料↔保存"按钮切换

统一用同一个`.swap-container`基础类，保证全站这类"悬浮替换"交互手感一致。

### 3.5 分页器

- 极简文字式分页（"← 上一页 / 第X页·共N件 / 下一页 →"），**不做数字按钮组**，这是demo选择的克制方案，比常见的"1 2 3 4 5"按钮组更安静
- 禁用态（如第一页的"上一页"）用`opacity: 0.3`+`cursor: not-allowed`，不要直接`display: none`隐藏，保持布局稳定

### 3.6 Toast通知

- 固定右下角，黑色圆角矩形，内含一个绿色呼吸圆点（`animate-ping`效果：一个小圆点持续做"扩散消失"的脉冲动画）+ 文字
- 进入动效：从下方8px+透明度0过渡进入；退出：纯透明度淡出（进入和退出用不同的时长/曲线是有意为之，进入慢一点显得"郑重"，退出快一点不拖沓）

```css
.toast {
  position: fixed;
  bottom: 1.5rem;
  right: 1.5rem;
  background: var(--color-accent);
  color: white;
  border-radius: var(--radius-input);
  padding: 0.75rem 1rem;
  display: flex;
  align-items: center;
  gap: 8px;
  box-shadow: 0 10px 30px rgba(0,0,0,0.15);
}
.toast-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #4ade80;
  animation: toast-ping 1.5s cubic-bezier(0, 0, 0.2, 1) infinite;
}
@keyframes toast-ping {
  75%, 100% { transform: scale(2); opacity: 0; }
}
```

把这个Toast组件抽成全局唯一的通知组件（`useToast()`composable或全局单例组件），替换掉项目里现有的任何零散toast实现，保证全站通知样式统一。

---

## 4. 与Element Plus共存的处理原则

项目商品/表单类页面如果已经用了Element Plus组件（`el-select`、`el-input`、`el-pagination`等），处理方式：

- **优先用Element Plus的样式覆盖能力**，通过`:deep()`选择器或Element Plus提供的CSS变量（`--el-color-primary`等）去覆盖默认主题色，让它趋近上面的token方案，而不是整体替换组件
- 如果某个Element Plus组件的默认交互/视觉和本文档要求差异太大（比如`el-pagination`的数字按钮组风格，和本文档"极简文字式分页"要求冲突），**该组件可以替换成自定义实现**（如3.5节的分页器），但前提是不影响原有的分页逻辑/事件绑定（只换UI，不换逻辑）
- 不要为了视觉统一去重写Element Plus的核心交互组件（如表单校验、弹窗），那些保持现状即可，本文档只管"看起来像什么"，不管"组件库选型"这件事

---

## 5. 验收标准

- [ ] 全站走查：缓动曲线只有`var(--ease-premium)`一种，没有遗留其他transition曲线
- [ ] 商品卡片"价格⇄购物车按钮"悬浮切换效果与demo一致，且已推广到购物车/订单/个人中心至少各一处同类交互
- [ ] backdrop-filter（玻璃模糊）只出现在商品角标这一处，没有扩散到卡片整体或其他大面积区域
- [ ] Toast通知已统一成单一全局组件，替换了所有零散实现
- [ ] 颜色全部引用`tokens.css`变量，没有写死的十六进制色值散落在组件里
- [ ] 中文文字显示正常（字体回退正确），价格/数字用Inter渲染（如果引入了该字体）
- [ ] Element Plus组件的核心交互逻辑（校验、分页跳转、下拉选择）未被破坏，只是外观贴近了新token
- [ ] `git diff`确认没有引入Tailwind依赖，没有修改任何`.java`/`.py`后端文件

## 6. 不在本次范围内

- 不引入Tailwind CSS（CDN或npm均不引入），本次明确选择"纯CSS复刻"路线
- 不替换Element Plus作为表单/弹窗等复杂交互组件的基础库
- 不做暗色模式（demo本身也没有暗色变体，保持单一浅色方案，避免不必要的工作量）
- 不把"角标玻璃模糊"的效果扩散到其他元素（性能边界，已在3.4节说明）
