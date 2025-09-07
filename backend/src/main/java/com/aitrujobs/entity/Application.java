package com.aitrujobs.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "applications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({
    "hibernateLazyInitializer", "handler",
    "user",
    "resume"
})
public class Application {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;
    
    // ATS Score calculated by AI (0.0 to 100.0)
    private Double atsScore;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;
    
    // AI-generated feedback for the application
    @Column(columnDefinition = "TEXT")
    private String feedback;
    
    // AI-generated rejection reason if rejected
    @Column(columnDefinition = "TEXT")
    private String rejectionReason;
    
    // Cover letter or additional notes
    @Column(columnDefinition = "TEXT")
    private String coverLetter;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    public enum Status {
        PENDING, REVIEWED, SHORTLISTED, REJECTED, HIRED
    }
}
