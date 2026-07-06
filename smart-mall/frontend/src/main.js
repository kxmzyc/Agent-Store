import { createApp } from 'vue'
import {
  LogOut, Search, ShoppingCart, UserRound, PackageSearch, Bot, Send,
  Plus, Minus, Trash2, CreditCard, ClipboardList, Settings, Home, ArrowLeft,
  Heart, RotateCcw, Eye, ChevronLeft, ChevronRight, Pause, Play, Ticket, Download,
  MessageSquare
} from 'lucide-vue-next'
import App from './App.vue'
import './styles/tokens.css'
import './style.css'
import { router } from './router'

if (import.meta.env.VITE_PERF_HUD === '1') {
  import('./dev/fxDiagnostics').then((module) => module.mountFxDiagnostics())
  import('./dev/perfHud').then((module) => module.mountPerfHud())
}

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
app.component('ArrowLeft', ArrowLeft)
app.component('Heart', Heart)
app.component('RotateCcw', RotateCcw)
app.component('Eye', Eye)
app.component('ChevronLeft', ChevronLeft)
app.component('ChevronRight', ChevronRight)
app.component('Pause', Pause)
app.component('Play', Play)
app.component('Ticket', Ticket)
app.component('Download', Download)
app.component('MessageSquare', MessageSquare)
app.mount('#app')
