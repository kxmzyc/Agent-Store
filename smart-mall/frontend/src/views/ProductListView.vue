<template>
  <section class="page">
    <div class="page-head">
      <div>
        <h1>商品中心</h1>
        <p>分类浏览、模糊搜索、排序分页都在这里演示。</p>
      </div>
      <router-link class="btn dark" to="/cart"><ShoppingCart size="18" /> 购物车</router-link>
    </div>

    <div class="toolbar">
      <form class="search-box" @submit.prevent="search">
        <Search size="18" />
        <input v-model="keyword" placeholder="搜索机械键盘、显示器、耳机" />
      </form>
      <select v-model="sort" @change="loadProducts(1)">
        <option value="sales_desc">销量优先</option>
        <option value="price_asc">价格升序</option>
        <option value="price_desc">价格降序</option>
        <option value="new_desc">最新上架</option>
      </select>
      <button class="primary" @click="search">搜索</button>
    </div>

    <div class="category-strip">
      <button class="chip" :class="{ active: !categoryId }" @click="selectCategory(null)">全部</button>
      <template v-for="c in categories" :key="c.id">
        <button class="chip" :class="{ active: categoryId === c.id }" @click="selectCategory(c.id)">{{ c.name }}</button>
        <button v-for="child in c.children" :key="child.id" class="chip" :class="{ active: categoryId === child.id }" @click="selectCategory(child.id)">
          {{ child.name }}
        </button>
      </template>
    </div>

    <p v-if="loadError" class="error">{{ loadError }}</p>
    <p v-else-if="isLoading" class="muted">商品加载中...</p>
    <p v-else-if="!products.length" class="muted">暂无商品</p>

    <div class="grid product-grid">
      <article v-for="p in products" :key="p.id" class="product-card">
        <router-link class="product-media" :to="`/products/${p.id}`">
          <img class="product-image" :src="p.imageUrl" :alt="p.name" />
          <span class="product-badge">{{ p.stock <= 0 ? 'SOLD' : p.salesCount > 300 ? 'HOT' : 'NEW' }}</span>
        </router-link>
        <div class="body">
          <router-link :to="`/products/${p.id}`"><h3>{{ p.name }}</h3></router-link>
          <p class="desc">{{ p.description }}</p>
          <div class="price-cart-swap">
            <div class="price-info">
              <span class="price">¥{{ p.price }}</span>
              <span class="meta">库存 {{ p.stock }} · 已售 {{ p.salesCount }}</span>
            </div>
            <button class="add-to-cart-btn" :disabled="p.stock <= 0" @click="addCart(p)">
              <ShoppingCart size="17" /> {{ p.stock <= 0 ? '已售罄' : '加入购物车' }}
            </button>
          </div>
        </div>
      </article>
    </div>

    <div class="pager">
      <button :disabled="page <= 1" @click="loadProducts(page - 1)">上一页</button>
      <span class="muted">第 {{ page }} 页 · 共 {{ total }} 件</span>
      <button :disabled="page * size >= total" @click="loadProducts(page + 1)">下一页</button>
    </div>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api, errorMessage } from '../api/http'
import { router } from '../router'
import { store } from '../store'
import { useToast } from '../composables/useToast'

const categories = ref([])
const products = ref([])
const keyword = ref('')
const categoryId = ref(null)
const sort = ref('sales_desc')
const page = ref(1)
const size = 12
const total = ref(0)
const searchMode = ref(false)
const isLoading = ref(false)
const loadError = ref('')
const toast = useToast()

onMounted(async () => {
  try {
    const cats = await api.get('/categories')
    categories.value = cats.data
  } catch (e) {
    toast.show(errorMessage(e))
  }
  await loadProducts(1)
})

function selectCategory(id) {
  categoryId.value = id
  searchMode.value = false
  loadProducts(1)
}

function search() {
  searchMode.value = !!keyword.value.trim()
  loadProducts(1)
}

async function loadProducts(nextPage) {
  page.value = nextPage
  isLoading.value = true
  loadError.value = ''
  const params = { page: page.value, size, sort: sort.value }
  if (categoryId.value) params.categoryId = categoryId.value
  try {
    if (searchMode.value) {
      const { data } = await api.get('/products/search', { params: { keyword: keyword.value, page: page.value, size } })
      products.value = data.list || []
      total.value = data.total || 0
    } else {
      const { data } = await api.get('/products', { params })
      products.value = data.list || []
      total.value = data.total || 0
    }
  } catch (e) {
    products.value = []
    total.value = 0
    loadError.value = errorMessage(e)
    toast.show(loadError.value)
  } finally {
    isLoading.value = false
  }
}

async function addCart(product) {
  if (!store.token) {
    router.push('/login')
    return
  }
  try {
    await api.post('/cart', { productId: product.id, quantity: 1 })
    toast.show('已加入购物车')
  } catch (e) {
    toast.show(errorMessage(e))
  }
}
</script>
