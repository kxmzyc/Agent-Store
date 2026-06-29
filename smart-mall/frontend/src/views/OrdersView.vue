<template>
  <section>
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
    <div class="panel" v-for="order in orders" :key="order.id" style="margin-bottom: 14px;">
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
      <div class="toolbar" style="margin-top: 12px;">
        <button v-if="order.status === 'PENDING_PAYMENT'" class="primary" @click="pay(order)">模拟支付</button>
        <button v-if="order.status === 'PENDING_PAYMENT'" @click="cancel(order)">取消订单</button>
        <button v-if="order.status === 'SHIPPED'" class="primary" @click="confirm(order)">确认收货</button>
      </div>
    </div>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api, errorMessage } from '../api/http'

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

async function act(url) {
  try {
    await api.put(url)
    await load()
  } catch (e) {
    alert(errorMessage(e))
  }
}

const pay = (order) => act(`/orders/${order.id}/pay`)
const cancel = (order) => act(`/orders/${order.id}/cancel`)
const confirm = (order) => act(`/orders/${order.id}/confirm`)
</script>
