package com.dbms.backend.modules.transaction.infrastructure;

import com.dbms.backend.modules.transaction.domain.TransactionManagerPort;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事务管理占位实现。
 * <p>
 * 当前只维护事务 ID，不执行真实日志暂存、提交刷盘和回滚。后续可在本类内接入 .log
 * 文件和锁管理。
 * </p>
 */
@Component
public class InMemoryTransactionManagerPort implements TransactionManagerPort {

    private final Set<String> activeTransactions = ConcurrentHashMap.newKeySet();

    @Override
    public String begin(String schemaName) {
        String txId = schemaName + "-" + UUID.randomUUID();
        activeTransactions.add(txId);
        return txId;
    }

    @Override
    public void commit(String transactionId) {
        activeTransactions.remove(transactionId);
    }

    @Override
    public void rollback(String transactionId) {
        activeTransactions.remove(transactionId);
    }
}
