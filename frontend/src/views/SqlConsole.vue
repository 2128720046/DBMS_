<template>
  <div class="sql-console">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
           <div class="context-info">
             <el-tag type="info">当前数据库: {{ currentDb || '未选择' }}</el-tag>
           </div>
          <div>
            <el-button type="success" @click="executeSql">执行 SQL</el-button>
            <el-button type="primary" plain @click="formatSql">格式化</el-button>
            <el-button type="warning" plain @click="clearEditor">清空</el-button>
            <el-button type="info" plain @click="saveScript">保存脚本</el-button>
            <el-button type="danger" plain @click="stopExecution">终止执行</el-button>
          </div>
        </div>
      </template>

      <!-- SQL Editor Area (Simulated with textarea for now) -->
      <div class="editor-area">
        <el-input
          v-model="sqlCode"
          type="textarea"
          :rows="12"
          placeholder="请输入 SQL 语句（支持多条，以分号 ; 分隔）"
          style="font-family: monospace;"
        />
      </div>
    </el-card>

    <div class="mt-20">
      <el-tabs v-model="activeTab" type="border-card">
        <el-tab-pane label="执行结果" name="result">
           <div v-if="executionResult.type === 'message'" class="result-message">
             <el-alert :title="executionResult.data" :type="executionResult.status" show-icon />
           </div>
           <div v-else-if="executionResult.type === 'table'" class="result-table">
               <el-table :data="executionResult.data" border style="width: 100%" height="300">
                    <el-table-column v-for="col in executionResult.columns" :key="col.prop" :prop="col.prop" :label="col.label" />
               </el-table>
           </div>
           <div v-else class="empty-state">
             暂无执行结果，请输入 SQL 并点击执行。
           </div>
        </el-tab-pane>
        <el-tab-pane label="执行信息" name="info">
           <el-descriptions v-if="executionInfo.startTime" :column="2" border>
            <el-descriptions-item label="开始时间">{{ executionInfo.startTime }}</el-descriptions-item>
            <el-descriptions-item label="结束时间">{{ executionInfo.endTime }}</el-descriptions-item>
            <el-descriptions-item label="耗时">{{ executionInfo.cost }} ms</el-descriptions-item>
            <el-descriptions-item label="影响行数">{{ executionInfo.affectedRows }}</el-descriptions-item>
            <el-descriptions-item label="状态">
                <el-tag :type="executionInfo.status === 'success' ? 'success' : 'danger'">
                    {{ executionInfo.status === 'success' ? '成功' : '失败' }}
                </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="错误详情" v-if="executionInfo.error">
              <span style="color: red">{{ executionInfo.error }}</span>
            </el-descriptions-item>
          </el-descriptions>
          <div v-else class="empty-state">暂无执行信息。</div>
        </el-tab-pane>
        <el-tab-pane label="执行历史" name="history">
            <el-table :data="history" style="width: 100%">
               <el-table-column prop="time" label="时间" width="180" />
               <el-table-column prop="sql" label="SQL" />
               <el-table-column prop="cost" label="耗时(ms)" width="100" />
               <el-table-column label="操作" width="100">
                 <template #default="scope">
                    <el-button link type="primary" size="small" @click="copyToEditor(scope.row.sql)">载入</el-button>
                 </template>
               </el-table-column>
            </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'

const route = useRoute()
const currentDb = ref('')
const sqlCode = ref('')
const activeTab = ref('result')

// Mock state
const executionResult = ref({ type: null, data: null, status: 'info', columns: [] })
const executionInfo = ref({ startTime: null, endTime: null, cost: 0, affectedRows: 0, status: null, error: null })
const history = ref([
    { time: '2026-04-22 10:00:00', sql: 'SELECT * FROM users LIMIT 10;', cost: 15 },
    { time: '2026-04-22 10:05:00', sql: 'UPDATE config SET val=1 WHERE key="a";', cost: 5 }
])

onMounted(() => {
   if(route.query.db) {
      currentDb.value = route.query.db
      sqlCode.value = `USE ${route.query.db};\n`
   }
})

const executeSql = () => {
    if(!sqlCode.value.trim()) return ElMessage.warning('请输入 SQL 语句')
    
    // Simulate execution based on statement type
    activeTab.value = 'result'
    const now = new Date()
    const startTimeStr = now.toLocaleTimeString()
    
    executionInfo.value = {
        startTime: startTimeStr,
        endTime: startTimeStr, // Mock fast
        cost: Math.floor(Math.random() * 50) + 10,
        status: 'success',
        affectedRows: 0,
        error: null
    }

    if (sqlCode.value.toLowerCase().includes('select')) {
        executionResult.value = {
            type: 'table',
            columns: [
                { prop: 'id', label: 'ID' },
                { prop: 'name', label: 'Name' },
                { prop: 'age', label: 'Age' }
            ],
            data: [
                { id: 1, name: 'Alice', age: 25 },
                { id: 2, name: 'Bob', age: 30 }
            ]
        }
        executionInfo.value.affectedRows = 2
    } else {
        executionResult.value = {
            type: 'message',
            status: 'success',
            data: 'SQL 执行成功'
        }
        executionInfo.value.affectedRows = 1
    }

    // Add to history
    history.value.unshift({
        time: startTimeStr,
        sql: sqlCode.value.substring(0, 100) + (sqlCode.value.length > 100 ? '...' : ''),
        cost: executionInfo.value.cost
    })
}

const formatSql = () => {
    ElMessage.info('格式化功能待接入 SQL Formatter 库')
}

const clearEditor = () => {
    sqlCode.value = ''
}

const saveScript = () => {
   ElMessage.success('脚本已保存至草稿箱')
}

const stopExecution = () => {
   ElMessage.warning('发送终止信号至后端')
}

const copyToEditor = (sql) => {
    sqlCode.value = sql
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.editor-area {
  margin-top: 10px;
}
.mt-20 {
  margin-top: 20px;
}
.empty-state {
    padding: 30px;
    text-align: center;
    color: #909399;
}
</style>
