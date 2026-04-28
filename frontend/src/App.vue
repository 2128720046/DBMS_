<template>
  <el-config-provider namespace="el">
    <router-view></router-view>
  </el-config-provider>
</template>

<script setup>
import { ElConfigProvider } from 'element-plus'
</script>

<style>
:root {
  --el-color-primary: #007AFF;
  --el-color-primary-light-3: #47A1FF;
  --el-color-primary-light-9: #E6F2FF;
  --el-border-radius-base: 8px;
  --bg-color: #F5F5F7;
  --sidebar-bg: #FFFFFF;
}

body {
  margin: 0;
  padding: 0;
  background-color: var(--bg-color);
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "Helvetica Neue", Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  overflow: hidden;
}

#app {
  height: 100vh;
  width: 100vw;
}

/* 统一卡片极致无边框质感 */
.el-card {
  border: none !important;
  border-radius: 12px !important;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04) !important;
  transition: transform 0.3s cubic-bezier(0.25, 0.8, 0.25, 1), box-shadow 0.3s ease;
}

/* 滚动条美化 */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}
::-webkit-scrollbar-thumb {
  background: rgba(0,0,0,0.15);
  border-radius: 10px;
}
::-webkit-scrollbar-track {
  background: transparent;
}
</style>