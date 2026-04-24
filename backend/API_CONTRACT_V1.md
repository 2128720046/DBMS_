# API Contract V1 (2-week delivery freeze)

## 1. Scope
This version freezes the minimum API set for frontend integration:
- Database (schema) management
- Table management
- Record CRUD

No index management or advanced constraints in this version.

## 2. Unified Response
All endpoints return:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {}
}
```

Error codes:
- `BAD_REQUEST`
- `DATA_ACCESS_ERROR`
- `INTERNAL_ERROR`

## 3. Database APIs
### 3.1 Create database
- `POST /api/databases`
- Request:
```json
{
  "dbName": "demo"
}
```

### 3.2 List databases
- `GET /api/databases`

### 3.3 Drop database
- `DELETE /api/databases/{databaseName}`

## 4. Table APIs
### 4.1 Create table
- `POST /api/databases/{databaseName}/tables`
- Request:
```json
{
  "tableName": "users",
  "columns": [
    { "name": "id", "type": "INT", "nullable": false },
    { "name": "name", "type": "VARCHAR(100)", "nullable": true }
  ]
}
```

### 4.2 List tables
- `GET /api/databases/{databaseName}/tables`

### 4.3 Drop table
- `DELETE /api/databases/{databaseName}/tables/{tableName}`

## 5. Record APIs
### 5.1 Query records
- `POST /api/databases/{databaseName}/tables/{tableName}/records/query`
- Request:
```json
{
  "filters": { "name": "Alice" },
  "limit": 50,
  "offset": 0
}
```

### 5.2 Insert record
- `POST /api/databases/{databaseName}/tables/{tableName}/records`
- Request:
```json
{
  "values": { "id": 1, "name": "Alice" }
}
```

### 5.3 Update record
- `PUT /api/databases/{databaseName}/tables/{tableName}/records`
- Request:
```json
{
  "filters": { "id": 1 },
  "values": { "name": "Alice 2" }
}
```

### 5.4 Delete record
- `DELETE /api/databases/{databaseName}/tables/{tableName}/records`
- Request:
```json
{
  "filters": { "id": 1 }
}
```

## 6. Safety Rules
- Identifier whitelist: starts with letter, then letters/digits/underscore.
- All identifiers are validated and quoted.
- All values use prepared parameters.
- Update/Delete require filters to avoid full-table operations.
