<template>
  <article class="product-card" @pointerenter="onPointerEnter" @pointerleave="onPointerLeave">
    <router-link class="product-media" :class="{ 'is-loading': !imageLoaded, 'is-loaded': imageLoaded }" :to="`/products/${product.id}`">
      <span v-if="!imageLoaded" class="image-skeleton" aria-hidden="true">
        <span class="image-shimmer"></span>
      </span>
      <img
        class="product-image"
        :src="product.imageUrl"
        :alt="product.name"
        width="640"
        height="480"
        loading="lazy"
        decoding="async"
        @load="imageLoaded = true"
        @error="imageLoaded = true"
      />
      <span class="product-badge">{{ badgeText }}</span>
    </router-link>
    <div class="body">
      <router-link :to="`/products/${product.id}`"><h3>{{ product.name }}</h3></router-link>
      <p class="desc">{{ product.description }}</p>
      <div class="product-card-foot">
        <div class="price-row">
          <span class="price">¥{{ product.price }}</span>
          <span class="meta">库存 {{ product.stock }}</span>
        </div>
        <span class="meta">已售 {{ product.salesCount }}</span>
        <button class="add-to-cart-btn" :disabled="product.stock <= 0" @click="emit('add-cart', product, $event)">
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

function onPointerEnter(event) {
  event.currentTarget.style.willChange = 'transform'
}

function onPointerLeave(event) {
  event.currentTarget.style.willChange = 'auto'
}

</script>
