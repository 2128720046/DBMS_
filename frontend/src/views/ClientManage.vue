<template>
  <div class="client-page">
    <el-card class="hero-card" shadow="never">
      <div class="hero-grid">
        <div>
          <h2 class="page-title">客户端会话管理</h2>
          <p class="page-desc">查看当前在线客户端连接，并管理会话。仅管理员可访问此页面</p>
        </div>
        <div class="hero-actions">
          <el-button type="primary" :loading="loadingClients" @click="fetchClients" icon="Refresh">刷新列表</el-button>
        </div>
      </div>
      <div class="hero-stats">
        <div class="stat-item">
          <span class="stat-label">在线客户端</span>
          <span class="stat-value">{{ clients.length }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">当前用户</span>
          <span class="stat-value">{{ currentUser }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">连接状态</span>
          <span class="stat-value success">已连接</span>
        </div>
      </div>
    </el-card>

    <el-card class="content-card" shadow="never">
      <template #header>
        <div class="section-header">
          <span>在线客户端列表</span>
          <span class="section-tip">实时查看已连接的客户端会话</span>
        </div>
      </template>
      <el-table :data="clients" v-loading="loadingClients" border empty-text="暂无在线客户端" style="width: 100%">
        <el-table-column prop="CLIENT_ID" label="客户端 ID" min-width="200" show-overflow-tooltip />
        <el-table-column prop="USER" label="用户名" width="140" />
        <el-table-column prop="IP_ADDRESS" label="IP 地址" width="150" />
        <el-table-column prop="PORT" label="端口" width="100" />
        <el-table-column prop="CONNECTED_AT" label="连接时间" width="180" show-overflow-tooltip />
        <el-table-column prop="CURRENT_DATABASE" label="当前数据库" width="150" />
        <el-table-column fixed="right" label="操作" width="120" align="center">
          <template #default="scope">
            <el-popconfirm
              title="确定断开此客户端连接吗？"
              confirm-button-text="确定"
              cancel-button-text="取消"
              @confirm="handleDisconnect(scope.row)"
            >
              <template #reference>
                <el-button link type="danger" size="small" :disabled="scope.row.USER === currentUser">断开</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { showClients, disconnectClient } from '../api/dbms'

const clients = ref([])
const loadingClients = ref(false)
const currentUser = ref('')

const fetchClients = async () => {
  loadingClients.value = true
  try {
    const res = await showClients()
    clients.value = res?.data || []
  } catch (error) {
    ElMessage.error(error.message || '获取客户端列表失败')
  } finally {
    loadingClients.value = false
  }
}

const handleDisconnect = async (row) => {
  try {
    await disconnectClient(row.CLIENT_ID)
    clients.value = clients.value.filter(c => c.CLIENT_ID !== row.CLIENT_ID)
    ElMessage.success(`已断开客户端 ${row.CLIENT_ID}`)
  } catch (error) {
    ElMessage.error(error.message || '断开连接失败')
  }
}

/** 窗口聚焦或登录状态变化时自动刷新 */
const autoRefresh = () => { fetchClients() }

onMounted(() => {
  try {
    const userStr = sessionStorage.getItem('dbms-user')
    if (userStr) {
      const userData = JSON.parse(userStr)
      currentUser.value = userData.username || '未知'
    }
  } catch (e) {}
  fetchClients()
  // 窗口聚焦时刷新（多标签页切换场景）
  window.addEventListener('focus', autoRefresh)
  window.addEventListener('dbms-login-changed', autoRefresh)
})

onBeforeUnmount(() => {
  window.removeEventListener('focus', autoRefresh)
  window.removeEventListener('dbms-login-changed', autoRefresh)
})
</script>

<style scoped>
.client-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 0;
}
.hero-card, .content-card {
  border-radius: 14px;
  border: 1px solid #e8eaef;
  background: linear-gradient(180deg, #ffffff 0%, #fbfcfe 100%);
}
.hero-grid {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: flex-end;
  flex-wrap: wrap;
}
.page-title {
  margin: 0;
  font-size: 28px;
  color: #111827;
}
.page-desc {
  margin: 10px 0 0;
  color: #6b7280;
  line-height: 1.7;
  max-width: 640px;
}
.hero-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.hero-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 20px;
}
.stat-item {
  padding: 14px 16px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #eef2f7;
}
.stat-label {
  display: block;
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 6px;
}
.stat-value {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}
.stat-value.success {
  color: #16a34a;
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.section-tip {
  color: #9ca3af;
  font-size: 13px;
}
:deep(.el-card__body) {
  padding: 20px;
}
@media (max-width: 960px) {
  .hero-stats { grid-template-columns: 1fr; }
}
</style>
