package com.aitrujobs.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RecruiterApplicationDTO {
    private Long id;
    private String status;
    private Double atsScore;
    private String coverLetter;
    private String feedback;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // Job summary
    private Long jobId;
    private String jobTitle;
    // Candidate summary
    private Long userId;
    private String userName;
    private String userEmail;
    // Resume summary (optional)
    private Long resumeId;
    private String resumeTitle;
}
