<template>
  <div class="shell">
    <header class="topbar">
      <router-link class="brand" to="/">
        <span class="brand-mark">SM</span>
        <span>Smart Mall</span>
      </router-link>
      <nav class="topnav" aria-label="主导航">
        <router-link to="/"><Home size="15" /> 商品</router-link>
        <router-link to="/cart" data-cart-target><ShoppingCart class="cart-icon" size="15" /> 购物车</router-link>
        <router-link to="/favorites"><Heart size="15" /> 收藏</router-link>
        <router-link to="/orders"><ClipboardList size="15" /> 订单</router-link>
        <router-link to="/profile"><UserRound size="15" /> 我的</router-link>
        <router-link v-if="store.user?.role === 'ADMIN'" to="/admin"><Settings size="15" /> 管理</router-link>
      </nav>
      <div class="account-box">
        <template v-if="store.user">
          <div class="avatar">{{ store.user.username.slice(0, 1).toUpperCase() }}</div>
          <div class="account-meta">
            <strong>{{ store.user.username }}</strong>
            <small>{{ store.user.role }}</small>
          </div>
          <button class="icon-btn" title="退出登录" @click="store.logout"><LogOut size="18" /></button>
        </template>
        <template v-else>
          <router-link class="btn primary" to="/login">登录</router-link>
        </template>
      </div>
    </header>
    <main class="content">
      <router-view v-slot="{ Component, route }">
        <transition name="route-fade" mode="out-in">
          <component :is="Component" :key="route.fullPath" />
        </transition>
      </router-view>
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
