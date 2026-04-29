package com.dbms.backend.application;

import com.dbms.backend.domain.DatabaseDomainService;
import com.dbms.backend.domain.spi.TableGateway;
import com.dbms.backend.dto.ColumnDefinition;
import com.dbms.backend.infrastructure.storage.config.StorageEngineConfig;
import com.dbms.backend.infrastructure.storage.io.BinaryIoUtils;
import com.dbms.backend.model.TableInfo;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TableApplicationService {

    private final DatabaseDomainService domainService;
    private final TableGateway tableGateway;

    public TableApplicationService(DatabaseDomainService domainService, TableGateway tableGateway) {
        this.domainService = domainService;
        this.tableGateway = tableGateway;
    }

    public void createTable(String databaseName, String tableName, List<ColumnDefinition> columns) {
        // Normalize identifiers first so H2 metadata and later lookups use the same schema/table form.
        String normalizedDb = domainService.normalizeDatabaseName(databaseName);
        String normalizedTable = domainService.normalizeIdentifier(tableName);
        tableGateway.createTable(normalizedDb, normalizedTable, columns);
    }

    public void updateTableStructure(String databaseName, String tableName, List<ColumnDefinition> columns) {
        // Structure changes must target the same canonical object names as the original create call.
        String normalizedDb = domainService.normalizeDatabaseName(databaseName);
        String normalizedTable = domainService.normalizeIdentifier(tableName);
        tableGateway.alterTableStructure(normalizedDb, normalizedTable, columns);
    }

    public void dropTable(String databaseName, String tableName) {
        // Drop the canonical identifier so case mismatches do not leave orphan metadata behind.
        String normalizedDb = domainService.normalizeDatabaseName(databaseName);
        String normalizedTable = domainService.normalizeIdentifier(tableName);
        tableGateway.dropTable(normalizedDb, normalizedTable);
    }

    public List<TableInfo> listTables(String databaseName) {
        String normalizedDb = domainService.normalizeDatabaseName(databaseName);
        return tableGateway.listTables(normalizedDb).stream().map(TableInfo::new).toList();
    }

    public Map<String, Object> getTableDetail(String databaseName, String tableName) {
        // 读取 .tdf 文件返回列信息
        Map<String, Object> detail = new HashMap<>();
        detail.put("tableName", tableName);
        
        try {
            List<Map<String, Object>> columns = readColumnDefinitions(databaseName, tableName);
            detail.put("columns", columns);
        } catch (Exception e) {
            detail.put("columns", new ArrayList<Map<String, Object>>());
            detail.put("error", "读取表定义失败：" + e.getMessage());
        }
        
        return detail;
    }
    
    /**
     * 从 .tdf 文件读取列定义
     */
    private List<Map<String, Object>> readColumnDefinitions(String databaseName, String tableName) throws IOException {
        String tdfPath = StorageEngineConfig.getDATA_DIR() 
            + File.separator + databaseName + File.separator + tableName + ".tdf";
        File tdfFile = new File(tdfPath);
        
        if (!tdfFile.exists()) {
            throw new IllegalArgumentException("表定义文件不存在：" + tdfPath);
        }
        
        List<Map<String, Object>> columns = new ArrayList<>();
        
        try (RandomAccessFile raf = new RandomAccessFile(tdfFile, "r")) {
            long fileLength = raf.length();
            long pos = 0;
            
            while (pos < fileLength) {
                raf.seek(pos);
                
                int order = raf.readInt();  // order
                String name = BinaryIoUtils.readFixedString(raf, 128);  // name
                int typeCode = raf.readInt();  // type
                int param = raf.readInt();  // param
                long mtime = BinaryIoUtils.readDateTime(raf);  // mtime
                int integrities = raf.readInt();  // integrities
                
                pos += 160;  // FIELD_BLOCK_SIZE
                
                if (!name.isEmpty()) {
                    Map<String, Object> column = new HashMap<>();
                    column.put("order", order);
                    column.put("name", name);
                    column.put("type", getTypeName(typeCode));
                    column.put("typeCode", typeCode);
                    column.put("length", param > 0 ? param : null);
                    column.put("nullable", (integrities & 1) == 0);  // bit0=0 表示允许空
                    column.put("primaryKey", (integrities & 2) != 0);  // bit1=1 表示主键
                    
                    columns.add(column);
                }
            }
        }
        
        return columns;
    }
    
    /**
     * 将类型代码转换为类型名称
     */
    private String getTypeName(int typeCode) {
        if (typeCode == 1) {
            return "INT";
        } else if (typeCode == 2) {
            return "BOOL";
        } else if (typeCode == 3) {
            return "DOUBLE";
        } else if (typeCode == 4) {
            return "VARCHAR";
        } else if (typeCode == 5) {
            return "DATETIME";
        } else {
            return "UNKNOWN";
        }
    }
}