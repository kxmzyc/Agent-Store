<template>
  <section class="page">
    <div class="page-head">
      <div>
        <h1>订单</h1>
        <p>订单状态机：待付款、待发货、待收货、已完成、已取消。</p>
      </div>
    </div>

    <div class="status-tabs">
      <button v-for="tab in tabs" :key="tab.value" :class="{ active: status === tab.value }" @click="select(tab.value)">
        {{ tab.label }}
      </button>
    </div>

    <div class="panel order-card" v-for="order in orders" :key="order.id">
      <div class="page-head">
        <div>
          <strong>{{ order.orderNo }}</strong>
          <p class="muted">{{ order.createdAt }} · {{ label(order.status) }}</p>
        </div>
        <strong class="price">¥{{ order.totalAmount }}</strong>
      </div>
      <table class="table">
        <tbody>
          <tr v-for="item in order.items" :key="item.productId">
            <td>{{ item.productName }}</td>
            <td>¥{{ item.price }}</td>
            <td>x{{ item.quantity }}</td>
          </tr>
        </tbody>
      </table>
      <div v-if="expanded.includes(order.id)" class="order-detail muted">
        <p>收货地址：{{ order.shippingAddress }}</p>
        <p v-if="order.paidAt">支付时间：{{ order.paidAt }}</p>
      </div>
      <div class="toolbar order-toolbar">
        <button v-if="order.status === 'PENDING_PAYMENT'" class="primary" @click="pay(order)">模拟支付</button>
        <div class="order-action-swap">
          <button class="order-view-action" type="button" @click="toggle(order)">
            {{ expanded.includes(order.id) ? '收起详情' : '查看详情' }}
          </button>
          <button v-if="order.status === 'PENDING_PAYMENT'" class="order-danger-action" type="button" @click="cancel(order)">取消订单</button>
        </div>
        <button v-if="order.status === 'SHIPPED'" class="primary" @click="confirm(order)">确认收货</button>
      </div>
    </div>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api, errorMessage } from '../api/http'
import { useToast } from '../composables/useToast'

const tabs = [
  { value: 'all', label: '全部' },
  { value: 'PENDING_PAYMENT', label: '待付款' },
  { value: 'PAID', label: '待发货' },
  { value: 'SHIPPED', label: '待收货' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' }
]
const status = ref('all')
const orders = ref([])
const expanded = ref([])
const toast = useToast()

function toggle(order) {
  const i = expanded.value.indexOf(order.id)
  if (i >= 0) expanded.value.splice(i, 1)
  else expanded.value.push(order.id)
}

onMounted(load)

function label(value) {
  return tabs.find(t => t.value === value)?.label || value
}

function select(value) {
  status.value = value
  load()
}

async function load() {
  const { data } = await api.get('/orders', { params: { status: status.value } })
  orders.value = data
}

async function act(url, message) {
  try {
    await api.put(url)
    toast.show(message)
    await load()
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

const pay = (order) => act(`/orders/${order.id}/pay`, '支付状态已更新')
const cancel = (order) => act(`/orders/${order.id}/cancel`, '订单已取消')
const confirm = (order) => act(`/orders/${order.id}/confirm`, '已确认收货')
</script>
