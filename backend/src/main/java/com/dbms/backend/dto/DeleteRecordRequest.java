package com.dbms.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Schema(description = "删除记录请求")
public class DeleteRecordRequest {

    /**
     * 兼容旧字段：过滤条件。
     */
    @Schema(description = "过滤条件")
    private Map<String, Object> filters;
    /**
     * 协议字段：ids 列表。
     */
    @Schema(description = "主键 ID 列表")
    private List<Object> ids;

    /**
     * @return 最终删除条件；若传 ids，自动映射为 id = 第一项。
     */
    public Map<String, Object> getFilters() {
        if (filters != null && !filters.isEmpty()) {
            return filters;
        }
        if (ids != null && !ids.isEmpty()) {
            Map<String, Object> mapped = new HashMap<>();
            mapped.put("id", ids.get(0));
            return mapped;
        }
        return filters;
    }

    /**
     * @param filters 旧字段过滤条件。
     */
    public void setFilters(Map<String, Object> filters) {
        this.filters = filters;
    }

    /**
     * @return 协议 ids。
     */
    public List<Object> getIds() {
        return ids;
    }

    /**
     * @param ids 协议 ids 参数。
     */
    public void setIds(List<Object> ids) {
        this.ids = ids;
    }
}
