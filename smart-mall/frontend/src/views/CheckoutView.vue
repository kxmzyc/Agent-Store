<template>
  <section class="page checkout-page">
    <div class="page-head">
      <div>
        <h1>确认订单</h1>
        <p>{{ isDirect ? '从商品详情直接下单，提交后进入模拟支付流程。' : '核对购物车勾选商品，确认地址后提交订单。' }}</p>
      </div>
      <router-link class="btn" :to="isDirect ? `/products/${directProductId}` : '/cart'">
        <ArrowLeft size="18" /> 返回
      </router-link>
    </div>

    <p v-if="loading" class="muted">结算信息加载中...</p>
    <p v-else-if="loadError" class="error">{{ loadError }}</p>

    <div v-else class="checkout-layout">
      <section class="panel checkout-lines-panel">
        <div class="panel-head">
          <div>
            <h2>商品清单</h2>
            <p>下单时会按当前价格生成订单快照。</p>
          </div>
          <span class="count-pill">{{ lines.length }} 件</span>
        </div>

        <div class="checkout-lines">
          <article v-for="line in lines" :key="line.key" class="checkout-line">
            <img :src="line.imageUrl" :alt="line.name" />
            <div>
              <strong>{{ line.name }}</strong>
              <small>{{ line.description || `库存 ${line.stock}` }}</small>
            </div>
            <span>¥{{ line.price }} × {{ line.quantity }}</span>
            <b>¥{{ line.subtotal }}</b>
          </article>
        </div>
      </section>

      <aside class="panel checkout-panel checkout-submit-panel">
        <div class="checkout-head">
          <div>
            <span class="muted">应付金额</span>
            <strong>¥{{ total }}</strong>
          </div>
          <small>{{ isDirect ? '立即购买' : '购物车结算' }}</small>
        </div>

        <form class="form checkout-form" @submit.prevent="submit">
          <label>收货地址
            <textarea v-model="shippingAddress" required placeholder="北京市海淀区实训中心 1 号楼" />
          </label>
          <p v-if="error" class="error">{{ error }}</p>
          <button class="primary checkout-submit" :disabled="submitting || !lines.length">
            <CreditCard size="18" />
            {{ submitting ? '提交中...' : '提交订单' }}
          </button>
        </form>
      </aside>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api, errorMessage } from '../api/http'
import { router } from '../router'
import { useToast } from '../composables/useToast'

const route = useRoute()
const toast = useToast()
const loading = ref(true)
const submitting = ref(false)
const loadError = ref('')
const error = ref('')
const lines = ref([])
const shippingAddress = ref('北京市海淀区实训中心 1 号楼')
const isDirect = computed(() => route.query.mode === 'direct')
const directProductId = computed(() => Number(route.query.productId || 0))
const directQuantity = computed(() => Math.max(1, Number(route.query.quantity || 1)))
const cartItemIds = computed(() => String(route.query.cartItemIds || '')
  .split(',')
  .map((value) => Number(value))
  .filter(Boolean))
const total = computed(() => lines.value
  .reduce((sum, line) => sum + Number(line.subtotal), 0)
  .toFixed(2))

onMounted(load)
watch(() => route.fullPath, load)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    if (isDirect.value) {
      if (!directProductId.value) throw new Error('缺少商品信息')
      const { data } = await api.get(`/products/${directProductId.value}`)
      if (data.stock <= 0) throw new Error('商品已售罄')
      if (directQuantity.value > data.stock) throw new Error('购买数量超出库存')
      lines.value = [{
        key: `product-${data.id}`,
        productId: data.id,
        name: data.name,
        description: data.description,
        imageUrl: data.imageUrl,
        price: data.price,
        stock: data.stock,
        quantity: directQuantity.value,
        subtotal: (Number(data.price) * directQuantity.value).toFixed(2)
      }]
    } else {
      if (!cartItemIds.value.length) throw new Error('请选择需要结算的购物车商品')
      const { data } = await api.get('/cart')
      const selected = data.filter((item) => cartItemIds.value.includes(item.id) && item.stock > 0)
      if (!selected.length) throw new Error('选中的商品不可结算')
      lines.value = selected.map((item) => ({
        key: `cart-${item.id}`,
        cartItemId: item.id,
        name: item.productName,
        imageUrl: item.imageUrl,
        price: item.price,
        stock: item.stock,
        quantity: item.quantity,
        subtotal: item.subtotal
      }))
    }
  } catch (e) {
    lines.value = []
    loadError.value = e.response ? errorMessage(e) : e.message || errorMessage(e)
  } finally {
    loading.value = false
  }
}

async function submit() {
  error.value = ''
  submitting.value = true
  try {
    const payload = { shippingAddress: shippingAddress.value }
    const { data } = isDirect.value
      ? await api.post('/orders/direct', { ...payload, productId: directProductId.value, quantity: directQuantity.value })
      : await api.post('/orders', { ...payload, cartItemIds: lines.value.map((line) => line.cartItemId) })
    toast.show('订单已提交')
    router.push(`/orders/${data.orderId}`)
  } catch (e) {
    error.value = errorMessage(e)
    toast.show(error.value)
  } finally {
    submitting.value = false
  }
}
</script>
