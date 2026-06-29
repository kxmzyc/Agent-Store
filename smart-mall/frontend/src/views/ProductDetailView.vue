<template>
  <section v-if="product" class="detail-layout">
    <img :src="product.imageUrl" :alt="product.name" />
    <div class="panel">
      <div class="page-head">
        <div>
          <h1>{{ product.name }}</h1>
          <p>{{ product.description }}</p>
        </div>
      </div>
      <p class="price">¥{{ product.price }}</p>
      <p :class="product.stock > 0 ? 'muted' : 'soldout'">库存：{{ product.stock > 0 ? product.stock : '已售罄' }}</p>
      <div class="toolbar">
        <button class="primary" :disabled="product.stock <= 0" @click="addCart">
          <ShoppingCart size="18" /> 加入购物车
        </button>
        <button class="dark" :disabled="product.stock <= 0" @click="buyNow">
          <CreditCard size="18" /> 立即购买
        </button>
      </div>
    </div>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, errorMessage } from '../api/http'
import { router } from '../router'
import { store } from '../store'

const route = useRoute()
const product = ref(null)

onMounted(async () => {
  const { data } = await api.get(`/products/${route.params.id}`)
  product.value = data
})

async function addCart() {
  if (!store.token) return router.push('/login')
  try {
    await api.post('/cart', { productId: product.value.id, quantity: 1 })
    alert('已加入购物车')
  } catch (e) {
    alert(errorMessage(e))
  }
}

async function buyNow() {
  await addCart()
  router.push('/cart')
}
</script>
