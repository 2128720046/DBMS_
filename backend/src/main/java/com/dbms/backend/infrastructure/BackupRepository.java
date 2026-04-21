package com.dbms.backend.infrastructure;

import com.dbms.backend.common.OperationResult;
import com.dbms.backend.model.BackupOptions;
import java.util.List;

/**
 * 备份仓储接口。
 * <p>
 * 职责：定义备份包创建、恢复、查询和删除协议。
 * 调用方：备份领域服务。
 */
public interface BackupRepository {

	/**
	 * 创建备份包。
	 *
	 * @param databaseName 目标数据库名称
	 * @param backupOptions 备份参数
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> createBackupPackage(String databaseName, BackupOptions backupOptions);

	/**
	 * 恢复备份包。
	 *
	 * @param backupName 备份名称
	 * @param targetDatabaseName 目标数据库名称
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> extractBackupPackage(String backupName, String targetDatabaseName);

	/**
	 * 查询备份包列表。
	 *
	 * @param databaseName 目标数据库名称
	 * @return 统一操作结果，data 中返回备份包列表
	 */
	OperationResult<List<String>> listBackupPackages(String databaseName);

	/**
	 * 删除备份包。
	 *
	 * @param backupName 备份名称
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> deleteBackupPackage(String backupName);
}
