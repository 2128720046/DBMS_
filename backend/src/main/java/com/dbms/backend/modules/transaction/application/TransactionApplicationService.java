package com.dbms.backend.modules.transaction.application;

import com.dbms.backend.core.naming.DatabaseDomainService;
import com.dbms.backend.modules.transaction.domain.TransactionManagerPort;
import org.springframework.stereotype.Service;

/**
 * 事务用例服务。
 */
@Service
public class TransactionApplicationService {

    private final DatabaseDomainService naming;
    private final TransactionManagerPort transactionManager;

    public TransactionApplicationService(DatabaseDomainService naming, TransactionManagerPort transactionManager) {
        this.naming = naming;
        this.transactionManager = transactionManager;
    }

    /**
     * 开启事务。
     */
    public String begin(String databaseName) {
        return transactionManager.begin(naming.normalizeDatabaseName(databaseName));
    }

    /**
     * 提交事务。
     */
    public void commit(String transactionId) {
        transactionManager.commit(transactionId);
    }

    /**
     * 回滚事务。
     */
    public void rollback(String transactionId) {
        transactionManager.rollback(transactionId);
    }

    /**
     * 在事务中记录一个撤销操作。
     * <p>
     * 外部模块（如 record 模块）在执行 insert/update/delete 时应当调用此方法，
     * 在事务内注册撤销该操作的逆操作。
     * </p>
     *
     * @param transactionId 事务 ID
     * @param undoAction    撤销操作（Runnable）
     */
    public void recordUndoOperation(String transactionId, Runnable undoAction) {
        transactionManager.recordUndoOperation(transactionId, undoAction);
    }
}
