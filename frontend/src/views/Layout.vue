<template>
  <el-container class="workspace-container">
    <el-header class="global-header">
      <div class="logo-section">
        <div class="g-logo">G</div>
        <span class="app-title">DBMS Studio</span>
      </div>

      <div class="toolbar-actions">
        <el-button-group>
          <el-button icon="Plus" plain @click="showLoginDialog">连接</el-button>
          <el-button icon="Refresh" plain @click="refreshTree">刷新</el-button>
        </el-button-group>

        <el-divider direction="vertical" />

        <el-button-group>
          <el-button icon="Coin" @click="requireLogin('/database')">数据库</el-button>
          <el-button icon="Grid" @click="requireLogin('/table')">数据表</el-button>
          <el-button icon="Document" @click="requireLogin('/record')">数据浏览</el-button>
          <el-button icon="Monitor" @click="requireLogin('/sql')">SQL终端</el-button>
          <el-button icon="DocumentCopy" @click="goBackupPage">备份恢复</el-button>
          <el-button icon="Right" @click="requireLogin('/transaction')">事务</el-button>
        </el-button-group>
      </div>

      <div class="user-profile">
        <template v-if="isLoggedIn">
          <el-dropdown>
            <span class="user-link">
              <el-tag :type="isAdmin ? 'danger' : 'info'" size="small" effect="plain" style="margin-right: 6px;">
                {{ isAdmin ? '管理员' : '用户' }}
              </el-tag>
              {{ displayName }} <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-if="isAdmin" @click="$router.push('/users')">
                  <el-icon><User /></el-icon> 用户权限管理
                </el-dropdown-item>
                <el-dropdown-item v-if="isAdmin" @click="$router.push('/clients')">
                  <el-icon><Connection /></el-icon> 客户端会话
                </el-dropdown-item>
                <el-dropdown-item @click="$router.push('/settings')">
                  <el-icon><Setting /></el-icon> 系统设置
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <el-button type="primary" size="small" @click="showLoginDialog">登录 / 注册</el-button>
        </template>
      </div>
    </el-header>

    <el-container class="lower-container">
      <!-- 未登录时显示提示，隐藏数据库树 -->
      <template v-if="isLoggedIn">
        <el-aside width="260px" class="object-tree-aside">
          <div class="tree-filter">
            <el-input v-model="filterText" placeholder="搜索数据库/表..." prefix-icon="Search" clearable size="small" />
          </div>
          <el-scrollbar>
            <el-tree
              ref="treeRef"
              :data="treeData"
              :props="defaultProps"
              highlight-current
              :filter-node-method="filterNode"
              @node-click="handleNodeClick"
            >
              <template #default="{ node, data }">
                <span class="custom-tree-node">
                  <el-icon v-if="data.type === 'db'" color="#5c6b77"><Coin /></el-icon>
                  <el-icon v-else-if="data.type === 'table'" color="#8ba3b8"><Grid /></el-icon>
                  <span class="node-label">{{ node.label }}</span>
                  <span v-if="data.size" class="node-extra">{{ data.size }}</span>
                </span>
              </template>
            </el-tree>
          </el-scrollbar>
        </el-aside>
      </template>
      <template v-else>
        <el-aside width="260px" class="object-tree-aside">
          <div class="login-prompt">
            <el-icon :size="36" color="#d0d5dd"><Lock /></el-icon>
            <p>请先登录以查看数据库</p>
            <el-button type="primary" size="small" @click="showLoginDialog">前往登录</el-button>
          </div>
        </el-aside>
      </template>

      <el-main class="main-workspace">
        <div class="tags-view-container" v-if="visitedViews.length > 0">
          <el-tabs
            v-model="activeTab"
            type="card"
            closable
            @tab-click="onTabClick"
            @tab-remove="onTabRemove"
            class="custom-tabs"
          >
            <el-tab-pane
              v-for="tab in visitedViews"
              :key="tab.fullPath"
              :label="tab.title"
              :name="tab.fullPath"
            />
          </el-tabs>
        </div>

        <div class="router-wrapper" :class="{ 'has-tabs': visitedViews.length > 0 }">
          <router-view v-slot="{ Component, route }">
            <keep-alive>
              <component :is="Component" :key="route.fullPath" />
            </keep-alive>
          </router-view>
        </div>

        <footer class="status-bar">
          <div class="log-preview">
            <el-icon><InfoFilled /></el-icon>
            <span>就绪. 当前路由: {{ currentRouteTitle }}</span>
          </div>
          <div class="connection-info">
            <span>{{ isLoggedIn ? `已登录: ${displayName}` : '未登录' }}</span>
            <el-divider direction="vertical" />
            <span>DBMS</span>
          </div>
        </footer>
      </el-main>
    </el-container>

    <!-- 登录对话框 -->
    <el-dialog v-model="loginDialogVisible" title="登录" width="400px" align-center :close-on-click-modal="false">
      <el-form :model="loginForm" label-position="top" @keyup.enter="handleLoginSubmit">
        <el-form-item label="用户名">
          <el-input v-model="loginForm.username" placeholder="输入用户名" :prefix-icon="UserIcon" clearable />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="loginForm.password" type="password" show-password placeholder="输入密码" :prefix-icon="Lock" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div style="display: flex; justify-content: flex-end; gap: 12px;">
          <el-button @click="loginDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="loginLoading" @click="handleLoginSubmit">登录</el-button>
        </div>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { listDatabases, listTables, login, disconnectSelf } from '../api/dbms'
import { ElMessage } from 'element-plus'
import {
  ArrowDown, InfoFilled, Coin, Grid, Plus, Refresh, Document, Monitor,
  DocumentCopy, User, Connection, Setting, SwitchButton, User as UserIcon, Lock, Right
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const filterText = ref('')
const treeRef = ref()
const treeData = ref([])

const defaultProps = { label: 'name', children: 'children' }
const currentRouteTitle = computed(() => route.meta?.title || '控制台')

// =========== 登录状态（使用 ref 而非 computed，确保登录后即时更新） ===========

const isLoggedIn = ref(!!sessionStorage.getItem('dbms-token'))
const isAdmin = ref(checkIsAdmin())
const displayName = ref(getDisplayName())

function checkIsAdmin() {
  try {
    const userStr = sessionStorage.getItem('dbms-user')
    if (userStr) return JSON.parse(userStr).username === 'admin'
  } catch (e) {}
  return false
}

function getDisplayName() {
  try {
    const userStr = sessionStorage.getItem('dbms-user')
    if (userStr) {
      const user = JSON.parse(userStr)
      return user.username || '未知'
    }
  } catch (e) {}
  return '未登录'
}

/** 登录/登出后同步更新 UI 状态 */
function syncLoginState() {
  isLoggedIn.value = !!sessionStorage.getItem('dbms-token')
  isAdmin.value = checkIsAdmin()
  displayName.value = getDisplayName()
  // 登录成功后刷新数据库树
  if (isLoggedIn.value) {
    refreshTree()
    // 通知所有 keep-alive 页面刷新数据
    window.dispatchEvent(new Event('dbms-login-changed'))
  }
}

// =========== 导航按钮需登录才能访问 ===========

const requireLogin = (path) => {
  if (!isLoggedIn.value) {
    ElMessage.warning('请先登录')
    showLoginDialog()
    return
  }
  router.push(path)
}

const goBackupPage = () => {
  if (!isLoggedIn.value) {
    ElMessage.warning('请先登录')
    showLoginDialog()
    return
  }
  const query = route.query?.db ? { db: route.query.db } : {}
  router.push({ path: '/backup', query })
}

// =========== 登录对话框 ===========

const loginDialogVisible = ref(false)
const loginLoading = ref(false)
const loginForm = ref({ username: '', password: '' })

const showLoginDialog = () => {
  loginForm.value = { username: '', password: '' }
  loginDialogVisible.value = true
}

const handleLoginSubmit = async () => {
  if (!loginForm.value.username || !loginForm.value.password) {
    return ElMessage.warning('用户名和密码不能为空')
  }

  loginLoading.value = true
  try {
    const res = await login({ username: loginForm.value.username, password: loginForm.value.password })
    const payload = res?.data || {}
    if (payload.token) {
      sessionStorage.setItem('dbms-token', payload.token)
    }
    sessionStorage.setItem('dbms-user', JSON.stringify(payload))

    loginDialogVisible.value = false
    syncLoginState()
    ElMessage.success(`登录成功，欢迎 ${loginForm.value.username}`)
  } catch (error) {
    ElMessage.error(error.message || '登录失败')
  } finally {
    loginLoading.value = false
  }
}

// =========== Tabs ===========

const visitedViews = ref([])
const activeTab = ref('')

watch(route, (newRoute) => {
  let title = newRoute.meta?.title || '主页'
  if (newRoute.query.table) {
    title = `表: ${newRoute.query.table}`
  } else if (newRoute.query.db && newRoute.path === '/table') {
    title = `库: ${newRoute.query.db}`
  } else if (newRoute.path === '/sql') {
    title = 'SQL控制台'
  }

  const exists = visitedViews.value.find(v => v.fullPath === newRoute.fullPath)
  if (!exists && newRoute.path !== '/') {
    visitedViews.value.push({
      fullPath: newRoute.fullPath,
      name: newRoute.name,
      title
    })
  }
  activeTab.value = newRoute.fullPath
}, { immediate: true })

const onTabClick = (tabPane) => {
  router.push(tabPane.paneName)
}

const onTabRemove = (targetName) => {
  const tabs = visitedViews.value
  let current = activeTab.value
  if (current === targetName) {
    tabs.forEach((tab, index) => {
      if (tab.fullPath === targetName) {
        const nextTab = tabs[index + 1] || tabs[index - 1]
        current = nextTab ? nextTab.fullPath : '/database'
      }
    })
  }
  activeTab.value = current
  visitedViews.value = tabs.filter(tab => tab.fullPath !== targetName)
  router.push(current)
}

const handleLogout = async () => {
  try {
    await disconnectSelf()
  } catch (e) {
    // 即使后端断开失败，本地也要清理
  }
  sessionStorage.removeItem('dbms-token')
  sessionStorage.removeItem('dbms-user')
  treeData.value = []
  syncLoginState()
  ElMessage.success('已退出登录')
  // 重新弹出登录框
  showLoginDialog()
}

// =========== 数据库树 ===========

const refreshTree = async () => {
  if (!sessionStorage.getItem('dbms-token')) return
  try {
    const res = await listDatabases()
    const dbs = res.data || []
    const tree = []
    for (const db of dbs) {
      const tablesRes = await listTables(db.name)
      tree.push({
        name: db.name,
        type: 'db',
        children: (tablesRes.data || []).map(t => ({
          name: t.name,
          type: 'table',
          dbName: db.name,
          size: t.rows ? `${t.rows} 行` : ''
        }))
      })
    }
    treeData.value = tree
  } catch (error) {
    console.error("加载树失败", error)
  }
}

const handleTreeRefresh = () => { refreshTree() }

const filterNode = (value, data) => {
  if (!value) return true
  return data.name.includes(value)
}

watch(filterText, (val) => {
  treeRef.value?.filter(val)
})

const handleNodeClick = (data) => {
  if (data.type === 'table') {
    router.push({ path: '/record', query: { db: data.dbName, table: data.name } })
  } else if (data.type === 'db') {
    router.push({ path: '/table', query: { db: data.name } })
  }
}

onMounted(() => {
  if (!isLoggedIn.value) {
    showLoginDialog()
  }
  refreshTree()
  window.addEventListener('dbms-tree-refresh', handleTreeRefresh)
})

onBeforeUnmount(() => {
  window.removeEventListener('dbms-tree-refresh', handleTreeRefresh)
})
</script>

<style scoped>
.workspace-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.global-header {
  background: #FFFFFF;
  border-bottom: 1px solid #EBECEF;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  height: 56px;
  z-index: 10;
}

.logo-section {
  display: flex;
  align-items: center;
  gap: 12px;
}

.g-logo {
  background: var(--el-color-primary);
  color: white;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 16px;
}

.app-title {
  font-weight: 600;
  font-size: 16px;
  color: #1D1D1F;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-link {
  cursor: pointer;
  color: #1D1D1F;
  font-weight: 500;
  display: flex;
  align-items: center;
}

.lower-container {
  flex: 1;
  overflow: hidden;
}

.object-tree-aside {
  background: #FFFFFF;
  border-right: 1px solid #EBECEF;
  display: flex;
  flex-direction: column;
}

.tree-filter {
  padding: 12px;
  border-bottom: 1px solid #F0F0F0;
}

.custom-tree-node {
  display: flex;
  align-items: center;
  font-size: 13px;
  width: 100%;
}

.node-label {
  margin-left: 8px;
  flex: 1;
  color: #333;
}

.node-extra {
  font-size: 11px;
  color: #A0A0A0;
  margin-right: 12px;
}

.main-workspace {
  padding: 0;
  display: flex;
  flex-direction: column;
  background-color: var(--bg-color);
}

.tags-view-container {
  height: 36px;
  background: #fdfdfd;
  border-bottom: 1px solid #ebecef;
}

.custom-tabs :deep(.el-tabs__header) {
  margin: 0;
  border-bottom: none;
}

.custom-tabs :deep(.el-tabs__nav) {
  border: none !important;
  border-top: none !important;
  border-radius: 0 !important;
}

.custom-tabs :deep(.el-tabs__item) {
  height: 36px;
  line-height: 36px;
  border-right: 1px solid #ebecef;
  border-bottom: none;
  font-size: 13px;
}

.custom-tabs :deep(.el-tabs__item.is-active) {
  background-color: #fff;
  border-bottom: 2px solid var(--el-color-primary);
}

.router-wrapper {
  flex: 1;
  padding: 10px;
  overflow-y: auto;
  overflow-x: hidden;
  height: calc(100% - 66px);
  position: relative;
}

.status-bar {
  height: 30px;
  background: #FFFFFF;
  border-top: 1px solid #EBECEF;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  font-size: 12px;
  color: #6B7280;
}

.log-preview, .connection-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.login-prompt {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 12px;
  color: #909399;
  padding: 40px 20px;
  text-align: center;
}

.login-prompt p {
  margin: 0;
  font-size: 14px;
}

@media (max-width: 960px) {
  .toolbar-actions .el-button-group .el-button {
    padding: 8px 10px;
    font-size: 12px;
  }
}
</style>
