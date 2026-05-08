// src/api/dbms.js
import http from './http'

const executeSqlRaw = (databaseName, sql) => http.post('/sql/execute', { databaseName, sql })

const wrapResponse = (res, data) => ({ ...res, data })

const readTableRows = (payload) => (Array.isArray(payload?.data) ? payload.data : [])

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
	if (upper === 'NULL' || upper === 'CURRENT_TIMESTAMP' || upper === 'NOW()') {
		return text
	}
	return formatSqlLiteral(text)
}

const buildColumnDefinition = (column) => {
	const name = String(column.name || '').trim()
	const type = String(column.type || '').trim().toUpperCase()
	const length = column.length ? String(column.length).trim() : ''
	const parts = []
	parts.push(name)
	parts.push(length ? `${type}(${length})` : type)
	if (column.pk) {
		parts.push('PRIMARY KEY')
	}
	if (column.uq) {
		parts.push('UNIQUE')
	}
	if (column.nullable === false || column.nn) {
		parts.push('NOT NULL')
	}
	if (column.defaultVal !== undefined && column.defaultVal !== '') {
		const def = formatDefaultLiteral(column.defaultVal)
		if (def) {
			parts.push(`DEFAULT ${def}`)
		}
	}
	return parts.join(' ')
}

export const executeSql = (data) => executeSqlRaw(data?.databaseName || '', data?.sql || '')

export const listDatabases = async () => {
	const res = await executeSqlRaw('', 'SHOW DATABASES')
	const rows = readTableRows(res.data)
	const data = rows.map((row) => ({ name: row.SCHEMA_NAME }))
	return wrapResponse(res, data)
}

export const createDatabase = async (data) => {
	const name = String(data?.name || '').trim()
	return executeSqlRaw('', `CREATE DATABASE ${name}`)
}

export const dropDatabase = async (name) => executeSqlRaw('', `DROP DATABASE ${name}`)

export const listTables = async (db) => {
	const res = await executeSqlRaw(db, 'SHOW TABLES')
	const rows = readTableRows(res.data)
	const data = rows.map((row) => ({ name: row.TABLE_NAME }))
	return wrapResponse(res, data)
}

export const getTableDetail = async (db, table) => {
	const descPromise = executeSqlRaw(db, `DESCRIBE ${table}`)
	const indexPromise = executeSqlRaw(db, `SHOW INDEXES FROM ${table}`)
	const constraintPromise = executeSqlRaw(db, `SHOW CONSTRAINTS FROM ${table}`)

	const [descRes, indexRes, constraintRes] = await Promise.allSettled([
		descPromise,
		indexPromise,
		constraintPromise
	])

	if (descRes.status === 'rejected') {
		throw descRes.reason
	}

	const descPayload = descRes.status === 'fulfilled' ? descRes.value.data : null
	const indexPayload = indexRes.status === 'fulfilled' ? indexRes.value.data : null
	const constraintPayload = constraintRes.status === 'fulfilled' ? constraintRes.value.data : null

	const columns = readTableRows(descPayload).map((row) => {
		const nullable = toBoolean(row.NULLABLE)
		const primaryKey = toBoolean(row.PRIMARY_KEY)
		return {
			name: row.COLUMN,
			type: row.TYPE,
			key: primaryKey ? 'PRI' : '',
			nn: !nullable,
			default: '',
			nullable,
			pk: primaryKey,
			uq: false
		}
	})

	const indexes = readTableRows(indexPayload).map((row) => ({
		name: row.name || row.NAME,
		column: Array.isArray(row.columns) ? row.columns.join(', ') : String(row.columns || row.column || '')
	}))

	const constraints = readTableRows(constraintPayload).map((row) => ({
		name: row.name || row.NAME,
		type: row.type || row.TYPE,
		expr: row.parameter || row.PARAMETER || row.column || row.COLUMN || ''
	}))

	const detail = {
		columns,
		constraints,
		fks: [],
		indexes,
		ddl: ''
	}

	return wrapResponse(descRes.value, detail)
}

export const createTable = async (db, data) => {
	const tableName = String(data?.name || '').trim()
	const columns = (data?.columns || []).map(buildColumnDefinition)
	const sql = `CREATE TABLE ${tableName} (${columns.join(', ')})`
	return executeSqlRaw(db, sql)
}

export const updateTableStructure = async (db, table, data) => {
	const columns = (data?.columns || []).map(buildColumnDefinition)
	const sql = `ALTER TABLE ${table} REPLACE COLUMNS (${columns.join(', ')})`
	return executeSqlRaw(db, sql)
}

export const dropTable = async (db, table) => executeSqlRaw(db, `DROP TABLE ${table}`)

export const queryRecords = async (db, table, data) => {
	const page = data?.page || 1
	const size = data?.size || 50
	const filters = data?.filters || {}
	const whereParts = []
	Object.entries(filters).forEach(([key, value]) => {
		if (value === '' || value === undefined || value === null) return
		whereParts.push(`${key} = ${formatSqlLiteral(value)}`)
	})
	const whereClause = whereParts.length ? ` WHERE ${whereParts.join(' AND ')}` : ''
	const sql = `SELECT * FROM ${table}${whereClause}`
	const res = await executeSqlRaw(db, sql)
	const rows = readTableRows(res.data)
	const total = rows.length
	const start = (page - 1) * size
	const list = rows.slice(start, start + size)
	return wrapResponse(res, { list, total })
}

export const insertRecord = async (db, table, data) => {
	const values = data?.values || {}
	const columns = Object.keys(values)
	const literalValues = columns.map((key) => formatSqlLiteral(values[key]))
	const sql = `INSERT INTO ${table} (${columns.join(', ')}) VALUES (${literalValues.join(', ')})`
	return executeSqlRaw(db, sql)
}

export const updateRecord = async (db, table, data) => {
	const values = data?.values || {}
	const filters = data?.filters || {}
	const assignments = Object.entries(values).map(([key, value]) => `${key} = ${formatSqlLiteral(value)}`)
	const whereParts = Object.entries(filters).map(([key, value]) => `${key} = ${formatSqlLiteral(value)}`)
	const whereClause = whereParts.length ? ` WHERE ${whereParts.join(' AND ')}` : ''
	const sql = `UPDATE ${table} SET ${assignments.join(', ')}${whereClause}`
	return executeSqlRaw(db, sql)
}

export const deleteRecord = async (db, table, data) => {
	const filters = data?.filters || {}
	const whereParts = Object.entries(filters).map(([key, value]) => `${key} = ${formatSqlLiteral(value)}`)
	const whereClause = whereParts.length ? ` WHERE ${whereParts.join(' AND ')}` : ''
	const sql = `DELETE FROM ${table}${whereClause}`
	return executeSqlRaw(db, sql)
}

export const listConstraints = async (db, table) => {
	const res = await executeSqlRaw(db, `SHOW CONSTRAINTS FROM ${table}`)
	const rows = readTableRows(res.data)
	const data = rows.map((row) => ({
		name: row.name || row.NAME,
		type: row.type || row.TYPE,
		columns: row.column ? [row.column] : [],
		reference: row.parameter || row.PARAMETER || ''
	}))
	return wrapResponse(res, data)
}

export const checkConstraints = async (db, table) => executeSqlRaw(db, `CHECK CONSTRAINTS FROM ${table}`)

export const dropConstraint = async (db, table, constraintName) =>
	executeSqlRaw(db, `ALTER TABLE ${table} DROP CONSTRAINT ${constraintName}`)

export const listIndexes = async (db, table) => {
	const res = await executeSqlRaw(db, `SHOW INDEXES FROM ${table}`)
	const rows = readTableRows(res.data)
	const data = rows.map((row) => ({
		name: row.name || row.NAME,
		columns: row.columns || row.COLUMNS || [],
		unique: toBoolean(row.unique || row.UNIQUE),
		ascending: toBoolean(row.ascending || row.ASCENDING)
	}))
	return wrapResponse(res, data)
}

export const createIndex = async (db, table, data) => {
	const name = String(data?.name || '').trim()
	const columns = (data?.columns || []).join(', ')
	const unique = data?.unique ? 'UNIQUE ' : ''
	const sql = `CREATE ${unique}INDEX ${name} ON ${table} (${columns})`
	return executeSqlRaw(db, sql)
}

export const dropIndex = async (db, table, indexName) =>
	executeSqlRaw(db, `DROP INDEX ${indexName} ON ${table}`)

export const rebuildIndex = async (db, table, indexName) =>
	executeSqlRaw(db, `REBUILD INDEX ${indexName} ON ${table}`)

export const listBackups = async (db) => {
	const res = await executeSqlRaw(db, `SHOW BACKUPS FROM ${db}`)
	const rows = readTableRows(res.data)
	const data = rows.map((row) => ({
		name: row.name || row.NAME,
		size: row.size ?? row.SIZE,
		updatedAt: row.updatedAt || row.UPDATEDAT || row.updated_at,
		desc: row.desc || row.DESC
	}))
	return wrapResponse(res, data)
}

export const createBackup = async (db) => executeSqlRaw(db, `BACKUP DATABASE ${db} TO 'auto'`)

export const restoreBackup = async (db, backupName) =>
	executeSqlRaw(db, `RESTORE DATABASE ${db} FROM ${formatSqlLiteral(backupName)}`)

export const deleteBackup = async (db, backupName) =>
	executeSqlRaw(db, `DELETE BACKUP ${formatSqlLiteral(backupName)} FROM ${db}`)

export const login = async (data) => {
	const username = formatSqlLiteral(data?.username || '')
	const password = formatSqlLiteral(data?.password || '')
	const res = await executeSqlRaw('', `CONNECT TO LOCAL USER ${username} IDENTIFIED BY ${password}`)
	return wrapResponse(res, res.data || {})
}

export const register = async (data) => {
	const username = formatSqlLiteral(data?.username || '')
	const password = formatSqlLiteral(data?.password || '')
	return executeSqlRaw('', `CREATE USER ${username} IDENTIFIED BY ${password}`)
}
