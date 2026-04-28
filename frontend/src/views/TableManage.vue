<template>
  <div>
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>表管理 ({{ currentDb }})</span>
          <el-select v-model="currentDb" placeholder="请选择数据库" @change="fetchTables">
            <el-option label="db_test_1" value="db_test_1" />
            <el-option label="db_online" value="db_online" />
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
                <el-table-column prop="name" label="字段名"></el-table-column>
                <el-table-column prop="type" label="类型"></el-table-column>
                <el-table-column prop="key" label="键"></el-table-column>
                <el-table-column prop="nn" label="非空">
                  <template #default="scope">
                    <el-icon v-if="scope.row.nn"><Check /></el-icon>
                  </template>
                </el-table-column>
                <el-table-column prop="default" label="默认值"></el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="约束 (Constraints)">
             <el-table :data="selectedTable.constraints" size="small" border>
                <el-table-column prop="name" label="约束名"></el-table-column>
                <el-table-column prop="type" label="类型 (PK/UQ/CHECK)"></el-table-column>
                <el-table-column prop="expr" label="定义/表达式"></el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="外键 (Foreign Keys)">
             <el-table :data="selectedTable.fks" size="small" border>
                <el-table-column prop="name" label="外键名"></el-table-column>
                <el-table-column prop="column" label="源字段"></el-table-column>
                <el-table-column prop="refTable" label="目标表"></el-table-column>
                <el-table-column prop="refColumn" label="目标字段"></el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="索引 (Indexes)">
             <el-table :data="selectedTable.indexes" size="small" border>
                <el-table-column prop="name" label="索引名"></el-table-column>
                <el-table-column prop="column" label="字段"></el-table-column>
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
                      <el-select v-model="scope.row.type" placeholder="选择" size="small" filterable allow-create>
                        <el-option label="INT" value="INT" />
                        <el-option label="VARCHAR" value="VARCHAR" />
                        <el-option label="TEXT" value="TEXT" />
                        <el-option label="DATE" value="DATE" />
                      </el-select>
                  </template>
              </el-table-column>
              <el-table-column label="长度" width="80">
                  <template #default="scope">
                      <el-input v-model="scope.row.length" size="small" />
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
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Plus } from '@element-plus/icons-vue'

const router = useRouter()
const currentDb = ref('db_test_1')
const drawer = ref(false)
const dialogVisible = ref(false)
const selectedTable = ref(null)

const tableData = ref([
  { name: 'users', rows: 1200, engine: 'InnoDB', comment: '用户信息表' },
  { name: 'orders', rows: 8500, engine: 'InnoDB', comment: '订单信息表' }
])

const form = ref({
    name: '',
    comment: '',
    columns: []
})

const fetchTables = () => {
   ElMessage.success(`刷新 ${currentDb.value} 的列表`)
}

const viewStructure = (row) => {
    // Mock data based on DBeaver like structure
    selectedTable.value = {
        name: row.name,
        columns: [
            { name: 'id', type: 'INT', key: 'PRI', nn: true, default: '' },
            { name: 'username', type: 'VARCHAR(50)', key: 'UNI', nn: true, default: '' },
            { name: 'status', type: 'INT', key: '', nn: false, default: '1' }
        ],
        constraints: [
            { name: 'PRIMARY', type: 'PRIMARY KEY', expr: '(`id`)' },
            { name: 'uk_username', type: 'UNIQUE', expr: '(`username`)' },
            { name: 'chk_status', type: 'CHECK', expr: '(`status` >= 0)' }
        ],
        fks: [
             { name: 'fk_user_org', column: 'org_id', refTable: 'organization', refColumn: 'id' }
        ],
        indexes: [
            { name: 'PRIMARY', column: 'id' },
            { name: 'uk_username', column: 'username' }
        ],
        ddl: `CREATE TABLE \`${row.name}\` (\n  \`id\` INT NOT NULL AUTO_INCREMENT,\n  \`username\` VARCHAR(50) NOT NULL,\n  \`status\` INT DEFAULT 1,\n  PRIMARY KEY (\`id\`),\n  UNIQUE KEY \`uk_username\` (\`username\`)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;`
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
          type: 'warning',
    }).then(() => {
          tableData.value = tableData.value.filter(item => item.name !== row.name)
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

const handleCreate = () => {
    if(!form.value.name) return ElMessage.warning('表名不能为空')
    tableData.value.push({
        name: form.value.name,
        rows: 0,
        engine: 'InnoDB',
        comment: form.value.comment
    })
    dialogVisible.value = false
    ElMessage.success('创建成功')
    form.value = { name: '', comment: '', columns: [] }
}
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
