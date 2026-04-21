package com.dbms.backend.application;

import com.dbms.backend.common.OperationResult;
import com.dbms.backend.model.QueryCondition;
import com.dbms.backend.model.RecordData;
import com.dbms.backend.model.UserSession;
import java.util.List;
import java.util.Map;

/**
 * 记录应用服务接口。
 * <p>
 * 职责：为记录增删改查提供统一流程入口，协调完整性校验、索引同步和事务边界控制。
 * 调用方：记录管理控制器。
 */
public interface RecordApplicationService {

    /**
     * 插入记录。
     *
     * @param databaseName 所属数据库名称
     * @param tableName 目标表名称
     * @param recordData 待插入记录
     * @param session 当前会话信息
     * @return 统一操作结果，成功时不携带额外数据
     */
    OperationResult<Void> insertRecord(String databaseName, String tableName, RecordData recordData, UserSession session);

    /**
     * 更新记录集合。
     *
     * @param databaseName 所属数据库名称
     * @param tableName 目标表名称
     * @param condition 更新筛选条件
     * @param updateValues 字段更新值映射
     * @param session 当前会话信息
     * @return 统一操作结果，成功时不携带额外数据
     */
    OperationResult<Void> updateRecords(
	    String databaseName,
	    String tableName,
	    QueryCondition condition,
	    Map<String, Object> updateValues,
	    UserSession session);

    /**
     * 删除记录集合。
     *
     * @param databaseName 所属数据库名称
     * @param tableName 目标表名称
     * @param condition 删除筛选条件
     * @param session 当前会话信息
     * @return 统一操作结果，成功时不携带额外数据
     */
    OperationResult<Void> deleteRecords(String databaseName, String tableName, QueryCondition condition, UserSession session);

    /**
     * 查询记录。
     *
     * @param databaseName 所属数据库名称
     * @param tableName 目标表名称
     * @param condition 查询条件
     * @param selectedFields 需要返回的字段列表
     * @param session 当前会话信息
     * @return 统一操作结果，data 中返回记录列表
     */
    OperationResult<List<RecordData>> queryRecords(
	    String databaseName,
	    String tableName,
	    QueryCondition condition,
	    List<String> selectedFields,
	    UserSession session);
}
