<template>
  <div>
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>记录管理 ({{ currentDb }}.{{ currentTable }})</span>
          <div>
            <el-select v-model="currentDb" placeholder="数据库" style="width: 150px; margin-right: 10px" @change="resetTable">
              <el-option v-for="db in databaseOptions" :key="db" :label="db" :value="db" />
            </el-select>
            <el-select v-model="currentTable" placeholder="表" style="width: 150px; margin-right: 10px" @change="handleTableChange">
              <el-option v-for="table in tableOptions" :key="table" :label="table" :value="table" />
            </el-select>
            <el-button type="primary" @click="dialogVisible = true" :disabled="!currentTable">新增记录</el-button>
            <el-button type="success" @click="fetchRecords" :disabled="!currentTable">查询</el-button>
          </div>
        </div>
      </template>

      <div v-if="currentTable" class="filter-area">
        <el-form :inline="true" :model="queryForm">
            <el-form-item label="快速条件 (AND)">
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
                <el-button type="primary" icon="Search" @click="fetchRecords" style="margin-left: 10px">搜索</el-button>
            </el-form-item>
        </el-form>
      </div>

      <div v-if="currentTable" style="margin-top: 15px">
           <el-table :data="records" style="width: 100%" height="500" border stripe>
            <el-table-column v-for="col in columns" :key="col.prop" :prop="col.prop" :label="col.label" show-overflow-tooltip />
            <el-table-column fixed="right" label="操作" width="120">
               <template #default="scope">
                 <el-button link type="danger" @click="deleteRow(scope.$index, scope.row)">删除</el-button>
               </template>
            </el-table-column>
           </el-table>
           <div style="margin-top: 10px; display: flex; justify-content: flex-end">
              <el-pagination
                v-model:current-page="pagination.page"
                v-model:page-size="pagination.size"
                layout="total, prev, pager, next"
                :total="pagination.total"
                @current-change="fetchRecords"
              />
           </div>
      </div>
      <el-empty v-else description="请从左侧树点击表，或在此选择数据库和表" />
    </el-card>

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
import { Plus, Minus, Search } from '@element-plus/icons-vue'
import {
    deleteRecord,
    getTableDetail,
    insertRecord,
    listDatabases,
    listTables,
    queryRecords
} from '../api/dbms'

const route = useRoute()
const databaseOptions = ref([])
const tableOptions = ref([])
const currentDb = ref('')
const currentTable = ref('')

const queryForm = ref({ conditions: [{ field: '', op: '=', value: '' }] })
const columns = ref([])
const records = ref([])
const pagination = ref({ page: 1, size: 50, total: 0 })
const dialogVisible = ref(false)
const newRecord = ref({})

// 修复问题1的关键：监听路由参数变化
watch(
  () => [route.query.db, route.query.table],
  async ([newDb, newTable]) => {
    if (newDb && newTable) {
      currentDb.value = newDb
      currentTable.value = newTable
      await syncContext()
    }
  },
  { immediate: true } // 立即执行一次以处理初始进入页面的情况
)

async function syncContext() {
  if (!currentDb.value || !currentTable.value) return
  try {
    await fetchTables() // 确保下拉列表同步
    await loadTableColumns()
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

const loadTableColumns = async () => {
    const res = await getTableDetail(currentDb.value, currentTable.value)
    const detailColumns = res.data?.columns || []
    columns.value = detailColumns.map((col) => ({ prop: col.name, label: col.name }))
}

const handleTableChange = async () => {
    pagination.value.page = 1
    await loadTableColumns()
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
    // 如果 URL 里已经有参数，syncContext 会被 watch 触发
})
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.filter-area { padding: 15px; background: #f8f9fa; border-radius: 8px; margin-bottom: 10px; }
.condition-item { display: inline-flex; align-items: center; margin-right: 10px; }
</style>