package com.dbms.backend.model;

import java.time.LocalDateTime;

/**
 * 用户会话对象?
 * <p>
 * 该对象用于表示用户登录后的运行时会话状态，主要包含会话编号、用户名、角色?
 * 登录时间以及当前正在访问的数据库?
 */
public class UserSession {

    private String sessionId;
    private String userName;
    private String role;
    private LocalDateTime loginTime;
    private String currentDatabase;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public LocalDateTime getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(LocalDateTime loginTime) {
        this.loginTime = loginTime;
    }

    public String getCurrentDatabase() {
        return currentDatabase;
    }

    public void setCurrentDatabase(String currentDatabase) {
        this.currentDatabase = currentDatabase;
    }
}
