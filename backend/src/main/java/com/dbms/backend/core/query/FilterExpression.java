package com.dbms.backend.core.query;

import java.util.List;

/**
 * WHERE 表达式：以 DNF（OR-of-AND）形式保存。
 * <p>
 * groups: 每个 group 是一组 AND 条件；groups 之间是 OR。
 * </p>
 */
public record FilterExpression(List<List<FilterPredicate>> groups, boolean hasOr, boolean hasNonEquality) {

    public static FilterExpression empty() {
        return new FilterExpression(List.of(), false, false);
    }

    public boolean isEmpty() {
        return groups == null || groups.isEmpty();
    }
}
