package com.dbms.backend.modules.maintenance.domain;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 数据库维护模块网关。
 * <p>
 * 该接口对应验收要求 3.10，负责数据库备份、还原以及后续可扩展的压缩、校验和修复。
 * 实现应只依赖数据库文件目录，不反向调用 Controller。
 * </p>
 */
public interface MaintenanceGateway {

    /**
     * 查询指定数据库已有备份。
     *
     * @param schemaName 数据库名称
     * @return 备份元数据列表，字段包括 name、size、updatedAt、desc 等
     */
    List<Map<String, Object>> listBackups(String schemaName);

    /**
     * 备份指定数据库的全部文件。
     *
     * @param schemaName 数据库名称
     * @param targetDir  备份目标目录
     * @return 生成的备份文件或目录路径
     */
    Path backupSchema(String schemaName, Path targetDir);

    /**
     * 从备份恢复指定数据库。
     *
     * @param schemaName 数据库名称
     * @param backupPath 备份文件或目录路径
     */
    void restoreSchema(String schemaName, Path backupPath);

    /**
     * 删除指定备份。
     *
     * @param schemaName 数据库名称
     * @param backupName 备份名称
     */
    void deleteBackup(String schemaName, String backupName);
}
