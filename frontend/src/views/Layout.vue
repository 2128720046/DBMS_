<template>
  <el-container class="workspace-container">
    <el-header class="global-header">
      <div class="logo-section">
        <div class="g-logo">G</div>
        <span class="app-title">DBMS Studio</span>
      </div>
      
      <div class="toolbar-actions">
        <el-button-group>
          <el-button icon="Plus" plain @click="$router.push('/database')">连接</el-button>
          <el-button icon="Refresh" plain @click="refreshTree">刷新</el-button>
        </el-button-group>
        
        <el-divider direction="vertical" />
        
        <el-button-group>
          <el-button icon="Coin" @click="$router.push('/database')">数据库</el-button>
          <el-button icon="Grid" @click="$router.push('/table')">数据表</el-button>
          <el-button icon="Document" @click="$router.push('/record')">数据浏览</el-button>
          <el-button icon="Monitor" @click="$router.push('/sql')">SQL终端</el-button>
        </el-button-group>
      </div>

      <div class="user-profile">
        <el-dropdown>
          <span class="user-link">
            Admin <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="$router.push('/settings')">系统设置</el-dropdown-item>
              <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-container class="lower-container">
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

      <el-main class="main-workspace">
        <div class="router-wrapper">
          <router-view v-slot="{ Component }">
            <transition name="fade-slide" mode="out-in">
              <component :is="Component" />
            </transition>
          </router-view>
        </div>
        
        <footer class="status-bar">
          <div class="log-preview">
            <el-icon><InfoFilled /></el-icon>
            <span>就绪. 当前路由: {{ currentRouteTitle }}</span>
          </div>
          <div class="connection-info">
            <span>Localhost (H2)</span>
            <el-divider direction="vertical" />
            <span>UTF-8</span>
          </div>
        </footer>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, onMounted, watch, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { listDatabases, listTables } from '../api/dbms'
import { ArrowDown, InfoFilled, Coin, Grid, Plus, Refresh, Document, Monitor } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const filterText = ref('')
const treeRef = ref()
const treeData = ref([])

const defaultProps = { label: 'name', children: 'children' }
const currentRouteTitle = computed(() => route.meta?.title || '控制台')

const handleLogout = () => {
  localStorage.removeItem('dbms-token')
  localStorage.removeItem('dbms-user')
  router.push('/login')
}

const refreshTree = async () => {
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
  refreshTree()
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

.router-wrapper {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
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

/* 路由平滑过渡动画 */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
}
.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(10px);
}
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>