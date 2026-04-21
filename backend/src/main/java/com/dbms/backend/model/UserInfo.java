package com.dbms.backend.model;

/**
 * 用户信息对象?
 * <p>
 * 该对象用于表示系统中的一个用户账号，主要包含用户名、密码摘要、角色和状态等内容?
 * 供登录、权限校验和用户管理模块使用?
 */
public class UserInfo {

    private String userName;
    private String passwordHash;
    private String role;
    private String status;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
