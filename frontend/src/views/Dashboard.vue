<template>
  <div>
    <el-row :gutter="20">
      <el-col :span="6" v-for="item in summary" :key="item.title">
        <el-card class="box-card">
          <template #header>
            <div class="card-header">
              <span>{{ item.title }}</span>
            </div>
          </template>
          <div class="card-content">
            {{ item.value }}
          </div>
        </el-card>
      </el-col>
    </el-row>

    <div class="mt-20">
      <h3>快捷入口</h3>
      <el-row :gutter="20">
        <el-col :span="4">
          <el-button type="primary" size="large" @click="$router.push('/database')">数据库管理</el-button>
        </el-col>
        <el-col :span="4">
          <el-button type="success" size="large" @click="$router.push('/table')">表管理</el-button>
        </el-col>
        <el-col :span="4">
          <el-button type="warning" size="large" @click="$router.push('/record')">记录查询</el-button>
        </el-col>
        <el-col :span="4">
          <el-button type="danger" size="large" @click="$router.push('/sql')">SQL 控制台</el-button>
        </el-col>
         <el-col :span="4">
          <el-button type="info" size="large" @click="checkHealth">后端健康检查</el-button>
        </el-col>
      </el-row>
    </div>

    <div class="mt-20">
      <el-card>
        <template #header>
          <div class="card-header"><span>最近 SQL 执行状态</span></div>
        </template>
        <el-table :data="recentSql" stripe style="width: 100%">
          <el-table-column prop="time" label="时间" width="180" />
          <el-table-column prop="sql" label="SQL 摘要" />
          <el-table-column prop="status" label="状态" width="100">
             <template #default="scope">
              <el-tag :type="scope.row.status === 'success' ? 'success' : 'danger'">
                {{ scope.row.status === 'success' ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="cost" label="耗时 (ms)" width="100" />
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listDatabases } from '../api/dbms'

const router = useRouter()

const summary = ref([
  { title: '当前系统用户', value: 'admin' },
  { title: '运行状态', value: '正常运行' },
  { title: '选中数据库', value: '未选中' },
  { title: '累计执行 SQL', value: '1,234 次' }
])

const recentSql = ref([
  { time: '2026-04-22 10:00', sql: 'SELECT * FROM users', status: 'success', cost: 12 },
  { time: '2026-04-22 10:05', sql: 'UPDATE sys_config SET value = 1', status: 'success', cost: 5 },
  { time: '2026-04-22 10:10', sql: 'DROP TABLE not_exist_table', status: 'error', cost: 2 }
])

const checkHealth = async () => {
  const res = await listDatabases()
  const count = Array.isArray(res.data) ? res.data.length : 0
  ElMessage.success(`SQL 服务正常，数据库数量: ${count}`)
}
</script>

<style scoped>
.box-card {
  margin-bottom: 20px;
}
.card-content {
  font-size: 24px;
  font-weight: bold;
  text-align: center;
}
.mt-20 {
  margin-top: 20px;
}
</style>
