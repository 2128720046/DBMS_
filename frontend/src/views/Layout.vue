<template>
  <el-container class="layout-container">
    <el-aside width="200px" class="aside">
      <div class="logo">DBMS Admin</div>
      <el-menu
        :default-active="activeMenu"
        class="el-menu-vertical"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataBoard /></el-icon>
          <span>首页 / 仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/database">
          <el-icon><Coin /></el-icon>
          <span>数据库管理</span>
        </el-menu-item>
        <el-menu-item index="/table">
          <el-icon><Grid /></el-icon>
          <span>表管理</span>
        </el-menu-item>
        <el-menu-item index="/record">
          <el-icon><Document /></el-icon>
          <span>记录管理</span>
        </el-menu-item>
        <el-menu-item index="/sql">
          <el-icon><Monitor /></el-icon>
          <span>SQL 控制台</span>
        </el-menu-item>
        <el-menu-item index="/backup">
          <el-icon><CopyDocument /></el-icon>
          <span>备份恢复</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-breadcrumb" style="display: flex; align-items: center; gap: 15px;">
          <el-button link icon="Back" @click="handleBack" v-if="route.path !== '/dashboard'">返回</el-button>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentRouteTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-user">
          <el-dropdown>
            <span class="el-dropdown-link">
              Admin <el-icon class="el-icon--right"><arrow-down /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main-content">
        <router-view></router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { DataBoard, Coin, Grid, Document, Monitor, CopyDocument, ArrowDown, Back } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const activeMenu = computed(() => route.path)
const currentRouteTitle = computed(() => route.meta?.title || '')

const handleBack = () => {
  router.go(-1)
}

const handleLogout = () => {
  router.push('/login')
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.aside {
  background-color: #304156;
  color: #fff;
}

.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  font-size: 20px;
  font-weight: bold;
  background-color: #2b3643;
}

.el-menu-vertical {
  border-right: none;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #dcdfe6;
  background-color: #fff;
}

.header-user {
  cursor: pointer;
}

.main-content {
  background-color: #f0f2f5;
  padding: 20px;
}
</style>
