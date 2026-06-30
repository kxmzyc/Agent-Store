<template>
  <section class="page">
    <div class="page-head">
      <div>
        <h1>购物车</h1>
        <p>勾选商品后填写地址并下单。</p>
      </div>
      <strong class="price">合计 ¥{{ total }}</strong>
    </div>

    <table class="table">
      <thead>
        <tr><th></th><th>商品</th><th>单价</th><th>数量</th><th>小计</th></tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.id">
          <td><input type="checkbox" v-model="checked" :value="item.id" /></td>
          <td>{{ item.productName }} <span v-if="item.stock <= 0" class="soldout">已售罄</span></td>
          <td>¥{{ item.price }}</td>
          <td>
            <div class="cart-row-swap">
              <span class="cart-qty-controls qty">
                <button @click="update(item, item.quantity - 1)" :disabled="item.quantity <= 1"><Minus size="15" /></button>
                {{ item.quantity }}
                <button @click="update(item, item.quantity + 1)"><Plus size="15" /></button>
              </span>
              <button class="cart-delete-btn" title="删除" @click="remove(item)"><Trash2 size="17" /> 删除</button>
            </div>
          </td>
          <td>¥{{ item.subtotal }}</td>
        </tr>
      </tbody>
    </table>

    <form class="form panel order-form" @submit.prevent="createOrder">
      <label>收货地址
        <textarea v-model="shippingAddress" required placeholder="北京市海淀区..." />
      </label>
      <p v-if="error" class="error">{{ error }}</p>
      <button class="primary" :disabled="!checked.length">提交订单</button>
    </form>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { api, errorMessage } from '../api/http'
import { router } from '../router'
import { useToast } from '../composables/useToast'

const items = ref([])
const checked = ref([])
const shippingAddress = ref('北京市海淀区实训中心1号楼')
const error = ref('')
const toast = useToast()

const total = computed(() => items.value
  .filter(i => checked.value.includes(i.id))
  .reduce((sum, i) => sum + Number(i.subtotal), 0)
  .toFixed(2))

onMounted(load)

async function load() {
  const { data } = await api.get('/cart')
  items.value = data
  checked.value = data.filter(i => i.stock > 0).map(i => i.id)
}

async function update(item, quantity) {
  await api.put(`/cart/${item.id}`, { quantity })
  await load()
}

async function remove(item) {
  await api.delete(`/cart/${item.id}`)
  toast.show('已从购物车移除')
  await load()
}

async function createOrder() {
  error.value = ''
  try {
    await api.post('/orders', { cartItemIds: checked.value, shippingAddress: shippingAddress.value })
    toast.show('订单已提交')
    router.push('/orders')
  } catch (e) {
    error.value = errorMessage(e)
    toast.show(error.value)
  }
}
</script>
