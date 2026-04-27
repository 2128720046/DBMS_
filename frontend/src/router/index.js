import { createRouter, createWebHistory } from 'vue-router'
import Layout from '../views/Layout.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue')
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '仪表盘' }
      },
      {
        path: 'database',
        name: 'DatabaseManage',
        component: () => import('../views/DatabaseManage.vue'),
        meta: { title: '数据库管理' }
      },
      {
        path: 'table',
        name: 'TableManage',
        component: () => import('../views/TableManage.vue'),
        meta: { title: '表管理' }
      },
      {
        path: 'column',
        name: 'ColumnManage',
        component: () => import('../views/ColumnManage.vue'),
        meta: { title: '字段管理' }
      },
      {
        path: 'record',
        name: 'RecordManage',
        component: () => import('../views/RecordManage.vue'),
        meta: { title: '记录管理' }
      },
      {
        path: 'sql',
        name: 'SqlConsole',
        component: () => import('../views/SqlConsole.vue'),
        meta: { title: 'SQL 控制台' }
      },
      {
        path: 'backup',
        name: 'BackupRestore',
        component: () => import('../views/BackupRestore.vue'),
        meta: { title: '备份恢复' }
      },
      {
        path: 'index-constraint',
        name: 'IndexConstraintManage',
        component: () => import('../views/IndexConstraintManage.vue'),
        meta: { title: '索引与约束管理' }
      },
      {
        path: 'settings',
        name: 'SystemSettings',
        component: () => import('../views/SystemSettings.vue'),
        meta: { title: '系统设置' }
      }
    ]
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
