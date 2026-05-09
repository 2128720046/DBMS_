package com.dbms.backend.modules.transaction.infrastructure;

import com.dbms.backend.modules.transaction.domain.TransactionManagerPort;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事务管理内存实现（方案 A：按库名追踪活跃事务）。
 * <p>
 * 维护三重映射：
 * <ul>
 *   <li>{@code activeTransactions} — 所有活跃事务 ID 集合</li>
 *   <li>{@code dbActiveTx} — 数据库名称 → 当前活跃事务 ID（一个库同时只支持一个活跃事务）</li>
 *   <li>{@code undoLogs} — 事务 ID → 撤销操作栈（支持真正的 rollback 逆序撤销）</li>
 * </ul>
 * </p>
 * <p>
 * {@code commit()} 和 {@code rollback()} 兼容两种入参：直接传事务 ID，或传数据库名称。
 * 因为当前 SqlExecutor 对 BEGIN 返回 txId，但对 COMMIT/ROLLBACK 传的是数据库名称。
 * </p>
 * <p>
 * 真正的回滚实现：
 * <ul>
 *   <li>{@code commit()} — 清除 undo 日志，保留数据变更</li>
 *   <li>{@code rollback()} — 逆序执行所有注册的撤销操作，然后清除日志</li>
 * </ul>
 * </p>
 */
@Component
public class InMemoryTransactionManagerPort implements TransactionManagerPort {

    /** 所有活跃事务 ID 集合 */
    private final Set<String> activeTransactions = ConcurrentHashMap.newKeySet();

    /** 数据库名称 → 当前活跃事务 ID（一个库同时只有一个活跃事务） */
    private final Map<String, String> dbActiveTx = new ConcurrentHashMap<>();

    /**
     * 事务 ID → 撤销操作栈（Deque 支持逆序回滚）。
     * 外部模块通过 {@link #recordUndoOperation(String, Runnable)} 注册撤销操作。
     */
    private final Map<String, Deque<Runnable>> undoLogs = new ConcurrentHashMap<>();

    @Override
    public String begin(String schemaName) {
        String txId = schemaName + "-" + UUID.randomUUID();
        // 如果该库已有活跃事务，先清理旧事务
        String oldTx = dbActiveTx.remove(schemaName);
        if (oldTx != null) {
            activeTransactions.remove(oldTx);
            undoLogs.remove(oldTx);
        }
        dbActiveTx.put(schemaName, txId);
        activeTransactions.add(txId);
        return txId;
    }

    @Override
    public void commit(String transactionIdOrDbName) {
        String txId = resolveTransactionId(transactionIdOrDbName);
        if (txId != null) {
            // 清除 undo 日志，保留数据变更
            undoLogs.remove(txId);
            activeTransactions.remove(txId);
            dbActiveTx.values().remove(txId);
        }
    }

    @Override
    public void rollback(String transactionIdOrDbName) {
        String txId = resolveTransactionId(transactionIdOrDbName);
        if (txId != null) {
            // 逆序执行所有撤销操作（后进先出）
            Deque<Runnable> operations = undoLogs.remove(txId);
            if (operations != null) {
                Iterator<Runnable> it = operations.descendingIterator();
                while (it.hasNext()) {
                    try {
                        it.next().run();
                    } catch (Exception e) {
                        // 单个撤销操作失败不影响后续撤销
                        System.err.println("[Transaction Rollback] Undo operation failed: " + e.getMessage());
                    }
                }
            }
            activeTransactions.remove(txId);
            dbActiveTx.values().remove(txId);
        }
    }

    @Override
    public void recordUndoOperation(String transactionId, Runnable undoAction) {
        if (transactionId == null || undoAction == null) {
            return;
        }
        if (activeTransactions.contains(transactionId)) {
            undoLogs.computeIfAbsent(transactionId, k -> new LinkedList<>()).addLast(undoAction);
        }
    }

    /**
     * 解析事务参数：兼容传入事务 ID 和数据库名称两种格式。
     *
     * @param transactionIdOrDbName 事务 ID 或数据库名称
     * @return 事务 ID（如果找不到返回 null）
     */
    private String resolveTransactionId(String transactionIdOrDbName) {
        if (transactionIdOrDbName == null) {
            return null;
        }
        // 优先按事务 ID 查找
        if (activeTransactions.contains(transactionIdOrDbName)) {
            return transactionIdOrDbName;
        }
        // 按数据库名称查找
        return dbActiveTx.get(transactionIdOrDbName);
    }
}
