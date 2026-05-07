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
}
