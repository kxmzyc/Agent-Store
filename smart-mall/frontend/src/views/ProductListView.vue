<template>
  <section class="page">
    <div class="page-head">
      <div>
        <h1>商品中心</h1>
        <p>分类浏览、模糊搜索、排序分页都在这里演示。</p>
      </div>
      <router-link class="btn dark" to="/cart"><ShoppingCart size="18" /> 购物车</router-link>
    </div>

    <div class="toolbar product-toolbar">
      <form class="search-box catalog-search" @submit.prevent="search">
        <Search size="18" />
        <input v-model="keyword" placeholder="搜索机械键盘、显示器、耳机" />
        <select v-model="sort" aria-label="商品排序" @change="loadProducts(1)">
          <option value="sales_desc">销量优先</option>
          <option value="price_asc">价格升序</option>
          <option value="price_desc">价格降序</option>
          <option value="new_desc">最新上架</option>
        </select>
        <button class="primary search-submit" type="submit">搜索</button>
      </form>
    </div>

    <div class="category-scroll">
      <div class="category-strip">
        <button class="chip" :class="{ active: !categoryId }" @click="selectCategory(null)">全部</button>
        <template v-for="c in categories" :key="c.id">
          <button class="chip" :class="{ active: categoryId === c.id }" @click="selectCategory(c.id)">{{ c.name }}</button>
          <button v-for="child in c.children" :key="child.id" class="chip" :class="{ active: categoryId === child.id }" @click="selectCategory(child.id)">
            {{ child.name }}
          </button>
        </template>
      </div>
    </div>

    <p v-if="loadError" class="error">{{ loadError }}</p>
    <p v-else-if="isLoading" class="muted">商品加载中...</p>
    <p v-else-if="!products.length" class="muted">暂无商品</p>

    <TransitionGroup :key="gridKey" tag="div" class="grid product-grid" name="product-card-list" appear>
      <ProductCard
        v-for="(p, index) in products"
        :key="p.id"
        :product="p"
        :index="index"
        :added="addedIds.includes(p.id)"
        @add-cart="addCart"
      />
    </TransitionGroup>

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
import { flyToCart } from '../composables/useFlyToCart'
import ProductCard from '../components/ProductCard.vue'

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
const addedIds = ref([])
const gridKey = ref(0)
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
    gridKey.value += 1
  } catch (e) {
    products.value = []
    total.value = 0
    loadError.value = errorMessage(e)
    toast.show(loadError.value)
  } finally {
    isLoading.value = false
  }
}

async function addCart(product, event) {
  if (!store.token) {
    router.push('/login')
    return
  }
  const card = event?.currentTarget?.closest('.product-card')
  markAdded(product.id)
  flyToCart(card?.querySelector('.product-image'), { origin: event?.currentTarget })
  try {
    await api.post('/cart', { productId: product.id, quantity: 1 })
    toast.show('已加入购物车')
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

function markAdded(id) {
  if (!addedIds.value.includes(id)) addedIds.value.push(id)
  window.setTimeout(() => {
    addedIds.value = addedIds.value.filter((value) => value !== id)
  }, 900)
}
</script>
