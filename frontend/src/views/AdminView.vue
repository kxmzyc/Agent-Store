<template>
  <section class="page admin-page">
    <div class="page-head">
      <div>
        <h1>运营工作台</h1>
        <p>集中处理商品、库存、订单状态和运营配置，答辩时可以直接展示管理侧闭环。</p>
      </div>
      <div class="toolbar admin-actions">
        <button type="button" @click="refreshAll">刷新</button>
        <button class="primary" type="button" @click="newProduct">新增商品</button>
      </div>
    </div>

    <div class="admin-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.value"
        :class="{ active: activeTab === tab.value }"
        type="button"
        @click="activeTab = tab.value"
      >
        {{ tab.label }}
      </button>
    </div>

    <p v-if="dashboardError" class="error">{{ dashboardError }}</p>

    <template v-if="activeTab === 'overview'">
      <div v-if="dashboard" class="metric-grid">
        <article v-for="metric in metrics" :key="metric.label" class="metric-card">
          <span>{{ metric.label }}</span>
          <strong>{{ metric.value }}</strong>
          <small>{{ metric.hint }}</small>
        </article>
      </div>

      <div v-if="dashboard" class="dashboard-grid">
        <section class="panel dashboard-panel">
          <div class="panel-head">
            <div>
              <h2>低库存预警</h2>
              <p>库存小于等于 10 的在售商品。</p>
            </div>
            <span class="count-pill">{{ dashboard.lowStockProducts.length }}</span>
          </div>
          <div v-if="!dashboard.lowStockProducts.length" class="empty-state">暂无库存风险</div>
          <div v-else class="compact-list">
            <article
              v-for="p in dashboard.lowStockProducts"
              :key="p.id"
              class="compact-item danger-line clickable-row"
              tabindex="0"
              @click="openLowStockProduct(p)"
              @keydown.enter="openLowStockProduct(p)"
            >
              <div>
                <strong>{{ p.name }}</strong>
                <small>{{ p.category || '未分类' }} · 已售 {{ p.salesCount }} · ¥{{ p.price }}</small>
              </div>
              <span class="stock-badge" :class="{ danger: p.stock === 0 }">库存 {{ p.stock }}</span>
            </article>
          </div>
        </section>

        <section class="panel dashboard-panel">
          <div class="panel-head">
            <div>
              <h2>热销商品</h2>
              <p>按销量排序，便于展示推荐来源。</p>
            </div>
          </div>
          <div class="compact-list">
            <article v-for="(p, index) in dashboard.topProducts" :key="p.id" class="compact-item">
              <span class="rank">{{ index + 1 }}</span>
              <div>
                <strong>{{ p.name }}</strong>
                <small>库存 {{ p.stock }} · ¥{{ p.price }}</small>
              </div>
              <b>{{ p.salesCount }}</b>
            </article>
          </div>
        </section>

        <section class="panel dashboard-panel">
          <div class="panel-head">
            <div>
              <h2>最近订单</h2>
              <p>快速检查订单状态流转。</p>
            </div>
          </div>
          <div v-if="!dashboard.recentOrders.length" class="empty-state">暂无订单</div>
          <div v-else class="compact-list">
            <article v-for="order in dashboard.recentOrders" :key="order.id" class="compact-item">
              <div>
                <strong>{{ order.orderNo }}</strong>
                <small>{{ formatTime(order.createdAt) }}</small>
              </div>
              <span class="status-pill">{{ orderLabel(order.status) }}</span>
              <b>¥{{ order.totalAmount }}</b>
            </article>
          </div>
        </section>

        <section class="panel dashboard-panel chart-panel">
          <div class="panel-head">
            <div>
              <h2>近 7 天订单趋势</h2>
              <p>用于现场展示运营监控能力。</p>
            </div>
          </div>
          <div ref="orderChartRef" class="chart-box"></div>
        </section>

        <section class="panel dashboard-panel chart-panel">
          <div class="panel-head">
            <div>
              <h2>分类销量 TOP 5</h2>
              <p>按订单明细数量聚合。</p>
            </div>
          </div>
          <div ref="categoryChartRef" class="chart-box"></div>
        </section>
      </div>
    </template>

    <template v-if="activeTab === 'products'">
      <div class="admin-filter-bar panel">
        <form class="search-box" @submit.prevent="loadProducts(1)">
          <Search size="18" />
          <input v-model="productFilters.keyword" placeholder="搜索商品名称或描述" />
        </form>
        <select v-model="productFilters.categoryId" @change="loadProducts(1)">
          <option :value="null">全部分类</option>
          <option v-for="c in flatCategories" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
        <select v-model="productFilters.status" @change="loadProducts(1)">
          <option value="all">全部状态</option>
          <option value="1">在售</option>
          <option value="0">已下架</option>
        </select>
        <button type="button" @click="loadProducts(1)">筛选</button>
      </div>

      <div v-if="editing" class="panel product-editor">
        <form class="form admin-form" @submit.prevent="save">
          <label>商品名 <input v-model="form.name" required /></label>
          <label>分类
            <select v-model.number="form.categoryId" required>
              <option v-for="c in flatCategories" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
          </label>
          <label>描述 <textarea v-model="form.description" /></label>
          <label>价格 <input v-model.number="form.price" type="number" min="0" step="0.01" required /></label>
          <label>库存 <input v-model.number="form.stock" type="number" min="0" required /></label>
          <label>图片 URL <input v-model="form.imageUrl" /></label>
          <label>商品标签
            <select v-model="form.tagIds" multiple size="3">
              <option v-for="tag in allTags" :key="tag.id" :value="tag.id">{{ tag.name }}</option>
            </select>
          </label>
          <div class="toolbar">
            <button class="primary">保存</button>
            <button type="button" @click="editing = false">取消</button>
          </div>
        </form>
      </div>

      <div class="section-title">
        <h2>商品管理</h2>
        <span class="muted">共 {{ productTotal }} 条商品记录</span>
      </div>

      <table class="table table-spaced admin-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>商品</th>
            <th>价格</th>
            <th>库存</th>
            <th>销量</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in products" :key="p.id" :class="{ 'row-inactive': p.status !== 1 }">
            <td>{{ p.id }}</td>
            <td>
              <strong>{{ p.name }}</strong>
              <small class="muted block-text">{{ p.description }}</small>
              <div v-if="p.tags?.length" class="tag-row">
                <span v-for="tag in p.tags" :key="tag.id" class="product-tag">{{ tag.name }}</span>
              </div>
            </td>
            <td>¥{{ p.price }}</td>
            <td>
              <div class="stock-stepper">
                <button type="button" :disabled="p.stock <= 0" @click="quickStock(p, -5)">-5</button>
                <strong>{{ p.stock }}</strong>
                <button type="button" @click="quickStock(p, 5)">+5</button>
              </div>
            </td>
            <td>{{ p.salesCount }}</td>
            <td><span class="status-pill">{{ p.status === 1 ? '在售' : '已下架' }}</span></td>
            <td>
              <div class="row-actions">
                <button type="button" @click="edit(p)">编辑</button>
                <button v-if="p.status === 1" type="button" @click="remove(p)">下架</button>
                <button v-else type="button" @click="activate(p)">上架</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="pager">
        <button :disabled="productPage <= 1" @click="loadProducts(productPage - 1)">上一页</button>
        <span class="muted">第 {{ productPage }} 页 · 共 {{ productTotal }} 条</span>
        <button :disabled="productPage * productSize >= productTotal" @click="loadProducts(productPage + 1)">下一页</button>
      </div>
    </template>

    <template v-if="activeTab === 'orders'">
      <div class="admin-filter-bar panel">
        <div class="status-tabs compact-tabs">
          <button
            v-for="tab in orderTabs"
            :key="tab.value"
            :class="{ active: orderStatus === tab.value }"
            type="button"
            @click="selectOrderStatus(tab.value)"
          >
            {{ tab.label }}
          </button>
        </div>
        <button class="primary" type="button" @click="exportOrders">
          <Download size="18" /> 导出 Excel
        </button>
      </div>

      <div class="admin-order-grid">
        <article v-for="order in orders" :key="order.id" class="panel admin-order-card">
          <div class="panel-head">
            <div>
              <h2>{{ order.orderNo }}</h2>
              <p>
                {{ formatTime(order.createdAt) }} ·
                {{ order.username || `用户${order.userId}` }} · 用户 ID {{ order.userId }}
                <span v-if="order.phone"> · {{ order.phone }}</span>
              </p>
            </div>
            <span class="status-pill">{{ orderLabel(order.status) }}</span>
          </div>
          <div class="order-lines">
            <div v-for="item in order.items" :key="`${order.id}-${item.productId}`">
              <span>{{ item.productName }}</span>
              <b>¥{{ item.price }} x {{ item.quantity }}</b>
            </div>
          </div>
          <div class="admin-order-foot">
            <strong>¥{{ order.totalAmount }}</strong>
            <button v-if="order.status === 'PAID'" class="primary" type="button" @click="ship(order)">标记发货</button>
            <button v-else type="button" disabled>{{ nextOrderAction(order.status) }}</button>
          </div>
        </article>
      </div>

      <div class="pager">
        <button :disabled="orderPage <= 1" @click="loadOrders(orderPage - 1)">上一页</button>
        <span class="muted">第 {{ orderPage }} 页 · 共 {{ orderTotal }} 条</span>
        <button :disabled="orderPage * orderSize >= orderTotal" @click="loadOrders(orderPage + 1)">下一页</button>
      </div>
    </template>

    <template v-if="activeTab === 'afterSales'">
      <div class="admin-filter-bar panel">
        <div class="status-tabs compact-tabs">
          <button
            v-for="tab in afterSaleTabs"
            :key="tab.value"
            :class="{ active: afterSaleStatus === tab.value }"
            type="button"
            @click="selectAfterSaleStatus(tab.value)"
          >
            {{ tab.label }}
          </button>
        </div>
        <button type="button" @click="loadAfterSales(afterSalePage)">刷新售后</button>
      </div>

      <div v-if="!afterSaleItems.length" class="panel empty-state">暂无售后申请</div>
      <div v-else class="after-sale-grid">
        <article v-for="item in afterSaleItems" :key="item.id" class="panel after-sale-card">
          <div class="panel-head">
            <div>
              <h2>{{ item.orderNo }}</h2>
              <p>用户 ID {{ item.userId }} · {{ formatTime(item.createdAt) }}</p>
            </div>
            <span class="status-pill">{{ item.statusLabel }}</span>
          </div>
          <dl class="order-summary after-sale-summary">
            <div>
              <dt>售后类型</dt>
              <dd>{{ item.typeLabel }}</dd>
            </div>
            <div>
              <dt>退款金额</dt>
              <dd class="price">¥{{ item.refundAmount }}</dd>
            </div>
            <div>
              <dt>申请原因</dt>
              <dd>{{ item.reason }}</dd>
            </div>
            <div v-if="item.handleRemark">
              <dt>处理说明</dt>
              <dd>{{ item.handleRemark }}</dd>
            </div>
          </dl>
          <div class="order-lines">
            <div v-for="line in item.items" :key="`${item.id}-${line.productId}`">
              <span>{{ line.productName }}</span>
              <b>¥{{ line.price }} x {{ line.quantity }}</b>
            </div>
          </div>
          <div class="admin-order-foot">
            <strong>{{ item.statusLabel }}</strong>
            <div class="row-actions">
              <button v-if="item.status === 0" class="primary" type="button" @click="approveAfterSale(item)">同意</button>
              <button v-if="item.status === 0" class="ghost danger-ghost" type="button" @click="rejectAfterSale(item)">拒绝</button>
              <button v-else type="button" disabled>{{ item.statusLabel }}</button>
            </div>
          </div>
        </article>
      </div>

      <div class="pager">
        <button :disabled="afterSalePage <= 1" @click="loadAfterSales(afterSalePage - 1)">上一页</button>
        <span class="muted">第 {{ afterSalePage }} 页 · 共 {{ afterSaleTotal }} 条</span>
        <button :disabled="afterSalePage * afterSaleSize >= afterSaleTotal" @click="loadAfterSales(afterSalePage + 1)">下一页</button>
      </div>
    </template>

    <template v-if="activeTab === 'feedback'">
      <div class="section-title">
        <h2>反馈管理</h2>
        <span class="muted">共 {{ feedbackItems.length }} 条反馈</span>
      </div>
      <div class="feedback-grid">
        <article v-for="item in feedbackItems" :key="item.id" class="panel feedback-card">
          <div class="panel-head">
            <div>
              <h2>{{ feedbackType(item.type) }} · {{ item.username }}</h2>
              <p>{{ formatTime(item.createdAt) }}</p>
            </div>
            <span class="status-pill">{{ item.status === 1 ? '已处理' : '待处理' }}</span>
          </div>
          <p>{{ item.content }}</p>
          <div v-if="item.reply" class="feedback-reply">回复：{{ item.reply }}</div>
          <form class="feedback-reply-form" @submit.prevent="replyFeedback(item)">
            <textarea v-model="feedbackReplies[item.id]" placeholder="填写处理回复" />
            <button class="primary">回复</button>
          </form>
        </article>
      </div>
    </template>

    <template v-if="activeTab === 'banners'">
      <section class="panel product-editor">
        <div class="panel-head">
          <div>
            <h2>推荐位配置</h2>
            <p>前台 Banner 从这里读取启用商品，按排序值升序展示。</p>
          </div>
        </div>
        <form class="form admin-form" @submit.prevent="createBannerSlot">
          <label>商品
            <select v-model.number="bannerForm.productId" required>
              <option :value="null" disabled>选择商品</option>
              <option v-for="p in products" :key="p.id" :value="p.id">{{ p.name }}</option>
            </select>
          </label>
          <label>排序 <input v-model.number="bannerForm.sortOrder" type="number" min="0" /></label>
          <label class="check-control">
            <input v-model="bannerForm.active" type="checkbox" />
            <span>启用</span>
          </label>
          <button class="primary">新增推荐位</button>
        </form>
      </section>

      <table class="table table-spaced admin-table">
        <thead>
          <tr><th>ID</th><th>商品</th><th>排序</th><th>状态</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="slot in bannerSlots" :key="slot.id">
            <td>{{ slot.id }}</td>
            <td>{{ slot.product?.name || `商品 ${slot.product?.id || '-'}` }}</td>
            <td><input v-model.number="slot.sortOrder" class="table-input" type="number" min="0" /></td>
            <td>
              <label class="check-control inline-check">
                <input v-model="slot.active" type="checkbox" />
                <span>{{ slot.active ? '启用' : '停用' }}</span>
              </label>
            </td>
            <td>
              <div class="row-actions">
                <button type="button" @click="updateBannerSlot(slot)">保存</button>
                <button class="ghost danger-ghost" type="button" @click="deleteBannerSlot(slot)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </template>

    <template v-if="activeTab === 'keywords'">
      <div class="section-title">
        <h2>搜索热词</h2>
        <span class="muted">屏蔽后不会出现在前台热搜标签中</span>
      </div>
      <table class="table table-spaced admin-table">
        <thead>
          <tr><th>关键词</th><th>次数</th><th>最后搜索</th><th>状态</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="item in keywordItems" :key="item.keyword">
            <td>{{ item.keyword }}</td>
            <td>{{ item.count }}</td>
            <td>{{ formatTime(item.lastSearchedAt) }}</td>
            <td><span class="status-pill">{{ item.blocked ? '已屏蔽' : '正常' }}</span></td>
            <td><button type="button" @click="toggleKeyword(item)">{{ item.blocked ? '取消屏蔽' : '屏蔽' }}</button></td>
          </tr>
        </tbody>
      </table>
    </template>

    <template v-if="activeTab === 'tags'">
      <section class="panel product-editor">
        <div class="panel-head">
          <div>
            <h2>商品标签</h2>
            <p>标签会展示在商品卡片和详情页。</p>
          </div>
        </div>
        <form class="form admin-form" @submit.prevent="createTag">
          <label>标签名称 <input v-model="tagName" required maxlength="32" /></label>
          <button class="primary">新增标签</button>
        </form>
      </section>
      <div class="tag-admin-list">
        <span v-for="tag in allTags" :key="tag.id" class="preference-chip">
          <span>{{ tag.name }}</span>
          <button type="button" title="删除标签" @click="deleteTag(tag)">x</button>
        </span>
      </div>
    </template>

    <template v-if="activeTab === 'logs'">
      <div class="admin-filter-bar panel">
        <input v-model="logFilters.adminId" placeholder="管理员 ID" />
        <input v-model="logFilters.action" placeholder="动作，如 product.update" />
        <button type="button" @click="loadOperationLogs(1)">筛选</button>
      </div>
      <table class="table table-spaced admin-table">
        <thead>
          <tr><th>ID</th><th>管理员</th><th>动作</th><th>对象</th><th>目标ID</th><th>时间</th></tr>
        </thead>
        <tbody>
          <tr v-for="item in operationLogs" :key="item.id">
            <td>{{ item.id }}</td>
            <td>{{ item.adminId }}</td>
            <td>{{ item.action }}</td>
            <td>{{ item.targetType }}</td>
            <td>{{ item.targetId || '-' }}</td>
            <td>{{ formatTime(item.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
      <div class="pager">
        <button :disabled="logPage <= 1" @click="loadOperationLogs(logPage - 1)">上一页</button>
        <span class="muted">第 {{ logPage }} 页 · 共 {{ logTotal }} 条</span>
        <button :disabled="logPage * logSize >= logTotal" @click="loadOperationLogs(logPage + 1)">下一页</button>
      </div>
    </template>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { Download, Search } from 'lucide-vue-next'
import { api, errorMessage } from '../api/http'
import { useToast } from '../composables/useToast'

const tabs = [
  { value: 'overview', label: '概览' },
  { value: 'products', label: '商品' },
  { value: 'orders', label: '订单' },
  { value: 'afterSales', label: '售后' },
  { value: 'banners', label: '推荐位' },
  { value: 'keywords', label: '热词' },
  { value: 'tags', label: '标签' },
  { value: 'logs', label: '日志' },
  { value: 'feedback', label: '反馈' }
]

const orderTabs = [
  { value: 'all', label: '全部' },
  { value: 'PENDING_PAYMENT', label: '待付款' },
  { value: 'PAID', label: '待发货' },
  { value: 'SHIPPED', label: '待收货' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'REFUNDED', label: '已退款' }
]

const orderLabels = Object.fromEntries(orderTabs.filter(t => t.value !== 'all').map(t => [t.value, t.label]))
const afterSaleTabs = [
  { value: '0', label: '待审核' },
  { value: 'all', label: '全部' },
  { value: '3', label: '已完成' },
  { value: '2', label: '已拒绝' }
]
const activeTab = ref('overview')
const categories = ref([])
const products = ref([])
const productTotal = ref(0)
const productPage = ref(1)
const productSize = 12
const orders = ref([])
const orderTotal = ref(0)
const orderPage = ref(1)
const orderSize = 8
const orderStatus = ref('all')
const afterSaleItems = ref([])
const afterSaleTotal = ref(0)
const afterSalePage = ref(1)
const afterSaleSize = 8
const afterSaleStatus = ref('0')
const feedbackItems = ref([])
const bannerSlots = ref([])
const keywordItems = ref([])
const allTags = ref([])
const operationLogs = ref([])
const logPage = ref(1)
const logTotal = ref(0)
const logSize = 12
const dashboard = ref(null)
const dashboardError = ref('')
const orderChartRef = ref(null)
const categoryChartRef = ref(null)
const editing = ref(false)
const form = reactive(blank())
const toast = useToast()
const productFilters = reactive({ keyword: '', categoryId: null, status: 'all' })
const feedbackReplies = reactive({})
const bannerForm = reactive({ productId: null, sortOrder: 0, active: true })
const tagName = ref('')
const logFilters = reactive({ adminId: '', action: '' })
let echartsModule = null
let orderChart = null
let categoryChart = null

const flatCategories = computed(() => categories.value.flatMap(c => [c, ...(c.children || [])]))

const metrics = computed(() => {
  const data = dashboard.value?.metrics
  if (!data) return []
  return [
    { label: '今日订单', value: dashboard.value.todayOrders, hint: `${data.pendingPaymentOrders} 个待付款` },
    { label: '本月销售额', value: `¥${dashboard.value.monthRevenue}`, hint: '不含已取消/已退款订单' },
    { label: '在售商品', value: dashboard.value.productCount, hint: `${data.soldOutProducts} 个商品售罄` },
    { label: '注册用户', value: dashboard.value.userCount, hint: '当前账户总量' },
    { label: '订单总数', value: data.orders, hint: '全量订单记录' },
    { label: '有效成交额', value: `¥${data.effectiveRevenue}`, hint: '历史有效成交' },
    { label: '待发货', value: data.paidOrders, hint: '需要管理员处理' },
    { label: '配送中', value: data.shippedOrders, hint: `${data.completedOrders} 个已完成` }
  ]
})

onMounted(async () => {
  await loadCategories()
  await loadTags()
  await refreshAll()
})

onBeforeUnmount(() => {
  orderChart?.dispose()
  categoryChart?.dispose()
})

watch(activeTab, async (tab) => {
  if (tab === 'orders' && !orders.value.length) await loadOrders(1)
  if (tab === 'afterSales' && !afterSaleItems.value.length) await loadAfterSales(1)
  if (tab === 'feedback' && !feedbackItems.value.length) await loadFeedback()
  if (tab === 'banners' && !bannerSlots.value.length) await loadBannerSlots()
  if (tab === 'keywords' && !keywordItems.value.length) await loadKeywords()
  if (tab === 'tags' && !allTags.value.length) await loadTags()
  if (tab === 'logs' && !operationLogs.value.length) await loadOperationLogs(1)
  if (tab === 'overview') await renderCharts()
})

watch(dashboard, async () => {
  if (activeTab.value === 'overview') await renderCharts()
})

function blank() {
  return { id: null, categoryId: 1, name: '', description: '', price: 0, stock: 0, imageUrl: '', status: 1, tagIds: [] }
}

async function loadCategories() {
  try {
    const { data } = await api.get('/categories')
    categories.value = data || []
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

async function refreshAll() {
  const tasks = [loadDashboard(), loadProducts(productPage.value), loadOrders(orderPage.value)]
  if (activeTab.value === 'afterSales' || afterSaleItems.value.length) tasks.push(loadAfterSales(afterSalePage.value))
  await Promise.all(tasks)
}

async function loadDashboard() {
  dashboardError.value = ''
  try {
    const { data } = await api.get('/admin/dashboard')
    dashboard.value = data
  } catch (e) {
    dashboardError.value = errorMessage(e)
    toast.show(dashboardError.value)
  }
}

async function renderCharts() {
  if (!dashboard.value) return
  await nextTick()
  if (!orderChartRef.value || !categoryChartRef.value) return
  if (!echartsModule) echartsModule = await import('echarts')
  if (!orderChart || orderChart.getDom() !== orderChartRef.value) {
    orderChart?.dispose()
    orderChart = echartsModule.init(orderChartRef.value)
  }
  if (!categoryChart || categoryChart.getDom() !== categoryChartRef.value) {
    categoryChart?.dispose()
    categoryChart = echartsModule.init(categoryChartRef.value)
  }
  const dates = dashboard.value.last7DaysOrders?.map(item => item.date.slice(5)) || []
  const counts = dashboard.value.last7DaysOrders?.map(item => item.count) || []
  orderChart.setOption({
    color: ['#2B45D8'],
    grid: { left: 36, right: 16, top: 20, bottom: 28 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: dates },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ type: 'line', smooth: true, data: counts, areaStyle: { opacity: 0.08 } }]
  })
  const categoryNames = dashboard.value.categoryTopSales?.map(item => item.category) || []
  const sales = dashboard.value.categoryTopSales?.map(item => item.sales) || []
  categoryChart.setOption({
    color: ['#2B45D8'],
    grid: { left: 42, right: 16, top: 20, bottom: 40 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: categoryNames, axisLabel: { interval: 0, rotate: 24 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ type: 'bar', data: sales, barMaxWidth: 28 }]
  })
}

async function loadProducts(nextPage = 1) {
  productPage.value = nextPage
  const params = {
    page: productPage.value,
    size: productSize,
    keyword: productFilters.keyword,
    status: productFilters.status
  }
  if (productFilters.categoryId) params.categoryId = productFilters.categoryId
  const { data } = await api.get('/products/admin', { params })
  products.value = data.list || []
  productTotal.value = data.total || 0
  if (!bannerForm.productId && products.value.length) bannerForm.productId = products.value[0].id
}

async function loadOrders(nextPage = 1) {
  orderPage.value = nextPage
  const { data } = await api.get('/admin/orders', {
    params: { page: orderPage.value, size: orderSize, status: orderStatus.value }
  })
  orders.value = data.list || []
  orderTotal.value = data.total || 0
}

async function loadAfterSales(nextPage = 1) {
  afterSalePage.value = nextPage
  const { data } = await api.get('/admin/after-sales', {
    params: { page: afterSalePage.value, size: afterSaleSize, status: afterSaleStatus.value }
  })
  afterSaleItems.value = data.list || []
  afterSaleTotal.value = data.total || 0
}

async function loadFeedback() {
  const { data } = await api.get('/admin/feedback')
  feedbackItems.value = data || []
  for (const item of feedbackItems.value) {
    feedbackReplies[item.id] = item.reply || ''
  }
}

function newProduct() {
  activeTab.value = 'products'
  Object.assign(form, blank(), { categoryId: flatCategories.value[0]?.id || 1 })
  editing.value = true
}

function edit(product) {
  Object.assign(form, product, { tagIds: product.tags?.map(tag => tag.id) || [] })
  editing.value = true
}

async function refreshAdminData() {
  await Promise.all([loadProducts(productPage.value), loadDashboard()])
}

async function save() {
  try {
    if (form.id) await api.put(`/products/${form.id}`, form)
    else await api.post('/products', form)
    editing.value = false
    toast.show('商品已保存')
    await refreshAdminData()
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

async function quickStock(product, delta) {
  const nextStock = Math.max(0, product.stock + delta)
  try {
    await api.put(`/products/${product.id}`, {
      categoryId: product.categoryId,
      name: product.name,
      description: product.description,
      price: product.price,
      stock: nextStock,
      imageUrl: product.imageUrl,
      status: product.status,
      tagIds: product.tags?.map(tag => tag.id) || []
    })
    toast.show(`库存已调整为 ${nextStock}`)
    await refreshAdminData()
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

async function openLowStockProduct(product) {
  activeTab.value = 'products'
  try {
    const { data } = await api.get(`/products/${product.id}`)
    edit(data)
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

async function remove(product) {
  if (!window.confirm(`确认下架 ${product.name}？`)) return
  try {
    await api.delete(`/products/${product.id}`)
    toast.show('商品已下架')
    await refreshAdminData()
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

async function activate(product) {
  try {
    await api.put(`/products/${product.id}`, {
      categoryId: product.categoryId,
      name: product.name,
      description: product.description,
      price: product.price,
      stock: product.stock,
      imageUrl: product.imageUrl,
      status: 1,
      tagIds: product.tags?.map(tag => tag.id) || []
    })
    toast.show('商品已上架')
    await refreshAdminData()
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

function selectOrderStatus(value) {
  orderStatus.value = value
  loadOrders(1)
}

function selectAfterSaleStatus(value) {
  afterSaleStatus.value = value
  loadAfterSales(1)
}

async function ship(order) {
  try {
    await api.put(`/orders/${order.id}/ship`)
    toast.show('订单已标记发货')
    await Promise.all([loadOrders(orderPage.value), loadDashboard()])
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

async function approveAfterSale(item) {
  const remark = window.prompt(`确认同意订单 ${item.orderNo} 的售后申请？`, '同意售后申请')
  if (remark === null) return
  try {
    await api.put(`/admin/after-sales/${item.id}/approve`, { remark })
    toast.show('售后已同意')
    await Promise.all([loadAfterSales(afterSalePage.value), loadOrders(orderPage.value), loadDashboard()])
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

async function rejectAfterSale(item) {
  const remark = window.prompt(`填写拒绝订单 ${item.orderNo} 售后的原因`, '不符合售后条件')
  if (remark === null) return
  try {
    await api.put(`/admin/after-sales/${item.id}/reject`, { remark })
    toast.show('售后已拒绝')
    await loadAfterSales(afterSalePage.value)
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

async function exportOrders() {
  try {
    const res = await api.get('/admin/orders/export', { responseType: 'blob' })
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = `orders_${new Date().toISOString().slice(0, 10)}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

async function replyFeedback(item) {
  const reply = (feedbackReplies[item.id] || '').trim()
  if (!reply) {
    toast.show('请填写回复内容')
    return
  }
  try {
    await api.put(`/admin/feedback/${item.id}/reply`, { reply })
    toast.show('反馈已回复')
    await loadFeedback()
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

async function loadTags() {
  try {
    const { data } = await api.get('/admin/tags')
    allTags.value = data || []
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

async function createTag() {
  const name = tagName.value.trim()
  if (!name) return
  try {
    await api.post('/admin/tags', { name })
    tagName.value = ''
    toast.show('标签已新增')
    await loadTags()
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

async function deleteTag(tag) {
  if (!window.confirm(`确认删除标签「${tag.name}」？`)) return
  try {
    await api.delete(`/admin/tags/${tag.id}`)
    toast.show('标签已删除')
    await loadTags()
    await loadProducts(productPage.value)
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

async function loadBannerSlots() {
  const { data } = await api.get('/admin/banner-slots')
  bannerSlots.value = data || []
}

async function createBannerSlot() {
  if (!bannerForm.productId) return
  try {
    await api.post('/admin/banner-slots', bannerForm)
    toast.show('推荐位已新增')
    bannerForm.sortOrder = 0
    bannerForm.active = true
    await loadBannerSlots()
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

async function updateBannerSlot(slot) {
  try {
    await api.put(`/admin/banner-slots/${slot.id}`, {
      productId: slot.product?.id,
      sortOrder: slot.sortOrder,
      active: slot.active
    })
    toast.show('推荐位已保存')
    await loadBannerSlots()
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

async function deleteBannerSlot(slot) {
  if (!window.confirm(`确认删除推荐位 ${slot.id}？`)) return
  try {
    await api.delete(`/admin/banner-slots/${slot.id}`)
    toast.show('推荐位已删除')
    await loadBannerSlots()
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

async function loadKeywords() {
  const { data } = await api.get('/admin/search-keywords', { params: { page: 1, size: 50 } })
  keywordItems.value = data.list || []
}

async function toggleKeyword(item) {
  try {
    await api.put(`/admin/search-keywords/${encodeURIComponent(item.keyword)}/block`, { blocked: !item.blocked })
    toast.show(item.blocked ? '已取消屏蔽' : '关键词已屏蔽')
    await loadKeywords()
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

async function loadOperationLogs(nextPage = 1) {
  logPage.value = nextPage
  const params = { page: logPage.value, size: logSize, action: logFilters.action }
  if (logFilters.adminId) params.adminId = Number(logFilters.adminId)
  const { data } = await api.get('/admin/operation-logs', { params })
  operationLogs.value = data.list || []
  logTotal.value = data.total || 0
}

function feedbackType(type) {
  if (type === 2) return '投诉'
  if (type === 3) return 'BUG'
  return '建议'
}

function orderLabel(status) {
  return orderLabels[status] || status
}

function nextOrderAction(status) {
  if (status === 'PENDING_PAYMENT') return '等待支付'
  if (status === 'SHIPPED') return '等待收货'
  if (status === 'COMPLETED') return '已完成'
  if (status === 'CANCELLED') return '已取消'
  if (status === 'REFUNDED') return '已退款'
  return '无操作'
}

function formatTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}
</script>
