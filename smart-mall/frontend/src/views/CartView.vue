<template>
  <section class="page cart-page">
    <div class="page-head cart-hero">
      <div>
        <h1>购物车</h1>
        <p>勾选商品后进入结算页，核对地址并提交订单。</p>
      </div>
      <div class="cart-total-chip" :class="{ pulse: totalPulse }">
        <span>已选 {{ checked.length }} 件</span>
        <strong>¥{{ total }}</strong>
      </div>
    </div>

    <div v-if="!items.length" class="panel empty-cart">
      <ShoppingCart size="44" />
      <h2>购物车还是空的</h2>
      <p>先去商品中心挑几件商品，再回来一起结算。</p>
      <router-link class="btn primary" to="/">去逛商品</router-link>
    </div>

    <template v-else>
      <div class="cart-toolbar panel">
        <label class="check-control">
          <input type="checkbox" :checked="allChecked" @change="toggleAll" />
          <span>全选可购买商品</span>
        </label>
        <div class="cart-toolbar-actions">
          <span class="muted">共 {{ items.length }} 件，{{ disabledCount }} 件不可购买</span>
          <button class="ghost remove-cart-item" type="button" @click="clearCart" :disabled="isClearing || !items.length">
            <Trash2 size="18" />
            {{ isClearing ? '清空中' : '清空购物车' }}
          </button>
        </div>
      </div>

      <div class="cart-layout">
        <TransitionGroup name="cart-list" tag="div" class="cart-list">
          <article
            v-for="item in items"
            :key="item.id"
            class="cart-item-card"
            :class="{
              selected: checked.includes(item.id),
              removing: removingIds.includes(item.id),
              updating: updatingIds.includes(item.id),
              soldout: item.stock <= 0
            }"
          >
            <label class="cart-select">
              <input type="checkbox" v-model="checked" :value="item.id" :disabled="item.stock <= 0" />
              <span></span>
            </label>

            <div class="cart-thumb">
              <img :src="item.imageUrl" :alt="item.productName" />
            </div>

            <div class="cart-info">
              <div>
                <h2>{{ item.productName }}</h2>
                <p>{{ item.stock <= 0 ? '该商品已售罄，无法结算' : `库存 ${item.stock} 件` }}</p>
              </div>
              <div class="cart-price-row">
                <span>单价 ¥{{ item.price }}</span>
                <strong>¥{{ item.subtotal }}</strong>
              </div>
            </div>

            <div class="cart-actions">
              <div class="qty cart-qty" :class="{ bump: bumpedIds.includes(item.id) }">
                <button type="button" @click="update(item, item.quantity - 1)" :disabled="item.quantity <= 1 || updatingIds.includes(item.id)">
                  <Minus size="15" />
                </button>
                <span>{{ item.quantity }}</span>
                <button type="button" @click="update(item, item.quantity + 1)" :disabled="item.quantity >= item.stock || updatingIds.includes(item.id)">
                  <Plus size="15" />
                </button>
              </div>
              <button class="ghost remove-cart-item" title="删除" type="button" @click="remove(item)" :disabled="removingIds.includes(item.id)">
                <Trash2 size="18" />
              </button>
            </div>
          </article>
        </TransitionGroup>

        <aside class="checkout-panel panel">
          <div class="checkout-meter">
            <span :style="{ width: checkoutProgress + '%' }"></span>
          </div>
          <div class="checkout-head">
            <div>
              <span class="muted">结算金额</span>
              <strong :class="{ pulse: totalPulse }">¥{{ total }}</strong>
            </div>
            <small>{{ checked.length }} 件商品</small>
          </div>

          <form class="form checkout-form" @submit.prevent="createOrder">
            <button class="primary checkout-submit" :disabled="!checked.length" @click.prevent="goCheckout">
              <CreditCard size="18" />
              去结算
            </button>
          </form>
        </aside>
      </div>
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { api, errorMessage } from '../api/http'
import { router } from '../router'
import { useToast } from '../composables/useToast'

const items = ref([])
const checked = ref([])
const updatingIds = ref([])
const removingIds = ref([])
const bumpedIds = ref([])
const totalPulse = ref(false)
const isClearing = ref(false)
const toast = useToast()

const availableIds = computed(() => items.value.filter(i => i.stock > 0).map(i => i.id))
const disabledCount = computed(() => items.value.length - availableIds.value.length)
const allChecked = computed(() => availableIds.value.length > 0 && availableIds.value.every(id => checked.value.includes(id)))
const checkoutProgress = computed(() => {
  if (!availableIds.value.length) return 0
  return Math.round((checked.value.length / availableIds.value.length) * 100)
})

const total = computed(() => items.value
  .filter(i => checked.value.includes(i.id))
  .reduce((sum, i) => sum + Number(i.subtotal), 0)
  .toFixed(2))

watch(total, () => {
  totalPulse.value = true
  window.setTimeout(() => { totalPulse.value = false }, 360)
})

onMounted(load)

async function load() {
  const { data } = await api.get('/cart')
  items.value = data
  checked.value = data.filter(i => i.stock > 0).map(i => i.id)
}

function toggleAll(event) {
  checked.value = event.target.checked ? [...availableIds.value] : []
}

function mark(listRef, id) {
  if (!listRef.value.includes(id)) listRef.value.push(id)
}

function unmark(listRef, id) {
  listRef.value = listRef.value.filter(value => value !== id)
}

async function update(item, quantity) {
  if (quantity < 1 || quantity > item.stock) return
  mark(updatingIds, item.id)
  mark(bumpedIds, item.id)
  try {
    await api.put(`/cart/${item.id}`, { quantity })
    await load()
  } catch (e) {
    toast.show(errorMessage(e))
    await load()
  } finally {
    unmark(updatingIds, item.id)
    window.setTimeout(() => unmark(bumpedIds, item.id), 260)
  }
}

async function remove(item) {
  mark(removingIds, item.id)
  window.setTimeout(async () => {
    try {
      await api.delete(`/cart/${item.id}`)
      toast.show('已从购物车移除')
      await load()
    } catch (e) {
      toast.show(errorMessage(e))
    } finally {
      unmark(removingIds, item.id)
    }
  }, 180)
}

async function clearCart() {
  if (!items.value.length || isClearing.value) return
  if (!window.confirm(`确认清空购物车中的 ${items.value.length} 件商品？`)) return
  isClearing.value = true
  removingIds.value = items.value.map((item) => item.id)
  window.setTimeout(async () => {
    try {
      await api.delete('/cart')
      items.value = []
      checked.value = []
      toast.show('购物车已清空')
    } catch (e) {
      toast.show(errorMessage(e))
      await load()
    } finally {
      removingIds.value = []
      isClearing.value = false
    }
  }, 180)
}

function createOrder() {
  goCheckout()
}

function goCheckout() {
  if (!checked.value.length) return
  router.push({ path: '/checkout', query: { cartItemIds: checked.value.join(',') } })
}
</script>
