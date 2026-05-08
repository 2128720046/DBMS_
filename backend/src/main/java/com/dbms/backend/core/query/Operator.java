package com.dbms.backend.core.query;

import java.util.Locale;

/**
 * WHERE 条件比较运算符。
 */
public enum Operator {
    EQ,
    NE,
    GT,
    LT,
    GTE,
    LTE;

    public static Operator fromSymbol(String symbol) {
        if (symbol == null) {
            return EQ;
        }
        String s = symbol.trim().toUpperCase(Locale.ROOT);
        return switch (s) {
            case "=" -> EQ;
            case "!=", "<>" -> NE;
            case ">" -> GT;
            case "<" -> LT;
            case ">=" -> GTE;
            case "<=" -> LTE;
            default -> EQ;
        };
    }
}
