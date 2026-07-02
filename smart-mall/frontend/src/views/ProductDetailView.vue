<template>
  <section v-if="product" class="detail-layout">
    <div class="detail-media">
      <img :src="product.imageUrl" :alt="product.name" />
    </div>
    <div class="panel">
      <div class="page-head">
        <div>
          <h1>{{ product.name }}</h1>
          <p>{{ product.description }}</p>
        </div>
      </div>
      <p class="price">¥{{ product.price }}</p>
      <p :class="product.stock > 0 ? 'muted' : 'soldout'">库存：{{ product.stock > 0 ? product.stock : '已售罄' }}</p>
      <div class="detail-service-row">
        <span>7天无理由</span>
        <span>极速发货</span>
        <span>库存实时校验</span>
      </div>
      <div class="qty detail-qty">
        <button type="button" :disabled="quantity <= 1" @click="quantity--"><Minus size="15" /></button>
        <span>{{ quantity }}</span>
        <button type="button" :disabled="quantity >= product.stock" @click="quantity++"><Plus size="15" /></button>
      </div>
      <div class="toolbar">
        <button class="primary" :class="{ added: added }" :disabled="product.stock <= 0" @click="addCart">
          <ShoppingCart size="18" /> 加入购物车
        </button>
        <button class="dark" :disabled="product.stock <= 0" @click="buyNow">
          <CreditCard size="18" /> 立即购买
        </button>
        <button type="button" :class="{ primary: favorited }" @click="toggleFavorite">
          <Heart size="18" /> {{ favorited ? '已收藏' : '收藏' }}
        </button>
      </div>
      <p class="muted detail-hint">销量 {{ product.salesCount }} · 商品 ID {{ product.id }} · 版本号 {{ product.version }}</p>
    </div>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, errorMessage } from '../api/http'
import { router } from '../router'
import { store } from '../store'
import { useToast } from '../composables/useToast'
import { flyToCart } from '../composables/useFlyToCart'

const route = useRoute()
const product = ref(null)
const favorited = ref(false)
const quantity = ref(1)
const added = ref(false)
const toast = useToast()

onMounted(async () => {
  const { data } = await api.get(`/products/${route.params.id}`)
  product.value = data
  if (store.token) {
    try {
      const status = await api.get(`/user/favorites/${route.params.id}`)
      favorited.value = status.data.favorited
    } catch {
      favorited.value = false
    }
  }
})

async function addCart() {
  if (!store.token) return router.push('/login')
  added.value = true
  flyToCart(document.querySelector('.detail-media img'))
  window.setTimeout(() => { added.value = false }, 900)
  try {
    await api.post('/cart', { productId: product.value.id, quantity: quantity.value })
    toast.show('已加入购物车')
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

async function buyNow() {
  if (!store.token) return router.push('/login')
  router.push({ path: '/checkout', query: { mode: 'direct', productId: product.value.id, quantity: quantity.value } })
}

async function toggleFavorite() {
  if (!store.token) return router.push('/login')
  try {
    if (favorited.value) {
      const { data } = await api.delete(`/user/favorites/${product.value.id}`)
      favorited.value = data.favorited
      toast.show('已取消收藏')
    } else {
      const { data } = await api.post(`/user/favorites/${product.value.id}`)
      favorited.value = data.favorited
      toast.show('已加入收藏')
    }
  } catch (e) {
    toast.show(errorMessage(e))
  }
}
</script>
