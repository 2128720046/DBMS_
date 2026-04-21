import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  timeout: 10000
})

export const getHealth = () => http.get('/health')

export default http
