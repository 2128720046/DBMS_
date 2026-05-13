// src/api/dbms.js
// 统一 API 层：所有操作先在前端拼装成后端规范的 SQL，通过唯一接口 /api/sql/execute 发送，
// 接收响应后按统一结构解析返回，前端组件通过 .data 获取结果数据。
import http from './http'

// ==================== 核心发送与响应解析 ====================

/** 发送 SQL 到后端唯一接口 */
const executeSqlRaw = (databaseName, sql) => {
	const token = sessionStorage.getItem('dbms-token') || ''
	return http.post('/sql/execute', { databaseName: databaseName || '', sql, token })
}

/** 从 SQL 响应中提取表格数据行 */
const extractRows = (response) => {
	const payload = response?.data
	if (payload && payload.type === 'table' && Array.isArray(payload.data)) {
		return payload.data
	}
	return []
}

/** 从 SQL 响应中提取影响行数 */
const extractAffected = (response) => {
	return response?.data?.affectedRows ?? 0
}

/** 从 SQL 响应中提取提示消息 */
const extractMsg = (response) => {
	return response?.data?.data || ''
}

// ==================== 工具函数 ====================

const toBoolean = (value) => {
	if (typeof value === 'boolean') return value
	if (typeof value === 'string') return value.toLowerCase() === 'true'
	return Boolean(value)
}

const formatSqlLiteral = (value) => {
	if (value === null || value === undefined) return 'NULL'
	if (typeof value === 'number') return String(value)
	if (typeof value === 'boolean') return value ? 'TRUE' : 'FALSE'
	return `'${String(value).replace(/'/g, "''")}'`
}

const formatDefaultLiteral = (value) => {
	if (value === null || value === undefined || value === '') return ''
	const text = String(value).trim()
	if (!text) return ''
	const upper = text.toUpperCase()
	if (upper === 'NULL' || upper === 'CURRENT_TIMESTAMP' || upper === 'NOW()') return text
	return formatSqlLiteral(text)
}

const buildColumnDefinition = (column) => {
	const name = String(column.name || '').trim()
	const type = String(column.type || '').trim().toUpperCase()
	const length = column.length ? String(column.length).trim() : ''
	const parts = [name, length ? `${type}(${length})` : type]
	if (column.pk) parts.push('PRIMARY KEY')
	if (column.uq) parts.push('UNIQUE')
	if (column.nullable === false || column.nn) parts.push('NOT NULL')
	if (column.defaultVal !== undefined && column.defaultVal !== '') {
		const def = formatDefaultLiteral(column.defaultVal)
		if (def) parts.push(`DEFAULT ${def}`)
	}
	// CHECK 约束（后端解析器识别 "CHECK (...)" 格式）
	const checkExpr = column.checkExpression || (column.check ? `CHECK (${name} ${column.check})` : '')
	if (checkExpr) parts.push(checkExpr)
	// 外键（后端解析器识别 "REFERENCES table(column)" 格式）
	if (column.foreignKeyTable && column.foreignKeyColumn) {
		parts.push(`REFERENCES ${column.foreignKeyTable}(${column.foreignKeyColumn})`)
	} else if (column.fk) {
		const [fkTable, fkCol] = column.fk.split('.')
		if (fkTable && fkCol) parts.push(`REFERENCES ${fkTable}(${fkCol})`)
	}
	return parts.join(' ')
}

// ==================== 数据库管理 ====================

export const listDatabases = async () => {
	const res = await executeSqlRaw('', 'SHOW DATABASES')
	const rows = extractRows(res)
	return { data: rows.map(r => ({ name: r.SCHEMA_NAME })) }
}

export const createDatabase = async (data) => {
	const name = String(data?.name || '').trim()
	return executeSqlRaw('', `CREATE DATABASE ${name}`)
}

export const dropDatabase = async (name) => executeSqlRaw('', `DROP DATABASE ${name}`)

// ==================== 表管理 ====================

export const listTables = async (db) => {
	const res = await executeSqlRaw(db, 'SHOW TABLES')
	const rows = extractRows(res)
	return { data: rows.map(r => ({ name: r.TABLE_NAME })) }
}

export const getTableDetail = async (db, table) => {
	const [descRes, indexRes, constraintRes] = await Promise.allSettled([
		executeSqlRaw(db, `DESCRIBE ${table}`),
		executeSqlRaw(db, `SHOW INDEXES FROM ${table}`),
		executeSqlRaw(db, `SHOW CONSTRAINTS FROM ${table}`)
	])
	if (descRes.status === 'rejected') throw descRes.reason

	const columns = extractRows(descRes.value).map(row => ({
		name: row.COLUMN, type: row.TYPE, key: toBoolean(row.PRIMARY_KEY) ? 'PRI' : '',
		nn: !toBoolean(row.NULLABLE), default: '', nullable: toBoolean(row.NULLABLE),
		pk: toBoolean(row.PRIMARY_KEY), uq: false
	}))
	const indexes = extractRows(indexRes.status === 'fulfilled' ? indexRes.value : null)
		.map(row => ({ name: row.name || row.NAME, columns: row.columns || row.COLUMNS || [] }))
    const allConstraints = (extractRows(constraintRes.status === 'fulfilled' ? constraintRes.value : null) || [])
        .map(row => ({
            name: row.name || row.NAME,
            type: row.type || row.TYPE,
            column: row.column || row.COLUMN || '',
            expr: row.parameter || row.PARAMETER || ''
        }))

    // 分离外键到 fks 数组，其余约束保留在 constraints
    const fks = allConstraints.filter(c =>
        c.type.toUpperCase().replace(' ', '_') === 'FOREIGN_KEY' ||
        c.type.toUpperCase().replace(' ', '_') === 'FOREIGN KEY'
    ).map(c => {
        // 从 expr 中解析引用表和字段，如 "FOREIGN KEY (dept_id) REFERENCES dept(id)"
        const fkMatch = c.expr.match(/REFERENCES\s+(\w+)\s*\((\w+)\)/i)
        return {
            name: c.name,
            column: c.column,
            refTable: fkMatch ? fkMatch[1] : '',
            refColumn: fkMatch ? fkMatch[2] : ''
        }
    })
    const constraints = allConstraints.filter(c =>
        !(c.type.toUpperCase().replace(' ', '_') === 'FOREIGN_KEY' ||
          c.type.toUpperCase().replace(' ', '_') === 'FOREIGN KEY')
    )

    // 简单 DDL 生成：从列定义拼凑 CREATE TABLE
    const ddlLines = columns.map(c => {
        const parts = [`  ${c.name} ${c.type}${c.type === 'VARCHAR' ? '(255)' : ''}`]
        if (c.pk) parts.push('PRIMARY KEY')
        if (c.uq) parts.push('UNIQUE')
        if (!c.nullable) parts.push('NOT NULL')
        return parts.join(' ')
    })
    const ddl = `CREATE TABLE ${table} (\n${ddlLines.join(',\n')}\n);`

    return { data: { columns, constraints, fks, indexes, ddl } }
}

export const createTable = async (db, data) => {
	const tableName = String(data?.name || '').trim()
	const columns = (data?.columns || []).map(buildColumnDefinition)
	return executeSqlRaw(db, `CREATE TABLE ${tableName} (${columns.join(', ')})`)
}

export const updateTableStructure = async (db, table, data) => {
	const columns = (data?.columns || []).map(buildColumnDefinition)
	return executeSqlRaw(db, `ALTER TABLE ${table} REPLACE COLUMNS (${columns.join(', ')})`)
}

export const dropTable = async (db, table) => executeSqlRaw(db, `DROP TABLE ${table}`)

// ==================== 记录管理（DML）====================

export const queryRecords = async (db, table, query) => {
	const page = query?.page || 1
	const size = query?.size || 50
	const filters = query?.filters || {}
	const whereParts = Object.entries(filters)
		.filter(([, v]) => v !== '' && v !== undefined && v !== null)
		.map(([k, v]) => `${k} = ${formatSqlLiteral(v)}`)
	const whereClause = whereParts.length ? ` WHERE ${whereParts.join(' AND ')}` : ''
	const res = await executeSqlRaw(db, `SELECT * FROM ${table}${whereClause}`)
	const rows = extractRows(res)
	const total = rows.length
	const start = (page - 1) * size
	return { data: { list: rows.slice(start, start + size), total } }
}

export const insertRecord = async (db, table, data) => {
	const values = data?.values || {}
	const cols = Object.keys(values)
	return executeSqlRaw(db, `INSERT INTO ${table} (${cols.join(', ')}) VALUES (${cols.map(k => formatSqlLiteral(values[k])).join(', ')})`)
}

export const updateRecord = async (db, table, data) => {
	const values = data?.values || {}
	const filters = data?.filters || {}
	const setClause = Object.entries(values).map(([k, v]) => `${k} = ${formatSqlLiteral(v)}`).join(', ')
	const whereClause = Object.entries(filters).map(([k, v]) => `${k} = ${formatSqlLiteral(v)}`).join(' AND ')
	return executeSqlRaw(db, `UPDATE ${table} SET ${setClause}${whereClause ? ' WHERE ' + whereClause : ''}`)
}

export const deleteRecord = async (db, table, data) => {
	const filters = data?.filters || {}
	const whereClause = Object.entries(filters).map(([k, v]) => `${k} = ${formatSqlLiteral(v)}`).join(' AND ')
	return executeSqlRaw(db, `DELETE FROM ${table}${whereClause ? ' WHERE ' + whereClause : ''}`)
}

// ==================== 索引管理 ====================

export const listIndexes = async (db, table) => {
	const res = await executeSqlRaw(db, `SHOW INDEXES FROM ${table}`)
	const rows = extractRows(res)
	return { data: rows.map(r => ({
		name: r.name || r.NAME,
		columns: r.columns || r.COLUMNS || [],
		unique: toBoolean(r.unique || r.UNIQUE),
		ascending: toBoolean(r.ascending || r.ASCENDING)
	}))}
}

export const createIndex = async (db, table, data) => {
	const name = String(data?.name || '').trim()
	const cols = (data?.columns || []).join(', ')
	const unique = data?.unique ? 'UNIQUE ' : ''
	return executeSqlRaw(db, `CREATE ${unique}INDEX ${name} ON ${table} (${cols})`)
}

export const dropIndex = async (db, table, indexName) =>
	executeSqlRaw(db, `DROP INDEX ${indexName} ON ${table}`)

export const rebuildIndex = async (db, table, indexName) =>
	executeSqlRaw(db, `REBUILD INDEX ${indexName} ON ${table}`)

// ==================== 完整性约束 ====================

export const listConstraints = async (db, table) => {
	const res = await executeSqlRaw(db, `SHOW CONSTRAINTS FROM ${table}`)
	const rows = extractRows(res)
	return { data: rows.map(r => ({
		name: r.name || r.NAME,
		type: r.type || r.TYPE,
		columns: r.column ? [r.column] : [],
		reference: r.parameter || r.PARAMETER || ''
	}))}
}

export const checkConstraints = async (db, table) => executeSqlRaw(db, `CHECK CONSTRAINTS FROM ${table}`)

export const dropConstraint = async (db, table, constraintName) =>
	executeSqlRaw(db, `ALTER TABLE ${table} DROP CONSTRAINT ${constraintName}`)

// ==================== 事务管理 ====================

export const beginTransaction = async (db) => executeSqlRaw(db || '', 'BEGIN')
export const commitTransaction = async (db) => executeSqlRaw(db || '', 'COMMIT')
export const rollbackTransaction = async (db) => executeSqlRaw(db || '', 'ROLLBACK')

// ==================== 备份恢复 ====================

export const listBackups = async (db) => {
	const res = await executeSqlRaw(db, `SHOW BACKUPS FROM ${db}`)
	const rows = extractRows(res)
	return { data: rows.map(r => ({
		name: r.name || r.NAME,
		size: r.size ?? r.SIZE,
		updatedAt: r.updatedAt || r.UPDATEDAT || r.updated_at,
		desc: r.desc || r.DESC
	}))}
}

export const createBackup = async (db) => executeSqlRaw(db, `BACKUP DATABASE ${db} TO 'auto'`)
export const restoreBackup = async (db, backupName) =>
	executeSqlRaw(db, `RESTORE DATABASE ${db} FROM ${formatSqlLiteral(backupName)}`)
export const deleteBackup = async (db, backupName) =>
	executeSqlRaw(db, `DELETE BACKUP ${formatSqlLiteral(backupName)} FROM ${db}`)

// ==================== 认证与会话 ====================

export const login = async (data) => {
	const res = await executeSqlRaw('',
		`CONNECT TO LOCAL USER ${formatSqlLiteral(data?.username || '')} IDENTIFIED BY ${formatSqlLiteral(data?.password || '')}`)
	// 响应体中的 data 字段为 { type, status, data, clientId, username, token, affectedRows }
	return { data: res?.data || {} }
}

export const disconnectSelf = async () => executeSqlRaw('', 'DISCONNECT')

export const showClients = async () => {
	const res = await executeSqlRaw('', 'SHOW CLIENTS')
	return { data: extractRows(res) }
}

export const disconnectClient = async (clientId) =>
	executeSqlRaw('', `DISCONNECT ${formatSqlLiteral(clientId)}`)

// ==================== 用户与权限管理 ====================

export const showUsers = async () => {
	const res = await executeSqlRaw('', 'SHOW USERS')
	return { data: extractRows(res) }
}

export const showGrants = async (username) => {
	const res = await executeSqlRaw('', `SHOW GRANTS FOR ${formatSqlLiteral(username)}`)
	return { data: extractRows(res) }
}

export const createNewUser = async (username, password) =>
	executeSqlRaw('', `CREATE USER ${formatSqlLiteral(username)} IDENTIFIED BY ${formatSqlLiteral(password)}`)

export const dropExistingUser = async (username) =>
	executeSqlRaw('', `DROP USER ${formatSqlLiteral(username)}`)

export const alterUserPassword = async (username, password) =>
	executeSqlRaw('', `ALTER USER ${formatSqlLiteral(username)} IDENTIFIED BY ${formatSqlLiteral(password)}`)

export const grantPrivilege = async (username, privilege, objectName) =>
	executeSqlRaw('', `GRANT ${privilege} ON ${objectName} TO ${formatSqlLiteral(username)}`)

export const revokePrivilege = async (username, privilege, objectName) =>
	executeSqlRaw('', `REVOKE ${privilege} ON ${objectName} FROM ${formatSqlLiteral(username)}`)

// ==================== 通用 SQL 执行 ====================

export const executeSql = (data) => executeSqlRaw(data?.databaseName || '', data?.sql || '')
