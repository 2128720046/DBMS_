// src/api/dbms.js (纯前端 Mock 数据版本，用于 UI 调试)

// 模拟网络延迟，让 Loading 动画和过渡效果显得更真实
const delay = (ms = 300) => new Promise(resolve => setTimeout(resolve, ms))

export const listDatabases = async () => {
  await delay()
  return {
    code: 200,
    data: [
      { name: 'g_clip_prod', size: '12.8 GB', createTime: '2025-12-01 10:00:00' },
      { name: 'campusmis', size: '256.0 MB', createTime: '2026-04-16 10:52:14' },
      { name: 'information_schema', size: '2.1 MB', createTime: '2020-01-01 00:00:00' }
    ]
  }
}

export const listTables = async (databaseName) => {
  await delay()
  if (databaseName === 'g_clip_prod') {
    return {
      data: [
        { name: 'sys_users', rows: 12540, engine: 'InnoDB', comment: '核心系统用户表' },
        { name: 'vip_orders', rows: 8920, engine: 'InnoDB', comment: '微信私域支付流水' },
        { name: 'video_render_tasks', rows: 342011, engine: 'InnoDB', comment: 'ComfyUI 云端口播视频渲染队列' },
        { name: 'api_keys_pool', rows: 150, engine: 'InnoDB', comment: 'DeepSeek/Gemini API 中转池' }
      ]
    }
  }
  return {
    data: [
      { name: 'student', rows: 5200, engine: 'InnoDB', comment: '学生信息表' },
      { name: 'course', rows: 120, engine: 'InnoDB', comment: '课程信息表' },
      { name: 'sc', rows: 15600, engine: 'InnoDB', comment: '学生成绩表' }
    ]
  }
}

export const getTableDetail = async (databaseName, tableName) => {
  await delay()
  // 模拟复杂表结构：video_render_tasks
  if (tableName === 'video_render_tasks') {
    return {
      data: {
        columns: [
          { name: 'task_id', type: 'VARCHAR(64)', key: 'PRI', nn: true, default: null },
          { name: 'user_id', type: 'BIGINT', key: 'MUL', nn: true, default: null },
          { name: 'status', type: 'VARCHAR(20)', key: '', nn: true, default: "'PENDING'" },
          { name: 'workflow_json', type: 'JSON', key: '', nn: false, default: null },
          { name: 'cost_credits', type: 'DECIMAL(10,2)', key: '', nn: true, default: '0.00' },
          { name: 'created_at', type: 'DATETIME', key: '', nn: true, default: 'CURRENT_TIMESTAMP' }
        ],
        indexes: [
          { name: 'PRIMARY', column: 'task_id', unique: true },
          { name: 'idx_user_status', column: 'user_id, status', unique: false }
        ],
        ddl: "CREATE TABLE `video_render_tasks` (\n  `task_id` varchar(64) NOT NULL,\n  `user_id` bigint NOT NULL,\n  `status` varchar(20) DEFAULT 'PENDING',\n  `workflow_json` json DEFAULT NULL,\n  `cost_credits` decimal(10,2) DEFAULT '0.00',\n  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,\n  PRIMARY KEY (`task_id`),\n  KEY `idx_user_status` (`user_id`,`status`)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;"
      }
    }
  }
  // 兜底基础结构
  return {
    data: {
      columns: [
        { name: 'id', type: 'INT', key: 'PRI', nn: true, default: null },
        { name: 'name', type: 'VARCHAR(255)', key: '', nn: false, default: null }
      ]
    }
  }
}

export const queryRecords = async (databaseName, tableName, payload) => {
  await delay(500)
  // 模拟复杂的渲染任务数据
  if (tableName === 'video_render_tasks') {
    return {
      data: {
        total: 342011,
        list: [
          { task_id: 'tsk_9f8a7c6e', user_id: 10029, status: 'SUCCESS', workflow_json: '{"model": "nano_banana", "fps": 30}', cost_credits: 2.50, created_at: '2026-04-28 15:30:00' },
          { task_id: 'tsk_1b2c3d4e', user_id: 10029, status: 'PROCESSING', workflow_json: '{"model": "veo_2", "resolution": "1080p"}', cost_credits: 15.00, created_at: '2026-04-28 15:32:15' },
          { task_id: 'tsk_5a6b7c8d', user_id: 28810, status: 'FAILED', workflow_json: '{"error": "CUDA out of memory"}', cost_credits: 0.00, created_at: '2026-04-28 15:40:22' },
          { task_id: 'tsk_0e9d8c7b', user_id: 99201, status: 'PENDING', workflow_json: '{"auto_caption": true, "voice": "zh-CN-XiaoxiaoNeural"}', cost_credits: 1.20, created_at: '2026-04-28 16:01:05' },
          { task_id: 'tsk_4f5e6d7c', user_id: 10029, status: 'SUCCESS', workflow_json: '{"batch_mode": true, "clips": 12}', cost_credits: 8.40, created_at: '2026-04-28 16:15:30' }
        ]
      }
    }
  }
  
  if (tableName === 'sc') {
     return {
      data: {
        total: 15600,
        list: [
          { Sno: '24301122', Cid: 1, Grade: 84 },
          { Sno: '24301122', Cid: 2, Grade: 90 },
          { Sno: '24308888', Cid: 2, Grade: 99 },
          { Sno: '24309010', Cid: 1, Grade: 69 }
        ]
      }
    }
  }

  return { data: { total: 0, list: [] } }
}

export const executeSql = async (payload) => {
  await delay(800)
  const sql = payload.sql.trim().toUpperCase()
  
  // 模拟错误
  if (sql.includes('DROP') || sql.includes('DELETE')) {
    return Promise.reject(new Error("You have an error in your SQL syntax; check the manual that corresponds to your MySQL server version for the right syntax to use near 'DROP' at line 1"))
  }
  
  // 模拟 SELECT 结果
  if (sql.startsWith('SELECT')) {
    return {
      data: {
        type: 'table',
        status: 'success',
        affectedRows: 0,
        columns: [
          { prop: 'Sno', label: '学号' },
          { prop: 'Name', label: '姓名' },
          { prop: 'Score', label: '总分' }
        ],
        data: [
          { Sno: '24301122', Name: 'Hoàng Dũng', Score: 285 },
          { Sno: '24309010', Name: '张三', Score: 190 }
        ]
      }
    }
  }
  
  // 模拟 INSERT/UPDATE
  return {
    data: {
      type: 'message',
      status: 'success',
      data: 'Query OK, 5 rows affected',
      affectedRows: 5
    }
  }
}

// 保留其他空方法的声明，防止控制台报错
export const listIndexes = async () => ({ data: [] })
export const listConstraints = async () => ({ data: [] })
export const createDatabase = async () => ({ code: 200 })
export const dropDatabase = async () => ({ code: 200 })
export const createTable = async () => ({ code: 200 })
export const updateTableStructure = async () => ({ code: 200 })
export const dropTable = async () => ({ code: 200 })
export const insertRecord = async () => ({ code: 200 })
export const updateRecord = async () => ({ code: 200 })
export const deleteRecord = async () => ({ code: 200 })
export const login = async () => ({ data: { token: 'mock-token-123' } })
export const listBackups = async () => ({ data: [{ name: 'auto-backup-2026.sql', size: 1048576, updatedAt: '2026-04-28 02:00:00'}] })