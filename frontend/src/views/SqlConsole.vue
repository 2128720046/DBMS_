<template>
  <div class="sql-ide-container">
    <div class="editor-pane">
      <div class="pane-header">
        <div class="header-left">
          <el-tag size="small" effect="plain" type="info">当前库: {{ currentDb || '未选择' }}</el-tag>
        </div>
        <div class="header-tools">
          <el-button size="small" type="success" icon="VideoPlay" @click="executeSql">执行 (F9)</el-button>
          <el-button size="small" link icon="MagicStick" @click="formatSql">格式化</el-button>
          <el-button size="small" link icon="Delete" @click="clearEditor">清空</el-button>
        </div>
      </div>
      <el-input
        v-model="sqlCode"
        type="textarea"
        resize="none"
        class="monaco-like-editor"
        placeholder="-- 在此输入 SQL 语句，多条语句请以分号 ; 隔开"
      />
    </div>

    <div class="resize-handle"></div>

    <div class="results-pane">
      <el-tabs v-model="activeTab" type="border-card" class="result-tabs">
        <el-tab-pane label="查询结果" name="result">
           <div v-if="executionResult.type === 'message'" class="msg-box">
             <el-alert :title="executionResult.data" :type="executionResult.status" show-icon :closable="false" />
           </div>
           <el-table 
             v-else-if="executionResult.type === 'table'"
             :data="executionResult.data" 
             border 
             stripe 
             height="100%"
             size="small"
           >
              <el-table-column v-for="col in executionResult.columns" :key="col.prop" :prop="col.prop" :label="col.label" show-overflow-tooltip />
           </el-table>
           <el-empty v-else description="暂无执行结果" :image-size="60" />
        </el-tab-pane>

        <el-tab-pane label="执行日志" name="log">
          <div class="log-console">
             <div v-for="(log, i) in history" :key="i" :class="['log-item', log.status]">
                <span class="log-time">[{{ log.time }}]</span>
                <span class="log-sql">{{ log.sql }}</span>
                <span class="log-cost">{{ log.cost }}ms</span>
             </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { executeSql as executeSqlApi } from '../api/dbms'
import { VideoPlay, MagicStick, Delete } from '@element-plus/icons-vue'

const route = useRoute()
const currentDb = ref('')
const sqlCode = ref('')
const activeTab = ref('result')

const executionResult = ref({ type: null, data: null, status: 'info', columns: [] })
const history = ref([])

onMounted(() => {
   if(route.query.db) {
      currentDb.value = route.query.db
      if (route.query.table) {
        sqlCode.value = `SELECT * FROM ${route.query.db}.${route.query.table} LIMIT 50;\n`
      }
   }
})

const executeSql = async () => {
  if (!sqlCode.value.trim()) return ElMessage.warning('请输入 SQL 语句')

  activeTab.value = 'result'
  const start = Date.now()
  const timeStr = new Date(start).toLocaleTimeString()

  try {
    const res = await executeSqlApi({
      databaseName: currentDb.value,
      sql: sqlCode.value
    })
    const cost = Date.now() - start
    const payload = res.data || {}

    executionResult.value = {
      type: payload.type || 'message',
      data: payload.data || 'SQL 执行成功',
      status: payload.status || 'success',
      columns: payload.columns || []
    }

    history.value.unshift({ time: timeStr, sql: sqlCode.value.split('\n')[0], cost, status: 'success' })
  } catch (error) {
    const cost = Date.now() - start
    executionResult.value = { type: 'message', status: 'error', data: error.message || '执行失败', columns: [] }
    history.value.unshift({ time: timeStr, sql: sqlCode.value.split('\n')[0], cost, status: 'error' })
    activeTab.value = 'log'
  }
}

const formatSql = () => ElMessage.info('格式化功能待接入')
const clearEditor = () => { sqlCode.value = ''; executionResult.value = { type: null } }
</script>

<style scoped>
.sql-ide-container {
  height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
  background: #FFF;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.editor-pane {
  flex: 5;
  display: flex;
  flex-direction: column;
}

.pane-header {
  height: 40px;
  background: #F9FAFB;
  border-bottom: 1px solid #EBECEF;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
}

.monaco-like-editor :deep(.el-textarea__inner) {
  flex: 1;
  height: 100%;
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 14px;
  border: none;
  background: #FFFFFF;
  padding: 16px;
  color: #1F2328;
  line-height: 1.6;
  box-shadow: none;
}
.monaco-like-editor :deep(.el-textarea__inner):focus {
  outline: none;
  box-shadow: none;
}

.resize-handle {
  height: 6px;
  background: #F3F4F6;
  cursor: row-resize;
  border-top: 1px solid #EBECEF;
  border-bottom: 1px solid #EBECEF;
}

.results-pane {
  flex: 4;
  overflow: hidden;
  background: #FFFFFF;
}

.result-tabs {
  border: none;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.result-tabs :deep(.el-tabs__content) {
  flex: 1;
  padding: 0;
  overflow: hidden;
}

.result-tabs :deep(.el-tab-pane) {
  height: 100%;
}

.msg-box {
  padding: 16px;
}

.log-console {
  padding: 12px;
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 12px;
  height: 100%;
  overflow-y: auto;
  background: #1E1E1E;
  color: #D4D4D4;
}

.log-item {
  margin-bottom: 6px;
  display: flex;
  gap: 12px;
}
.log-item.error { color: #F85149; }
.log-item.success { color: #3FB950; }
.log-time { color: #8B949E; }
.log-sql { flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.log-cost { color: #8B949E; }
</style>