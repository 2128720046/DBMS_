<template>
  <div>
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>备份与恢复</span>
          <div>
            <el-select v-model="selectedDatabase" placeholder="选择数据库" style="width: 180px; margin-right: 12px;">
              <el-option v-for="item in databaseOptions" :key="item.name || item" :label="item.name || item" :value="item.name || item" />
            </el-select>
            <el-button type="primary" @click="handleBackup">立即备份</el-button>
            <el-button type="success" @click="fetchBackups">刷新列表</el-button>
          </div>
        </div>
      </template>

      <el-table :data="tableData" style="width: 100%">
        <el-table-column prop="name" label="备份文件名" />
        <el-table-column prop="size" label="大小" width="120" />
        <el-table-column prop="createTime" label="备份时间" width="180" />
        <el-table-column prop="desc" label="描述" />
        <el-table-column fixed="right" label="操作" width="200">
          <template #default="scope">
            <el-popconfirm
              title="确定恢复此备份吗？当前数据将被覆盖且无法撤销！"
              confirm-button-text="确认恢复"
              cancel-button-text="点错了"
              type="error"
              width="250"
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

    <el-dialog v-model="dialogVisible" title="创建新备份" width="30%">
      <el-form :model="form" label-width="100px">
        <el-form-item label="备份数据库">
          <el-input v-model="selectedDatabase" disabled />
        </el-form-item>
        <el-form-item label="备份描述">
           <el-input v-model="form.desc" type="textarea" placeholder="可选备注..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitBackup">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createBackup as apiCreateBackup, deleteBackup as apiDeleteBackup, listBackups as apiListBackups, listDatabases, restoreBackup as apiRestoreBackup } from '../api/dbms'

const tableData = ref([])
const databaseOptions = ref([])
const selectedDatabase = ref('')

const dialogVisible = ref(false)
const form = ref({
  desc: ''
})

const loadDatabases = async () => {
  try {
    const response = await listDatabases()
    databaseOptions.value = response?.data || []
    if (!selectedDatabase.value && databaseOptions.value.length > 0) {
      selectedDatabase.value = databaseOptions.value[0].name || databaseOptions.value[0]
    }
  } catch (error) {
    ElMessage.error(error.message || '加载数据库列表失败')
  }
}

onMounted(async () => {
  await loadDatabases()
  await fetchBackups()
})

const fetchBackups = async () => {
  if (!selectedDatabase.value) {
    return ElMessage.warning('请先选择数据库')
  }
  try {
    const response = await apiListBackups(selectedDatabase.value)
    tableData.value = (response?.data || []).map((item) => ({
      name: item.name,
      size: item.size >= 0 ? `${Math.max(1, Math.round(item.size / 1024))} KB` : '-',
      createTime: item.updatedAt || '',
      desc: 'H2 脚本备份'
    }))
    ElMessage.success('备份列表已刷新')
  } catch (error) {
    ElMessage.error(error.message || '刷新备份列表失败')
  }
}

const handleBackup = () => {
  if (!selectedDatabase.value) {
    return ElMessage.warning('请先选择数据库')
  }
  form.value = { desc: '' }
  dialogVisible.value = true
}

const submitBackup = async () => {
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
  }
}

const handleRestore = async (row) => {
  try {
    await apiRestoreBackup(selectedDatabase.value, row.name)
    ElMessage.success(`已从备份 ${row.name} 恢复数据`)
  } catch (error) {
    ElMessage.error(error.message || '恢复失败')
  }
}

const handleDelete = (row) => {
    ElMessageBox.confirm(`确定删除备份文件 ${row.name} 吗？`, '警告', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning',
    }).then(() => {
      apiDeleteBackup(selectedDatabase.value, row.name)
      .then(() => {
        tableData.value = tableData.value.filter(item => item.name !== row.name)
        ElMessage({ type: 'success', message: '删除成功' })
      })
      .catch((error) => {
        ElMessage.error(error.message || '删除失败')
      })
    })
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
