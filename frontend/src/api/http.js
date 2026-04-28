import axios from 'axios'

/**
 * Axios 实例：统一管理前端到后端的 HTTP 访问。
 * - baseURL 使用 /api，配合 Vite 代理转发到 Spring Boot。
 * - timeout 防止请求长时间无响应。
 */
const http = axios.create({
  baseURL: '/api',
  timeout: 10000
})

/**
 * 响应拦截器：
 * 直接返回协议体 { code, message, data }，减少页面层重复解包。
 */
http.interceptors.response.use(
  (response) => {
    const payload = response.data
    if (payload && typeof payload.code === 'number' && payload.code !== 200) {
      return Promise.reject(new Error(payload.message || '请求失败'))
    }
    return payload
  },
  (error) => {
    const message = error?.response?.data?.message || error.message || '网络请求失败'
    return Promise.reject(new Error(message))
  }
)

/**
 * 系统健康检查接口。
 * @returns {Promise<{code:number,message:string,data:object}>}
 */
export const getHealth = () => http.get('/system/health')

export default http
