<template>
  <div>
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>表管理 ({{ currentDb }})</span>
          <el-select v-model="currentDb" placeholder="请选择数据库" @change="fetchTables">
            <el-option
              v-for="db in databaseOptions"
              :key="db"
              :label="db"
              :value="db"
            />
          </el-select>
          <el-button class="button" type="primary" @click="dialogVisible = true">新建表</el-button>
          <el-button class="button" type="success" @click="fetchTables">刷新列表</el-button>
        </div>
      </template>

      <el-table :data="tableData" style="width: 100%">
        <el-table-column prop="name" label="表名" width="180" />
        <el-table-column prop="rows" label="行数" width="120" />
        <el-table-column prop="engine" label="引擎" width="120" />
        <el-table-column prop="comment" label="注释" />
        <el-table-column fixed="right" label="操作" width="250">
          <template #default="scope">
            <el-button link type="primary" size="small" @click="viewStructure(scope.row)">表结构</el-button>
            <el-button link type="primary" size="small" @click="manageColumns(scope.row)">字段管理</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="drawer" :title="`表结构总览 - ${selectedTable?.name}`" size="50%">
      <div v-if="selectedTable" class="drawer-tabs">
        <el-tabs type="border-card">
          <el-tab-pane label="列 (Columns)">
            <el-table :data="selectedTable.columns" size="small" border>
              <el-table-column prop="name" label="字段名" />
              <el-table-column prop="type" label="类型" />
              <el-table-column prop="key" label="键" />
              <el-table-column prop="nn" label="非空">
                <template #default="scope">
                  <el-icon v-if="scope.row.nn"><Check /></el-icon>
                </template>
              </el-table-column>
              <el-table-column prop="default" label="默认值" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="约束 (Constraints)">
            <el-table :data="selectedTable.constraints" size="small" border>
              <el-table-column prop="name" label="约束名" />
              <el-table-column prop="type" label="类型 (PK/UQ/CHECK)" />
              <el-table-column prop="expr" label="定义/表达式" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="外键 (Foreign Keys)">
            <el-table :data="selectedTable.fks" size="small" border>
              <el-table-column prop="name" label="外键名" />
              <el-table-column prop="column" label="源字段" />
              <el-table-column prop="refTable" label="目标表" />
              <el-table-column prop="refColumn" label="目标字段" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="索引 (Indexes)">
            <el-table :data="selectedTable.indexes" size="small" border>
              <el-table-column prop="name" label="索引名" />
              <el-table-column prop="column" label="字段" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="DDL">
            <div class="ddl-code">
              {{ selectedTable.ddl }}
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <el-dialog v-model="dialogVisible" title="新建表" width="75%">
      <el-form :model="form" label-position="top">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="表名">
              <el-input v-model="form.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="注释">
              <el-input v-model="form.comment" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="字段定义 (属性与约束)">
          <el-button type="primary" size="small" @click="addColumn" icon="Plus">添加字段</el-button>
          <el-table :data="form.columns" style="width: 100%; margin-top: 10px" border size="small">
            <el-table-column label="字段名" width="130">
              <template #default="scope">
                <el-input v-model="scope.row.name" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="类型" width="110">
              <template #default="scope">
                <el-select
                  v-model="scope.row.type"
                  placeholder="选择"
                  size="small"
                  filterable
                  allow-create
                  @change="val => handleTypeChange(scope.row, val)"
                >
                  <el-option label="INT" value="INT" />
                  <el-option label="BIGINT" value="BIGINT" />
                  <el-option label="VARCHAR" value="VARCHAR" />
                  <el-option label="CHAR" value="CHAR" />
                  <el-option label="TEXT" value="TEXT" />
                  <el-option label="DATE" value="DATE" />
                  <el-option label="DECIMAL" value="DECIMAL" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="长度" width="80">
              <template #default="scope">
                <el-input
                  v-model="scope.row.length"
                  size="small"
                  :disabled="!needsLength(scope.row.type)"
                  @change="val => handleLengthChange(scope.row, val)"
                />
              </template>
            </el-table-column>
            <el-table-column label="PK" width="55" align="center" header-align="center">
              <template #default="scope">
                <el-checkbox v-model="scope.row.pk" />
              </template>
            </el-table-column>
            <el-table-column label="NN" width="55" align="center" header-align="center">
              <template #default="scope">
                <el-checkbox v-model="scope.row.nn" :disabled="scope.row.pk" />
              </template>
            </el-table-column>
            <el-table-column label="UQ" width="55" align="center" header-align="center">
              <template #default="scope">
                <el-checkbox v-model="scope.row.uq" :disabled="scope.row.pk" />
              </template>
            </el-table-column>
            <el-table-column label="默认值" width="100">
              <template #default="scope">
                <el-input v-model="scope.row.defaultVal" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="Check约束" width="120">
              <template #default="scope">
                <el-input v-model="scope.row.check" size="small" placeholder="如: >0" />
              </template>
            </el-table-column>
            <el-table-column label="外键连接 (目标表.字段)" width="160">
              <template #default="scope">
                <el-input v-model="scope.row.fk" size="small" placeholder="例: users.id" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="60" align="center">
              <template #default="scope">
                <el-button link type="danger" @click="removeColumn(scope.$index)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
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
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Plus } from '@element-plus/icons-vue'
import {
  createTable,
  dropTable,
  getTableDetail,
  listDatabases,
  listTables
} from '../api/dbms'

const router = useRouter()
const route = useRoute()
const databaseOptions = ref([])
const currentDb = ref('')
const drawer = ref(false)
const dialogVisible = ref(false)
const selectedTable = ref(null)
const tableData = ref([])

const form = ref({
  name: '',
  comment: '',
  columns: []
})

const needsLength = (type) => ['VARCHAR', 'CHAR', 'DECIMAL'].includes(String(type || '').toUpperCase())

const fetchDatabases = async () => {
  const res = await listDatabases()
  databaseOptions.value = (res.data || []).map((item) => item.name)

  const routeDb = route.query.db ? String(route.query.db) : ''
  if (routeDb && databaseOptions.value.includes(routeDb)) {
    currentDb.value = routeDb
  } else if (!currentDb.value && databaseOptions.value.length > 0) {
    currentDb.value = databaseOptions.value[0]
  }
}

const fetchTables = async () => {
  if (!currentDb.value) {
    tableData.value = []
    return
  }

  const res = await listTables(currentDb.value)
  tableData.value = (res.data || []).map((item) => ({
    name: item.name,
    rows: item.rows || '-',
    engine: item.engine || '404',
    comment: item.comment || '-'
  }))
}

const viewStructure = async (row) => {
  const res = await getTableDetail(currentDb.value, row.name)
  selectedTable.value = {
    name: row.name,
    columns: res.data?.columns || [],
    constraints: res.data?.constraints || [],
    fks: res.data?.fks || [],
    indexes: res.data?.indexes || [],
    ddl: res.data?.ddl || ''
  }
  drawer.value = true
}

const manageColumns = (row) => {
  router.push({ path: '/column', query: { db: currentDb.value, table: row.name } })
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`确定永久删除表 ${row.name} 吗？`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await dropTable(currentDb.value, row.name)
    await fetchTables()
    window.dispatchEvent(new Event('dbms-tree-refresh'))
    ElMessage({ type: 'success', message: '删除成功' })
  })
}

const addColumn = () => {
  form.value.columns.push({
    name: '',
    type: 'VARCHAR',
    length: '255',
    pk: false,
    nn: false,
    uq: false,
    defaultVal: '',
    check: '',
    fk: ''
  })
}

const removeColumn = (index) => {
  form.value.columns.splice(index, 1)
}

const handleTypeChange = (row, type) => {
  if (!needsLength(type)) {
    row.length = ''
    return
  }

  if (!row.length) {
    row.length = '255'
  }
}

const handleLengthChange = (row, value) => {
  if (!needsLength(row.type)) {
    row.length = ''
    return
  }

  row.length = String(value || '').trim()
}

const handleCreate = async () => {
  if (!currentDb.value) return ElMessage.warning('请先选择数据库')
  if (!form.value.name.trim()) return ElMessage.warning('表名不能为空')
  if (form.value.columns.length === 0) return ElMessage.warning('请至少添加一个字段')

  const hasPk = form.value.columns.some(col => col.pk)
  if (!hasPk) return ElMessage.warning('请至少选择一个字段作为主键 (PK)')

  for (const column of form.value.columns) {
    if (!column.name.trim()) return ElMessage.warning('字段名不能为空')
    if (needsLength(column.type)) {
      const length = Number(String(column.length || '').trim())
      if (!Number.isInteger(length) || length <= 0) {
        return ElMessage.warning(`${column.name || '字段'} 需要填写有效长度`)
      }
    }
  }

  const payload = {
    name: form.value.name.trim(),
    comment: form.value.comment,
    columns: form.value.columns.map((col) => ({
      name: col.name.trim(),
      type: String(col.type || '').toUpperCase(),
      length: needsLength(col.type) ? Number(String(col.length || '').trim()) : null,
      nullable: !col.nn,
      pk: !!col.pk,
      uq: !!col.pk || !!col.uq,
      checkExpression: col.check ? `CHECK (${col.name} ${col.check})` : undefined,
      foreignKeyTable: col.fk ? col.fk.split('.')[0] : undefined,
      foreignKeyColumn: col.fk ? col.fk.split('.')[1] : undefined
    }))
  }

  try {
    await createTable(currentDb.value, payload)
    dialogVisible.value = false
    form.value = { name: '', comment: '', columns: [] }
    await fetchTables()
    window.dispatchEvent(new Event('dbms-tree-refresh'))
    ElMessage.success('创建成功')
  } catch (e) {
    ElMessage.error(e.message || '创建表失败')
  }
}

watch(() => route.query.db, async (dbName) => {
  if (dbName && String(dbName) !== currentDb.value) {
    currentDb.value = String(dbName)
    await fetchTables()
  }
})

onMounted(async () => {
  try {
    await fetchDatabases()
    await fetchTables()
  } catch (error) {
    ElMessage.error(error.message || '加载数据失败')
  }
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.drawer-tabs {
  padding: 0 10px;
}

.ddl-code {
  background: #f5f5f5;
  padding: 15px;
  border-radius: 4px;
  font-family: monospace;
  white-space: pre-wrap;
}
</style>
