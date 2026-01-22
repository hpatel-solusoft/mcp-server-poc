package com.solusoft.ai.mcp.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "mcp.security") 
public class McpAuthProperties {

    // This Map allows dynamic keys! 
    // Structure: LogicalName (e.g., "finance") -> UserDetail object
    private Map<String, UserDetail> users = new HashMap<>();

    public Map<String, UserDetail> getUsers() {
        return users;
    }

    public void setUsers(Map<String, UserDetail> users) {
        this.users = users;
    }

    public static class UserDetail {
        private String key;
        private String role;

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }
}