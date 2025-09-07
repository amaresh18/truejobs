package com.aitrujobs.controller;

import com.aitrujobs.dto.WebSocketMessage;
import com.aitrujobs.entity.User;
import com.aitrujobs.repository.UserRepository;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * WebSocket message controller for real-time communication
 */
@Controller
public class WebSocketController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private UserRepository userRepository;

    /**
     * Handle incoming chat messages
     */
    @MessageMapping("/chat.send")
    public void sendChatMessage(@Payload WebSocketMessage message) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }
        
        String email = authentication.getName();
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return;
        }
        
        User user = userOpt.get();
        
        // Set sender information
        message.setUserId(user.getId());
        message.setTimestamp(LocalDateTime.now());
        
        // Send message to specific user if targetUserId is provided
        if (message.getTargetUserId() != null) {
            messagingTemplate.convertAndSendToUser(
                message.getTargetUserId().toString(), 
                "/queue/messages", 
                message
            );
        } else {
            // Broadcast to all users (public chat)
            messagingTemplate.convertAndSend("/topic/messages", message);
        }
    }

    /**
     * Handle user presence updates (online/offline)
     */
    @MessageMapping("/presence.update")
    @SendTo("/topic/presence")
    public WebSocketMessage updatePresence(@Payload Map<String, Object> presenceData) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        
        String email = authentication.getName();
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return null;
        }
        
        User user = userOpt.get();
        
        return new WebSocketMessage(
            WebSocketMessage.Type.PRESENCE_UPDATE,
            Map.of(
                "userId", user.getId(),
                "username", user.getName(),
                "status", presenceData.get("status"),
                "timestamp", LocalDateTime.now()
            )
        );
    }

    /**
     * Send notification to specific user
     */
    public void sendNotification(Long userId, String title, String message, Object data) {
        WebSocketMessage notification = new WebSocketMessage(
            WebSocketMessage.Type.NOTIFICATION,
            Map.of(
                "title", title,
                "message", message,
                "data", data != null ? data : Map.of()
            )
        );
        
        messagingTemplate.convertAndSendToUser(
            userId.toString(),
            "/queue/notifications",
            notification
        );
    }

    /**
     * Broadcast application status update
     */
    public void broadcastApplicationUpdate(Long applicationId, String newStatus, Long candidateId, Long recruiterId) {
        WebSocketMessage message = new WebSocketMessage(
            WebSocketMessage.Type.APPLICATION_UPDATE,
            Map.of(
                "applicationId", applicationId,
                "status", newStatus,
                "timestamp", LocalDateTime.now()
            )
        );

        // Send to candidate
        messagingTemplate.convertAndSendToUser(
            candidateId.toString(),
            "/queue/application-updates",
            message
        );

        // Send to recruiter
        messagingTemplate.convertAndSendToUser(
            recruiterId.toString(),
            "/queue/application-updates",
            message
        );
    }

    /**
     * Broadcast new job posting
     */
    public void broadcastNewJob(Long jobId, String title, String company, String location) {
        WebSocketMessage message = new WebSocketMessage(
            WebSocketMessage.Type.NEW_JOB,
            Map.of(
                "jobId", jobId,
                "title", title,
                "company", company,
                "location", location,
                "timestamp", LocalDateTime.now()
            )
        );

        // Broadcast to all candidates
        messagingTemplate.convertAndSend("/topic/new-jobs", message);
    }

    /**
     * Send system-wide message
     */
    public void broadcastSystemMessage(String title, String message) {
        WebSocketMessage systemMsg = new WebSocketMessage(
            WebSocketMessage.Type.SYSTEM_MESSAGE,
            Map.of(
                "title", title,
                "message", message,
                "timestamp", LocalDateTime.now()
            )
        );

        messagingTemplate.convertAndSend("/topic/system", systemMsg);
    }
}
