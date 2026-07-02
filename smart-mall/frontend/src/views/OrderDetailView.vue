<template>
  <section class="page order-detail-page">
    <div class="page-head">
      <div>
        <h1>订单详情</h1>
        <p v-if="order">{{ order.orderNo }} · {{ label(order.status) }}</p>
      </div>
      <router-link class="btn" to="/orders"><ArrowLeft size="18" /> 返回订单</router-link>
    </div>

    <p v-if="loading" class="muted">订单加载中...</p>
    <p v-else-if="loadError" class="error">{{ loadError }}</p>

    <template v-else-if="order">
      <section class="panel order-timeline-panel">
        <div class="panel-head">
          <div>
            <h2>状态时间线</h2>
            <p>{{ statusHint(order.status) }}</p>
          </div>
          <span class="status-pill">{{ label(order.status) }}</span>
        </div>

        <div class="order-timeline" :class="{ cancelled: order.status === 'CANCELLED' }">
          <div
            v-for="step in timelineSteps"
            :key="step.value"
            class="timeline-step"
            :class="{ active: isActive(step.value), current: order.status === step.value }"
          >
            <span></span>
            <strong>{{ step.label }}</strong>
            <small>{{ stepTime(step.value) || step.hint }}</small>
          </div>
        </div>
      </section>

      <div class="order-detail-layout">
        <section class="panel order-lines-panel">
          <div class="panel-head">
            <div>
              <h2>商品明细</h2>
              <p>商品名和价格为下单时刻快照。</p>
            </div>
            <strong class="price">¥{{ order.totalAmount }}</strong>
          </div>

          <div class="order-detail-lines">
            <div v-for="item in order.items" :key="`${order.id}-${item.productId}`" class="order-detail-line">
              <div>
                <strong>{{ item.productName }}</strong>
                <small>商品 ID {{ item.productId }}</small>
              </div>
              <span>¥{{ item.price }}</span>
              <span>× {{ item.quantity }}</span>
              <b>¥{{ lineTotal(item) }}</b>
            </div>
          </div>
        </section>

        <aside class="panel order-summary-panel">
          <div class="panel-head">
            <div>
              <h2>收货与操作</h2>
              <p>只显示当前状态允许的操作。</p>
            </div>
          </div>

          <dl class="order-summary">
            <div>
              <dt>订单编号</dt>
              <dd>{{ order.orderNo }}</dd>
            </div>
            <div>
              <dt>收货地址</dt>
              <dd>{{ order.shippingAddress }}</dd>
            </div>
            <div>
              <dt>创建时间</dt>
              <dd>{{ formatTime(order.createdAt) }}</dd>
            </div>
            <div v-if="order.paidAt">
              <dt>支付时间</dt>
              <dd>{{ formatTime(order.paidAt) }}</dd>
            </div>
            <div>
              <dt>商品件数</dt>
              <dd>{{ totalQuantity }} 件</dd>
            </div>
            <div>
              <dt>订单金额</dt>
              <dd class="price">¥{{ order.totalAmount }}</dd>
            </div>
          </dl>

          <div class="detail-actions">
            <button v-if="order.status === 'PENDING_PAYMENT'" class="primary" type="button" :disabled="acting" @click="pay">
              <CreditCard size="18" /> 模拟支付
            </button>
            <button v-if="order.status === 'PENDING_PAYMENT'" type="button" :disabled="acting" @click="cancel">
              取消订单
            </button>
            <button v-if="order.status === 'SHIPPED'" class="primary" type="button" :disabled="acting" @click="confirm">
              确认收货
            </button>
            <button type="button" :disabled="acting" @click="rebuy">
              <RotateCcw size="18" /> 再次购买
            </button>
          </div>
        </aside>
      </div>
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, errorMessage } from '../api/http'
import { router } from '../router'
import { useToast } from '../composables/useToast'

const route = useRoute()
const toast = useToast()
const order = ref(null)
const loading = ref(true)
const loadError = ref('')
const acting = ref(false)

const labels = {
  PENDING_PAYMENT: '待付款',
  PAID: '待发货',
  SHIPPED: '待收货',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}

const timelineSteps = [
  { value: 'PENDING_PAYMENT', label: '待付款', hint: '订单已创建' },
  { value: 'PAID', label: '待发货', hint: '模拟支付完成' },
  { value: 'SHIPPED', label: '待收货', hint: '管理员已发货' },
  { value: 'COMPLETED', label: '已完成', hint: '用户确认收货' }
]

const statusRank = computed(() => timelineSteps.findIndex((step) => step.value === order.value?.status))
const totalQuantity = computed(() => (order.value?.items || []).reduce((sum, item) => sum + Number(item.quantity || 0), 0))

onMounted(load)

function label(status) {
  return labels[status] || status
}

function statusHint(status) {
  const hints = {
    PENDING_PAYMENT: '订单已创建，库存已锁定，等待完成模拟支付。',
    PAID: '订单已支付，等待管理员从后台标记发货。',
    SHIPPED: '订单已发货，确认收货后订单完成。',
    COMPLETED: '订单已完成，可再次购买加入购物车。',
    CANCELLED: '订单已取消，系统已按订单明细回补库存。'
  }
  return hints[status] || status
}

function isActive(status) {
  if (order.value?.status === 'CANCELLED') return status === 'PENDING_PAYMENT'
  const rank = timelineSteps.findIndex((step) => step.value === status)
  return rank >= 0 && rank <= statusRank.value
}

function stepTime(status) {
  if (status === 'PENDING_PAYMENT') return formatTime(order.value?.createdAt)
  if (status === 'PAID') return formatTime(order.value?.paidAt)
  return ''
}

function formatTime(value) {
  return value ? value.replace('T', ' ').slice(0, 16) : ''
}

function lineTotal(item) {
  return (Number(item.price) * Number(item.quantity)).toFixed(2)
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await api.get(`/orders/${route.params.id}`)
    order.value = data
  } catch (e) {
    loadError.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

async function act(url, message) {
  acting.value = true
  try {
    await api.put(url)
    toast.show(message)
    await load()
  } catch (e) {
    toast.show(errorMessage(e))
  } finally {
    acting.value = false
  }
}

const pay = () => act(`/orders/${order.value.id}/pay`, '支付状态已更新')
const cancel = () => act(`/orders/${order.value.id}/cancel`, '订单已取消')
const confirm = () => act(`/orders/${order.value.id}/confirm`, '已确认收货')

async function rebuy() {
  acting.value = true
  try {
    const { data } = await api.post(`/orders/${order.value.id}/rebuy`)
    toast.show(`已加入购物车 ${data.addedCount} 件`)
    router.push('/cart')
  } catch (e) {
    toast.show(errorMessage(e))
  } finally {
    acting.value = false
  }
}
</script>
