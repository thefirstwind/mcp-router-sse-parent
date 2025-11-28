package com.pajk.mcpbridge.core.session;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;

public class SessionMeta {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private String sessionId;
    private String instanceId;
    private String serviceName;
    private String backendSessionId;
    private String transportType;
    private LocalDateTime lastActive;
    private boolean active;

    public SessionMeta() {
    }

    public SessionMeta(String sessionId, String instanceId, String serviceName,
                       String backendSessionId, String transportType,
                       LocalDateTime lastActive, boolean active) {
        this.sessionId = sessionId;
        this.instanceId = instanceId;
        this.serviceName = serviceName;
        this.backendSessionId = backendSessionId;
        this.transportType = transportType;
        this.lastActive = lastActive;
        this.active = active;
    }

    public static SessionMeta fromMap(Map<Object, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        SessionMeta meta = new SessionMeta();
        meta.setSessionId((String) map.get("sessionId"));
        meta.setInstanceId((String) map.get("instanceId"));
        meta.setServiceName((String) map.get("serviceName"));
        meta.setBackendSessionId((String) map.get("backendSessionId"));
        meta.setTransportType((String) map.get("transportType"));
        String lastActiveStr = (String) map.get("lastActive");
        if (lastActiveStr != null) {
            meta.setLastActive(LocalDateTime.parse(lastActiveStr, FORMATTER));
        }
        String activeStr = (String) map.get("active");
        meta.setActive(activeStr == null || Boolean.parseBoolean(activeStr));
        return meta;
    }

    public Map<String, String> toMap() {
        return Map.of(
                "sessionId", Objects.toString(sessionId, ""),
                "instanceId", Objects.toString(instanceId, ""),
                "serviceName", Objects.toString(serviceName, ""),
                "backendSessionId", Objects.toString(backendSessionId, ""),
                "transportType", Objects.toString(transportType, ""),
                "lastActive", FORMATTER.format(lastActive != null ? lastActive : LocalDateTime.now()),
                "active", Boolean.toString(active)
        );
    }

    // Getters and setters
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getBackendSessionId() {
        return backendSessionId;
    }

    public void setBackendSessionId(String backendSessionId) {
        this.backendSessionId = backendSessionId;
    }

    public String getTransportType() {
        return transportType;
    }

    public void setTransportType(String transportType) {
        this.transportType = transportType;
    }

    public LocalDateTime getLastActive() {
        return lastActive;
    }

    public void setLastActive(LocalDateTime lastActive) {
        this.lastActive = lastActive;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}

