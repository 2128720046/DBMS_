package com.dbms.backend.model;

/**
 * Simple database info model for SQL responses.
 */
/**
 * 数据库信息模型类，用于封装数据库的基本信息。
 * <p>
 * 主要用于在应用层和表示层之间传递数据库信息。
 * </p>
 *
 * @author DBMS Team
 */
public class DatabaseInfo {

    /** 数据库名称 */
    private String name;

    /**
     * 默认构造方法。
     */
    public DatabaseInfo() {
    }

    /**
     * 构造方法。
     *
     * @param name 数据库名称
     */
    public DatabaseInfo(String name) {
        this.name = name;
    }

    /**
     * 获取数据库名称。
     *
     * @return 数据库名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置数据库名称。
     *
     * @param name 数据库名称
     */
    public void setName(String name) {
        this.name = name;
    }
}