<template>
  <div class="shell">
    <aside class="sidebar">
      <router-link class="brand" to="/">
        <span class="brand-mark">SM</span>
        <span>Smart Mall</span>
      </router-link>
      <nav>
        <router-link to="/"><Home size="18" /> 商品</router-link>
        <router-link to="/cart" data-cart-target><ShoppingCart size="18" /> 购物车</router-link>
        <router-link to="/favorites"><Heart size="18" /> 收藏</router-link>
        <router-link to="/orders"><ClipboardList size="18" /> 订单</router-link>
        <router-link to="/profile"><UserRound size="18" /> 个人中心</router-link>
        <router-link v-if="store.user?.role === 'ADMIN'" to="/admin"><Settings size="18" /> 管理</router-link>
      </nav>
      <div class="account-box">
        <template v-if="store.user">
          <div class="avatar">{{ store.user.username.slice(0, 1).toUpperCase() }}</div>
          <div>
            <strong>{{ store.user.username }}</strong>
            <small>{{ store.user.role }}</small>
          </div>
          <button class="icon-btn" title="退出登录" @click="store.logout"><LogOut size="18" /></button>
        </template>
        <template v-else>
          <router-link class="btn primary full" to="/login">登录</router-link>
        </template>
      </div>
    </aside>
    <main class="content">
      <router-view />
    </main>
    <ChatWidget />
    <GlobalToast />
  </div>
</template>

<script setup>
import { store } from './store'
import ChatWidget from './components/ChatWidget.vue'
import GlobalToast from './components/GlobalToast.vue'
</script>
