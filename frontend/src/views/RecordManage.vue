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
            <el-button type="warning" plain @click="toSql" :disabled="!currentTable">转 SQL</el-button>
          </div>
        </div>
      </template>

      <!-- Condition Builder -->
      <div v-if="currentTable" class="filter-area">
        <el-form :inline="true" :model="queryForm">
            <el-form-item label="字段投影">
               <el-select v-model="queryForm.fields" multiple collapse-tags placeholder="选择显示字段" style="width:240px;">
                    <el-option v-for="col in columns" :key="col.prop" :label="col.label" :value="col.prop" />
               </el-select>
            </el-form-item>
            <el-form-item label="条件组 (AND)">
                <div v-for="(cond, index) in queryForm.conditions" :key="index" class="condition-item">
                    <el-select v-model="cond.field" style="width: 120px" placeholder="字段">
                         <el-option v-for="col in columns" :key="col.prop" :label="col.label" :value="col.prop" />
                    </el-select>
                    <el-select v-model="cond.op" style="width: 90px; margin: 0 5px">
                        <el-option label="等于 =" value="=" />
                        <el-option label="大于 >" value=">" />
                        <el-option label="小于 <" value="<" />
                        <el-option label="LIKE" value="LIKE" />
                    </el-select>
                    <el-input v-model="cond.value" style="width: 150px" placeholder="值" />
                    <el-button link type="danger" icon="Minus" @click="removeCond(index)" />
                </div>
                <el-button link type="primary" icon="Plus" @click="addCond" style="margin-right: 15px;">添加条件</el-button>
                <el-button type="primary" icon="Search" @click="fetchRecords">条件查询</el-button>
            </el-form-item>
        </el-form>
      </div>

      <!-- Table Result -->
      <div v-if="currentTable" style="margin-top: 15px">
           <el-table
            :data="records"
            style="width: 100%"
            height="400"
            border
            @selection-change="handleSelectionChange"
           >
            <el-table-column type="selection" width="55" />
            <el-table-column
                v-for="col in displayedColumns"
                :key="col.prop"
                :prop="col.prop"
                :label="col.label"
            >
                <template #default="scope">
                    <div v-if="editingRow === scope.$index">
                        <el-input v-model="scope.row[col.prop]" size="small" />
                    </div>
                    <span v-else>{{ scope.row[col.prop] }}</span>
                </template>
            </el-table-column>
            <el-table-column fixed="right" label="操作" width="150">
               <template #default="scope">
                 <el-button v-if="editingRow !== scope.$index" link type="primary" @click="editRow(scope.$index, scope.row)">编辑</el-button>
                 <el-button v-else link type="success" @click="saveRow(scope.$index, scope.row)">保存</el-button>
                 <el-button link type="danger" @click="deleteRow(scope.$index, scope.row)">删除</el-button>
               </template>
            </el-table-column>
           </el-table>
           <div style="margin-top: 10px; display: flex; justify-content: space-between">
              <div>
                  <el-button type="danger" :disabled="!selectedRows.length" plain @click="batchDelete">批量删除</el-button>
                  <el-button type="info" :disabled="!selectedRows.length" plain>导出选中 (.csv)</el-button>
              </div>
              <el-pagination
                v-model:current-page="pagination.page"
                v-model:page-size="pagination.size"
                :page-sizes="[10, 50, 100, 500]"
                layout="total, sizes, prev, pager, next, jumper"
                :total="pagination.total"
                @size-change="fetchRecords"
                @current-change="fetchRecords"
              />
           </div>
      </div>
      <div v-else class="empty-state">
          请选择数据库和表
      </div>
    </el-card>

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
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Minus, Search } from '@element-plus/icons-vue'
import {
    deleteRecord,
    getTableDetail,
    insertRecord,
    listDatabases,
    listTables,
    queryRecords,
    updateRecord
} from '../api/dbms'

const router = useRouter()
const databaseOptions = ref([])
const tableOptions = ref([])
const currentDb = ref('')
const currentTable = ref('')

const queryForm = ref({
    fields: [],
    conditions: [{ field: 'id', op: '=', value: '' }]
})

const columns = ref([
    { prop: 'id', label: 'id' }
])

const displayedColumns = computed(() => {
    if(queryForm.value.fields.length === 0) return columns.value
    return columns.value.filter(c => queryForm.value.fields.includes(c.prop))
})

const records = ref([])
const pagination = ref({ page: 1, size: 50, total: 100 })
const selectedRows = ref([])
const editingRow = ref(-1)
const editCache = ref({})

const dialogVisible = ref(false)
const newRecord = ref({})

const resetTable = async () => {
    currentTable.value = ''
    tableOptions.value = []
    columns.value = []
    records.value = []
    await fetchTables()
}

const fetchDatabases = async () => {
    const res = await listDatabases()
    databaseOptions.value = (res.data || []).map((item) => item.name)
    if (!currentDb.value && databaseOptions.value.length > 0) {
        currentDb.value = databaseOptions.value[0]
    }
}

const fetchTables = async () => {
    if (!currentDb.value) {
        tableOptions.value = []
        return
    }
    const res = await listTables(currentDb.value)
    tableOptions.value = (res.data || []).map((item) => item.name)
}

const loadTableColumns = async () => {
    if (!currentDb.value || !currentTable.value) {
        columns.value = []
        return
    }
    const res = await getTableDetail(currentDb.value, currentTable.value)
    const detailColumns = res.data?.columns || []
    columns.value = detailColumns.map((col) => ({
        prop: col.name,
        label: col.name
    }))
}

const handleTableChange = async () => {
    pagination.value.page = 1
    await loadTableColumns()
    await fetchRecords()
}

const fetchRecords = async () => {
    if (!currentDb.value || !currentTable.value) {
        records.value = []
        return
    }
    const unsupported = queryForm.value.conditions.filter((c) => c.value && c.op !== '=')
    if (unsupported.length > 0) {
        ElMessage.warning('当前后端仅支持等值条件，已忽略非 = 条件')
    }

    const filters = {}
    queryForm.value.conditions
        .filter((c) => c.field && c.value !== '' && c.value !== null && c.op === '=')
        .forEach((c) => {
            filters[c.field] = c.value
        })

    const res = await queryRecords(currentDb.value, currentTable.value, {
        page: pagination.value.page,
        size: pagination.value.size,
        filters
    })

    const data = res.data || {}
    records.value = data.list || []
    pagination.value.total = Number(data.total || 0)
}

const addCond = () => queryForm.value.conditions.push({ field: '', op: '=', value: '' })
const removeCond = (idx) => queryForm.value.conditions.splice(idx, 1)

const handleSelectionChange = (val) => { selectedRows.value = val }

const editRow = (index, row) => {
    editCache.value = { ...row }
    editingRow.value = index
}

const saveRow = (index, row) => {
    const keyColumn = columns.value[0]?.prop
    if (!keyColumn || row[keyColumn] === undefined || row[keyColumn] === null) {
        ElMessage.warning('缺少可用于更新的主键字段')
        return
    }
    updateRecord(currentDb.value, currentTable.value, {
        where: { [keyColumn]: row[keyColumn] },
        updates: row
    }).then(async () => {
        editingRow.value = -1
        await fetchRecords()
        ElMessage.success('更新成功')
    })
}

const deleteRow = (index, row) => {
    const keyColumn = columns.value[0]?.prop
    if (!keyColumn || row[keyColumn] === undefined || row[keyColumn] === null) {
        ElMessage.warning('缺少可用于删除的主键字段')
        return
    }
    ElMessageBox.confirm('确定删除该记录吗？', '提示').then(async () => {
        await deleteRecord(currentDb.value, currentTable.value, {
            filters: { [keyColumn]: row[keyColumn] }
        })
        await fetchRecords()
        ElMessage.success('已删除')
    })
}

const handleInsert = async () => {
    const values = { ...newRecord.value }
    Object.keys(values).forEach((key) => {
        if (values[key] === '' || values[key] === null || values[key] === undefined) {
            delete values[key]
        }
    })
    if (Object.keys(values).length === 0) {
        ElMessage.warning('请至少填写一个字段值')
        return
    }
    await insertRecord(currentDb.value, currentTable.value, { values })
    dialogVisible.value = false
    newRecord.value = {}
    await fetchRecords()
    ElMessage.success('已插入')
}

const batchDelete = async () => {
    const keyColumn = columns.value[0]?.prop
    if (!keyColumn) {
        ElMessage.warning('当前表缺少可用于删除的标识字段')
        return
    }
    for (const row of selectedRows.value) {
        if (row[keyColumn] !== undefined && row[keyColumn] !== null) {
            await deleteRecord(currentDb.value, currentTable.value, {
                filters: { [keyColumn]: row[keyColumn] }
            })
        }
    }
    await fetchRecords()
    ElMessage.success('批量删除完成')
}

const toSql = () => {
    let sql = `SELECT ${queryForm.value.fields.length ? queryForm.value.fields.join(', ') : '*'} \nFROM ${currentDb.value}.${currentTable.value}`
    if (queryForm.value.conditions[0].value) {
        sql += `\nWHERE ` + queryForm.value.conditions.map(c => `${c.field} ${c.op} '${c.value}'`).join(' AND ')
    }
    sql += `\nLIMIT ${pagination.value.size};`

        router.push({ path: '/sql', query: { db: currentDb.value, table: currentTable.value } })
    setTimeout(() => {
        ElMessage.warning('草稿 SQL 已复制到剪贴板(模拟): ' + sql)
    }, 500)
}

onMounted(async () => {
    try {
        await fetchDatabases()
        await fetchTables()
    } catch (error) {
        ElMessage.error(error.message || '初始化记录页失败')
    }
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.filter-area {
    padding: 15px;
    background: #f8f9fa;
    border-radius: 4px;
}
.condition-item {
    display: inline-flex;
    align-items: center;
    margin-right: 15px;
    margin-bottom: 5px;
}
.empty-state {
    padding: 50px;
    text-align: center;
    color: #909399;
}
</style>
