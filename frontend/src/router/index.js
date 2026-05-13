import { createRouter, createWebHistory } from 'vue-router'
import Layout from '../views/Layout.vue'

const routes = [
  {
    path: '/',
    component: Layout,
    // 默认进入 SQL 控制台
    redirect: '/sql', 
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
        path: 'transaction',
        name: 'TransactionManage',
        component: () => import('../views/TransactionManage.vue'),
        meta: { title: '事务监控' }
      },
      {
        path: 'users',
        name: 'UserManage',
        component: () => import('../views/UserManage.vue'),
        meta: { title: '用户权限管理', requiresAdmin: true }
      },
      {
        path: 'clients',
        name: 'ClientManage',
        component: () => import('../views/ClientManage.vue'),
        meta: { title: '客户端会话', requiresAdmin: true }
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

// 路由守卫：管理员页面仅允许 admin 用户访问
router.beforeEach((to, from, next) => {
  if (to.meta && to.meta.requiresAdmin) {
    try {
      const userStr = sessionStorage.getItem('dbms-user')
      if (userStr) {
        const user = JSON.parse(userStr)
        if (user.username === 'admin') {
          return next()
        }
      }
    } catch (e) {}
    // 非管理员跳转到控制台
    return next('/sql')
  }
  next()
})

export default router