package com.dbms.backend.core.query;

/**
 * 单个比较谓词：column op literal。
 */
public record FilterPredicate(String column, Operator operator, Object value) {
}
