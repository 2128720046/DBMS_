package com.dbms.backend.dto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryRecordRequest {

    /**
     * 兼容旧字段：直接等值过滤。
     */
    private Map<String, Object> filters;
    /**
     * 兼容旧字段：limit。
     */
    private Integer limit = 50;
    /**
     * 兼容旧字段：offset。
     */
    private Integer offset = 0;
    /**
     * 协议字段：页码，从 1 开始。
     */
    private Integer page = 1;
    /**
     * 协议字段：每页条数。
     */
    private Integer size = 50;
    /**
     * 协议字段：条件列表。
     */
    private List<Condition> conditions;

    /**
     * @return 统一过滤条件；若条件列表存在，则提取等值条件映射为 filters。
     */
    public Map<String, Object> getFilters() {
        if (filters != null && !filters.isEmpty()) {
            return filters;
        }
        if (conditions == null || conditions.isEmpty()) {
            return filters;
        }
        Map<String, Object> mapped = new HashMap<>();
        for (Condition condition : conditions) {
            if (condition == null || condition.getField() == null || condition.getField().isBlank()) {
                continue;
            }
            if ("=".equals(condition.getOp())) {
                mapped.put(condition.getField(), condition.getValue());
            }
        }
        return mapped;
    }

    /**
     * @param filters 旧字段过滤条件。
     */
    public void setFilters(Map<String, Object> filters) {
        this.filters = filters;
    }

    /**
     * @return 查询限制条数；优先协议 size。
     */
    public Integer getLimit() {
        return size != null ? size : limit;
    }

    /**
     * @param limit 旧字段 limit。
     */
    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    /**
     * @return 查询偏移量；若提供 page/size，则自动计算 offset。
     */
    public Integer getOffset() {
        if (page != null && size != null) {
            int safePage = Math.max(page, 1);
            int safeSize = Math.max(size, 1);
            return (safePage - 1) * safeSize;
        }
        return offset;
    }

    /**
     * @param offset 旧字段 offset。
     */
    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    /**
     * @return 协议 page。
     */
    public Integer getPage() {
        return page;
    }

    /**
     * @param page 协议 page 参数。
     */
    public void setPage(Integer page) {
        this.page = page;
    }

    /**
     * @return 协议 size。
     */
    public Integer getSize() {
        return size;
    }

    /**
     * @param size 协议 size 参数。
     */
    public void setSize(Integer size) {
        this.size = size;
    }

    /**
     * @return 协议条件列表。
     */
    public List<Condition> getConditions() {
        return conditions;
    }

    /**
     * @param conditions 协议条件数组。
     */
    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions;
    }

    /**
     * 条件对象，对齐协议 conditions[]。
     */
    public static class Condition {
        private String field;
        private String op;
        private Object value;

        /**
         * @return 字段名。
         */
        public String getField() {
            return field;
        }

        /**
         * @param field 字段名。
         */
        public void setField(String field) {
            this.field = field;
        }

        /**
         * @return 操作符，例如 =。
         */
        public String getOp() {
            return op;
        }

        /**
         * @param op 操作符。
         */
        public void setOp(String op) {
            this.op = op;
        }

        /**
         * @return 条件值。
         */
        public Object getValue() {
            return value;
        }

        /**
         * @param value 条件值。
         */
        public void setValue(Object value) {
            this.value = value;
        }
    }
}
