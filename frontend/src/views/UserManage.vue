<template>
  <div class="user-manage-page">
    <el-card class="hero-card" shadow="never">
      <div class="hero-grid">
        <div>
          <h2 class="page-title">用户权限管理</h2>
          <p class="page-desc">管理系统用户、密码与访问权限。仅管理员可访问此页面</p>
        </div>
        <div class="hero-actions">
          <el-button type="primary" @click="showCreateDialog" icon="Plus">新建用户</el-button>
          <el-button type="success" @click="fetchAllUsers" icon="Refresh">刷新列表</el-button>
        </div>
      </div>
      <div class="hero-stats">
        <div class="stat-item">
          <span class="stat-label">当前用户</span>
          <span class="stat-value">{{ currentUser }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">注册用户数</span>
          <span class="stat-value">{{ users.length }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">连接状态</span>
          <span class="stat-value success">已连接</span>
        </div>
      </div>
    </el-card>

    <el-card class="content-card" shadow="never">
      <template #header>
        <div class="section-header">
          <span>用户列表</span>
          <span class="section-tip">从后端获取所有已注册的用户</span>
        </div>
      </template>
      <el-table :data="users" v-loading="loadingUsers" border empty-text="暂无用户" style="width: 100%">
        <el-table-column prop="USERNAME" label="用户名" min-width="180" />
        <el-table-column label="角色" width="140">
          <template #default="scope">
            <el-tag :type="scope.row.USERNAME === 'admin' ? 'danger' : 'info'">
              {{ scope.row.ROLE || (scope.row.USERNAME === 'admin' ? '管理员' : '普通用户') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="权限" width="120">
          <template #default="scope">
            <el-tag v-if="scope.row.USERNAME === 'admin'" type="warning">全部权限</el-tag>
            <el-button v-else link type="primary" size="small" @click="lookupGrants(scope.row)">查看</el-button>
          </template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" width="300" align="center">
          <template #default="scope">
            <el-button link type="primary" size="small" @click="lookupGrants(scope.row)">查看权限</el-button>
            <el-button link type="warning" size="small" @click="showAlterDialog(scope.row)" :disabled="scope.row.USERNAME === 'admin'">改密码</el-button>
            <el-button link type="success" size="small" @click="showGrantDialog(scope.row)" :disabled="scope.row.USERNAME === 'admin'">授权</el-button>
            <el-button link type="danger" size="small" @click="handleDropUser(scope.row)" :disabled="scope.row.USERNAME === 'admin'">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="selectedUser && permissions.length > 0" class="content-card" shadow="never">
      <template #header>
        <div class="section-header">
          <span>用户「{{ selectedUser }}」的权限列表</span>
          <el-button link type="danger" size="small" @click="permissions = []; selectedUser = ''">关闭</el-button>
        </div>
      </template>
      <el-table :data="permissions" border style="width: 100%">
        <el-table-column prop="privilege" label="权限" width="140">
          <template #default="scope">
            <el-tag>{{ scope.row.privilege }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="object" label="作用对象" min-width="200" />
        <el-table-column fixed="right" label="操作" width="100" align="center">
          <template #default="scope">
            <el-button link type="danger" size="small" @click="handleRevoke(scope.row)">撤销</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createDialogVisible" title="新建用户" width="420px" align-center>
      <el-form :model="createForm" label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="createForm.username" placeholder="输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="createForm.password" type="password" show-password placeholder="输入密码" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="createForm.confirmPassword" type="password" show-password placeholder="再次输入密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creatingUser" @click="handleCreateUser">确定创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="alterDialogVisible" title="修改用户密码" width="420px" align-center>
      <el-form :model="alterForm" label-position="top">
        <el-form-item label="用户名">
          <el-input :model-value="alterForm.username" disabled />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="alterForm.password" type="password" show-password placeholder="输入新密码" />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="alterForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="alterDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="alteringUser" @click="handleAlterUser">确定修改</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="grantDialogVisible" title="授予权限" width="480px" align-center>
      <el-form :model="grantForm" label-position="top">
        <el-form-item label="目标用户">
          <el-input :model-value="grantForm.username" disabled />
        </el-form-item>
        <el-form-item label="权限类型">
          <el-select v-model="grantForm.privilege" placeholder="选择权限" style="width: 100%">
            <el-option v-for="p in privilegeOptions" :key="p.value" :label="p.label" :value="p.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="作用对象">
          <el-input v-model="grantForm.objectName" placeholder="例如: DEMO_DB.student 或 *.*" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="grantDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="granting" @click="handleGrant">确定授权</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  showGrants as apiShowGrants, showUsers, createNewUser,
  dropExistingUser, alterUserPassword,
  grantPrivilege, revokePrivilege
} from '../api/dbms'

const currentUser = ref('')
const users = ref([])
const loadingUsers = ref(false)
const permissions = ref([])
const selectedUser = ref('')

const privilegeOptions = [
  { label: 'SELECT (查询)', value: 'SELECT' },
  { label: 'INSERT (插入)', value: 'INSERT' },
  { label: 'UPDATE (更新)', value: 'UPDATE' },
  { label: 'DELETE (删除)', value: 'DELETE' },
  { label: 'CREATE (创建)', value: 'CREATE' },
  { label: 'DROP (删除对象)', value: 'DROP' },
  { label: 'ALTER (修改)', value: 'ALTER' },
  { label: 'INDEX (索引)', value: 'INDEX' },
  { label: 'BACKUP (备份)', value: 'BACKUP' },
  { label: 'RESTORE (还原)', value: 'RESTORE' },
  { label: 'ALL PRIVILEGES (所有权限)', value: 'ALL PRIVILEGES' }
]

// Create dialog
const createDialogVisible = ref(false)
const creatingUser = ref(false)
const createForm = ref({ username: '', password: '', confirmPassword: '' })

// Alter dialog
const alterDialogVisible = ref(false)
const alteringUser = ref(false)
const alterForm = ref({ username: '', password: '', confirmPassword: '' })

// Grant dialog
const grantDialogVisible = ref(false)
const granting = ref(false)
const grantForm = ref({ username: '', privilege: 'SELECT', objectName: '*.*' })

const fetchAllUsers = async () => {
  loadingUsers.value = true
  try {
    const res = await showUsers()
    users.value = res?.data || []
  } catch (error) {
    ElMessage.error(error.message || '获取用户列表失败')
  } finally {
    loadingUsers.value = false
  }
}

const showCreateDialog = () => {
  createForm.value = { username: '', password: '', confirmPassword: '' }
  createDialogVisible.value = true
}

const handleCreateUser = async () => {
  if (!createForm.value.username || !createForm.value.password) {
    return ElMessage.warning('用户名和密码不能为空')
  }
  if (createForm.value.password !== createForm.value.confirmPassword) {
    return ElMessage.warning('两次输入的密码不一致')
  }
  creatingUser.value = true
  try {
    await createNewUser(createForm.value.username, createForm.value.password)
    createDialogVisible.value = false
    await fetchAllUsers()
    ElMessage.success(`用户 ${createForm.value.username} 创建成功（默认拥有 SELECT 权限）`)
  } catch (error) {
    ElMessage.error(error.message || '创建用户失败')
  } finally {
    creatingUser.value = false
  }
}

const showAlterDialog = (row) => {
  alterForm.value = { username: row.USERNAME, password: '', confirmPassword: '' }
  alterDialogVisible.value = true
}

const handleAlterUser = async () => {
  if (!alterForm.value.password) return ElMessage.warning('新密码不能为空')
  if (alterForm.value.password !== alterForm.value.confirmPassword) return ElMessage.warning('两次输入的密码不一致')
  alteringUser.value = true
  try {
    await alterUserPassword(alterForm.value.username, alterForm.value.password)
    alterDialogVisible.value = false
    ElMessage.success('密码修改成功')
  } catch (error) {
    ElMessage.error(error.message || '修改密码失败')
  } finally {
    alteringUser.value = false
  }
}

const handleDropUser = (row) => {
  ElMessageBox.confirm(
    `确定永久删除用户「${row.USERNAME}」吗？此操作不可撤销！`,
    '警告',
    { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    try {
      await dropExistingUser(row.USERNAME)
      await fetchAllUsers()
      ElMessage.success(`用户 ${row.USERNAME} 已删除`)
    } catch (error) {
      ElMessage.error(error.message || '删除用户失败')
    }
  }).catch(() => {})
}

const showGrantDialog = (row) => {
  grantForm.value = { username: row.USERNAME, privilege: 'SELECT', objectName: '*.*' }
  grantDialogVisible.value = true
}

const handleGrant = async () => {
  granting.value = true
  try {
    await grantPrivilege(grantForm.value.username, grantForm.value.privilege, grantForm.value.objectName)
    grantDialogVisible.value = false
    ElMessage.success('权限授予成功')
    if (selectedUser.value === grantForm.value.username) {
      await lookupGrants({ USERNAME: grantForm.value.username })
    }
  } catch (error) {
    ElMessage.error(error.message || '授权失败')
  } finally {
    granting.value = false
  }
}

const handleRevoke = async (perm) => {
  ElMessageBox.confirm(
    `确定撤销用户「${selectedUser.value}」的 ${perm.privilege} ON ${perm.object} 权限吗？`,
    '警告',
    { confirmButtonText: '确定撤销', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    try {
      await revokePrivilege(selectedUser.value, perm.privilege, perm.object)
      permissions.value = permissions.value.filter(p => !(p.privilege === perm.privilege && p.object === perm.object))
      ElMessage.success('权限已撤销')
    } catch (error) {
      ElMessage.error(error.message || '撤销权限失败')
    }
  }).catch(() => {})
}

const lookupGrants = async (row) => {
  selectedUser.value = row.USERNAME
  try {
    const res = await apiShowGrants(row.USERNAME)
    const grants = res?.data || []
    permissions.value = (grants[0]?.GRANTS || []).map(g => {
      const parts = String(g).split(' ON ')
      return { privilege: parts[0], object: parts[1] || '*.*' }
    })
    if (permissions.value.length === 0) {
      ElMessage.info('该用户暂无显式权限')
    }
  } catch (error) {
    ElMessage.error(error.message || '查询权限失败')
  }
}

/** 窗口聚焦或收到用户变更事件时自动刷新 */
const autoRefresh = () => { fetchAllUsers() }

onMounted(async () => {
  try {
    const userStr = sessionStorage.getItem('dbms-user')
    if (userStr) {
      const userData = JSON.parse(userStr)
      currentUser.value = userData.username || '未知'
    }
  } catch (e) {}
  await fetchAllUsers()
  // 窗口聚焦时刷新（多标签页切换场景）
  window.addEventListener('focus', autoRefresh)
  // 收到注册/创建用户事件时刷新
  window.addEventListener('dbms-users-changed', autoRefresh)
  window.addEventListener('dbms-login-changed', autoRefresh)
})

onBeforeUnmount(() => {
  window.removeEventListener('focus', autoRefresh)
  window.removeEventListener('dbms-users-changed', autoRefresh)
  window.removeEventListener('dbms-login-changed', autoRefresh)
})
</script>

<style scoped>
.user-manage-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 0;
}
.hero-card, .content-card {
  border-radius: 14px;
  border: 1px solid #e8eaef;
  background: linear-gradient(180deg, #ffffff 0%, #fbfcfe 100%);
}
.hero-grid {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: flex-end;
  flex-wrap: wrap;
}
.page-title {
  margin: 0;
  font-size: 28px;
  color: #111827;
}
.page-desc {
  margin: 10px 0 0;
  color: #6b7280;
  line-height: 1.7;
  max-width: 640px;
}
.hero-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.hero-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 20px;
}
.stat-item {
  padding: 14px 16px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #eef2f7;
}
.stat-label {
  display: block;
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 6px;
}
.stat-value {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}
.stat-value.success { color: #16a34a; }
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.section-tip { color: #9ca3af; font-size: 13px; }
:deep(.el-card__body) { padding: 20px; }
@media (max-width: 960px) {
  .hero-stats { grid-template-columns: 1fr; }
  .hero-actions { width: 100%; }
}
</style>
