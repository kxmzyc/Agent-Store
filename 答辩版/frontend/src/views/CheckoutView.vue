<template>
  <section class="page checkout-page">
    <div class="page-head">
      <div>
        <h1>确认订单</h1>
        <p>{{ isDirect ? '从商品详情直接下单，提交后进入模拟支付流程。' : '核对购物车勾选商品，确认地址后提交订单。' }}</p>
      </div>
      <router-link class="btn" :to="isDirect ? `/products/${directProductId}` : '/cart'">
        <ArrowLeft size="18" /> 返回
      </router-link>
    </div>

    <p v-if="loading" class="muted">结算信息加载中...</p>
    <p v-else-if="loadError" class="error">{{ loadError }}</p>

    <div v-else class="checkout-layout">
      <section class="panel checkout-lines-panel">
        <div class="panel-head">
          <div>
            <h2>商品清单</h2>
            <p>下单时会按当前价格生成订单快照。</p>
          </div>
          <span class="count-pill">{{ lines.length }} 件</span>
        </div>

        <div class="checkout-lines">
          <article v-for="line in lines" :key="line.key" class="checkout-line">
            <img :src="line.imageUrl" :alt="line.name" width="74" height="74" loading="lazy" decoding="async" />
            <div>
              <strong>{{ line.name }}</strong>
              <small>{{ line.description || `库存 ${line.stock}` }}</small>
            </div>
            <span>¥{{ line.price }} × {{ line.quantity }}</span>
            <b>¥{{ line.subtotal }}</b>
          </article>
        </div>
      </section>

      <aside class="panel checkout-panel checkout-submit-panel">
        <div class="checkout-head">
          <div>
            <span class="muted">应付金额</span>
            <strong>¥{{ payableTotal }}</strong>
          </div>
          <small>原价 ¥{{ subtotal }}</small>
        </div>

        <div class="checkout-benefits">
          <div class="benefit-head">
            <strong>优惠抵扣</strong>
            <router-link to="/coupons">去领券</router-link>
          </div>

          <div class="coupon-picker">
            <button
              v-for="coupon in usableCoupons"
              :key="coupon.id"
              type="button"
              class="benefit-option"
              :class="{ active: selectedCouponId === coupon.id }"
              :disabled="!canUseCoupon(coupon)"
              @click="selectedCouponId = selectedCouponId === coupon.id ? null : coupon.id"
            >
              <span>{{ coupon.name }}</span>
              <small>{{ couponRule(coupon) }} · {{ couponValue(coupon) }}</small>
            </button>
            <p v-if="!usableCoupons.length && !benefitsLoading" class="muted">暂无可用优惠券。</p>
            <p v-if="benefitsLoading" class="muted">优惠信息加载中...</p>
          </div>

          <label class="point-toggle">
            <input v-model="usePoints" type="checkbox" :disabled="!pointsInfo.points" />
            <span>使用积分抵扣</span>
            <small>余额 {{ pointsInfo.points || 0 }}，本单最多抵 ¥{{ maxPointDiscount }}</small>
          </label>

          <div class="checkout-discount-lines">
            <div><span>优惠券</span><b>-¥{{ couponDiscount }}</b></div>
            <div><span>积分</span><b>-¥{{ pointDiscount }}</b></div>
          </div>
        </div>

        <form class="form checkout-form" @submit.prevent="submit">
          <div class="address-picker">
            <strong>收货地址</strong>
            <div v-if="addresses.length" class="address-option-list">
              <label v-for="address in addresses" :key="address.id" class="address-option" :class="{ active: addressMode === 'saved' && selectedAddressId === address.id }">
                <input v-model="selectedAddressId" type="radio" :value="address.id" @change="addressMode = 'saved'" />
                <span>
                  <b>{{ address.receiverName }} · {{ address.phone }}</b>
                  <small>{{ formatAddress(address) }}</small>
                </span>
                <em v-if="address.isDefault">默认</em>
              </label>
            </div>
            <button type="button" class="ghost" @click="addressMode = 'new'">使用新地址</button>
            <div v-if="addressMode === 'new'" class="address-form-grid">
              <input v-model="tempAddress.receiverName" required placeholder="收件人" />
              <input v-model="tempAddress.phone" required placeholder="联系电话" />
              <input v-model="tempAddress.province" required placeholder="省份" />
              <input v-model="tempAddress.city" required placeholder="城市" />
              <input v-model="tempAddress.district" required placeholder="区县" />
              <input v-model="tempAddress.detailAddress" required placeholder="详细地址" />
            </div>
          </div>
          <p v-if="error" class="error">{{ error }}</p>
          <button class="primary checkout-submit" :disabled="submitting || !lines.length">
            <CreditCard size="18" />
            {{ submitting ? '提交中...' : '提交订单' }}
          </button>
        </form>
      </aside>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api, errorMessage, refreshCartCount } from '../api/http'
import { router } from '../router'
import { store } from '../store'
import { useToast } from '../composables/useToast'

const route = useRoute()
const toast = useToast()
const loading = ref(true)
const submitting = ref(false)
const loadError = ref('')
const error = ref('')
const lines = ref([])
const usableCoupons = ref([])
const addresses = ref([])
const selectedAddressId = ref(null)
const addressMode = ref('new')
const tempAddress = reactive({
  receiverName: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detailAddress: ''
})
const selectedCouponId = ref(null)
const pointsInfo = ref({ points: 0, records: [] })
const usePoints = ref(false)
const benefitsLoading = ref(false)
const isDirect = computed(() => route.query.mode === 'direct')
const directProductId = computed(() => Number(route.query.productId || 0))
const directQuantity = computed(() => Math.max(1, Number(route.query.quantity || 1)))
const cartItemIds = computed(() => String(route.query.cartItemIds || '')
  .split(',')
  .map((value) => Number(value))
  .filter(Boolean))
const subtotal = computed(() => lines.value
  .reduce((sum, line) => sum + Number(line.subtotal), 0)
  .toFixed(2))
const selectedCoupon = computed(() => usableCoupons.value.find(coupon => coupon.id === selectedCouponId.value) || null)
const couponDiscount = computed(() => money(calculateCouponDiscount(selectedCoupon.value, Number(subtotal.value))))
const amountAfterCoupon = computed(() => Math.max(0.01, Number(subtotal.value) - Number(couponDiscount.value)))
const maxPointDiscount = computed(() => {
  const yuan = Math.floor((pointsInfo.value.points || 0) / 100)
  const maxYuan = Math.floor(Math.max(0, amountAfterCoupon.value - 0.01))
  return money(Math.min(yuan, maxYuan))
})
const pointDiscount = computed(() => usePoints.value ? maxPointDiscount.value : '0.00')
const payableTotal = computed(() => money(Math.max(0.01, Number(subtotal.value) - Number(couponDiscount.value) - Number(pointDiscount.value))))
const selectedAddress = computed(() => addresses.value.find(address => address.id === selectedAddressId.value) || null)
const shippingAddressText = computed(() => addressMode.value === 'saved'
  ? formatShippingAddress(selectedAddress.value)
  : formatShippingAddress(tempAddress))

onMounted(load)
watch(() => route.fullPath, load)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    if (isDirect.value) {
      if (!directProductId.value) throw new Error('缺少商品信息')
      const { data } = await api.get(`/products/${directProductId.value}`)
      if (data.stock <= 0) throw new Error('商品已售罄')
      if (directQuantity.value > data.stock) throw new Error('购买数量超出库存')
      lines.value = [{
        key: `product-${data.id}`,
        productId: data.id,
        name: data.name,
        description: data.description,
        imageUrl: data.imageUrl,
        price: data.price,
        stock: data.stock,
        quantity: directQuantity.value,
        subtotal: (Number(data.price) * directQuantity.value).toFixed(2)
      }]
    } else {
      if (!cartItemIds.value.length) throw new Error('请选择需要结算的购物车商品')
      const { data } = await api.get('/cart')
      const selected = data.filter((item) => cartItemIds.value.includes(item.id) && item.stock > 0)
      if (!selected.length) throw new Error('选中的商品不可结算')
      lines.value = selected.map((item) => ({
        key: `cart-${item.id}`,
        cartItemId: item.id,
        name: item.productName,
        imageUrl: item.imageUrl,
        price: item.price,
        stock: item.stock,
        quantity: item.quantity,
        subtotal: item.subtotal
      }))
    }
    await Promise.all([loadBenefits(), loadAddresses()])
  } catch (e) {
    lines.value = []
    loadError.value = e.response ? errorMessage(e) : e.message || errorMessage(e)
  } finally {
    loading.value = false
  }
}

async function loadAddresses() {
  try {
    const { data } = await api.get('/addresses')
    addresses.value = data || []
    const defaultAddress = addresses.value.find(address => address.isDefault) || addresses.value[0]
    if (defaultAddress) {
      selectedAddressId.value = defaultAddress.id
      addressMode.value = 'saved'
    } else {
      addressMode.value = 'new'
    }
  } catch {
    addresses.value = []
    addressMode.value = 'new'
  }
}

async function loadBenefits() {
  benefitsLoading.value = true
  try {
    const [coupons, points] = await Promise.all([
      api.get('/coupons/my', { params: { status: 0 } }),
      api.get('/points/my')
    ])
    usableCoupons.value = coupons.data.filter((coupon) => coupon.status === 0)
    pointsInfo.value = points.data
    if (store.user) {
      store.user.points = points.data.points || 0
      store.persistUser()
    }
    if (selectedCoupon.value && !canUseCoupon(selectedCoupon.value)) selectedCouponId.value = null
  } catch {
    usableCoupons.value = []
    pointsInfo.value = { points: 0, records: [] }
  } finally {
    benefitsLoading.value = false
  }
}

async function submit() {
  error.value = ''
  const address = shippingAddressText.value.trim()
  if (!address) {
    error.value = '请填写收货地址'
    return
  }
  submitting.value = true
  try {
    const payload = { shippingAddress: address }
    if (selectedCoupon.value && canUseCoupon(selectedCoupon.value)) payload.userCouponId = selectedCoupon.value.id
    payload.usePoints = usePoints.value
    const { data } = isDirect.value
      ? await api.post('/orders/direct', { ...payload, productId: directProductId.value, quantity: directQuantity.value })
      : await api.post('/orders', { ...payload, cartItemIds: lines.value.map((line) => line.cartItemId) })
    if (store.user && data.pointsUsed) {
      store.user.points = Math.max(0, (store.user.points || 0) - data.pointsUsed)
      store.persistUser()
    }
    await refreshCartCount()
    toast.show('订单已提交')
    router.push(`/orders/${data.orderId}`)
  } catch (e) {
    error.value = errorMessage(e)
    toast.show(error.value)
  } finally {
    submitting.value = false
  }
}

function canUseCoupon(coupon) {
  return coupon && Number(subtotal.value) >= Number(coupon.threshold || 0)
}

function calculateCouponDiscount(coupon, amount) {
  if (!coupon || !canUseCoupon(coupon)) return 0
  const raw = coupon.type === 2
    ? amount - amount * Number(coupon.discount)
    : Number(coupon.discount)
  return Math.min(Math.max(raw, 0), Math.max(0, amount - 0.01))
}

function couponValue(coupon) {
  return coupon.type === 2 ? `${Number(coupon.discount) * 10}折` : `¥${coupon.discount}`
}

function couponRule(coupon) {
  return Number(coupon.threshold) > 0 ? `满 ¥${coupon.threshold} 可用` : '无门槛'
}

function money(value) {
  return Number(value || 0).toFixed(2)
}

function formatAddress(address) {
  if (!address) return ''
  return [address.province, address.city, address.district, address.detailAddress]
    .filter(Boolean)
    .join(' ')
}

function formatShippingAddress(address) {
  if (!address) return ''
  return [address.receiverName, address.phone, formatAddress(address)]
    .filter(Boolean)
    .join(' ')
}
</script>
