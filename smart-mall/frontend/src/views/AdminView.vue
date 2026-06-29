<template>
  <section>
    <div class="page-head">
      <div>
        <h1>商品管理</h1>
        <p>管理员维护商品基础信息、分类、价格和库存。</p>
      </div>
      <button class="primary" @click="newProduct">新增商品</button>
    </div>
    <div class="panel" v-if="editing">
      <form class="form" @submit.prevent="save">
        <label>商品名 <input v-model="form.name" required /></label>
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
    <table class="table" style="margin-top: 18px;">
      <thead>
        <tr><th>ID</th><th>商品</th><th>价格</th><th>库存</th><th>销量</th><th>操作</th></tr>
      </thead>
      <tbody>
        <tr v-for="p in products" :key="p.id">
          <td>{{ p.id }}</td>
          <td>{{ p.name }}</td>
          <td>¥{{ p.price }}</td>
          <td>{{ p.stock }}</td>
          <td>{{ p.salesCount }}</td>
          <td>
            <button @click="edit(p)">编辑</button>
            <button @click="remove(p)">下架</button>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { api, errorMessage } from '../api/http'

const categories = ref([])
const products = ref([])
const editing = ref(false)
const form = reactive(blank())

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
  const { data } = await api.get('/products', { params: { page: 1, size: 100 } })
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
    await load()
  } catch (e) {
    alert(errorMessage(e))
  }
}

async function remove(product) {
  if (!confirm(`确认下架 ${product.name}？`)) return
  await api.delete(`/products/${product.id}`)
  await load()
}
</script>
