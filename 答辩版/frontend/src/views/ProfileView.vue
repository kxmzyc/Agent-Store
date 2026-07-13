<template>
  <section class="page profile-page">
    <div class="page-head">
      <div>
        <h1>个人中心</h1>
        <p>集中查看账户资料、订单待办、收藏商品、浏览足迹和 AI 偏好记忆。</p>
      </div>
      <div class="toolbar">
        <button type="button" @click="feedbackOpen = true"><MessageSquare size="18" /> 意见反馈</button>
        <router-link class="btn" to="/coupons"><Ticket size="18" /> 我的优惠券</router-link>
        <router-link class="btn" to="/favorites"><Heart size="18" /> 我的收藏</router-link>
      </div>
    </div>

    <div v-if="overview" class="profile-overview-grid">
      <article v-for="card in overviewCards" :key="card.label" class="metric-card profile-metric">
        <span>{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
        <small>{{ card.hint }}</small>
      </article>
    </div>

    <div class="profile-dashboard-grid">
      <section class="panel profile-card">
        <div class="panel-head">
          <div>
            <h2>账户资料</h2>
            <p>维护手机号和头像地址，登录状态刷新后保持。</p>
          </div>
        </div>
        <form class="form" @submit.prevent="save">
          <label>用户名
            <input :value="profile.username" disabled />
          </label>
          <label>手机号
            <input v-model="profile.phone" />
          </label>
          <label>头像 URL
            <input v-model="profile.avatarUrl" />
          </label>
          <label>创建时间
            <input :value="formatTime(profile.createdAt)" disabled />
          </label>
          <p v-if="message" :class="messageType">{{ message }}</p>
          <div class="profile-action-swap">
            <span class="profile-edit-label">编辑资料</span>
            <button class="profile-save-btn">保存</button>
          </div>
        </form>
      </section>

      <section class="panel profile-list-panel">
        <div class="panel-head">
          <div>
            <h2>最近浏览</h2>
            <p>商品详情页会自动记录登录用户的浏览足迹。</p>
          </div>
          <button class="ghost" @click="loadOverview">刷新</button>
        </div>
        <div v-if="overview?.recentViews?.length" class="compact-list">
          <router-link
            v-for="item in overview.recentViews"
            :key="item.id"
            class="compact-item profile-product-row"
            :to="`/products/${item.product.id}`"
          >
            <img :src="item.product.imageUrl" :alt="item.product.name" width="58" height="58" loading="lazy" decoding="async" />
            <div>
              <strong>{{ item.product.name }}</strong>
              <small>浏览 {{ item.viewCount }} 次 · {{ formatTime(item.lastViewedAt) }}</small>
            </div>
            <b>¥{{ item.product.price }}</b>
          </router-link>
        </div>
        <p v-else class="muted">暂无浏览足迹。</p>
      </section>
    </div>

    <section class="panel address-book-panel">
      <div class="panel-head">
        <div>
          <h2>收货地址</h2>
          <p>结算页会默认选中默认地址，也可以临时填写新地址。</p>
        </div>
        <button class="primary" type="button" @click="openAddressForm()">新增地址</button>
      </div>
      <div v-if="addresses.length" class="address-card-grid">
        <article v-for="address in addresses" :key="address.id" class="address-card" :class="{ active: address.isDefault }">
          <div>
            <strong>{{ address.receiverName }} · {{ address.phone }}</strong>
            <p>{{ formatAddress(address) }}</p>
          </div>
          <span v-if="address.isDefault" class="status-pill">默认</span>
          <div class="row-actions">
            <button type="button" @click="openAddressForm(address)">编辑</button>
            <button type="button" :disabled="address.isDefault" @click="setDefaultAddress(address)">设为默认</button>
            <button class="ghost danger-ghost" type="button" @click="deleteAddress(address)">删除</button>
          </div>
        </article>
      </div>
      <p v-else class="muted">暂无收货地址，新增后结算页会自动读取。</p>
    </section>

    <section class="panel profile-list-panel">
      <div class="panel-head">
        <div>
          <h2>近期收藏</h2>
          <p>收藏可以沉淀用户兴趣，也方便答辩展示真实商城体验。</p>
        </div>
        <router-link class="btn" to="/favorites">查看全部</router-link>
      </div>
      <div v-if="overview?.recentFavorites?.length" class="compact-list favorite-preview-list">
        <router-link
          v-for="item in overview.recentFavorites"
          :key="item.id"
          class="compact-item profile-product-row"
          :to="`/products/${item.product.id}`"
        >
          <img :src="item.product.imageUrl" :alt="item.product.name" width="58" height="58" loading="lazy" decoding="async" />
          <div>
            <strong>{{ item.product.name }}</strong>
            <small>收藏于 {{ formatTime(item.createdAt) }} · 已售 {{ item.product.salesCount }}</small>
          </div>
          <b>¥{{ item.product.price }}</b>
        </router-link>
      </div>
      <p v-else class="muted">暂无收藏商品。</p>
    </section>

    <section class="panel preference-panel">
      <div class="panel-head">
        <div>
          <h2>AI 偏好记忆</h2>
          <p>导购助手会根据这些标签做个性化推荐。</p>
        </div>
        <button class="ghost" @click="loadPreferences">刷新</button>
      </div>
      <div v-if="preferences.length" class="preference-list">
        <span v-for="item in preferences" :key="item.tag" class="preference-chip">
          <span>{{ item.tag }}</span>
          <small>{{ item.weight.toFixed(1) }}</small>
          <button type="button" title="删除偏好" @click="removePreference(item.tag)">×</button>
        </span>
      </div>
      <p v-else class="muted">暂无偏好标签。可以在 AI 聊天窗里告诉助手你的购物偏好。</p>
    </section>

    <div v-if="feedbackOpen" class="modal-backdrop" @click.self="feedbackOpen = false">
      <section class="panel feedback-modal">
        <div class="panel-head">
          <div>
            <h2>意见反馈</h2>
            <p>提交后管理员可在运营工作台里处理并回复。</p>
          </div>
          <button class="ghost" type="button" @click="feedbackOpen = false">关闭</button>
        </div>
        <form class="form" @submit.prevent="submitFeedback">
          <label>类型
            <select v-model.number="feedbackForm.type">
              <option :value="1">建议</option>
              <option :value="2">投诉</option>
              <option :value="3">BUG</option>
            </select>
          </label>
          <label>内容
            <textarea v-model="feedbackForm.content" required maxlength="1000" placeholder="请描述你的建议、问题或复现步骤" />
          </label>
          <button class="primary" :disabled="feedbackSubmitting">{{ feedbackSubmitting ? '提交中...' : '提交反馈' }}</button>
        </form>
      </section>
    </div>

    <div v-if="addressOpen" class="modal-backdrop" @click.self="addressOpen = false">
      <section class="panel feedback-modal">
        <div class="panel-head">
          <div>
            <h2>{{ addressForm.id ? '编辑地址' : '新增地址' }}</h2>
            <p>这些字段会拼成订单收货地址快照。</p>
          </div>
          <button class="ghost" type="button" @click="addressOpen = false">关闭</button>
        </div>
        <form class="form address-modal-form" @submit.prevent="saveAddress">
          <input v-model="addressForm.receiverName" required placeholder="收件人" />
          <input v-model="addressForm.phone" required placeholder="联系电话" />
          <input v-model="addressForm.province" required placeholder="省份" />
          <input v-model="addressForm.city" required placeholder="城市" />
          <input v-model="addressForm.district" required placeholder="区县" />
          <input v-model="addressForm.detailAddress" required placeholder="详细地址" />
          <label class="check-control">
            <input v-model="addressForm.isDefault" type="checkbox" />
            <span>设为默认地址</span>
          </label>
          <button class="primary" :disabled="addressSaving">{{ addressSaving ? '保存中...' : '保存地址' }}</button>
        </form>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { agentApi, api, errorMessage } from '../api/http'
import { store } from '../store'
import { useToast } from '../composables/useToast'

const profile = reactive({ username: '', phone: '', avatarUrl: '', createdAt: '' })
const message = ref('')
const messageType = ref('muted')
const preferences = ref([])
const overview = ref(null)
const addresses = ref([])
const addressOpen = ref(false)
const addressSaving = ref(false)
const feedbackOpen = ref(false)
const feedbackSubmitting = ref(false)
const feedbackForm = reactive({ type: 1, content: '' })
const addressForm = reactive(blankAddress())
const toast = useToast()

const overviewCards = computed(() => {
  const data = overview.value
  if (!data) return []
  return [
    { label: '积分', value: profile.points || 0, hint: '100 积分抵 1 元' },
    { label: '购物车', value: data.cartItems, hint: '当前待结算商品' },
    { label: '收藏', value: data.favorites, hint: '心愿单商品' },
    { label: '浏览足迹', value: data.viewedProducts, hint: '已记录商品' },
    { label: '待付款', value: data.pendingPaymentOrders, hint: '需要继续支付' },
    { label: '待发货', value: data.paidOrders, hint: '管理员待处理' },
    { label: '待收货', value: data.shippedOrders, hint: `${data.completedOrders} 个已完成` }
  ]
})

onMounted(() => {
  load()
  loadOverview()
  loadPreferences()
  loadAddresses()
})

async function load() {
  const { data } = await api.get('/user/profile')
  Object.assign(profile, data)
}

async function loadPreferences() {
  const { data } = await agentApi.get('/preferences')
  preferences.value = data
}

async function loadOverview() {
  try {
    const { data } = await api.get('/user/overview')
    overview.value = data
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

async function save() {
  try {
    const { data } = await api.put('/user/profile', { phone: profile.phone, avatarUrl: profile.avatarUrl })
    Object.assign(profile, data)
    store.user = data
    store.persistUser()
    messageType.value = 'muted'
    message.value = '已保存'
    toast.show('个人资料已保存')
  } catch (e) {
    messageType.value = 'error'
    message.value = errorMessage(e)
    toast.show(message.value)
  }
}

async function removePreference(tag) {
  try {
    await agentApi.delete(`/preferences/${encodeURIComponent(tag)}`)
    preferences.value = preferences.value.filter((item) => item.tag !== tag)
    toast.show('偏好标签已删除')
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

async function loadAddresses() {
  try {
    const { data } = await api.get('/addresses')
    addresses.value = data || []
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

function blankAddress() {
  return {
    id: null,
    receiverName: '',
    phone: '',
    province: '',
    city: '',
    district: '',
    detailAddress: '',
    isDefault: false
  }
}

function openAddressForm(address = null) {
  Object.assign(addressForm, blankAddress(), address || {})
  addressOpen.value = true
}

async function saveAddress() {
  addressSaving.value = true
  try {
    const payload = {
      receiverName: addressForm.receiverName,
      phone: addressForm.phone,
      province: addressForm.province,
      city: addressForm.city,
      district: addressForm.district,
      detailAddress: addressForm.detailAddress,
      isDefault: addressForm.isDefault
    }
    if (addressForm.id) await api.put(`/addresses/${addressForm.id}`, payload)
    else await api.post('/addresses', payload)
    addressOpen.value = false
    toast.show('地址已保存')
    await loadAddresses()
  } catch (e) {
    toast.show(errorMessage(e))
  } finally {
    addressSaving.value = false
  }
}

async function setDefaultAddress(address) {
  try {
    await api.put(`/addresses/${address.id}/default`)
    toast.show('默认地址已更新')
    await loadAddresses()
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

async function deleteAddress(address) {
  if (!window.confirm(`确认删除 ${address.receiverName} 的地址？`)) return
  try {
    await api.delete(`/addresses/${address.id}`)
    toast.show('地址已删除')
    await loadAddresses()
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

async function submitFeedback() {
  feedbackSubmitting.value = true
  try {
    await api.post('/feedback', feedbackForm)
    feedbackForm.type = 1
    feedbackForm.content = ''
    feedbackOpen.value = false
    toast.show('反馈已提交')
  } catch (e) {
    toast.show(errorMessage(e))
  } finally {
    feedbackSubmitting.value = false
  }
}

function formatTime(value) {
  if (!value) return ''
  return value.replace('T', ' ').slice(0, 16)
}

function formatAddress(address) {
  return [address.province, address.city, address.district, address.detailAddress]
    .filter(Boolean)
    .join(' ')
}
</script>
