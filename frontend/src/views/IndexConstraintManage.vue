<template>
  <div class="col-manage">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
           <div class="context-info">
             <el-tag type="info">当前数据库: {{ currentDb || '未选择' }}</el-tag>
             <el-tag type="info" style="margin-left: 10px;">当前表: {{ currentTable || '未选择' }}</el-tag>
           </div>
          <div>
            <el-button type="success" @click="refreshData" :disabled="!currentTable">刷新列表</el-button>
          </div>
        </div>
      </template>

      <el-tabs v-model="activeTab" type="border-card" v-if="currentTable">
        <el-tab-pane label="索引管理" name="index">
           <div style="margin-bottom: 15px">
              <el-button type="primary" @click="dialogVisible = true" icon="Plus">创建索引</el-button>
           </div>
           <el-table :data="indexes" border style="width: 100%">
               <el-table-column prop="name" label="索引名称" width="180" />
               <el-table-column prop="type" label="类型" width="120">
                   <template #default="scope">
                       <el-tag :type="scope.row.type === 'PRIMARY' ? 'danger' : (scope.row.type === 'UNIQUE' ? 'warning' : 'info')">
                           {{ scope.row.type }}
                       </el-tag>
                   </template>
               </el-table-column>
               <el-table-column prop="columns" label="包含字段（逗号分隔）" />
               <el-table-column fixed="right" label="操作" width="150" align="center">
                 <template #default="scope">
                    <el-button link type="primary" @click="rebuildIndex(scope.row)">重建</el-button>
                    <el-button link type="danger" @click="dropIndex(scope.$index, scope.row)" :disabled="scope.row.type === 'PRIMARY'">删除</el-button>
                 </template>
               </el-table-column>
           </el-table>
        </el-tab-pane>

        <el-tab-pane label="约束与完整性" name="constraint">
           <div style="margin-bottom: 15px; display: flex; justify-content: space-between">
               <span>当前表的完整性规则列表 (主键、唯一键、外键、检查约束)</span>
               <el-button type="warning" plain @click="checkConstraints" icon="Check">执行全表数据校验</el-button>
           </div>
           
           <el-table :data="constraints" border style="width: 100%">
               <el-table-column prop="name" label="约束名称" width="180" />
               <el-table-column prop="type" label="类型" width="150">
                    <template #default="scope">
                       <el-tag :type="scope.row.type === 'FOREIGN KEY' ? 'success' : (scope.row.type === 'CHECK' ? 'primary' : 'info')">
                           {{ scope.row.type }}
                       </el-tag>
                   </template>
               </el-table-column>
               <el-table-column prop="definition" label="定义 / 规则表达式" />
               <el-table-column fixed="right" label="操作" width="100" align="center">
                 <template #default="scope">
                    <el-button link type="danger" @click="dropConstraint(scope.$index, scope.row)">删除</el-button>
                 </template>
               </el-table-column>
           </el-table>
        </el-tab-pane>
      </el-tabs>

      <div v-else class="empty-state">
          请从表管理页面或 URL 携带 db, table 参数进入
          <br><br>
          <el-button type="primary" @click="$router.push('/table')">返回表管理</el-button>
      </div>

    </el-card>

    <!-- Create Index Dialog -->
    <el-dialog v-model="dialogVisible" title="创建新索引" width="500px">
        <el-form label-width="100px">
           <el-form-item label="索引名称">
               <el-input v-model="newIndex.name" placeholder="idx_name" />
           </el-form-item>
           <el-form-item label="索引类型">
               <el-select v-model="newIndex.type" placeholder="选择类型" style="width: 100%">
                   <el-option label="NORMAL (普通索引)" value="NORMAL" />
                   <el-option label="UNIQUE (唯一索引)" value="UNIQUE" />
                   <el-option label="FULLTEXT (全文索引)" value="FULLTEXT" />
               </el-select>
           </el-form-item>
            <el-form-item label="包含字段">
               <el-select v-model="newIndex.columns" multiple placeholder="选择字段 (可多选)" style="width: 100%">
                   <el-option label="id" value="id" />
                   <el-option label="username" value="username" />
                   <el-option label="status" value="status" />
               </el-select>
           </el-form-item>
        </el-form>
        <template #footer>
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" @click="handleCreateIndex">确定</el-button>
        </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Check } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const currentDb = ref('')
const currentTable = ref('')
const activeTab = ref('index')

const indexes = ref([])
const constraints = ref([])

const dialogVisible = ref(false)
const newIndex = ref({ name: '', type: 'NORMAL', columns: [] })

onMounted(() => {
   if(route.query.db && route.query.table) {
      currentDb.value = route.query.db
      currentTable.value = route.query.table
      
      // Mock fetch
      refreshData()
   }
})

const refreshData = () => {
    indexes.value = [
        { name: 'PRIMARY', type: 'PRIMARY', columns: 'id' },
        { name: 'uk_username', type: 'UNIQUE', columns: 'username' },
        { name: 'idx_status', type: 'NORMAL', columns: 'status' }
    ]
    constraints.value = [
        { name: 'PRIMARY', type: 'PRIMARY KEY', definition: 'PRIMARY KEY (`id`)' },
        { name: 'uk_username', type: 'UNIQUE KEY', definition: 'UNIQUE KEY (`username`)' },
        { name: 'chk_status', type: 'CHECK', definition: 'CHECK (`status` IN (0, 1))' }
    ]
    if (route.query.table) ElMessage.success('索引与约束列表已刷新')
}

// Index Operations
const rebuildIndex = (row) => {
    ElMessage.success(`正在重建索引 ${row.name}... (Mock)`)
}

const dropIndex = (index, row) => {
    ElMessageBox.confirm(`确定删除索引 ${row.name} 吗？此操作可能影响查询性能。`, '警告', { type: 'warning' }).then(() => {
        indexes.value.splice(index, 1)
        ElMessage.success('已删除')
    }).catch(()=>{})
}

const handleCreateIndex = () => {
    if (!newIndex.value.name || !newIndex.value.columns.length) {
        return ElMessage.warning('名称和字段不能为空')
    }
    indexes.value.push({
        name: newIndex.value.name,
        type: newIndex.value.type,
        columns: newIndex.value.columns.join(', ')
    })
    dialogVisible.value = false
    newIndex.value = { name: '', type: 'NORMAL', columns: [] }
    ElMessage.success('索引创建成功')
}

// Constraint Operations
const checkConstraints = () => {
    // Call POST /api/databases/{databaseName}/tables/{tableName}/constraints/check
    ElMessage.success(`正在对全表 ${currentTable.value} 进行数据合规校验...`)
    setTimeout(() => {
        ElMessageBox.alert('校验完成：未发现违反约束的数据行。', '校验结果', { type: 'success' })
    }, 1500)
}

const dropConstraint = (index, row) => {
    ElMessageBox.confirm(`确定删除约束 ${row.name} 吗？`, '警告', { type: 'warning' }).then(() => {
        constraints.value.splice(index, 1)
        ElMessage.success('已删除')
    }).catch(()=>{})
}

</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.empty-state {
    padding: 50px;
    text-align: center;
    color: #909399;
}
</style>
