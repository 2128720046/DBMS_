<template>
  <div class="transaction-page">
    <el-card class="hero-card" shadow="never">
      <div class="hero-grid">
        <div>
          <h2 class="page-title">事务监控</h2>
          <p class="page-desc">管理数据库事务的生命周期：开启、提交与回滚</p>
        </div>
        <div class="hero-actions">
          <el-select v-model="selectedDatabase" placeholder="选择数据库" class="db-select" filterable @change="resetState">
            <el-option v-for="item in databaseOptions" :key="item.name || item" :label="item.name || item" :value="item.name || item" />
          </el-select>
          <el-button type="success" :disabled="!selectedDatabase || txActive" @click="handleBegin" icon="VideoPlay">开启事务</el-button>
          <el-button type="primary" :disabled="!txActive" @click="handleCommit" icon="Select">提交</el-button>
          <el-button type="danger" :disabled="!txActive" @click="handleRollback" icon="RefreshLeft">回滚</el-button>
        </div>
      </div>

      <div class="hero-stats">
        <div class="stat-item">
          <span class="stat-label">当前数据库</span>
          <span class="stat-value">{{ selectedDatabase || '未选择' }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">事务状态</span>
          <span class="stat-value">
            <el-tag :type="txActive ? 'warning' : 'info'" size="large">
              {{ txActive ? '活跃中' : '未开启' }}
            </el-tag>
          </span>
        </div>
        <div class="stat-item">
          <span class="stat-label">操作记录</span>
          <span class="stat-value">{{ historyLog.length }}</span>
        </div>
      </div>
    </el-card>

    <!-- 操作按钮区 -->
    <el-card class="content-card" shadow="never">
      <template #header>
        <div class="section-header">
          <span>事务操作历史</span>
          <el-button size="small" link @click="historyLog = []">清空记录</el-button>
        </div>
      </template>
      <el-timeline v-if="historyLog.length > 0">
        <el-timeline-item
          v-for="(item, index) in historyLog"
          :key="index"
          :timestamp="item.time"
          :type="item.type"
        >
          {{ item.message }}
        </el-timeline-item>
      </el-timeline>
      <div v-else class="empty-state">
        <el-icon :size="48" color="#d0d5dd"><InfoFilled /></el-icon>
        <p>暂无事务操作记录</p>
        <p class="empty-hint">选择一个数据库，然后点击「开启事务」开始</p>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { InfoFilled, VideoPlay, Select, RefreshLeft } from '@element-plus/icons-vue'
import { listDatabases, beginTransaction, commitTransaction, rollbackTransaction } from '../api/dbms'

const route = useRoute()
const databaseOptions = ref([])
const selectedDatabase = ref('')
const txActive = ref(false)
const historyLog = ref([])

const resetState = () => {
  txActive.value = false
}

const loadDatabases = async () => {
  try {
    const res = await listDatabases()
    databaseOptions.value = res?.data || []
    const routeDb = route.query?.db
    if (routeDb && databaseOptions.value.find(d => (d.name || d) === routeDb)) {
      selectedDatabase.value = routeDb
    } else if (!selectedDatabase.value && databaseOptions.value.length > 0) {
      selectedDatabase.value = databaseOptions.value[0].name || databaseOptions.value[0]
    }
  } catch (error) {
    ElMessage.error(error.message || '加载数据库列表失败')
  }
}

const addHistory = (type, message) => {
  const now = new Date()
  const time = now.toLocaleTimeString()
  historyLog.value.unshift({ type, time, message })
}

const handleBegin = async () => {
  if (!selectedDatabase.value) return ElMessage.warning('请先选择数据库')
  try {
    await beginTransaction(selectedDatabase.value)
    txActive.value = true
    addHistory('warning', `事务已开启 — 数据库: ${selectedDatabase.value}`)
    ElMessage.success('事务已开启')
  } catch (error) {
    addHistory('danger', `开启事务失败: ${error.message}`)
    ElMessage.error(error.message || '开启事务失败')
  }
}

const handleCommit = async () => {
  if (!selectedDatabase.value) return ElMessage.warning('请先选择数据库')
  ElMessageBox.confirm(
    '确定提交当前事务吗？提交后所有变更将永久生效。',
    '确认提交',
    { confirmButtonText: '确定提交', cancelButtonText: '取消', type: 'info' }
  ).then(async () => {
    try {
      await commitTransaction(selectedDatabase.value)
      txActive.value = false
      addHistory('success', `事务已提交 — 数据库: ${selectedDatabase.value}`)
      ElMessage.success('事务已提交')
    } catch (error) {
      addHistory('danger', `提交事务失败: ${error.message}`)
      ElMessage.error(error.message || '提交事务失败')
    }
  }).catch(() => {})
}

const handleRollback = async () => {
  if (!selectedDatabase.value) return ElMessage.warning('请先选择数据库')
  ElMessageBox.confirm(
    '确定回滚当前事务吗？所有未提交的变更将丢失。',
    '确认回滚',
    { confirmButtonText: '确定回滚', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    try {
      await rollbackTransaction(selectedDatabase.value)
      txActive.value = false
      addHistory('danger', `事务已回滚 — 数据库: ${selectedDatabase.value}`)
      ElMessage.success('事务已回滚')
    } catch (error) {
      addHistory('danger', `回滚事务失败: ${error.message}`)
      ElMessage.error(error.message || '回滚事务失败')
    }
  }).catch(() => {})
}

onMounted(async () => {
  await loadDatabases()
})
</script>

<style scoped>
.transaction-page {
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
.db-select {
  width: 200px;
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
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.empty-state {
  padding: 40px;
  text-align: center;
  color: #909399;
}
.empty-state p {
  margin: 12px 0 0;
}
.empty-hint {
  font-size: 13px;
  color: #b0b5bd;
}
:deep(.el-card__body) {
  padding: 20px;
}
@media (max-width: 960px) {
  .hero-stats { grid-template-columns: 1fr; }
  .hero-actions { width: 100%; }
  .db-select { width: 100%; }
}
</style>
