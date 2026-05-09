package com.dbms.backend.modules.transaction.infrastructure;

import com.dbms.backend.modules.transaction.domain.TransactionManagerPort;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事务管理内存实现（方案 A：按库名追踪活跃事务）。
 * <p>
 * 维护两重映射：
 * <ul>
 *   <li>{@code activeTransactions} — 所有活跃事务 ID 集合</li>
 *   <li>{@code dbActiveTx} — 数据库名称 → 当前活跃事务 ID（一个库同时只支持一个活跃事务）</li>
 * </ul>
 * </p>
 * <p>
 * {@code commit()} 和 {@code rollback()} 兼容两种入参：直接传事务 ID，或传数据库名称。
 * 因为当前 SqlExecutor 对 BEGIN 返回 txId，但对 COMMIT/ROLLBACK 传的是数据库名称。
 * </p>
 */
@Component
public class InMemoryTransactionManagerPort implements TransactionManagerPort {

    /** 所有活跃事务 ID 集合 */
    private final Set<String> activeTransactions = ConcurrentHashMap.newKeySet();

    /** 数据库名称 → 当前活跃事务 ID（一个库同时只有一个活跃事务） */
    private final Map<String, String> dbActiveTx = new ConcurrentHashMap<>();

    @Override
    public String begin(String schemaName) {
        String txId = schemaName + "-" + UUID.randomUUID();
        // 如果该库已有活跃事务，先清理旧事务
        String oldTx = dbActiveTx.remove(schemaName);
        if (oldTx != null) {
            activeTransactions.remove(oldTx);
        }
        dbActiveTx.put(schemaName, txId);
        activeTransactions.add(txId);
        return txId;
    }

    @Override
    public void commit(String transactionIdOrDbName) {
        // 优先按事务 ID 查找
        if (activeTransactions.contains(transactionIdOrDbName)) {
            activeTransactions.remove(transactionIdOrDbName);
            // 清理 dbActiveTx 中的对应条目
            dbActiveTx.values().remove(transactionIdOrDbName);
            return;
        }
        // 按数据库名称查找（兼容 SqlExecutor 传入库名的情况）
        String txId = dbActiveTx.remove(transactionIdOrDbName);
        if (txId != null) {
            activeTransactions.remove(txId);
        }
    }

    @Override
    public void rollback(String transactionIdOrDbName) {
        // commit 与 rollback 逻辑相同，都是移除事务
        commit(transactionIdOrDbName);
    }
}
