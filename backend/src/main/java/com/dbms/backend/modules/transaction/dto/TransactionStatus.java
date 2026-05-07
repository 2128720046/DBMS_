package com.dbms.backend.modules.transaction.dto;

/**
 * 事务状态返回对象。
 */
public class TransactionStatus {
    private String transactionId;
    private String status;

    public TransactionStatus() {
    }

    public TransactionStatus(String transactionId, String status) {
        this.transactionId = transactionId;
        this.status = status;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
