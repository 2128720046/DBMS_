package com.dbms.backend.dto;

import java.util.Map;

public class UpdateRecordRequest {

    /**
     * 兼容旧字段：过滤条件。
     */
    private Map<String, Object> filters;
    /**
     * 兼容旧字段：更新值。
     */
    private Map<String, Object> values;
    /**
     * 协议字段：where。
     */
    private Map<String, Object> where;
    /**
     * 协议字段：updates。
     */
    private Map<String, Object> updates;

    /**
     * @return 最终过滤条件，优先 where，兼容 filters。
     */
    public Map<String, Object> getFilters() {
        return where != null && !where.isEmpty() ? where : filters;
    }

    /**
     * @param filters 旧字段过滤条件。
     */
    public void setFilters(Map<String, Object> filters) {
        this.filters = filters;
    }

    /**
     * @return 最终更新字段，优先 updates，兼容 values。
     */
    public Map<String, Object> getValues() {
        return updates != null && !updates.isEmpty() ? updates : values;
    }

    /**
     * @param values 旧字段更新值。
     */
    public void setValues(Map<String, Object> values) {
        this.values = values;
    }

    /**
     * @return 协议字段 where。
     */
    public Map<String, Object> getWhere() {
        return where;
    }

    /**
     * @param where 协议 where 条件。
     */
    public void setWhere(Map<String, Object> where) {
        this.where = where;
    }

    /**
     * @return 协议字段 updates。
     */
    public Map<String, Object> getUpdates() {
        return updates;
    }

    /**
     * @param updates 协议更新值。
     */
    public void setUpdates(Map<String, Object> updates) {
        this.updates = updates;
    }
}
