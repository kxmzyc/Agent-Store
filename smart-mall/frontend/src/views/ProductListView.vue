<template>
  <section class="page product-page">
    <div ref="heroRef" class="commerce-hero">
      <p class="hero-kicker">SMART MALL</p>
      <h1>把真实商品数据，摆进一个安静的展厅。</h1>
      <p class="hero-copy">没有虚构的好评，没有摆拍的库存。这里的每一件，都是它本来的样子。</p>
      <div class="hero-stats" aria-label="商品统计">
        <span>{{ total }} 件商品</span>
        <span>{{ categories.length }} 个一级分类</span>
      </div>
    </div>

    <div ref="dockRef" class="search-dock">
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

    <div class="category-scroll" aria-label="商品分类">
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
    <p v-else-if="isLoading" class="muted product-state">商品加载中...</p>
    <p v-else-if="!products.length" class="muted product-state">暂无商品</p>

    <TransitionGroup
      name="product-list"
      tag="div"
      class="grid product-grid"
      :css="categoryTransition"
    >
      <ProductCard
        v-for="(p, index) in products"
        :key="p.id"
        :product="p"
        :class="{ 'product-card--featured': isFeaturedProduct(p, index) }"
        :style="{ '--card-index': index % 12 }"
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
import { useScrollInertia } from '../composables/useScrollInertia'
import ProductCard from '../components/ProductCard.vue'

const heroRef = ref(null)
const dockRef = ref(null)
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
const categoryTransition = ref(false)
const toast = useToast()
let categoryTransitionTimer = null

useScrollInertia(heroRef, dockRef)

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
  // Diagnostic-only switch, populated by dev/fxDiagnostics.js for isolated perf runs.
  categoryTransition.value = !document.documentElement.classList.contains('fx-off-product-list')
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
    if (categoryTransition.value) {
      window.clearTimeout(categoryTransitionTimer)
      categoryTransitionTimer = window.setTimeout(() => {
        categoryTransition.value = false
      }, 360)
    }
  }
}

async function addCart(product, event) {
  if (!store.token) {
    router.push('/login')
    return
  }
  flyToCart(event, { imageUrl: product.imageUrl })
  try {
    await api.post('/cart', { productId: product.id, quantity: 1 })
    toast.show('已加入购物车')
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

function isFeaturedProduct(product, index) {
  return page.value === 1 && index === 0 && product.salesCount > 300
}
</script>
