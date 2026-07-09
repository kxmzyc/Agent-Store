<template>
  <section class="page orders-page">
    <div class="page-head">
      <div>
        <h1>订单中心</h1>
        <p>按状态筛选订单，列表查看摘要，详情页查看完整时间线、地址和操作记录。</p>
      </div>
    </div>

    <div class="status-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.value"
        :class="{ active: status === tab.value }"
        type="button"
        @click="select(tab.value)"
      >
        {{ tab.label }}
      </button>
    </div>

    <p v-if="loading" class="muted">订单加载中...</p>
    <p v-else-if="loadError" class="error">{{ loadError }}</p>
    <div v-else-if="!orders.length" class="panel empty-cart">
      <ClipboardList size="44" />
      <h2>暂无订单</h2>
      <p>完成下单后，订单会出现在这里。</p>
      <router-link class="btn primary" to="/">去逛商品</router-link>
    </div>

    <div v-else class="order-list">
      <article v-for="order in orders" :key="order.id" class="panel order-card">
        <div class="order-card-head">
          <div class="order-meta">
            <div>
              <strong>{{ order.orderNo }}</strong>
              <p class="muted">{{ formatTime(order.createdAt) }}</p>
            </div>
            <span class="status-pill">{{ label(order.status) }}</span>
          </div>
          <strong class="price">¥{{ order.totalAmount }}</strong>
        </div>

        <div class="order-preview">
          <div v-for="item in previewItems(order)" :key="`${order.id}-${item.productId}`">
            <span>{{ item.productName }}</span>
            <b>¥{{ item.price }} × {{ item.quantity }}</b>
          </div>
          <small v-if="order.items.length > 3">还有 {{ order.items.length - 3 }} 件商品</small>
        </div>

        <div v-if="expanded.includes(order.id)" class="order-inline-detail">
          <dl class="order-summary">
            <div>
              <dt>收货地址</dt>
              <dd>{{ order.shippingAddress }}</dd>
            </div>
            <div v-if="order.paidAt">
              <dt>支付时间</dt>
              <dd>{{ formatTime(order.paidAt) }}</dd>
            </div>
            <div>
              <dt>订单状态</dt>
              <dd>{{ statusHint(order.status) }}</dd>
            </div>
          </dl>
        </div>

        <div class="order-actions">
          <button v-if="order.status === 'PENDING_PAYMENT'" class="primary" type="button" :disabled="actingId === order.id" @click="pay(order)">
            <CreditCard size="17" /> 模拟支付
          </button>
          <button v-if="order.status === 'PENDING_PAYMENT'" type="button" :disabled="actingId === order.id" @click="cancel(order)">
            取消订单
          </button>
          <button v-if="order.status === 'SHIPPED'" class="primary" type="button" :disabled="actingId === order.id" @click="confirm(order)">
            确认收货
          </button>
          <button type="button" :disabled="actingId === order.id" @click="rebuy(order)">
            <RotateCcw size="17" /> 再次购买
          </button>
          <button type="button" @click="toggle(order)">
            <Eye size="17" /> {{ expanded.includes(order.id) ? '收起摘要' : '展开摘要' }}
          </button>
          <router-link class="btn dark" :to="`/orders/${order.id}`">查看详情</router-link>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api, errorMessage } from '../api/http'
import { router } from '../router'
import { useToast } from '../composables/useToast'

const tabs = [
  { value: 'all', label: '全部' },
  { value: 'PENDING_PAYMENT', label: '待付款' },
  { value: 'PAID', label: '待发货' },
  { value: 'SHIPPED', label: '待收货' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'REFUNDED', label: '已退款' }
]

const status = ref('all')
const orders = ref([])
const expanded = ref([])
const loading = ref(false)
const loadError = ref('')
const actingId = ref(null)
const toast = useToast()

onMounted(load)

function label(value) {
  return tabs.find((tab) => tab.value === value)?.label || value
}

function statusHint(value) {
  const hints = {
    PENDING_PAYMENT: '订单已创建，等待模拟支付。',
    PAID: '支付已完成，等待管理员发货。',
    SHIPPED: '商品已发货，等待确认收货。',
    COMPLETED: '订单已完成。',
    CANCELLED: '订单已取消，库存已回补。',
    REFUNDED: '售后已完成，订单已退款。'
  }
  return hints[value] || value
}

function formatTime(value) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}

function previewItems(order) {
  return (order.items || []).slice(0, 3)
}

function toggle(order) {
  const index = expanded.value.indexOf(order.id)
  if (index >= 0) expanded.value.splice(index, 1)
  else expanded.value.push(order.id)
}

function select(value) {
  status.value = value
  expanded.value = []
  load()
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await api.get('/orders', { params: { status: status.value } })
    orders.value = data
  } catch (e) {
    loadError.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

async function act(order, url, message) {
  actingId.value = order.id
  try {
    await api.put(url)
    toast.show(message)
    await load()
  } catch (e) {
    toast.show(errorMessage(e))
  } finally {
    actingId.value = null
  }
}

const pay = (order) => act(order, `/orders/${order.id}/pay`, '支付状态已更新')
const cancel = (order) => act(order, `/orders/${order.id}/cancel`, '订单已取消')
const confirm = (order) => act(order, `/orders/${order.id}/confirm`, '已确认收货')

async function rebuy(order) {
  actingId.value = order.id
  try {
    const { data } = await api.post(`/orders/${order.id}/rebuy`)
    toast.show(`已加入购物车 ${data.addedCount} 件`)
    router.push('/cart')
  } catch (e) {
    toast.show(errorMessage(e))
  } finally {
    actingId.value = null
  }
}
</script>
