import http from './http'

export const listDatabases = () => http.get('/databases')
export const createDatabase = (payload) => http.post('/databases', payload)
export const dropDatabase = (databaseName) => http.delete(`/databases/${encodeURIComponent(databaseName)}`)

export const listTables = (databaseName) =>
  http.get(`/databases/${encodeURIComponent(databaseName)}/tables`)

export const createTable = (databaseName, payload) =>
  http.post(`/databases/${encodeURIComponent(databaseName)}/tables`, payload)

export const dropTable = (databaseName, tableName) =>
  http.delete(`/databases/${encodeURIComponent(databaseName)}/tables/${encodeURIComponent(tableName)}`)

export const getTableDetail = (databaseName, tableName) =>
  http.get(`/databases/${encodeURIComponent(databaseName)}/tables/${encodeURIComponent(tableName)}`)

export const queryRecords = (databaseName, tableName, payload) =>
  http.post(`/databases/${encodeURIComponent(databaseName)}/tables/${encodeURIComponent(tableName)}/records/query`, payload)

export const insertRecord = (databaseName, tableName, payload) =>
  http.post(`/databases/${encodeURIComponent(databaseName)}/tables/${encodeURIComponent(tableName)}/records`, payload)

export const updateRecord = (databaseName, tableName, payload) =>
  http.put(`/databases/${encodeURIComponent(databaseName)}/tables/${encodeURIComponent(tableName)}/records`, payload)

export const deleteRecord = (databaseName, tableName, payload) =>
  http.delete(`/databases/${encodeURIComponent(databaseName)}/tables/${encodeURIComponent(tableName)}/records`, { data: payload })

export const executeSql = (payload) => http.post('/sql/execute', payload)
