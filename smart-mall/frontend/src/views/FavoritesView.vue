<template>
  <section class="page favorites-page">
    <div class="page-head">
      <div>
        <h1>我的收藏</h1>
        <p>把感兴趣的商品先收进心愿单，之后可以快速加入购物车或继续对比。</p>
      </div>
      <router-link class="btn" to="/"><ArrowLeft size="18" /> 继续逛</router-link>
    </div>

    <p v-if="loading" class="muted">收藏加载中...</p>
    <p v-else-if="loadError" class="error">{{ loadError }}</p>

    <div v-else-if="!favorites.length" class="panel empty-cart">
      <Heart size="44" />
      <h2>还没有收藏商品</h2>
      <p>在商品详情页点击收藏后，这里会形成你的心愿单。</p>
      <router-link class="btn primary" to="/">去发现商品</router-link>
    </div>

    <div v-else class="grid product-grid">
      <article v-for="item in favorites" :key="item.id" class="product-card">
        <router-link class="product-media" :to="`/products/${item.product.id}`">
          <img class="product-image" :src="item.product.imageUrl" :alt="item.product.name" />
          <span class="product-badge">{{ item.product.stock <= 0 ? 'SOLD' : 'WISH' }}</span>
        </router-link>
        <div class="body">
          <router-link :to="`/products/${item.product.id}`"><h3>{{ item.product.name }}</h3></router-link>
          <p class="desc">{{ item.product.description }}</p>
          <div class="favorite-card-foot">
            <div>
              <span class="price">¥{{ item.product.price }}</span>
              <span class="meta">库存 {{ item.product.stock }} · 收藏于 {{ formatTime(item.createdAt) }}</span>
            </div>
            <div class="favorite-actions">
              <button :disabled="item.product.stock <= 0" @click="addCart(item.product, $event)">
                <ShoppingCart size="17" /> 加购
              </button>
              <button class="ghost danger-ghost" title="取消收藏" @click="remove(item)">
                <Trash2 size="17" />
              </button>
            </div>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api, errorMessage } from '../api/http'
import { useToast } from '../composables/useToast'
import { flyToCart } from '../composables/useFlyToCart'

const favorites = ref([])
const loading = ref(true)
const loadError = ref('')
const toast = useToast()

onMounted(load)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await api.get('/user/favorites', { params: { size: 50 } })
    favorites.value = data
  } catch (e) {
    loadError.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

async function addCart(product, event) {
  const card = event?.currentTarget?.closest('.product-card')
  flyToCart(card?.querySelector('.product-image'), { origin: event?.currentTarget })
  try {
    await api.post('/cart', { productId: product.id, quantity: 1 })
    toast.show('已加入购物车')
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

async function remove(item) {
  try {
    await api.delete(`/user/favorites/${item.product.id}`)
    favorites.value = favorites.value.filter((favorite) => favorite.id !== item.id)
    toast.show('已取消收藏')
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

function formatTime(value) {
  if (!value) return ''
  return value.replace('T', ' ').slice(0, 10)
}
</script>
