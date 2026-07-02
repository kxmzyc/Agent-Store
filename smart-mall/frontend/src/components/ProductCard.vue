<template>
  <article class="product-card" :style="{ '--stagger-index': index }">
    <router-link class="product-media has-skeleton" :class="{ 'is-loaded': imageLoaded }" :to="`/products/${product.id}`">
      <img
        class="product-image"
        :src="product.imageUrl"
        :alt="product.name"
        @load="imageLoaded = true"
        @error="imageLoaded = true"
      />
      <span class="product-badge">{{ badgeText }}</span>
    </router-link>
    <div class="body">
      <router-link :to="`/products/${product.id}`"><h3>{{ product.name }}</h3></router-link>
      <p class="desc">{{ product.description }}</p>
      <div class="product-card-foot">
        <span class="price">¥{{ product.price }}</span>
        <span class="meta">库存 {{ product.stock }} · 已售 {{ product.salesCount }}</span>
        <button class="add-to-cart-btn" :class="{ added }" :disabled="product.stock <= 0" @click="emit('add-cart', product, $event)">
          <ShoppingCart size="17" /> {{ product.stock <= 0 ? '已售罄' : '加入购物车' }}
        </button>
      </div>
    </div>
  </article>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  product: {
    type: Object,
    required: true
  },
  index: {
    type: Number,
    default: 0
  },
  added: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['add-cart'])
const imageLoaded = ref(false)

watch(
  () => props.product.imageUrl,
  () => {
    imageLoaded.value = false
  }
)

const badgeText = computed(() => {
  if (props.product.stock <= 0) return 'SOLD'
  return props.product.salesCount > 300 ? 'HOT' : 'NEW'
})
</script>
