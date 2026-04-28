import http from './http'

export const listDatabases = () => http.get('/databases')
export const createDatabase = (payload) => http.post('/databases', payload)
export const dropDatabase = (databaseName) => http.delete(`/databases/${encodeURIComponent(databaseName)}`)

export const listTables = (databaseName) =>
  http.get(`/databases/${encodeURIComponent(databaseName)}/tables`)

export const createTable = (databaseName, payload) =>
  http.post(`/databases/${encodeURIComponent(databaseName)}/tables`, payload)

export const updateTableStructure = (databaseName, tableName, payload) =>
  http.put(`/databases/${encodeURIComponent(databaseName)}/tables/${encodeURIComponent(tableName)}`, payload)

export const dropTable = (databaseName, tableName) =>
  http.delete(`/databases/${encodeURIComponent(databaseName)}/tables/${encodeURIComponent(tableName)}`)

export const getTableDetail = (databaseName, tableName) =>
  http.get(`/databases/${encodeURIComponent(databaseName)}/tables/${encodeURIComponent(tableName)}`)

export const listIndexes = (databaseName, tableName) =>
  http.get(`/databases/${encodeURIComponent(databaseName)}/tables/${encodeURIComponent(tableName)}/indexes`)

export const createIndex = (databaseName, tableName, payload) =>
  http.post(`/databases/${encodeURIComponent(databaseName)}/tables/${encodeURIComponent(tableName)}/indexes`, payload)

export const dropIndex = (databaseName, tableName, indexName) =>
  http.delete(`/databases/${encodeURIComponent(databaseName)}/tables/${encodeURIComponent(tableName)}/indexes/${encodeURIComponent(indexName)}`)

export const rebuildIndex = (databaseName, tableName, indexName) =>
  http.post(`/databases/${encodeURIComponent(databaseName)}/tables/${encodeURIComponent(tableName)}/indexes/${encodeURIComponent(indexName)}/rebuild`)

export const listConstraints = (databaseName, tableName) =>
  http.get(`/databases/${encodeURIComponent(databaseName)}/tables/${encodeURIComponent(tableName)}/constraints`)

export const checkConstraints = (databaseName, tableName) =>
  http.post(`/databases/${encodeURIComponent(databaseName)}/tables/${encodeURIComponent(tableName)}/constraints/check`)

export const dropConstraint = (databaseName, tableName, constraintName) =>
  http.delete(`/databases/${encodeURIComponent(databaseName)}/tables/${encodeURIComponent(tableName)}/constraints/${encodeURIComponent(constraintName)}`)

export const queryRecords = (databaseName, tableName, payload) =>
  http.post(`/databases/${encodeURIComponent(databaseName)}/tables/${encodeURIComponent(tableName)}/records/query`, payload)

export const insertRecord = (databaseName, tableName, payload) =>
  http.post(`/databases/${encodeURIComponent(databaseName)}/tables/${encodeURIComponent(tableName)}/records`, payload)

export const updateRecord = (databaseName, tableName, payload) =>
  http.put(`/databases/${encodeURIComponent(databaseName)}/tables/${encodeURIComponent(tableName)}/records`, payload)

export const deleteRecord = (databaseName, tableName, payload) =>
  http.delete(`/databases/${encodeURIComponent(databaseName)}/tables/${encodeURIComponent(tableName)}/records`, { data: payload })

export const executeSql = (payload) => http.post('/sql/execute', payload)

export const login = (payload) => http.post('/auth/login', payload)

export const listBackups = (databaseName) =>
  http.get(`/databases/${encodeURIComponent(databaseName)}/backups`)

export const createBackup = (databaseName) =>
  http.post(`/databases/${encodeURIComponent(databaseName)}/backups`)

export const restoreBackup = (databaseName, backupName) =>
  http.post(`/databases/${encodeURIComponent(databaseName)}/backups/${encodeURIComponent(backupName)}/restore`)

export const deleteBackup = (databaseName, backupName) =>
  http.delete(`/databases/${encodeURIComponent(databaseName)}/backups/${encodeURIComponent(backupName)}`)
