<template>
  <div class="login-container">
    <el-card class="box-card login-card">
      <template #header>
        <div class="card-header">
          <span>DBMS系统登录</span>
        </div>
      </template>
      <el-form :model="loginForm" :rules="rules" ref="loginFormRef" label-width="80px">
        <el-form-item label="账号" prop="username">
          <el-input v-model="loginForm.username"></el-input>
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input type="password" v-model="loginForm.password" show-password></el-input>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="loginForm.remember">记住账号</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleLogin(loginFormRef)">登录</el-button>
          <el-button @click="resetForm(loginFormRef)">清空</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loginFormRef = ref(null)

const loginForm = reactive({
  username: '',
  password: '',
  remember: false
})

const rules = reactive({
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
})

const handleLogin = async (formEl) => {
  if (!formEl) return
  await formEl.validate((valid, fields) => {
    if (valid) {
      if(loginForm.username === 'admin' && loginForm.password === '123456') {
        ElMessage.success('登录成功')
        router.push('/dashboard')
      } else {
         ElMessage.error('账号或密码错误 (测试admin/123456)')
      }
    } else {
      console.log('error submit!', fields)
    }
  })
}

const resetForm = (formEl) => {
  if (!formEl) return
  formEl.resetFields()
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background-color: #2b3b4d;
}

.login-card {
  width: 400px;
}

.card-header {
  text-align: center;
  font-size: 20px;
  font-weight: bold;
}
</style>
