package com.dbms.backend.modules.index.infrastructure;

import com.dbms.backend.modules.index.domain.IndexGateway;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 索引网关占位实现。
 * <p>
 * 文件已提前创建。后续实现时在本类内补充 .tid 索引描述文件和 .ix 索引数据文件的
 * 读写逻辑即可，不需要新增接口或改 SQL 执行层。
 * </p>
 */
@Repository
public class TodoIndexGatewayImpl implements IndexGateway {

    @Override
    public void createIndex(String schemaName, String tableName, String indexName,
                            List<String> columns, boolean unique, boolean ascending) {
        throw new UnsupportedOperationException("索引创建功能尚未实现，请在 TodoIndexGatewayImpl#createIndex 中补充代码");
    }

    @Override
    public void dropIndex(String schemaName, String tableName, String indexName) {
        throw new UnsupportedOperationException("索引删除功能尚未实现，请在 TodoIndexGatewayImpl#dropIndex 中补充代码");
    }

    @Override
    public List<Map<String, Object>> listIndexes(String schemaName, String tableName) {
        return List.of();
    }

    @Override
    public void rebuildIndex(String schemaName, String tableName, String indexName) {
        throw new UnsupportedOperationException("索引重建功能尚未实现，请在 TodoIndexGatewayImpl#rebuildIndex 中补充代码");
    }
}
