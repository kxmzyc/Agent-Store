import { createApp } from 'vue'
import {
  LogOut, Search, ShoppingCart, UserRound, PackageSearch, Bot, Send,
  Plus, Minus, Trash2, CreditCard, ClipboardList, Settings, Home
} from 'lucide-vue-next'
import App from './App.vue'
import './styles/tokens.css'
import './style.css'
import { router } from './router'

const app = createApp(App)
app.use(router)
app.component('LogOut', LogOut)
app.component('Search', Search)
app.component('ShoppingCart', ShoppingCart)
app.component('UserRound', UserRound)
app.component('PackageSearch', PackageSearch)
app.component('Bot', Bot)
app.component('Send', Send)
app.component('Plus', Plus)
app.component('Minus', Minus)
app.component('Trash2', Trash2)
app.component('CreditCard', CreditCard)
app.component('ClipboardList', ClipboardList)
app.component('Settings', Settings)
app.component('Home', Home)
app.mount('#app')
