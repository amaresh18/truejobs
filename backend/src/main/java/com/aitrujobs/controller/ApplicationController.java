package com.aitrujobs.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.aitrujobs.entity.Application;
import com.aitrujobs.entity.Job;
import com.aitrujobs.entity.Resume;
import com.aitrujobs.entity.User;
import com.aitrujobs.exception.ResourceNotFoundException;
import com.aitrujobs.exception.UnauthorizedException;
import com.aitrujobs.repository.ApplicationRepository;
import com.aitrujobs.repository.JobRepository;
import com.aitrujobs.repository.ResumeRepository;
import com.aitrujobs.repository.UserRepository;
import com.aitrujobs.service.OpenAIService;
import com.aitrujobs.service.NotificationService;

import jakarta.validation.Valid;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/applications")
@CrossOrigin(origins = "*")
@Slf4j
public class ApplicationController {

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OpenAIService openAIService;

    @Autowired
    private NotificationService notificationService;

    @PostMapping
    public ResponseEntity<?> createApplication(@Valid @RequestBody CreateApplicationRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("User not authenticated");
        }
        
        String email = authentication.getName();
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            throw new ResourceNotFoundException("User not found");
        }
        
        User user = userOpt.get();
        
        // Prevent recruiters from applying to jobs
        if (user.getRole() == User.Role.RECRUITER) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Recruiters cannot apply to jobs"));
        }
        
        // Validate job exists
        Optional<Job> jobOpt = jobRepository.findById(request.getJobId());
        if (jobOpt.isEmpty()) {
            throw new ResourceNotFoundException("Job not found");
        }
        
        // Validate resume exists and belongs to user
        Optional<Resume> resumeOpt = resumeRepository.findById(request.getResumeId());
        if (resumeOpt.isEmpty()) {
            throw new ResourceNotFoundException("Resume not found");
        }
        
        Resume resume = resumeOpt.get();
        if (!resume.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("Resume does not belong to user");
        }
        
        // Check if user already applied for this job
        Optional<Application> existingApp = applicationRepository.findByUserAndJob(user, jobOpt.get());
        if (existingApp.isPresent()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "You have already applied for this job"));
        }
        
        // Create application
        Application application = new Application();
        application.setUser(user);
        application.setJob(jobOpt.get());
        application.setResume(resume);
        application.setStatus(Application.Status.PENDING);
        application.setCoverLetter(request.getCoverLetter());
        application.setCreatedAt(LocalDateTime.now());
        application.setUpdatedAt(LocalDateTime.now());
        
        // Calculate ATS score asynchronously
        Job job = jobOpt.get();
        String resumeText = resume.getExtractedText();
        String jobDescription = job.getDescription();
        
        if (resumeText != null && !resumeText.trim().isEmpty() && 
            jobDescription != null && !jobDescription.trim().isEmpty()) {
            try {
                // Calculate ATS score using OpenAI service
                Double atsScore = openAIService.calculateATSScore(resumeText, jobDescription)
                    .doOnSuccess(score -> log.info("ATS score calculated: {} for application to job {}", score, job.getId()))
                    .doOnError(error -> log.error("Failed to calculate ATS score for job {}: ", job.getId(), error))
                    .onErrorReturn(0.0) // Default score if calculation fails
                    .block(); // Block to get the result synchronously
                
                application.setAtsScore(atsScore);
            } catch (Exception e) {
                log.error("Error calculating ATS score for job {}: ", job.getId(), e);
                application.setAtsScore(0.0); // Default score on error
            }
        } else {
            log.warn("Cannot calculate ATS score - missing resume text or job description for job {}", job.getId());
            application.setAtsScore(0.0); // Default score when text is missing
        }
        
        Application savedApplication = applicationRepository.save(application);
        
        Map<String, Object> response = new HashMap<>();
        response.put("id", savedApplication.getId());
        response.put("status", savedApplication.getStatus());
        response.put("message", "Application submitted successfully");
        
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Transactional
    public ResponseEntity<Page<Application>> getUserApplications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("User not authenticated");
        }
        
        String email = authentication.getName();
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            throw new ResourceNotFoundException("User not found");
        }
        
        User user = userOpt.get();
        
        Sort sort = sortDir.equalsIgnoreCase("desc") ? 
            Sort.by(sortBy).descending() : 
            Sort.by(sortBy).ascending();
        
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Application> applications = applicationRepository.findByUser(user, pageable);
        
        return ResponseEntity.ok(applications);
    }

    @GetMapping("/jobs/{jobId}/exists")
    public ResponseEntity<Map<String, Boolean>> hasAppliedToJob(@PathVariable Long jobId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("User not authenticated");
        }

        String email = authentication.getName();
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            throw new ResourceNotFoundException("User not found");
        }

        Optional<Job> jobOpt = jobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            throw new ResourceNotFoundException("Job not found");
        }

        boolean exists = applicationRepository.existsByUserAndJob(userOpt.get(), jobOpt.get());
        return ResponseEntity.ok(Map.of("applied", exists));
    }

    @GetMapping("/recruiter")
    @Transactional
    public ResponseEntity<Page<com.aitrujobs.dto.RecruiterApplicationDTO>> getRecruiterApplications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String status) {
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("User not authenticated");
        }
        
        String email = authentication.getName();
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            throw new ResourceNotFoundException("User not found");
        }
        
        User recruiter = userOpt.get();
        
        if (!recruiter.getRole().equals(User.Role.RECRUITER)) {
            throw new UnauthorizedException("Access denied: Recruiter role required");
        }
        
        Sort sort = sortDir.equalsIgnoreCase("desc") ? 
            Sort.by(sortBy).descending() : 
            Sort.by(sortBy).ascending();
        
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<Application> applications;
        if (status != null && !status.isEmpty()) {
            Application.Status statusEnum = Application.Status.valueOf(status.toUpperCase());
            applications = applicationRepository.findByJobUserAndStatus(recruiter, statusEnum, pageable);
        } else {
            applications = applicationRepository.findByJobUser(recruiter, pageable);
        }

        // Map to DTOs to avoid serialization issues
        Page<com.aitrujobs.dto.RecruiterApplicationDTO> dtoPage = applications.map(app -> {
            com.aitrujobs.dto.RecruiterApplicationDTO dto = new com.aitrujobs.dto.RecruiterApplicationDTO();
            dto.setId(app.getId());
            dto.setStatus(app.getStatus().toString());
            dto.setAtsScore(app.getAtsScore());
            dto.setCoverLetter(app.getCoverLetter());
            dto.setFeedback(app.getFeedback());
            dto.setRejectionReason(app.getRejectionReason());
            dto.setCreatedAt(app.getCreatedAt());
            dto.setUpdatedAt(app.getUpdatedAt());
            if (app.getJob() != null) {
                dto.setJobId(app.getJob().getId());
                dto.setJobTitle(app.getJob().getTitle());
            }
            if (app.getUser() != null) {
                dto.setUserId(app.getUser().getId());
                dto.setUserName(app.getUser().getName());
                dto.setUserEmail(app.getUser().getEmail());
            }
            if (app.getResume() != null) {
                dto.setResumeId(app.getResume().getId());
                dto.setResumeTitle(app.getResume().getTitle());
            }
            return dto;
        });
        return ResponseEntity.ok(dtoPage);
    }

    @PutMapping("/{id}/status")
    @Transactional
    public ResponseEntity<?> updateApplicationStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request) {
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("User not authenticated");
        }
        
    Optional<Application> applicationOpt = applicationRepository.findByIdWithAssociations(id);
        if (applicationOpt.isEmpty()) {
            throw new ResourceNotFoundException("Application not found");
        }
        
        Application application = applicationOpt.get();
        String email = authentication.getName();
        
        // Only the recruiter who posted the job can update status
        if (!application.getJob().getCreatedBy().getEmail().equals(email)) {
            throw new UnauthorizedException("Access denied");
        }
        
        Application.Status newStatus = Application.Status.valueOf(request.getStatus());
        Application.Status oldStatus = application.getStatus(); // Capture old status before updating
        
        if (newStatus == Application.Status.REJECTED) {
            String reason = request.getFeedback();
            if (reason == null || reason.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Rejection requires a reason"));
            }
            application.setRejectionReason(reason.trim());
        } else {
            application.setFeedback(request.getFeedback());
            // Clear any prior rejection reason if status moves away from REJECTED
            application.setRejectionReason(null);
        }
        application.setStatus(newStatus);
        application.setUpdatedAt(LocalDateTime.now());
        
        Application updatedApplication = applicationRepository.save(application);
        
        // Send real-time notification about status change
        notificationService.notifyApplicationStatusChange(
            updatedApplication.getId(),
            oldStatus.toString(),
            newStatus.toString(),
            updatedApplication.getUser().getId(),
            updatedApplication.getJob().getCreatedBy().getId(),
            updatedApplication.getJob().getTitle()
        );
        
        Map<String, Object> response = new HashMap<>();
        response.put("id", updatedApplication.getId());
        response.put("status", updatedApplication.getStatus());
        response.put("feedback", updatedApplication.getFeedback());
        response.put("rejectionReason", updatedApplication.getRejectionReason());
        response.put("message", "Application status updated successfully");
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Transactional
    public ResponseEntity<Application> getApplication(@PathVariable Long id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("User not authenticated");
        }
        
        Optional<Application> applicationOpt = applicationRepository.findById(id);
        if (applicationOpt.isEmpty()) {
            throw new ResourceNotFoundException("Application not found");
        }
        
        Application application = applicationOpt.get();
        String email = authentication.getName();
        
        // User can view their own applications or recruiter can view applications for their jobs
        boolean canView = application.getUser().getEmail().equals(email) ||
                         application.getJob().getCreatedBy().getEmail().equals(email);
        
        if (!canView) {
            throw new UnauthorizedException("Access denied");
        }
        
        return ResponseEntity.ok(application);
    }

    @Data
    static class CreateApplicationRequest {
        private Long jobId;
        private Long resumeId;
        private String coverLetter;
    }

    @Data
    static class UpdateStatusRequest {
        private String status; // PENDING, REVIEWED, SHORTLISTED, REJECTED, HIRED
        private String feedback; // Use for general feedback or rejection reason when REJECTED
    }
}
