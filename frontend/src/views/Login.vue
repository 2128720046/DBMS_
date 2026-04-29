<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <div class="logo">DBMS 404</div>
        <div class="subtitle">{{ isLogin ? '登录到您的账户' : '创建新账户' }}</div>
      </div>
      
      <div class="login-body">
        <el-form :model="form" :rules="rules" ref="formRef" @keyup.enter="handleSubmit" size="large">
          <el-form-item prop="username">
            <el-input 
               v-model="form.username" 
               placeholder="请输入账号" 
               :prefix-icon="User" 
               clearable 
            />
          </el-form-item>
          
          <el-form-item prop="password">
            <el-input 
              v-model="form.password" 
              type="password" 
              placeholder="请输入密码" 
              :prefix-icon="Lock" 
              show-password 
            />
          </el-form-item>

          <el-form-item prop="confirmPassword" v-if="!isLogin">
            <el-input 
              v-model="form.confirmPassword" 
              type="password" 
              placeholder="请确认密码" 
              :prefix-icon="Lock" 
              show-password 
            />
          </el-form-item>

          <div class="form-actions" v-if="isLogin">
            <el-checkbox v-model="form.remember">记住账号</el-checkbox>
            <el-link type="primary" :underline="false">忘记密码？</el-link>
          </div>

          <el-form-item>
            <el-button type="primary" class="submit-btn" :loading="loading" @click="handleSubmit">
              {{ isLogin ? '登 录' : '注 册' }}
            </el-button>
          </el-form-item>
        </el-form>

        <div class="toggle-mode">
          {{ isLogin ? "还没有账号？" : "已有账号？" }}
          <el-link type="primary" @click="toggleMode" :underline="false">
            {{ isLogin ? '立即注册' : '去登录' }}
          </el-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login, register } from '../api/dbms'

const router = useRouter()
const formRef = ref(null)
const isLogin = ref(true)
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  remember: false
})

const validateConfirmPassword = (rule, value, callback) => {
  if (!isLogin.value && value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = reactive({
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 3, max: 20, message: '长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '长度在 6 到 20 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
})

onMounted(() => {
  const savedUser = localStorage.getItem('dbms-saved-user')
  if (savedUser) {
    try {
      const data = JSON.parse(savedUser)
      form.username = data.username
      form.password = data.password
      form.remember = true
    } catch(e) {}
  }
})

const toggleMode = () => {
  isLogin.value = !isLogin.value
  formRef.value?.resetFields()
}

const handleSubmit = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        if (isLogin.value) {
          const payloadData = {
            username: form.username,
            password: form.password
          }
          const res = await login(payloadData)
          const payload = res?.data || {}
          
          if (form.remember) {
            localStorage.setItem('dbms-saved-user', JSON.stringify({
              username: form.username,
              password: form.password
            }))
          } else {
            localStorage.removeItem('dbms-saved-user')
          }
          
          if (payload.token) {
             localStorage.setItem('dbms-token', payload.token)
          }
          localStorage.setItem('dbms-user', JSON.stringify(payload))
          
          ElMessage.success('登录成功')
          router.push('/dashboard')
        } else {
          await register({
            username: form.username,
            password: form.password
          })
          
          ElMessage.success('注册成功，请使用新账号登录')
          isLogin.value = true
        }
      } catch (error) {
        ElMessage.error(error.message || (isLogin.value ? '登录失败，请检查账号密码' : '注册失败，账号可能已存在'))
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: #f0f2f5;
  background-image: radial-gradient(#e5e7eb 1px, transparent 1px);
  background-size: 20px 20px;
}

.login-box {
  width: 100%;
  max-width: 420px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  overflow: hidden;
  padding: 40px;
  transition: transform 0.3s ease;
}

.login-box:hover {
  transform: translateY(-2px);
}

.login-header {
  text-align: center;
  margin-bottom: 40px;
}

.logo {
  font-size: 32px;
  font-weight: 800;
  color: #1f2937;
  letter-spacing: -0.5px;
  margin-bottom: 8px;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.subtitle {
  color: #6b7280;
  font-size: 15px;
}

.login-body {
  width: 100%;
}

.form-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  margin-top: -8px;
}

.submit-btn {
  width: 100%;
  font-size: 16px;
  font-weight: 500;
  letter-spacing: 0.5px;
  height: 44px;
  border-radius: 6px;
  margin-top: 8px;
}

.toggle-mode {
  text-align: center;
  margin-top: 24px;
  color: #6b7280;
  font-size: 14px;
}

.toggle-mode .el-link {
  margin-left: 6px;
  font-weight: 500;
}

:deep(.el-input__wrapper) {
  background-color: #f9fafb;
  box-shadow: 0 0 0 1px #e5e7eb inset;
  border-radius: 6px;
  transition: all 0.2s ease;
}

:deep(.el-input__wrapper.is-focus) {
  background-color: #ffffff;
  box-shadow: 0 0 0 2px #3b82f6 inset;
}

:deep(.el-input__inner) {
  height: 44px;
}

:deep(.el-form-item) {
  margin-bottom: 24px;
}

</style>
