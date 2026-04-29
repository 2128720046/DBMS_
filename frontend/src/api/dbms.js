// src/api/dbms.js
import http from './http'

export const listDatabases = () => http.get('/databases')
export const createDatabase = (data) => http.post('/databases', data)
export const dropDatabase = (name) => http.delete(`/databases/${name}`)

export const listTables = (db) => http.get(`/databases/${db}/tables`)
export const getTableDetail = (db, table) => http.get(`/databases/${db}/tables/${table}`)
export const createTable = (db, data) => http.post(`/databases/${db}/tables`, data)
export const updateTableStructure = (db, table, data) => http.put(`/databases/${db}/tables/${table}`, data)
export const dropTable = (db, table) => http.delete(`/databases/${db}/tables/${table}`)

export const queryRecords = (db, table, data) => http.post(`/databases/${db}/tables/${table}/records/query`, data)
export const insertRecord = (db, table, data) => http.post(`/databases/${db}/tables/${table}/records`, data)
export const updateRecord = (db, table, data) => http.put(`/databases/${db}/tables/${table}/records`, data)
export const deleteRecord = (db, table, data) => http.delete(`/databases/${db}/tables/${table}/records`, data)

export const executeSql = (data) => http.post('/sql/execute', data)

export const listConstraints = (db, table) => http.get(`/databases/${db}/tables/${table}/constraints`)
export const checkConstraints = (db, table) => http.post(`/databases/${db}/tables/${table}/constraints/check`)
export const dropConstraint = (db, table, constraintName) => http.delete(`/databases/${db}/tables/${table}/constraints/${constraintName}`)

export const listIndexes = (db, table) => http.get(`/databases/${db}/tables/${table}/indexes`)
export const createIndex = (db, table, data) => http.post(`/databases/${db}/tables/${table}/indexes`, data)
export const dropIndex = (db, table, indexName) => http.delete(`/databases/${db}/tables/${table}/indexes/${indexName}`)
export const rebuildIndex = (db, table, indexName) => http.post(`/databases/${db}/tables/${table}/indexes/${indexName}/rebuild`)

export const listBackups = (db) => http.get(`/databases/${db}/backups`)
export const createBackup = (db) => http.post(`/databases/${db}/backups`)
export const restoreBackup = (db, backupName) => http.post(`/databases/${db}/backups/${backupName}/restore`)
export const deleteBackup = (db, backupName) => http.delete(`/databases/${db}/backups/${backupName}`)

export const login = (data) => http.post('/auth/login', data)
export const register = (data) => http.post('/auth/register', data)
