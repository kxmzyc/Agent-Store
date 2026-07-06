import { createRouter, createWebHistory } from 'vue-router'
import { store } from './store'
import LoginView from './views/LoginView.vue'
import RegisterView from './views/RegisterView.vue'
import ProductListView from './views/ProductListView.vue'
import ProductDetailView from './views/ProductDetailView.vue'
import CartView from './views/CartView.vue'
import CheckoutView from './views/CheckoutView.vue'
import OrdersView from './views/OrdersView.vue'
import OrderDetailView from './views/OrderDetailView.vue'
import FavoritesView from './views/FavoritesView.vue'
import ProfileView from './views/ProfileView.vue'
import CouponView from './views/CouponView.vue'
import AdminView from './views/AdminView.vue'

const routes = [
  { path: '/', component: ProductListView },
  { path: '/login', component: LoginView },
  { path: '/register', component: RegisterView },
  { path: '/products/:id', component: ProductDetailView },
  { path: '/cart', component: CartView, meta: { auth: true } },
  { path: '/checkout', component: CheckoutView, meta: { auth: true } },
  { path: '/orders', component: OrdersView, meta: { auth: true } },
  { path: '/orders/:id', component: OrderDetailView, meta: { auth: true } },
  { path: '/favorites', component: FavoritesView, meta: { auth: true } },
  { path: '/coupons', component: CouponView, meta: { auth: true } },
  { path: '/profile', component: ProfileView, meta: { auth: true } },
  { path: '/admin', component: AdminView, meta: { auth: true, admin: true } }
]

export const router = createRouter({ history: createWebHistory(), routes })
store.router = router

router.beforeEach((to) => {
  if (to.meta.auth && !store.token) return '/login'
  if (to.meta.admin && store.user?.role !== 'ADMIN') return '/'
})
