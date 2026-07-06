<template>
  <section v-if="product" class="detail-layout">
    <div class="detail-media">
      <img :src="product.imageUrl" :alt="product.name" width="800" height="800" decoding="async" />
    </div>
    <div class="panel">
      <div class="page-head">
        <div>
          <h1>{{ product.name }}</h1>
          <p>{{ product.description }}</p>
        </div>
      </div>
      <p class="price">¥{{ product.price }}</p>
      <p class="muted">评分 {{ product.avgRating || 0 }} / 5 · {{ product.reviewCount || 0 }} 条评价</p>
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
        <button class="primary" :disabled="product.stock <= 0" @click="addCart($event)">
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

  <section v-if="product" class="panel review-panel">
    <div class="panel-head">
      <div>
        <h2>商品评价</h2>
        <p>评价来自已完成订单，按提交时间倒序展示。</p>
      </div>
      <span class="count-pill">{{ reviewTotal }} 条</span>
    </div>

    <div class="review-summary">
      <div>
        <span>综合评分</span>
        <strong>{{ reviewScore }}</strong>
        <small>/ 5</small>
      </div>
      <p>仅允许已完成订单提交评价，避免演示数据和真实购买反馈混在一起。</p>
      <ul>
        <li>购买完成后开放评价入口</li>
        <li>同一订单商品只能评价一次</li>
        <li>评分会同步到商品详情</li>
      </ul>
    </div>

    <form v-if="store.token" class="review-form" @submit.prevent="submitReview">
      <div class="star-picker" aria-label="选择评分">
        <button
          v-for="star in 5"
          :key="star"
          type="button"
          :class="{ active: star <= reviewForm.rating }"
          @click="reviewForm.rating = star"
        >★</button>
      </div>
      <textarea v-model="reviewForm.content" maxlength="500" placeholder="写下真实使用感受，最多 500 字" />
      <button class="primary" :disabled="reviewSubmitting">{{ reviewSubmitting ? '提交中...' : '提交评价' }}</button>
    </form>
    <p v-else class="muted">登录后，完成购买的用户可以提交评价。</p>

    <div v-if="reviews.length" class="review-list">
      <article v-for="review in reviews" :key="review.id" class="review-item">
        <span class="review-avatar">{{ displayName(review).slice(0, 1).toUpperCase() }}</span>
        <div>
          <div class="review-item-head">
            <strong>{{ displayName(review) }}</strong>
            <span>{{ '★'.repeat(review.rating) }}{{ '☆'.repeat(5 - review.rating) }}</span>
          </div>
          <p>{{ review.content || '用户未填写文字评价。' }}</p>
          <small>{{ formatTime(review.createdAt) }}</small>
        </div>
      </article>
    </div>
    <div v-else class="review-empty">
      <strong>暂时还没有文字评价</strong>
      <p>这个商品还没有完成订单评价。你可以先查看库存、销量和服务承诺；购买完成后，系统会开放评价提交。</p>
      <router-link class="btn" to="/">继续浏览商品</router-link>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
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
const reviews = ref([])
const reviewTotal = ref(0)
const reviewSubmitting = ref(false)
const reviewForm = reactive({ rating: 5, content: '' })
const toast = useToast()
const reviewScore = computed(() => Number(product.value?.avgRating || 0).toFixed(1))

onMounted(async () => {
  const { data } = await api.get(`/products/${route.params.id}`)
  product.value = data
  await loadReviews()
  if (store.token) {
    try {
      const status = await api.get(`/user/favorites/${route.params.id}`)
      favorited.value = status.data.favorited
    } catch {
      favorited.value = false
    }
  }
})

async function loadReviews() {
  const { data } = await api.get(`/products/${route.params.id}/reviews`, { params: { page: 1, size: 10 } })
  reviews.value = data.list || []
  reviewTotal.value = data.total || 0
}

async function addCart(event) {
  if (!store.token) return router.push('/login')
  flyToCart(event, { imageUrl: product.value.imageUrl })
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

async function submitReview() {
  reviewSubmitting.value = true
  try {
    await api.post(`/products/${product.value.id}/reviews`, {
      rating: reviewForm.rating,
      content: reviewForm.content
    })
    reviewForm.rating = 5
    reviewForm.content = ''
    toast.show('评价已提交')
    const { data } = await api.get(`/products/${product.value.id}`)
    product.value = data
    await loadReviews()
  } catch (e) {
    toast.show(errorMessage(e))
  } finally {
    reviewSubmitting.value = false
  }
}

function formatTime(value) {
  if (!value) return ''
  return value.replace('T', ' ').slice(0, 16)
}

function displayName(review) {
  return review.username || '用户'
}
</script>
