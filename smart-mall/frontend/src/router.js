import { createRouter, createWebHistory } from 'vue-router'
import { store } from './store'
import LoginView from './views/LoginView.vue'
import RegisterView from './views/RegisterView.vue'
import ProductListView from './views/ProductListView.vue'
import ProductDetailView from './views/ProductDetailView.vue'
import CartView from './views/CartView.vue'
import OrdersView from './views/OrdersView.vue'
import ProfileView from './views/ProfileView.vue'
import AdminView from './views/AdminView.vue'

const routes = [
  { path: '/', component: ProductListView },
  { path: '/login', component: LoginView },
  { path: '/register', component: RegisterView },
  { path: '/products/:id', component: ProductDetailView },
  { path: '/cart', component: CartView, meta: { auth: true } },
  { path: '/orders', component: OrdersView, meta: { auth: true } },
  { path: '/profile', component: ProfileView, meta: { auth: true } },
  { path: '/admin', component: AdminView, meta: { auth: true, admin: true } }
]

export const router = createRouter({ history: createWebHistory(), routes })
store.router = router

router.beforeEach((to) => {
  if (to.meta.auth && !store.token) return '/login'
  if (to.meta.admin && store.user?.role !== 'ADMIN') return '/'
})
