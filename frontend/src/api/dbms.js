// src/api/dbms.js
import http from './http'

const execute = (databaseName, sql) => http.post('/sql/execute', { databaseName, sql })

const ident = (name) => String(name || '').trim()

const literal = (value) => {
  if (value === null || value === undefined || value === '') return 'NULL'
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  return `'${String(value).replace(/'/g, "''")}'`
}

const unwrapRows = (response) => response?.data?.data || []

const normalizeSqlResponse = (response, data) => ({
  code: response?.code ?? 200,
  message: response?.message || 'success',
  data
})

const buildWhere = (filters = {}) => {
  const entries = Object.entries(filters).filter(([, value]) => value !== undefined && value !== null && value !== '')
  if (!entries.length) return ''
  return ` WHERE ${entries.map(([key, value]) => `${ident(key)} = ${literal(value)}`).join(' AND ')}`
}

const columnType = (column) => {
  const type = String(column.type || 'VARCHAR').toUpperCase()
  if (column.length && ['VARCHAR', 'CHAR', 'DECIMAL'].includes(type)) {
    return `${type}(${column.length})`
  }
  return type
}

const columnConstraints = (column) => {
  const parts = []
  if (column.pk) parts.push('PRIMARY KEY')
  if (column.nullable === false || column.nn) parts.push('NOT NULL')
  if (column.uq) parts.push('UNIQUE')
  if (column.defaultVal !== undefined && column.defaultVal !== '') parts.push(`DEFAULT ${literal(column.defaultVal)}`)
  return parts.join(' ')
}

export const executeSql = (data) => http.post('/sql/execute', data)

export const listDatabases = async () => {
  const res = await execute('', 'SHOW DATABASES')
  const rows = unwrapRows(res).map((row) => ({
    name: row.SCHEMA_NAME,
    size: row.size || '-',
    createTime: row.createTime || '-'
  }))
  return normalizeSqlResponse(res, rows)
}

export const createDatabase = (data) => execute('', `CREATE DATABASE ${ident(data.name)}`)

export const dropDatabase = (name) => execute('', `DROP DATABASE ${ident(name)}`)

export const listTables = async (db) => {
  const res = await execute(db, 'SHOW TABLES')
  const rows = unwrapRows(res).map((row) => ({
    name: row.TABLE_NAME,
    rows: row.rows || '-',
    engine: row.engine || '404',
    comment: row.comment || '-'
  }))
  return normalizeSqlResponse(res, rows)
}

export const getTableDetail = async (db, table) => {
  const res = await execute(db, `DESCRIBE ${ident(table)}`)
  const columns = unwrapRows(res).map((row) => ({
    name: row.COLUMN,
    type: row.TYPE,
    nullable: row.NULLABLE,
    primaryKey: row.PRIMARY_KEY,
    pk: row.PRIMARY_KEY,
    key: row.PRIMARY_KEY ? 'PRI' : '',
    nn: row.NULLABLE === false
  }))
  return normalizeSqlResponse(res, {
    tableName: table,
    columns,
    constraints: [],
    fks: [],
    indexes: [],
    ddl: ''
  })
}

export const createTable = (db, data) => {
  const columns = (data.columns || []).map((column) => {
    const constraints = columnConstraints(column)
    return `${ident(column.name)} ${columnType(column)}${constraints ? ` ${constraints}` : ''}`
  })
  return execute(db, `CREATE TABLE ${ident(data.name)} (${columns.join(', ')})`)
}

export const updateTableStructure = (db, table, data) => {
  const columns = data.columns || []
  const definitions = columns.map((column) => {
    const constraints = columnConstraints(column)
    return `${ident(column.name)} ${columnType(column)}${constraints ? ` ${constraints}` : ''}`
  })
  // 当前后端 SQL 子集没有批量替换表结构语法，使用项目内部约定命令，后续由 Parser/Executor 接入。
  return execute(db, `ALTER TABLE ${ident(table)} REPLACE COLUMNS (${definitions.join(', ')})`)
}

export const dropTable = (db, table) => execute(db, `DROP TABLE ${ident(table)}`)

export const queryRecords = async (db, table, data = {}) => {
  const page = data.page || 1
  const size = data.size || 50
  const offset = (page - 1) * size
  const res = await execute(db, `SELECT * FROM ${ident(table)}${buildWhere(data.filters)} LIMIT ${offset}, ${size}`)
  const rows = unwrapRows(res)
  return normalizeSqlResponse(res, {
    total: rows.length,
    page,
    size,
    list: rows
  })
}

export const insertRecord = (db, table, data = {}) => {
  const values = data.values || data.records?.[0] || data
  const columns = Object.keys(values)
  return execute(db, `INSERT INTO ${ident(table)} (${columns.map(ident).join(', ')}) VALUES (${columns.map((key) => literal(values[key])).join(', ')})`)
}

export const updateRecord = (db, table, data = {}) => {
  const values = data.values || data.updates || {}
  const filters = data.filters || data.where || {}
  const setSql = Object.entries(values).map(([key, value]) => `${ident(key)} = ${literal(value)}`).join(', ')
  return execute(db, `UPDATE ${ident(table)} SET ${setSql}${buildWhere(filters)}`)
}

export const deleteRecord = (db, table, data = {}) => {
  const filters = data.filters || data.where || {}
  return execute(db, `DELETE FROM ${ident(table)}${buildWhere(filters)}`)
}

export const listIndexes = async (db, table) => {
  const res = await execute(db, `SHOW INDEXES FROM ${ident(table)}`)
  return normalizeSqlResponse(res, unwrapRows(res))
}

export const createIndex = (db, table, data) => {
  const unique = data.unique || data.type === 'UNIQUE' ? 'UNIQUE ' : ''
  const columns = (data.columns || []).map(ident).join(', ')
  return execute(db, `CREATE ${unique}INDEX ${ident(data.name)} ON ${ident(table)} (${columns})`)
}

export const dropIndex = (db, table, indexName) => execute(db, `DROP INDEX ${ident(indexName)} ON ${ident(table)}`)

export const rebuildIndex = (db, table, indexName) => execute(db, `REBUILD INDEX ${ident(indexName)} ON ${ident(table)}`)

export const listConstraints = async (db, table) => {
  const res = await execute(db, `SHOW CONSTRAINTS FROM ${ident(table)}`)
  return normalizeSqlResponse(res, unwrapRows(res))
}

export const checkConstraints = async (db, table) => {
  const res = await execute(db, `CHECK CONSTRAINTS FROM ${ident(table)}`)
  return normalizeSqlResponse(res, {
    passed: res?.data?.passed ?? true,
    issues: res?.data?.issues || []
  })
}

export const dropConstraint = (db, table, constraintName) => (
  execute(db, `ALTER TABLE ${ident(table)} DROP CONSTRAINT ${ident(constraintName)}`)
)

export const listBackups = async (db) => {
  const res = await execute(db, `SHOW BACKUPS FROM ${ident(db)}`)
  return normalizeSqlResponse(res, unwrapRows(res))
}

export const createBackup = (db) => execute(db, `BACKUP DATABASE ${ident(db)} TO 'auto'`)

export const restoreBackup = (db, backupName) => execute(db, `RESTORE DATABASE ${ident(db)} FROM ${literal(backupName)}`)

export const deleteBackup = (db, backupName) => execute(db, `DELETE BACKUP ${literal(backupName)} FROM ${ident(db)}`)

export const login = (data) => execute('', `CONNECT TO localhost USER ${literal(data.username)} IDENTIFIED BY ${literal(data.password)}`)

export const register = (data) => execute('', `CREATE USER ${literal(data.username)} IDENTIFIED BY ${literal(data.password)}`)
