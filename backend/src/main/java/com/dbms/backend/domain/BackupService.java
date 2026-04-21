package com.dbms.backend.domain;

import com.dbms.backend.common.OperationResult;
import com.dbms.backend.model.BackupOptions;

/**
 * 备份领域服务接口。
 * <p>
 * 职责：定义快照创建、恢复与备份可用性校验规则。
 * 调用方：备份应用服务。
 */
public interface BackupService {

	/**
	 * 创建数据库快照。
	 *
	 * @param databaseName 目标数据库名称
	 * @param backupOptions 备份参数
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> createSnapshot(String databaseName, BackupOptions backupOptions);

	/**
	 * 恢复数据库快照。
	 *
	 * @param backupName 备份名称
	 * @param targetDatabaseName 恢复后的目标数据库名称
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> restoreSnapshot(String backupName, String targetDatabaseName);

	/**
	 * 校验备份可用性。
	 *
	 * @param backupName 备份名称
	 * @return 统一操作结果，data=true 表示备份可用
	 */
	OperationResult<Boolean> verifyBackup(String backupName);
}
