package com.aitrujobs.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

/**
 * WebSocket message DTO for real-time communication
 */
public class WebSocketMessage {
    
    public enum Type {
        APPLICATION_UPDATE,    // Application status changed
        NEW_JOB,              // New job posted
        CHAT_MESSAGE,         // Chat message sent
        NOTIFICATION,         // General notification
        PRESENCE_UPDATE,      // User online/offline status
        SYSTEM_MESSAGE       // System-wide announcements
    }
    
    private Type type;
    private Object payload;
    private Long userId;
    private Long targetUserId;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    
    public WebSocketMessage() {
        this.timestamp = LocalDateTime.now();
    }
    
    public WebSocketMessage(Type type, Object payload) {
        this();
        this.type = type;
        this.payload = payload;
    }
    
    public WebSocketMessage(Type type, Object payload, Long userId) {
        this(type, payload);
        this.userId = userId;
    }
    
    public WebSocketMessage(Type type, Object payload, Long userId, Long targetUserId) {
        this(type, payload, userId);
        this.targetUserId = targetUserId;
    }
    
    // Getters and setters
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    
    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }
    
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    
    public Long getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
