package com.dbms.backend.application;

import com.dbms.backend.common.OperationResult;
import com.dbms.backend.model.ConstraintDefinition;
import com.dbms.backend.model.QueryCondition;
import com.dbms.backend.model.RecordData;
import java.util.List;
import java.util.Map;

/**
 * 完整性应用服务接口。
 * <p>
 * 职责：暴露约束维护与完整性校验流程入口，确保记录写入前执行规则验证。
 * 调用方：记录管理流程和结构管理流程。
 */
public interface IntegrityApplicationService {

    /**
     * 新增约束。
     *
     * @param databaseName 所属数据库名称
     * @param tableName 目标表名称
     * @param constraintDefinition 约束定义
     * @return 统一操作结果，成功时不携带额外数据
     */
    OperationResult<Void> addConstraint(
	    String databaseName,
	    String tableName,
	    ConstraintDefinition constraintDefinition);

    /**
     * 删除约束。
     *
     * @param databaseName 所属数据库名称
     * @param tableName 目标表名称
     * @param constraintName 约束名称
     * @return 统一操作结果，成功时不携带额外数据
     */
    OperationResult<Void> removeConstraint(String databaseName, String tableName, String constraintName);

    /**
     * 查询约束列表。
     *
     * @param databaseName 所属数据库名称
     * @param tableName 目标表名称
     * @return 统一操作结果，data 中返回约束定义列表
     */
    OperationResult<List<ConstraintDefinition>> listConstraints(String databaseName, String tableName);

    /**
     * 插入前校验。
     *
     * @param databaseName 所属数据库名称
     * @param tableName 目标表名称
     * @param recordData 待插入记录
     * @return 统一操作结果，data=true 表示校验通过
     */
    OperationResult<Boolean> validateInsert(String databaseName, String tableName, RecordData recordData);

    /**
     * 更新前校验。
     *
     * @param databaseName 所属数据库名称
     * @param tableName 目标表名称
     * @param condition 更新条件
     * @param updateValues 更新值映射
     * @return 统一操作结果，data=true 表示校验通过
     */
    OperationResult<Boolean> validateUpdate(
	    String databaseName,
	    String tableName,
	    QueryCondition condition,
	    Map<String, Object> updateValues);
}
