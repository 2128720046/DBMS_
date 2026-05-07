package com.dbms.backend.modules.index.application;

import com.dbms.backend.core.naming.DatabaseDomainService;
import com.dbms.backend.modules.index.domain.IndexGateway;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 索引用例服务。
 * <p>
 * SQL 执行层只调用本服务，不直接调用 .tid/.ix 文件实现。后续实现索引时，优先填写
 * {@code modules.index.infrastructure} 中的网关代码。
 * </p>
 */
@Service
public class IndexApplicationService {

    private final DatabaseDomainService naming;
    private final IndexGateway indexGateway;

    public IndexApplicationService(DatabaseDomainService naming, IndexGateway indexGateway) {
        this.naming = naming;
        this.indexGateway = indexGateway;
    }

    /**
     * 查询表索引列表。
     */
    public List<Map<String, Object>> listIndexes(String databaseName, String tableName) {
        return indexGateway.listIndexes(naming.normalizeDatabaseName(databaseName), naming.normalizeIdentifier(tableName));
    }

    /**
     * 创建索引。
     */
    public void createIndex(String databaseName, String tableName, String indexName,
                            List<String> columns, boolean unique, boolean ascending) {
        indexGateway.createIndex(naming.normalizeDatabaseName(databaseName), naming.normalizeIdentifier(tableName),
                naming.normalizeIdentifier(indexName), columns.stream().map(naming::normalizeIdentifier).toList(),
                unique, ascending);
    }

    /**
     * 删除索引。
     */
    public void dropIndex(String databaseName, String tableName, String indexName) {
        indexGateway.dropIndex(naming.normalizeDatabaseName(databaseName), naming.normalizeIdentifier(tableName),
                naming.normalizeIdentifier(indexName));
    }

    /**
     * 重建索引数据文件。
     */
    public void rebuildIndex(String databaseName, String tableName, String indexName) {
        indexGateway.rebuildIndex(naming.normalizeDatabaseName(databaseName), naming.normalizeIdentifier(tableName),
                naming.normalizeIdentifier(indexName));
    }
}
