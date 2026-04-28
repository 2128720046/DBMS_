package com.dbms.backend.dto;

import java.util.List;
import java.util.Map;

public class CreateRecordRequest {

    /**
     * 兼容旧字段：单条记录键值对。
     */
    private Map<String, Object> values;
    /**
     * 对齐协议字段：支持批量 records。
     */
    private List<Map<String, Object>> records;

    /**
     * @return 实际用于写入的第一条记录。
     */
    public Map<String, Object> getValues() {
        if (values != null && !values.isEmpty()) {
            return values;
        }
        if (records != null && !records.isEmpty()) {
            return records.get(0);
        }
        return values;
    }

    /**
     * @param values 旧协议单条记录。
     */
    public void setValues(Map<String, Object> values) {
        this.values = values;
    }

    /**
     * @return 批量记录。
     */
    public List<Map<String, Object>> getRecords() {
        return records;
    }

    /**
     * @param records 协议中的 records 数组。
     */
    public void setRecords(List<Map<String, Object>> records) {
        this.records = records;
    }
}
