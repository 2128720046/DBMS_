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
            <el-button type="primary" @click="addEmptyRow" :disabled="!currentTable" plain>新增结尾字段</el-button>
            <el-button type="success" @click="saveChanges" :disabled="!currentTable" :loading="saving">保存结构变更</el-button>
             <el-dropdown style="margin-left: 10px;" trigger="click">
              <el-button type="warning" plain>高级操作<el-icon class="el-icon--right"><arrow-down /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                    <el-dropdown-item @click="importJson">JSON 批量导入</el-dropdown-item>
                    <el-dropdown-item @click="genTemplate">生成默认约束模板</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </template>

      <!-- Columns Editable Table -->
      <div v-if="currentTable">
           <el-table
            :data="columns"
            style="width: 100%"
            height="500"
            border
            row-key="id"
           >
            <!-- Sort handler for dragging (mock) -->
            <el-table-column width="40" align="center">
                <template #default>
                    <el-icon style="cursor: move;"><Sort /></el-icon>
                </template>
            </el-table-column>
            
            <el-table-column label="字段名" width="180">
                <template #default="scope">
                    <el-input v-model="scope.row.name" :class="{'is-error': !scope.row.name}" />
                </template>
            </el-table-column>

            <el-table-column label="类型" width="160">
                 <template #default="scope">
                    <el-select v-model="scope.row.type" placeholder="选择类型" filterable allow-create>
                      <el-option label="INT" value="INT" />
                      <el-option label="BIGINT" value="BIGINT" />
                      <el-option label="VARCHAR" value="VARCHAR" />
                      <el-option label="TEXT" value="TEXT" />
                      <el-option label="DATE" value="DATE" />
                      <el-option label="DATETIME" value="DATETIME" />
                      <el-option label="DECIMAL" value="DECIMAL" />
                    </el-select>
                </template>
            </el-table-column>

            <el-table-column label="长度/值" width="100">
                <template #default="scope">
                    <el-input v-model="scope.row.length" :disabled="!needsLength(scope.row.type)" />
                </template>
            </el-table-column>

             <el-table-column label="非空(NN)" width="80" align="center">
                <template #default="scope">
                    <el-checkbox v-model="scope.row.nn" :disabled="scope.row.pk" />
                </template>
            </el-table-column>

            <el-table-column label="主键(PK)" width="80" align="center">
                <template #default="scope">
                    <el-checkbox v-model="scope.row.pk" @change="val => handlePkChange(scope.row, val)" />
                </template>
            </el-table-column>

             <el-table-column label="唯一(UQ)" width="80" align="center">
                <template #default="scope">
                    <el-checkbox v-model="scope.row.uq" :disabled="scope.row.pk" />
                </template>
            </el-table-column>

             <el-table-column label="自增(AI)" width="80" align="center">
                <template #default="scope">
                    <el-checkbox v-model="scope.row.ai" :disabled="!isInteger(scope.row.type) || !scope.row.pk" />
                </template>
            </el-table-column>

             <el-table-column label="默认值" width="150">
                <template #default="scope">
                     <el-input v-model="scope.row.defaultVal" :disabled="scope.row.ai" placeholder="NULL" />
                </template>
            </el-table-column>
            
             <el-table-column label="Check约束" width="130">
                <template #default="scope">
                    <el-input v-model="scope.row.check" size="small" placeholder="例: > 0" />
                </template>
            </el-table-column>

             <el-table-column label="外键(目标表.字段)" width="160">
                <template #default="scope">
                    <el-input v-model="scope.row.fk" size="small" placeholder="例: users.id" />
                </template>
            </el-table-column>

             <el-table-column label="注释">
                <template #default="scope">
                     <el-input v-model="scope.row.comment" />
                </template>
            </el-table-column>

            <el-table-column fixed="right" label="操作" width="100" align="center">
               <template #default="scope">
                 <el-button link type="danger" @click="delRow(scope.$index)">移除</el-button>
               </template>
            </el-table-column>
           </el-table>
      </div>
      <div v-else class="empty-state">
          请从表管理页面进入此页面，或在 URL 中指定 db 和 table 参数
          <br><br>
          <el-button type="primary" @click="$router.push('/table')">返回表管理</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Sort, ArrowDown } from '@element-plus/icons-vue'
import { getTableDetail, updateTableStructure } from '../api/dbms'

const route = useRoute()
const router = useRouter()
const currentDb = ref('')
const currentTable = ref('')
const saving = ref(false)

// Generate unique row id for tracking
const genId = () => Math.random().toString(36).substr(2, 9)

const columns = ref([])

const splitType = (typeText) => {
    const match = String(typeText || '').match(/^([A-Za-z_]+)(?:\(([^)]+)\))?$/)
    if (!match) {
        return { type: typeText || 'VARCHAR', length: '' }
    }
    return {
        type: match[1].toUpperCase(),
        length: match[2] || ''
    }
}

onMounted(async () => {
   if(route.query.db && route.query.table) {
      currentDb.value = route.query.db
      currentTable.value = route.query.table
      await loadColumns()
   }
})

async function loadColumns() {
  try {
    const response = await getTableDetail(currentDb.value, currentTable.value)
    const list = response?.data?.columns || []
    columns.value = list.map((column) => {
        const typeParts = splitType(column.type)
        return {
           id: genId(),
           name: column.name,
           type: typeParts.type,
           length: typeParts.length,
           nn: Boolean(column.nn),
           pk: Boolean(column.pk),
           uq: Boolean(column.uq),
           ai: false,
           defaultVal: column.default || '',
           check: '',
           fk: '',
           comment: ''
        }
    })
  } catch (error) {
    ElMessage.error(error.message || '加载字段失败')
  }
}

// Validation helpers
const needsLength = (type) => ['VARCHAR', 'CHAR', 'DECIMAL'].includes(type?.toUpperCase())
const isInteger = (type) => ['INT', 'BIGINT', 'TINYINT'].includes(type?.toUpperCase())

const handlePkChange = (row, isPk) => {
    if (isPk) {
        row.nn = true
        row.uq = true  // PK 自动附带 UNIQUE
    } else {
        row.ai = false
        row.uq = false
    }
}

const addEmptyRow = () => {
    columns.value.push({
        id: genId(),
        name: '',
        type: 'VARCHAR',
        length: '255',
        nn: false,
        pk: false,
        uq: false,
        ai: false,
        defaultVal: '',
        check: '',
        fk: '',
        comment: ''
    })
}

const delRow = (index) => {
    columns.value.splice(index, 1)
}

const saveChanges = async () => {
    for (const col of columns.value) {
        if (!col.name.trim()) return ElMessage.error('字段名不能为空')
        if (needsLength(col.type) && !col.length) return ElMessage.error(`${col.name} 字段需要指定长度`)
    }

    saving.value = true
    try {
        const payload = {
            name: currentTable.value,
            columns: columns.value.map((col) => ({
                name: col.name.trim(),
                type: col.type,
                length: col.length ? Number(col.length) : undefined,
                nullable: !Boolean(col.nn),
                pk: Boolean(col.pk),
                uq: Boolean(col.pk) || Boolean(col.uq),
                checkExpression: col.check ? `CHECK (${col.name} ${col.check})` : undefined,
                foreignKeyTable: col.fk ? col.fk.split('.')[0] : undefined,
                foreignKeyColumn: col.fk ? col.fk.split('.')[1] : undefined
            }))
        }
        await updateTableStructure(currentDb.value, currentTable.value, payload)
        ElMessage.success('表结构变更保存成功')
        router.push({ path: '/table', query: { db: currentDb.value } })
    } catch (e) {
        ElMessage.error(e.message || '保存字段结构失败')
    } finally {
        saving.value = false
    }
}

// Advanced Actions
const importJson = () => {
    ElMessageBox.prompt('贴入字段定义 JSON', '批量导入', {
        confirmButtonText: '导入',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '[{"name": "col1", "type": "INT"}]'
    }).then(({ value }) => {
        try {
            const arr = JSON.parse(value)
            if(Array.isArray(arr)) {
                const mapped = arr.map(item => ({
                   id: genId(),
                   name: item.name || 'new_col',
                   type: item.type || 'VARCHAR',
                   length: item.length || (item.type === 'VARCHAR' ? '255' : ''),
                   nn: !!item.nn, pk: !!item.pk, uq: !!item.uq, ai: !!item.ai,
                   defaultVal: item.default || '', comment: item.comment || ''
                }))
                columns.value.push(...mapped)
                ElMessage.success(`成功导入 ${mapped.length} 个字段`)
            }
        } catch(e) {
            ElMessage.error('JSON 解析失败')
        }
    }).catch(() => {})
}

const genTemplate = () => {
    const tmpl = [
        { id: genId(), name: 'create_time', type: 'DATETIME', length: '', nn: false, pk: false, uq: false, ai: false, defaultVal: 'CURRENT_TIMESTAMP', comment: '创建时间' },
        { id: genId(), name: 'update_time', type: 'DATETIME', length: '', nn: false, pk: false, uq: false, ai: false, defaultVal: 'CURRENT_TIMESTAMP', comment: '更新时间' },
        { id: genId(), name: 'is_deleted', type: 'INT', length: '1', nn: false, pk: false, uq: false, ai: false, defaultVal: '0', comment: '逻辑删除标识' }
    ]
    columns.value.push(...tmpl)
    ElMessage.success('已追加审计字段模板')
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
.is-error :deep(.el-input__wrapper) {
    box-shadow: 0 0 0 1px var(--el-color-danger) inset;
}
</style>
