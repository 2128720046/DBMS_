package com.dbms.backend.modules.table.application;

import com.dbms.backend.core.naming.DatabaseDomainService;
import com.dbms.backend.modules.table.domain.TableGateway;
import com.dbms.backend.modules.table.dto.ColumnDefinition;
import com.dbms.backend.core.storage.config.StorageEngineConfig;
import com.dbms.backend.core.storage.io.BinaryIoUtils;
import com.dbms.backend.modules.table.model.TableInfo;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据表用例服务：表的创建/修改/删除/查询与表结构读取。
 */
@Service
public class TableApplicationService {

    /** 数据库领域服务，用于名称校验和规范化 */
    private final DatabaseDomainService domainService;

    /** 表网关，负责实际的表结构操作 */
    private final TableGateway tableGateway;

    /**
     * 构造方法。
     *
     * @param domainService 数据库领域服务
     * @param tableGateway  表网关
     */
    public TableApplicationService(DatabaseDomainService domainService, TableGateway tableGateway) {
        this.domainService = domainService;
        this.tableGateway = tableGateway;
    }

    /**
     * 在指定数据库中创建表。
     * <p>
     * 先对数据库名和表名进行规范化，再调用网关创建表结构。
     * </p>
     *
     * @param databaseName 数据库名称
     * @param tableName    表名
     * @param columns      列定义列表
     */
    public void createTable(String databaseName, String tableName, List<ColumnDefinition> columns) {
        String normalizedDb = domainService.normalizeDatabaseName(databaseName);
        String normalizedTable = domainService.normalizeIdentifier(tableName);
        tableGateway.createTable(normalizedDb, normalizedTable, columns);
    }

    /**
     * 更新指定数据库中的表结构。
     *
     * @param databaseName 数据库名称
     * @param tableName    表名
     * @param columns      新的列定义列表
     */
    public void updateTableStructure(String databaseName, String tableName, List<ColumnDefinition> columns) {
        String normalizedDb = domainService.normalizeDatabaseName(databaseName);
        String normalizedTable = domainService.normalizeIdentifier(tableName);
        tableGateway.alterTableStructure(normalizedDb, normalizedTable, columns);
    }

    /**
     * 删除指定数据库中的表。
     *
     * @param databaseName 数据库名称
     * @param tableName    表名
     */
    public void dropTable(String databaseName, String tableName) {
        String normalizedDb = domainService.normalizeDatabaseName(databaseName);
        String normalizedTable = domainService.normalizeIdentifier(tableName);
        tableGateway.dropTable(normalizedDb, normalizedTable);
    }

    /**
     * 获取指定数据库中的所有表列表。
     *
     * @param databaseName 数据库名称
     * @return 表信息列表
     */
    public List<TableInfo> listTables(String databaseName) {
        String normalizedDb = domainService.normalizeDatabaseName(databaseName);
        return tableGateway.listTables(normalizedDb).stream().map(TableInfo::new).toList();
    }

    /**
     * 获取指定表的详细信息。
     * <p>
     * 通过读取自定义 .tdf 文件来获取表的列定义详情。
     * </p>
     *
     * @param databaseName 数据库名称
     * @param tableName    表名
     * @return 包含表名和列定义列表的 Map
     */
    public Map<String, Object> getTableDetail(String databaseName, String tableName) {
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
     * 从 .tdf 文件读取列定义。
     * <p>
     * .tdf 文件是自定义的二进制表定义文件格式，每个字段占用 160 字节（FIELD_BLOCK_SIZE），
     * 包含顺序号、名称（128字节定长字符串）、类型代码、参数、修改时间和完整性约束标志。
     * </p>
     *
     * @param databaseName 数据库名称
     * @param tableName    表名
     * @return 列定义列表
     * @throws IOException 如果读取文件失败
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
                
                int order = raf.readInt();                    // 字段顺序号
                String name = BinaryIoUtils.readFixedString(raf, 128); // 字段名称（128字节定长）
                int typeCode = raf.readInt();                 // 字段类型代码
                int param = raf.readInt();                    // 字段参数（如长度）
                long mtime = BinaryIoUtils.readDateTime(raf); // 最后修改时间
                int integrities = raf.readInt();              // 完整性约束标志位
                
                pos += 160;  // FIELD_BLOCK_SIZE：每个字段块大小
                
                if (!name.isEmpty()) {
                    Map<String, Object> column = new HashMap<>();
                    column.put("order", order);
                    column.put("name", name);
                    column.put("type", getTypeName(typeCode));
                    column.put("typeCode", typeCode);
                    column.put("length", param > 0 ? param : null);
                    column.put("nullable", (integrities & 1) == 0);   // bit0=0 表示允许空
                    column.put("primaryKey", (integrities & 2) != 0); // bit1=1 表示主键
                    
                    columns.add(column);
                }
            }
        }
        
        return columns;
    }
    
    /**
     * 将类型代码转换为对应的类型名称。
     *
     * @param typeCode 类型代码（1~5）
     * @return 类型名称（INT、BOOL、DOUBLE、VARCHAR、DATETIME 或 UNKNOWN）
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


