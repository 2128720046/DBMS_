package com.dbms.backend.application;

import com.dbms.backend.common.OperationResult;
import com.dbms.backend.model.BackupOptions;
import java.util.List;

/**
 * 备份应用服务接口。
 * <p>
 * 职责：暴露备份、恢复、查询备份列表与删除备份的流程入口。
 * 调用方：系统维护控制器。
 */
public interface BackupApplicationService {

	/**
	 * 备份数据库。
	 *
	 * @param databaseName 目标数据库名称
	 * @param backupOptions 备份参数
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> backupDatabase(String databaseName, BackupOptions backupOptions);

	/**
	 * 恢复数据库。
	 *
	 * @param backupName 备份名称
	 * @param targetDatabaseName 恢复后的目标数据库名称
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> restoreDatabase(String backupName, String targetDatabaseName);

	/**
	 * 查询备份列表。
	 *
	 * @param databaseName 目标数据库名称
	 * @return 统一操作结果，data 中返回备份名称列表
	 */
	OperationResult<List<String>> listBackups(String databaseName);

	/**
	 * 删除备份。
	 *
	 * @param backupName 备份名称
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> deleteBackup(String backupName);
}
