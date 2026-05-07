package com.dbms.backend.modules.transaction.domain;

/**
 * 事务模块端口。
 * <p>
 * 该接口对应验收要求 3.8，用于隔离事务生命周期、锁、日志和回滚策略。当前记录模块
 * 仍直接写文件，后续实现事务时应由应用服务在写入前后调用本端口，而不是让记录模块
 * 直接知道具体事务实现。
 * </p>
 */
public interface TransactionManagerPort {

    /**
     * 开启事务并返回事务标识。
     *
     * @param schemaName 数据库名称
     * @return 事务 ID
     */
    String begin(String schemaName);

    /**
     * 提交事务，使事务内变更对外可见。
     *
     * @param transactionId 事务 ID
     */
    void commit(String transactionId);

    /**
     * 回滚事务，撤销事务内尚未提交的变更。
     *
     * @param transactionId 事务 ID
     */
    void rollback(String transactionId);
}
