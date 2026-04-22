<template>
  <div>
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>数据库管理</span>
          <el-button class="button" type="primary" @click="dialogVisible = true">新建数据库</el-button>
          <el-button class="button" type="success" @click="fetchDatabases">刷新列表</el-button>
        </div>
      </template>

      <el-table :data="tableData" style="width: 100%">
        <el-table-column prop="name" label="数据库名称" width="180" />
        <el-table-column prop="size" label="大小 (MB)" width="120" />
        <el-table-column prop="createTime" label="创建时间" />
        <el-table-column fixed="right" label="操作" width="200">
          <template #default="scope">
            <el-button link type="primary" size="small" @click="openConsole(scope.row)">SQL 控制台</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新建数据库" width="30%">
      <el-form :model="form" label-width="100px">
        <el-form-item label="数据库名称">
          <el-input v-model="form.name" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleCreate">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()

const tableData = ref([
  { name: 'db_test_1', size: '150', createTime: '2026-04-01' },
  { name: 'db_online', size: '1024', createTime: '2026-04-10' }
])

const dialogVisible = ref(false)
const form = ref({
  name: ''
})

const fetchDatabases = () => {
    ElMessage.success('列表已刷新')
}

const handleCreate = () => {
    if(!form.value.name) return ElMessage.warning('请输入名称')
    tableData.value.push({
        name: form.value.name,
        size: '0',
        createTime: new Date().toISOString().split('T')[0]
    })
    dialogVisible.value = false
    ElMessage.success('创建成功')
    form.value = { name: '' }
}

const handleDelete = (row) => {
    ElMessageBox.confirm(
        `确定永久删除数据库 ${row.name} 吗？`,
        '警告',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning',
        }
      )
        .then(() => {
          tableData.value = tableData.value.filter(item => item.name !== row.name)
          ElMessage({ type: 'success', message: '删除成功' })
        })
        .catch(() => {
          ElMessage({ type: 'info', message: '已取消删除' })
        })
}

const openConsole = (row) => {
    // Navigate and set context context via store/query params
    router.push({ path: '/sql', query: { db: row.name } })
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
