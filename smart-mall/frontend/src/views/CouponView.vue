<template>
  <section class="page coupon-page">
    <div class="page-head">
      <div>
        <h1>购物权益</h1>
        <p>管理账户里的抵扣权益，结算时系统会按门槛和有效期自动校验。</p>
      </div>
      <router-link class="btn" to="/profile"><UserRound size="18" /> 返回我的</router-link>
    </div>

    <div class="coupon-brief">
      <div>
        <span>可收入账户</span>
        <strong>{{ availableCoupons.length }}</strong>
      </div>
      <div>
        <span>当前可用</span>
        <strong>{{ usableCount }}</strong>
      </div>
      <p>权益不会在商品页打断浏览，只在结算页展示可用抵扣，避免用户反复判断门槛。</p>
    </div>

    <div class="status-tabs">
      <button :class="{ active: activeTab === 'available' }" @click="activeTab = 'available'">可用权益</button>
      <button :class="{ active: activeTab === 'mine' }" @click="activeTab = 'mine'">账户记录</button>
    </div>

    <p v-if="loading" class="muted">权益信息加载中...</p>

    <div v-if="activeTab === 'available'" class="coupon-grid">
      <article v-for="coupon in availableCoupons" :key="coupon.id" class="coupon-card">
        <div>
          <span class="coupon-kicker">{{ coupon.type === 1 ? 'ORDER CREDIT' : 'PRICE ADJUST' }}</span>
          <h2>{{ coupon.name }}</h2>
          <p>{{ couponRule(coupon) }} · 收入账户后 {{ coupon.validDays }} 天内可用于结算</p>
        </div>
        <strong>{{ couponValue(coupon) }}</strong>
        <button class="primary" :disabled="coupon.claimed || coupon.remainCount <= 0" @click="claim(coupon)">
          {{ coupon.claimed ? '已在账户' : coupon.remainCount <= 0 ? '本期已满' : '收入账户' }}
        </button>
      </article>
      <p v-if="!availableCoupons.length && !loading" class="muted">当前没有可配置的新权益。</p>
    </div>

    <div v-else class="coupon-groups">
      <section v-for="group in myGroups" :key="group.status" class="panel">
        <div class="panel-head">
          <div>
            <h2>{{ group.label }}</h2>
            <p>{{ group.hint }}</p>
          </div>
          <span class="count-pill">{{ group.items.length }}</span>
        </div>
        <div v-if="group.items.length" class="coupon-grid compact-coupon-grid">
          <article v-for="coupon in group.items" :key="coupon.id" class="coupon-card" :class="{ used: coupon.status !== 0 }">
            <div>
              <span class="coupon-kicker">{{ coupon.type === 1 ? 'ORDER CREDIT' : 'PRICE ADJUST' }}</span>
              <h2>{{ coupon.name }}</h2>
              <p>{{ couponRule(coupon) }} · {{ coupon.expireAt }} 到期</p>
            </div>
            <strong>{{ couponValue(coupon) }}</strong>
            <span class="status-pill">{{ statusLabel(coupon.status) }}</span>
          </article>
        </div>
        <p v-else class="muted">暂无{{ group.label }}。</p>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { api, errorMessage } from '../api/http'
import { useToast } from '../composables/useToast'

const activeTab = ref('available')
const availableCoupons = ref([])
const myCoupons = ref([])
const loading = ref(false)
const toast = useToast()
const usableCount = computed(() => myCoupons.value.filter(c => c.status === 0).length)

const myGroups = computed(() => [
  { status: 0, label: '可用权益', hint: '结算页会按订单金额判断是否可用。', items: myCoupons.value.filter(c => c.status === 0) },
  { status: 1, label: '已核销', hint: '已经随订单完成抵扣的权益。', items: myCoupons.value.filter(c => c.status === 1) },
  { status: 2, label: '已失效', hint: '超过有效期后不可继续使用。', items: myCoupons.value.filter(c => c.status === 2) }
])

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [available, mine] = await Promise.all([
      api.get('/coupons/available'),
      api.get('/coupons/my')
    ])
    availableCoupons.value = available.data
    myCoupons.value = mine.data
  } catch (e) {
    toast.show(errorMessage(e))
  } finally {
    loading.value = false
  }
}

async function claim(coupon) {
  try {
    await api.post(`/coupons/${coupon.id}/claim`)
    toast.show('已加入权益账户')
    await load()
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

function couponValue(coupon) {
  return coupon.type === 2 ? `${Number(coupon.discount) * 10}折` : `¥${coupon.discount}`
}

function couponRule(coupon) {
  return Number(coupon.threshold) > 0 ? `满 ¥${coupon.threshold} 可用` : '无门槛'
}

function statusLabel(status) {
  if (status === 1) return '已使用'
  if (status === 2) return '已过期'
  return '可使用'
}
</script>
