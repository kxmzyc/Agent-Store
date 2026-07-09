<template>
  <section class="page product-page">
    <div ref="heroRef" class="commerce-hero product-carousel" aria-roledescription="carousel" aria-label="主推商品轮播">
      <span class="sr-only" aria-live="polite">{{ carouselStatusText }}</span>
      <div class="carousel-copy">
        <p class="hero-kicker">SMART MALL PICKS</p>
        <h1>{{ activeHeroProduct?.name || '把真实商品数据，摆进一个安静的展厅。' }}</h1>
        <p class="hero-copy">
          {{ activeHeroProduct?.description || '没有虚构的好评，没有摆拍的库存。这里的每一件，都是它本来的样子。' }}
        </p>
        <div class="hero-stats" aria-label="商品统计">
          <span>{{ total }} 件商品</span>
          <span>{{ visibleCategories.length }} 个一级分类</span>
          <span v-if="activeHeroProduct">¥{{ activeHeroProduct.price }} · 库存 {{ activeHeroProduct.stock }}</span>
        </div>
        <div class="carousel-actions" v-if="activeHeroProduct">
          <router-link class="btn primary" :to="`/products/${activeHeroProduct.id}`">查看商品</router-link>
          <button class="btn ghost carousel-toggle" type="button" :aria-label="isCarouselPaused ? '继续轮播' : '暂停轮播'" @click="toggleCarousel">
            <Play v-if="isCarouselPaused" size="16" />
            <Pause v-else size="16" />
          </button>
        </div>
      </div>

      <router-link
        v-if="activeHeroProduct"
        class="carousel-visual"
        :to="`/products/${activeHeroProduct.id}`"
        :aria-label="`查看 ${activeHeroProduct.name}`"
      >
        <img :src="activeHeroProduct.imageUrl" :alt="activeHeroProduct.name" />
        <span class="carousel-badge">HOT PICK</span>
      </router-link>

      <div v-if="heroProducts.length > 1" class="carousel-controls" aria-label="轮播控制">
        <button type="button" class="carousel-arrow" aria-label="上一件商品" @click="previousHeroProduct">
          <ChevronLeft size="18" />
        </button>
        <div class="carousel-dots">
          <button
            v-for="(product, index) in heroProducts"
            :key="product.id"
            type="button"
            :class="{ active: index === heroIndex }"
            :aria-label="`切换到 ${product.name}`"
            :aria-current="index === heroIndex"
            @click="selectHeroProduct(index)"
          />
        </div>
        <button type="button" class="carousel-arrow" aria-label="下一件商品" @click="nextHeroProduct">
          <ChevronRight size="18" />
        </button>
      </div>
    </div>

    <BannerCarousel v-if="hotProducts.length" :items="hotProducts" />

    <section v-if="recommendations.length" class="recommend-section">
      <div class="section-title">
        <h2>{{ recommendationTitle }}</h2>
        <span class="muted">{{ recommendationSource === 'personalized' ? '已排除近期购买商品' : '按全站销量生成' }}</span>
      </div>
      <div class="recommend-showcase">
        <router-link class="recommend-lead" :to="`/products/${leadRecommendation.id}`">
          <div class="recommend-lead-media">
            <img :src="leadRecommendation.imageUrl" :alt="leadRecommendation.name" />
          </div>
          <div class="recommend-lead-copy">
            <span>{{ recommendationBadge }}</span>
            <h3>{{ leadRecommendation.name }}</h3>
            <p>{{ leadRecommendation.description }}</p>
            <div class="recommend-lead-meta">
              <strong>¥{{ leadRecommendation.price }}</strong>
              <small>库存 {{ leadRecommendation.stock }} · 已售 {{ leadRecommendation.salesCount }}</small>
            </div>
          </div>
        </router-link>

        <div class="recommend-stack">
          <article v-for="p in sideRecommendations" :key="`rec-mini-${p.id}`" class="recommend-mini">
            <router-link class="recommend-mini-link" :to="`/products/${p.id}`">
              <img :src="p.imageUrl" :alt="p.name" />
              <span>
                <strong>{{ p.name }}</strong>
                <small>¥{{ p.price }} · 库存 {{ p.stock }}</small>
              </span>
            </router-link>
            <button type="button" :disabled="p.stock <= 0" @click="addCart(p, $event)">
              <ShoppingCart size="16" /> 加购
            </button>
          </article>
        </div>
      </div>
    </section>

    <div ref="dockRef" class="search-dock">
      <form class="search-box catalog-search" @submit.prevent="search">
        <Search size="18" />
        <input
          v-model="keyword"
          placeholder="搜索机械键盘、显示器、耳机"
          @focus="openHotKeywords"
          @input="showHotKeywords = !keyword.trim()"
          @blur="closeHotKeywords"
        />
        <select v-model="sort" aria-label="商品排序" @change="loadProducts(1)">
          <option value="sales_desc">销量优先</option>
          <option value="price_asc">价格升序</option>
          <option value="price_desc">价格降序</option>
          <option value="new_desc">最新上架</option>
        </select>
        <button class="primary search-submit" type="submit">搜索</button>
      </form>
      <div v-if="showHotKeywords && !keyword.trim() && hotKeywords.length" class="hot-keyword-popover">
        <button
          v-for="item in hotKeywords"
          :key="item.keyword"
          type="button"
          @mousedown.prevent="searchHotKeyword(item.keyword)"
        >{{ item.keyword }}</button>
      </div>
    </div>

    <div class="category-scroll" aria-label="商品分类">
      <div class="category-strip">
        <button class="chip" :class="{ active: !categoryId }" @click="selectCategory(null)">全部</button>
        <template v-for="c in visibleCategories" :key="c.id">
          <button class="chip" :class="{ active: categoryId === c.id }" @click="selectCategory(c.id)">{{ c.name }}</button>
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
        v-for="(p, index) in catalogProducts"
        :key="p.id"
        :product="p"
        :keyword="searchMode ? keyword : ''"
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
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { api, errorMessage, refreshCartCount } from '../api/http'
import { router } from '../router'
import { store } from '../store'
import { useToast } from '../composables/useToast'
import { flyToCart } from '../composables/useFlyToCart'
import { useScrollInertia } from '../composables/useScrollInertia'
import ProductCard from '../components/ProductCard.vue'
import BannerCarousel from '../components/BannerCarousel.vue'

const heroRef = ref(null)
const dockRef = ref(null)
const categories = ref([])
const products = ref([])
const hotProducts = ref([])
const recommendations = ref([])
const recommendationSource = ref('fallback')
const hotKeywords = ref([])
const showHotKeywords = ref(false)
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
const visibleCategories = computed(() => categories.value.filter((category) => category.parentId == null))
const heroIndex = ref(0)
const isCarouselPaused = ref(false)
const heroProducts = computed(() => products.value.slice(0, Math.min(products.value.length, 5)))
const activeHeroProduct = computed(() => heroProducts.value[heroIndex.value] || null)
const recommendationTitle = computed(() => recommendationSource.value === 'personalized'
  ? '猜你喜欢 · 基于你的浏览与偏好'
  : '新品热卖 · 为你精选')
const recommendationBadge = computed(() => recommendationSource.value === 'personalized' ? 'PERSONAL PICK' : 'CURATED HOT')
const leadRecommendation = computed(() => recommendations.value[0] || null)
const sideRecommendations = computed(() => recommendations.value.slice(1, 5))
const recommendationIds = computed(() => new Set(recommendations.value.map((product) => product.id)))
const catalogProducts = computed(() => {
  if (page.value !== 1 || searchMode.value || categoryId.value || !recommendationIds.value.size) return products.value
  const filtered = products.value.filter((product) => !recommendationIds.value.has(product.id))
  return filtered.length >= 4 ? filtered : products.value
})
const carouselStatusText = computed(() => {
  if (!activeHeroProduct.value) return '当前没有可展示的主推商品'
  return `正在展示第 ${heroIndex.value + 1} 件商品：${activeHeroProduct.value.name}`
})
let categoryTransitionTimer = null
let carouselTimer = null

useScrollInertia(heroRef, dockRef)

onMounted(async () => {
  try {
    const cats = await api.get('/categories')
    categories.value = cats.data
  } catch (e) {
    toast.show(errorMessage(e))
  }
  loadHotProducts()
  loadRecommendations()
  await loadProducts(1)
  startCarousel()
})

onBeforeUnmount(() => {
  stopCarousel()
})

watch(heroProducts, (items) => {
  if (heroIndex.value >= items.length) heroIndex.value = 0
  startCarousel()
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

function startCarousel() {
  stopCarousel()
  if (isCarouselPaused.value || heroProducts.value.length < 2) return
  carouselTimer = window.setInterval(() => {
    heroIndex.value = (heroIndex.value + 1) % heroProducts.value.length
  }, 5200)
}

function stopCarousel() {
  if (carouselTimer) {
    window.clearInterval(carouselTimer)
    carouselTimer = null
  }
}

function nextHeroProduct() {
  if (!heroProducts.value.length) return
  heroIndex.value = (heroIndex.value + 1) % heroProducts.value.length
  startCarousel()
}

function previousHeroProduct() {
  if (!heroProducts.value.length) return
  heroIndex.value = (heroIndex.value - 1 + heroProducts.value.length) % heroProducts.value.length
  startCarousel()
}

function selectHeroProduct(index) {
  heroIndex.value = index
  startCarousel()
}

function toggleCarousel() {
  isCarouselPaused.value = !isCarouselPaused.value
  startCarousel()
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

async function loadHotProducts() {
  try {
    const { data } = await api.get('/banner-slots/active')
    hotProducts.value = data || []
  } catch {
    hotProducts.value = []
  }
}

async function loadRecommendations() {
  try {
    const { data } = await api.get('/products/recommendations', { params: { limit: 5 } })
    recommendations.value = data.list || []
    recommendationSource.value = data.source || 'fallback'
  } catch {
    recommendations.value = []
    recommendationSource.value = 'fallback'
  }
}

async function loadHotKeywords() {
  if (hotKeywords.value.length) return
  try {
    const { data } = await api.get('/search/hot-keywords')
    hotKeywords.value = data || []
  } catch {
    hotKeywords.value = []
  }
}

function openHotKeywords() {
  showHotKeywords.value = !keyword.value.trim()
  loadHotKeywords()
}

function closeHotKeywords() {
  window.setTimeout(() => { showHotKeywords.value = false }, 120)
}

function searchHotKeyword(value) {
  keyword.value = value
  showHotKeywords.value = false
  search()
}

async function addCart(product, event) {
  if (!store.token) {
    router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
  flyToCart(event, { imageUrl: product.imageUrl })
  try {
    await api.post('/cart', { productId: product.id, quantity: 1 })
    await refreshCartCount()
    toast.show('已加入购物车')
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

function isFeaturedProduct(product, index) {
  return page.value === 1 && index === 0 && product.salesCount > 300
}
</script>
