<template>
  <div>
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>系统设置</span>
        </div>
      </template>

      <el-form :model="form" label-width="150px">
        <el-form-item label="后端 API 地址">
          <el-input v-model="form.apiUrl" style="width: 300px" />
        </el-form-item>
        <el-form-item label="查询超时时间 (秒)">
          <el-input-number v-model="form.timeout" :min="1" :max="300" />
        </el-form-item>
        <el-form-item label="登录有效期 (天)">
          <el-input-number v-model="form.tokenExp" :min="1" :max="30" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveSettings">保存配置</el-button>
          <el-button @click="resetSettings">恢复默认</el-button>
        </el-form-item>
      </el-form>

      <el-divider />

      <h3>系统运维</h3>
      <div style="display: flex; gap: 20px;">
        <el-button type="danger" @click="clearCache">清理前端缓存</el-button>
        <el-button type="info" @click="exportLogs">导出系统日志</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const form = ref({
  apiUrl: 'http://localhost:8080/api',
  timeout: 30,
  tokenExp: 7
})

const saveSettings = () => {
    ElMessage.success('配置已保存 (Mock)')
}

const resetSettings = () => {
    form.value = {
        apiUrl: 'http://localhost:8080/api',
        timeout: 30,
        tokenExp: 7
    }
    ElMessage.success('已恢复默认配置')
}

const clearCache = () => {
    localStorage.clear()
    sessionStorage.clear()
    ElMessage.success('前端缓存已清理，刷新页面生效')
}

const exportLogs = () => {
    ElMessage.success('正在准备日志文件 (Mock)... 预计耗时 1 分钟')
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
