<template>
  <div>
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>备份与恢复</span>
          <div>
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
        <el-form-item label="备份内容">
          <el-select v-model="form.target" placeholder="选择数据库">
            <el-option label="所有数据库" value="all" />
            <el-option label="db_test_1" value="db_test_1" />
            <el-option label="db_online" value="db_online" />
          </el-select>
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
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const tableData = ref([
  { name: 'backup_all_20260421.sql', size: '150 MB', createTime: '2026-04-21 23:00:00', desc: '每日自动备份' },
  { name: 'backup_db_online_v2.sql', size: '10 MB', createTime: '2026-04-22 09:30:15', desc: '临上线前手动备份' }
])

const dialogVisible = ref(false)
const form = ref({
  target: 'all',
  desc: ''
})

const fetchBackups = () => {
    ElMessage.success('备份列表已刷新')
}

const handleBackup = () => {
    form.value = { target: 'all', desc: '' }
    dialogVisible.value = false
}

const submitBackup = () => {
    tableData.value.unshift({
        name: `backup_${form.value.target}_${Date.now()}.sql`,
        size: '10 KB',
        createTime: new Date().toLocaleString(),
        desc: form.value.desc
    })
    dialogVisible.value = false
    ElMessage.success('备份任务已提交后台执行')
}

const handleRestore = (row) => {
    // Need explicit confirmation again for high risk
    ElMessage.success(`正在从备份 ${row.name} 恢复数据... 预计耗时 10 分钟`)
}

const handleDelete = (row) => {
    ElMessageBox.confirm(`确定删除备份文件 ${row.name} 吗？`, '警告', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning',
    }).then(() => {
          tableData.value = tableData.value.filter(item => item.name !== row.name)
          ElMessage({ type: 'success', message: '删除成功' })
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
