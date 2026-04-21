<template>
  <main class="page">
    <h1>DBMS 管理系统（B/S 架构）</h1>
    <p>前端：Vue 3 + Vite</p>
    <p>后端：Spring Boot 3</p>
    <button :disabled="loading" @click="checkHealth">
      {{ loading ? '检查中...' : '检查后端服务状态' }}
    </button>
    <pre v-if="result">{{ result }}</pre>
  </main>
</template>

<script setup>
import { ref } from 'vue'
import { getHealth } from './api/http'

const loading = ref(false)
const result = ref('')

const checkHealth = async () => {
  loading.value = true
  try {
    const res = await getHealth()
    result.value = JSON.stringify(res.data, null, 2)
  } catch (e) {
    result.value = '请求失败，请先启动后端服务。'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page {
  max-width: 760px;
  margin: 48px auto;
  font-family: "Microsoft YaHei", sans-serif;
  line-height: 1.7;
}
button {
  margin-top: 12px;
  padding: 8px 14px;
  border: 1px solid #0f766e;
  background: #0f766e;
  color: #fff;
  cursor: pointer;
}
pre {
  margin-top: 14px;
  background: #f5f5f5;
  padding: 12px;
  border-radius: 6px;
  overflow: auto;
}
</style>
