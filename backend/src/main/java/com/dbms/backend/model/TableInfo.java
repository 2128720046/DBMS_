package com.dbms.backend.model;

/**
 * Simple table info model for SQL responses.
 */
/**
 * 表信息模型类，用于封装数据表的基本信息。
 * <p>
 * 主要用于在应用层和表示层之间传递表信息。
 * </p>
 *
 * @author DBMS Team
 */
public class TableInfo {

    /** 表名 */
    private String name;

    /**
     * 默认构造方法。
     */
    public TableInfo() {
    }

    /**
     * 构造方法。
     *
     * @param name 表名
     */
    public TableInfo(String name) {
        this.name = name;
    }

    /**
     * 获取表名。
     *
     * @return 表名
     */
    public String getName() {
        return name;
    }

    /**
     * 设置表名。
     *
     * @param name 表名
     */
    public void setName(String name) {
        this.name = name;
    }
}