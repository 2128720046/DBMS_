<template>
  <div class="table-viewer-layout">
    <div class="tabs-header-container">
       <el-tabs v-model="viewMode" type="border-card" class="top-nav-tabs">
          <el-tab-pane label="属性" name="properties">
            <template #label><el-icon><Tickets /></el-icon> 属性</template>
            <div class="properties-container">
               <el-card class="box-card" shadow="never" :body-style="{ padding: '10px' }">
                  <template #header>
                    <div style="font-weight: bold; font-size: 14px;">表: {{ currentTable }} ({{ currentDb }})</div>
                  </template>
                  
                  <el-tabs v-model="propertyTab" tab-position="left" style="height: 100%;">
                     <el-tab-pane label="列" name="columns">
                        <el-table :data="tableDetail.columns || []" border stripe size="small" height="calc(100vh - 270px)">
                            <el-table-column prop="name" label="字段名称" />
                            <el-table-column prop="type" label="数据类型" width="120" />
                            <el-table-column label="非空(NN)" width="80" align="center">
                               <template #default="{row}"><el-checkbox :model-value="!row.nullable" disabled /></template>
                            </el-table-column>
                            <el-table-column label="主键(PK)" width="80" align="center">
                               <template #default="{row}"><el-checkbox :model-value="row.pk" disabled /></template>
                            </el-table-column>
                            <el-table-column label="唯一(UQ)" width="80" align="center">
                               <template #default="{row}"><el-checkbox :model-value="row.uq" disabled /></template>
                            </el-table-column>
                        </el-table>
                     </el-tab-pane>
                     <el-tab-pane label="约束" name="constraints">
                         <el-table :data="tableDetail.constraints || []" border size="small" height="calc(100vh - 270px)">
                             <el-table-column prop="name" label="约束名" />
                             <el-table-column prop="type" label="类型" />
                             <el-table-column prop="expr" label="表达式" />
                         </el-table>
                     </el-tab-pane>
                     <el-tab-pane label="外键" name="fks">
                         <el-table :data="tableDetail.fks || []" border size="small" height="calc(100vh - 270px)">
                             <el-table-column prop="name" label="键名" />
                             <el-table-column prop="column" label="源列" />
                             <el-table-column prop="refTable" label="目标表" />
                             <el-table-column prop="refColumn" label="目标列" />
                         </el-table>
                     </el-tab-pane>
                     <el-tab-pane label="索引" name="indexes">
                         <el-table :data="tableDetail.indexes || []" border size="small" height="calc(100vh - 270px)">
                             <el-table-column prop="name" label="索引名称" />
                             <el-table-column prop="column" label="列名" />
                         </el-table>
                     </el-tab-pane>
                     <el-tab-pane label="DDL源" name="ddl">
                         <div class="ddl-code">{{ tableDetail.ddl || '-- 无 DDL 信息' }}</div>
                     </el-tab-pane>
                  </el-tabs>
               </el-card>
            </div>
          </el-tab-pane>
          
          <el-tab-pane label="图/数据" name="data">
            <template #label><el-icon><Grid /></el-icon> 数据记录</template>
            <div class="data-container">
               <el-card class="box-card" shadow="never" :body-style="{ padding: '10px' }">
                  <template #header>
                    <div class="card-header">
                      <div class="action-bar-right">
                         <el-select v-model="currentDb" placeholder="数据库" style="width: 150px; margin-right: 10px" @change="resetTable">
                             <el-option v-for="db in databaseOptions" :key="db" :label="db" :value="db" />
                         </el-select>
                         <el-select v-model="currentTable" placeholder="表" style="width: 150px; margin-right: 10px" @change="handleTableChange">
                             <el-option v-for="table in tableOptions" :key="table" :label="table" :value="table" />
                         </el-select>
                         <el-button type="primary" @click="dialogVisible = true" :disabled="!currentTable">新增记录</el-button>
                         <el-button type="success" @click="fetchRecords" :disabled="!currentTable" icon="Refresh">刷新</el-button>
                      </div>
                    </div>
                  </template>

                  <div v-if="currentTable" class="filter-area">
                    <el-form :inline="true" :model="queryForm">
                        <el-form-item label="快速条件 (AND)" style="margin-bottom: 0;">
                            <div v-for="(cond, index) in queryForm.conditions" :key="index" class="condition-item">
                                <el-select v-model="cond.field" style="width: 120px" placeholder="字段">
                                     <el-option v-for="col in columns" :key="col.prop" :label="col.label" :value="col.prop" />
                                </el-select>
                                <el-select v-model="cond.op" style="width: 90px; margin: 0 5px">
                                    <el-option label="=" value="=" />
                                    <el-option label="LIKE" value="LIKE" />
                                </el-select>
                                <el-input v-model="cond.value" style="width: 150px" placeholder="值" />
                                <el-button link type="danger" icon="Minus" @click="removeCond(index)" />
                            </div>
                            <el-button link type="primary" icon="Plus" @click="addCond">添加</el-button>
                            <el-button type="primary" size="small" icon="Search" @click="fetchRecords" style="margin-left: 10px">搜索</el-button>
                        </el-form-item>
                    </el-form>
                  </div>

                  <div v-if="currentTable" style="margin-top: 10px">
                       <el-table :data="records" style="width: 100%" height="calc(100vh - 350px)" border stripe size="small">
                        <el-table-column v-for="col in columns" :key="col.prop" :prop="col.prop" :label="col.label" show-overflow-tooltip />
                        <el-table-column fixed="right" label="操作" width="80" align="center">
                           <template #default="scope">
                             <el-button link type="danger" @click="deleteRow(scope.$index, scope.row)" icon="Delete" />
                           </template>
                        </el-table-column>
                       </el-table>
                       <div style="margin-top: 10px; display: flex; justify-content: space-between; align-items: center">
                          <span style="font-size: 12px; color: #909399">共 {{ pagination.total }} 条记录</span>
                          <el-pagination
                            v-model:current-page="pagination.page"
                            v-model:page-size="pagination.size"
                            layout="prev, pager, next"
                            :total="pagination.total"
                            @current-change="fetchRecords"
                            small
                          />
                       </div>
                  </div>
                  <el-empty v-else description="请先选择数据库和表" />
               </el-card>
            </div>
          </el-tab-pane>
       </el-tabs>
    </div>

    <!-- Insert Dialog -->
    <el-dialog v-model="dialogVisible" title="新增记录" width="500px">
        <el-form label-width="100px">
           <el-form-item v-for="col in columns" :key="col.prop" :label="col.label">
               <el-input v-model="newRecord[col.prop]" />
           </el-form-item>
        </el-form>
        <template #footer>
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" @click="handleInsert">确定</el-button>
        </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Minus, Search, Tickets, Grid, Delete, Refresh } from '@element-plus/icons-vue'
import { deleteRecord, getTableDetail, insertRecord, listDatabases, listTables, queryRecords } from '../api/dbms'

const route = useRoute()
const databaseOptions = ref([])
const tableOptions = ref([])
const currentDb = ref('')
const currentTable = ref('')

const viewMode = ref('data') // properties or data
const propertyTab = ref('columns')
const tableDetail = ref({}) // stores full metadata

const queryForm = ref({ conditions: [{ field: '', op: '=', value: '' }] })
const columns = ref([])
const records = ref([])
const pagination = ref({ page: 1, size: 50, total: 0 })
const dialogVisible = ref(false)
const newRecord = ref({})

// Watch for route query changes
watch(
  () => [route.query.db, route.query.table],
  async ([newDb, newTable]) => {
    if (newDb && newTable) {
      currentDb.value = newDb
      currentTable.value = newTable
      await syncContext()
    }
  },
  { immediate: true }
)

async function syncContext() {
  if (!currentDb.value || !currentTable.value) return
  try {
    await fetchTables()
    await loadTableColumnsAndMeta()
    await fetchRecords()
  } catch (e) {
    console.error(e)
  }
}

const resetTable = async () => {
    currentTable.value = ''
    tableOptions.value = []
    await fetchTables()
}

const fetchDatabases = async () => {
    const res = await listDatabases()
    databaseOptions.value = (res.data || []).map((item) => item.name)
}

const fetchTables = async () => {
    if (!currentDb.value) return
    const res = await listTables(currentDb.value)
    tableOptions.value = (res.data || []).map((item) => item.name)
}

const loadTableColumnsAndMeta = async () => {
    const res = await getTableDetail(currentDb.value, currentTable.value)
    tableDetail.value = res.data || {}
    const detailColumns = res.data?.columns || []
    columns.value = detailColumns.map((col) => ({ prop: col.name, label: col.name }))
}

const handleTableChange = async () => {
    pagination.value.page = 1
    await loadTableColumnsAndMeta()
    await fetchRecords()
}

const fetchRecords = async () => {
    const filters = {}
    queryForm.value.conditions
        .filter((c) => c.field && c.value !== '')
        .forEach((c) => { filters[c.field] = c.value })

    const res = await queryRecords(currentDb.value, currentTable.value, {
        page: pagination.value.page,
        size: pagination.value.size,
        filters
    })
    records.value = res.data?.list || []
    pagination.value.total = res.data?.total || 0
}

const addCond = () => queryForm.value.conditions.push({ field: '', op: '=', value: '' })
const removeCond = (idx) => queryForm.value.conditions.splice(idx, 1)

const deleteRow = (index, row) => {
    const key = columns.value[0]?.prop
    ElMessageBox.confirm('确定删除该记录吗？', '提示').then(async () => {
        await deleteRecord(currentDb.value, currentTable.value, { filters: { [key]: row[key] } })
        await fetchRecords()
        ElMessage.success('已删除')
    })
}

const handleInsert = async () => {
    await insertRecord(currentDb.value, currentTable.value, { values: newRecord.value })
    dialogVisible.value = false
    newRecord.value = {}
    await fetchRecords()
    ElMessage.success('已插入')
}

onMounted(async () => {
    await fetchDatabases()
})
</script>

<style scoped>
.table-viewer-layout {
  height: 100%;
}
.tabs-header-container {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.top-nav-tabs {
  height: 100%;
  display: flex;
  flex-direction: column;
}
:deep(.el-tabs__content) {
  flex: 1;
  padding: 0;
  overflow: hidden;
}
.properties-container, .data-container {
  padding: 10px;
  height: 100%;
  overflow-y: auto;
}
.card-header { display: flex; justify-content: flex-end; align-items: center; }
.action-bar-right { display: flex; align-items: center; }
.filter-area { padding: 10px 15px; background: #f8f9fa; border-radius: 4px; margin-bottom: 10px; border: 1px solid #ebecef; }
.condition-item { display: inline-flex; align-items: center; margin-right: 15px; }
.ddl-code { background: #f5f5f5; padding: 15px; border-radius: 4px; font-family: 'Fira Code', monospace; white-space: pre-wrap; font-size: 13px; }
</style>