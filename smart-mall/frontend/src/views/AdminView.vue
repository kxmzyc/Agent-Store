<template>
  <section class="page admin-page">
    <div class="page-head">
      <div>
        <h1>运营工作台</h1>
        <p>集中处理商品、库存和订单状态，答辩时可以直接展示管理侧闭环。</p>
      </div>
      <div class="toolbar admin-actions">
        <button type="button" @click="refreshAll">刷新</button>
        <button class="primary" type="button" @click="newProduct">新增商品</button>
      </div>
    </div>

    <div class="admin-tabs">
      <button v-for="tab in tabs" :key="tab.value" :class="{ active: activeTab === tab.value }" @click="activeTab = tab.value">
        {{ tab.label }}
      </button>
    </div>

    <p v-if="dashboardError" class="error">{{ dashboardError }}</p>

    <template v-if="activeTab === 'overview'">
      <div class="metric-grid" v-if="dashboard">
        <article v-for="metric in metrics" :key="metric.label" class="metric-card">
          <span>{{ metric.label }}</span>
          <strong>{{ metric.value }}</strong>
          <small>{{ metric.hint }}</small>
        </article>
      </div>

      <div class="dashboard-grid" v-if="dashboard">
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
              <p>按销量排序，便于演示推荐来源。</p>
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
          <input v-model="productFilters.keyword" placeholder="搜索商品名或描述" />
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

      <div class="panel product-editor" v-if="editing">
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
          <tr><th>ID</th><th>商品</th><th>价格</th><th>库存</th><th>销量</th><th>状态</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="p in products" :key="p.id" :class="{ 'row-inactive': p.status !== 1 }">
            <td>{{ p.id }}</td>
            <td>
              <strong>{{ p.name }}</strong>
              <small class="muted block-text">{{ p.description }}</small>
            </td>
            <td>¥{{ p.price }}</td>
            <td>
              <div class="stock-stepper">
                <button type="button" @click="quickStock(p, -5)" :disabled="p.stock <= 0">-5</button>
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
          <button v-for="tab in orderTabs" :key="tab.value" :class="{ active: orderStatus === tab.value }" @click="selectOrderStatus(tab.value)">
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
              <p>{{ formatTime(order.createdAt) }} · 用户 ID {{ order.id }}</p>
            </div>
            <span class="status-pill">{{ orderLabel(order.status) }}</span>
          </div>
          <div class="order-lines">
            <div v-for="item in order.items" :key="`${order.id}-${item.productId}`">
              <span>{{ item.productName }}</span>
              <b>¥{{ item.price }} × {{ item.quantity }}</b>
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
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { api, errorMessage } from '../api/http'
import { useToast } from '../composables/useToast'

const tabs = [
  { value: 'overview', label: '概览' },
  { value: 'products', label: '商品' },
  { value: 'orders', label: '订单' },
  { value: 'feedback', label: '反馈' }
]
const orderTabs = [
  { value: 'all', label: '全部' },
  { value: 'PENDING_PAYMENT', label: '待付款' },
  { value: 'PAID', label: '待发货' },
  { value: 'SHIPPED', label: '待收货' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' }
]
const orderLabels = Object.fromEntries(orderTabs.filter(t => t.value !== 'all').map(t => [t.value, t.label]))

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
const feedbackItems = ref([])
const dashboard = ref(null)
const orderChartRef = ref(null)
const categoryChartRef = ref(null)
const dashboardError = ref('')
const editing = ref(false)
const form = reactive(blank())
const toast = useToast()
const productFilters = reactive({ keyword: '', categoryId: null, status: 'all' })
const feedbackReplies = reactive({})
let echartsModule = null
let orderChart = null
let categoryChart = null

const flatCategories = computed(() => categories.value.flatMap(c => [c, ...(c.children || [])]))

const metrics = computed(() => {
  const data = dashboard.value?.metrics
  if (!data) return []
  return [
    { label: '今日订单', value: dashboard.value.todayOrders, hint: `${data.pendingPaymentOrders} 个待付款` },
    { label: '本月销售额', value: `¥${dashboard.value.monthRevenue}`, hint: '不含已取消订单' },
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
  await refreshAll()
})

onBeforeUnmount(() => {
  orderChart?.dispose()
  categoryChart?.dispose()
})

watch(activeTab, async (tab) => {
  if (tab === 'orders' && !orders.value.length) await loadOrders(1)
  if (tab === 'feedback' && !feedbackItems.value.length) await loadFeedback()
  if (tab === 'overview') await renderCharts()
})

watch(dashboard, async () => {
  if (activeTab.value === 'overview') await renderCharts()
})

function blank() {
  return { id: null, categoryId: 1, name: '', description: '', price: 0, stock: 0, imageUrl: '', status: 1 }
}

async function loadCategories() {
  try {
    const cats = await api.get('/categories')
    categories.value = cats.data
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

async function refreshAll() {
  await Promise.all([loadDashboard(), loadProducts(productPage.value), loadOrders(orderPage.value)])
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
  const categories = dashboard.value.categoryTopSales?.map(item => item.category) || []
  const sales = dashboard.value.categoryTopSales?.map(item => item.sales) || []
  categoryChart.setOption({
    color: ['#2B45D8'],
    grid: { left: 42, right: 16, top: 20, bottom: 40 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: categories, axisLabel: { interval: 0, rotate: 24 } },
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
  products.value = data.list
  productTotal.value = data.total
}

async function loadOrders(nextPage = 1) {
  orderPage.value = nextPage
  const { data } = await api.get('/admin/orders', {
    params: { page: orderPage.value, size: orderSize, status: orderStatus.value }
  })
  orders.value = data.list
  orderTotal.value = data.total
}

async function loadFeedback() {
  const { data } = await api.get('/admin/feedback')
  feedbackItems.value = data
  for (const item of data) {
    feedbackReplies[item.id] = item.reply || ''
  }
}

function newProduct() {
  activeTab.value = 'products'
  Object.assign(form, blank(), { categoryId: flatCategories.value[0]?.id || 1 })
  editing.value = true
}

function edit(product) {
  Object.assign(form, product)
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
      status: product.status
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
  if (!confirm(`确认下架 ${product.name}？`)) return
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
      status: 1
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

async function ship(order) {
  try {
    await api.put(`/orders/${order.id}/ship`)
    toast.show('订单已标记发货')
    await Promise.all([loadOrders(orderPage.value), loadDashboard()])
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
  return '无操作'
}

function formatTime(value) {
  if (!value) return ''
  return value.replace('T', ' ').slice(0, 16)
}
</script>
