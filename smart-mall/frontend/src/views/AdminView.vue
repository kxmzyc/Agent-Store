<template>
  <section class="page">
    <div class="page-head">
      <div>
        <h1>商品管理</h1>
        <p>管理员维护商品基础信息、分类、价格和库存。</p>
      </div>
      <button class="primary" @click="newProduct">新增商品</button>
    </div>
    <div class="panel" v-if="editing">
      <form class="form" @submit.prevent="save">
        <label>商品名<input v-model="form.name" required /></label>
        <label>分类
          <select v-model.number="form.categoryId" required>
            <option v-for="c in flatCategories" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
        </label>
        <label>描述 <textarea v-model="form.description" /></label>
        <label>价格 <input v-model.number="form.price" type="number" min="0" step="0.01" required /></label>
        <label>库存 <input v-model.number="form.stock" type="number" min="0" required /></label>
        <label>图片 URL <input v-model="form.imageUrl" /></label>
        <div class="toolbar">
          <button class="primary">保存</button>
          <button type="button" @click="editing = false">取消</button>
        </div>
      </form>
    </div>
    <table class="table table-spaced">
      <thead>
        <tr><th>ID</th><th>商品</th><th>价格</th><th>库存</th><th>销量</th><th>状态</th><th>操作</th></tr>
      </thead>
      <tbody>
        <tr v-for="p in products" :key="p.id" :class="{ 'row-inactive': p.status !== 1 }">
          <td>{{ p.id }}</td>
          <td>{{ p.name }}</td>
          <td>¥{{ p.price }}</td>
          <td>{{ p.stock }}</td>
          <td>{{ p.salesCount }}</td>
          <td>{{ p.status === 1 ? '在售' : '已下架' }}</td>
          <td>
            <button @click="edit(p)">编辑</button>
            <button v-if="p.status === 1" @click="remove(p)">下架</button>
            <button v-else @click="activate(p)">上架</button>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { api, errorMessage } from '../api/http'
import { useToast } from '../composables/useToast'

const categories = ref([])
const products = ref([])
const editing = ref(false)
const form = reactive(blank())
const toast = useToast()

const flatCategories = computed(() => categories.value.flatMap(c => [c, ...(c.children || [])]))

onMounted(async () => {
  const cats = await api.get('/categories')
  categories.value = cats.data
  await load()
})

function blank() {
  return { id: null, categoryId: 1, name: '', description: '', price: 0, stock: 0, imageUrl: '', status: 1 }
}

async function load() {
  const { data } = await api.get('/products/admin', { params: { page: 1, size: 100 } })
  products.value = data.list
}

function newProduct() {
  Object.assign(form, blank(), { categoryId: flatCategories.value[0]?.id || 1 })
  editing.value = true
}

function edit(product) {
  Object.assign(form, product)
  editing.value = true
}

async function save() {
  try {
    if (form.id) await api.put(`/products/${form.id}`, form)
    else await api.post('/products', form)
    editing.value = false
    toast.show('商品已保存')
    await load()
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

async function remove(product) {
  if (!confirm(`确认下架 ${product.name}？`)) return
  try {
    await api.delete(`/products/${product.id}`)
    toast.show('商品已下架')
    await load()
  } catch (e) {
    toast.show(errorMessage(e))
  }
}

async function activate(product) {
  try {
    await api.put(`/products/${product.id}`, {
      categoryId: product.categoryId,
      name: product.name,
      description: product.description,
      price: product.price,
      stock: product.stock,
      imageUrl: product.imageUrl,
      status: 1
    })
    toast.show('商品已上架')
    await load()
  } catch (e) {
    toast.show(errorMessage(e))
  }
}
</script>
