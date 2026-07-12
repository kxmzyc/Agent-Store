<template>
  <section class="hot-banner" aria-label="本周精选与购物权益">
    <div class="hot-banner-head">
      <span class="hot-banner-kicker">CURATED LIST</span>
      <h2>本周高频选择</h2>
      <p>从真实销量里挑出三件适合作为演示入口的商品，浏览、加购和下单链路都能直接验证。</p>
    </div>

    <div class="hot-banner-list">
      <article v-for="(item, index) in safeItems" :key="item.id" class="hot-banner-item">
        <router-link class="hot-banner-image" :to="`/products/${item.id}`" :aria-label="`查看 ${item.name}`">
          <img :src="item.imageUrl" :alt="item.name" loading="lazy" decoding="async" />
        </router-link>
        <div class="hot-banner-copy">
          <span class="hot-banner-rank">TOP {{ index + 1 }}</span>
          <h3>{{ item.name }}</h3>
          <p>{{ item.description || '高销量真实商品，适合答辩现场展示搜索、下单与推荐闭环。' }}</p>
          <div class="hot-banner-meta">
            <strong>¥{{ item.price }}</strong>
            <span>已售 {{ item.salesCount || 0 }} · 库存 {{ item.stock }}</span>
          </div>
        </div>
      </article>
    </div>

    <aside class="hot-banner-benefit" aria-label="购物权益提示">
      <span>结算权益</span>
      <strong>自动匹配</strong>
      <p>可在结算页自动匹配账户内抵扣券和积分，库存会在提交订单时实时校验。</p>
      <router-link class="btn ghost" to="/coupons">查看权益</router-link>
    </aside>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  items: {
    type: Array,
    default: () => []
  }
})

const safeItems = computed(() => props.items.slice(0, 3))
</script>
