package com.aitrujobs.service;

import com.aitrujobs.controller.WebSocketController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for managing real-time notifications
 */
@Service
public class NotificationService {

    @Autowired
    private WebSocketController webSocketController;

    /**
     * Send application status update notification
     */
    public void notifyApplicationStatusChange(Long applicationId, String oldStatus, String newStatus, 
                                            Long candidateId, Long recruiterId, String jobTitle) {
        
        // Notify candidate about status change
        String candidateMessage = String.format(
            "Your application for '%s' has been updated to: %s", 
            jobTitle, 
            formatStatus(newStatus)
        );
        
        webSocketController.sendNotification(
            candidateId, 
            "Application Update", 
            candidateMessage, 
            null
        );

        // Broadcast the update
        webSocketController.broadcastApplicationUpdate(applicationId, newStatus, candidateId, recruiterId);
    }

    /**
     * Notify about new job posting
     */
    public void notifyNewJob(Long jobId, String title, String company, String location) {
        webSocketController.broadcastNewJob(jobId, title, company, location);
        
        // Also send targeted notifications to candidates (could be based on preferences later)
        String message = String.format("New job posted: %s at %s in %s", title, company, location);
        webSocketController.broadcastSystemMessage("New Job Alert", message);
    }

    /**
     * Send direct notification to user
     */
    public void sendDirectNotification(Long userId, String title, String message) {
        webSocketController.sendNotification(userId, title, message, null);
    }

    /**
     * Send notification with custom data
     */
    public void sendNotificationWithData(Long userId, String title, String message, Object data) {
        webSocketController.sendNotification(userId, title, message, data);
    }

    /**
     * Broadcast system announcement
     */
    public void broadcastSystemMessage(String title, String message) {
        webSocketController.broadcastSystemMessage(title, message);
    }

    /**
     * Format status for user-friendly display
     */
    private String formatStatus(String status) {
        switch (status.toUpperCase()) {
            case "PENDING": return "Under Review";
            case "REVIEWED": return "Reviewed";
            case "SHORTLISTED": return "Shortlisted";
            case "REJECTED": return "Not Selected";
            case "HIRED": return "Congratulations! You've been selected";
            default: return status;
        }
    }
}
