<template>
  <div class="backup-page">
    <el-card class="hero-card" shadow="never">
      <div class="hero-grid">
        <div>
        
          <h2 class="page-title">备份恢复中心</h2>
          <p class="page-desc">统一管理数据库备份、恢复与清理</p>
        </div>

        <div class="hero-actions">
          <el-select
            v-model="selectedDatabase"
            placeholder="选择数据库"
            class="db-select"
            filterable
            @change="fetchBackups"
          >
            <el-option
              v-for="item in databaseOptions"
              :key="item.name || item"
              :label="item.name || item"
              :value="item.name || item"
            />
          </el-select>
          <el-button type="primary" :loading="creatingBackup" @click="handleBackup">立即备份</el-button>
          <el-button type="success" :loading="loadingBackups" @click="refreshAll">刷新列表</el-button>
        </div>
      </div>

      <div class="hero-stats">
        <div class="stat-item">
          <span class="stat-label">当前数据库</span>
          <span class="stat-value">{{ selectedDatabase || '未选择' }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">备份数量</span>
          <span class="stat-value">{{ tableData.length }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">连接状态</span>
          <span class="stat-value success">{{ connectionState }}</span>
        </div>
      </div>
    </el-card>

    <el-card class="content-card" shadow="never">
      <template #header>
        <div class="section-header">
          <span>备份列表</span>
          <span class="section-tip">恢复前请确认目标库正确</span>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loadingBackups" empty-text="暂无备份记录" style="width: 100%">
        <el-table-column prop="name" label="备份文件名" min-width="240" show-overflow-tooltip />
        <el-table-column prop="size" label="大小" width="120" />
        <el-table-column prop="createTime" label="备份时间" width="180" />
        <el-table-column prop="desc" label="描述" min-width="180" show-overflow-tooltip />
        <el-table-column fixed="right" label="操作" width="220">
          <template #default="scope">
            <el-popconfirm
              title="确定恢复此备份吗？当前数据将被覆盖且无法撤销！"
              confirm-button-text="确认恢复"
              cancel-button-text="取消"
              type="warning"
              width="260"
              @confirm="handleRestore(scope.row)"
            >
              <template #reference>
                <el-button link type="warning" size="small">恢复</el-button>
              </template>
            </el-popconfirm>

            <el-button link type="danger" size="small" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="创建新备份" width="420px" align-center>
      <el-form :model="form" label-position="top">
        <el-form-item label="备份数据库">
          <el-input :model-value="selectedDatabase" disabled />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.desc" type="textarea" :rows="4" placeholder="可选备注，例如：月末归档、升级前快照" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="creatingBackup" @click="submitBackup">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createBackup as apiCreateBackup,
  deleteBackup as apiDeleteBackup,
  listBackups as apiListBackups,
  listDatabases,
  restoreBackup as apiRestoreBackup
} from '../api/dbms'

const route = useRoute()
const tableData = ref([])
const databaseOptions = ref([])
const selectedDatabase = ref('')
const loadingDatabases = ref(false)
const loadingBackups = ref(false)
const creatingBackup = ref(false)
const dialogVisible = ref(false)

const form = ref({ desc: '' })

const connectionState = computed(() => {
  if (loadingDatabases.value || loadingBackups.value) return '连接中'
  if (!selectedDatabase.value) return '未连接'
  return '已连接'
})

const normalizeDatabaseName = (item) => item?.name || item || ''

const syncRouteDatabase = () => {
  const routeDb = route.query?.db ? String(route.query.db) : ''
  if (routeDb) {
    selectedDatabase.value = routeDb
  }
}

const loadDatabases = async () => {
  loadingDatabases.value = true
  try {
    const response = await listDatabases()
    databaseOptions.value = response?.data || []
    if (!selectedDatabase.value && databaseOptions.value.length > 0) {
      selectedDatabase.value = normalizeDatabaseName(databaseOptions.value[0])
    }
    syncRouteDatabase()
  } catch (error) {
    ElMessage.error(error.message || '加载数据库列表失败')
  } finally {
    loadingDatabases.value = false
  }
}

const fetchBackups = async () => {
  if (!selectedDatabase.value) {
    tableData.value = []
    return ElMessage.warning('请先选择数据库')
  }

  loadingBackups.value = true
  try {
    const response = await apiListBackups(selectedDatabase.value)
    tableData.value = (response?.data || []).map((item) => ({
      name: item.name,
      size: item.size >= 0 ? `${Math.max(1, Math.round(item.size / 1024))} KB` : '-',
      createTime: item.updatedAt || new Date().toLocaleString(),
      desc: item.desc || 'H2 脚本备份'
    }))
  } catch (error) {
    ElMessage.error(error.message || '刷新备份列表失败')
  } finally {
    loadingBackups.value = false
  }
}

const refreshAll = async () => {
  await loadDatabases()
  await fetchBackups()
}

const handleBackup = () => {
  if (!selectedDatabase.value) {
    return ElMessage.warning('请先选择数据库')
  }
  form.value = { desc: '' }
  dialogVisible.value = true
}

const submitBackup = async () => {
  if (!selectedDatabase.value) {
    return ElMessage.warning('请先选择数据库')
  }

  creatingBackup.value = true
  try {
    const response = await apiCreateBackup(selectedDatabase.value)
    const item = response?.data || {}
    tableData.value.unshift({
      name: item.name,
      size: '1 KB',
      createTime: new Date().toLocaleString(),
      desc: form.value.desc || '手动备份'
    })
    dialogVisible.value = false
    ElMessage.success('备份已生成')
  } catch (error) {
    ElMessage.error(error.message || '创建备份失败')
  } finally {
    creatingBackup.value = false
  }
}

const handleRestore = async (row) => {
  if (!selectedDatabase.value) {
    return ElMessage.warning('请先选择数据库')
  }

  try {
    await apiRestoreBackup(selectedDatabase.value, row.name)
    await fetchBackups()
    ElMessage.success(`已从备份 ${row.name} 恢复数据`)
  } catch (error) {
    ElMessage.error(error.message || '恢复失败')
  }
}

const handleDelete = (row) => {
  if (!selectedDatabase.value) {
    return ElMessage.warning('请先选择数据库')
  }

  ElMessageBox.confirm(`确定删除备份文件 ${row.name} 吗？`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await apiDeleteBackup(selectedDatabase.value, row.name)
      tableData.value = tableData.value.filter((item) => item.name !== row.name)
      ElMessage.success('删除成功')
    } catch (error) {
      ElMessage.error(error.message || '删除失败')
    }
  })
}

watch(
  () => route.query?.db,
  async (dbName) => {
    if (dbName && String(dbName) !== selectedDatabase.value) {
      selectedDatabase.value = String(dbName)
      await fetchBackups()
    }
  }
)

watch(selectedDatabase, async (value) => {
  if (value) {
    await fetchBackups()
  }
})

onMounted(async () => {
  await loadDatabases()
  await fetchBackups()
})
</script>

<style scoped>
.backup-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 0;
}

.hero-card,
.content-card {
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

.page-kicker {
  color: #6b7280;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  margin-bottom: 8px;
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
  width: 220px;
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

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

:deep(.el-card__body) {
  padding: 20px;
}

@media (max-width: 960px) {
  .hero-stats {
    grid-template-columns: 1fr;
  }

  .hero-actions {
    width: 100%;
  }

  .db-select {
    width: 100%;
  }
}
</style>
