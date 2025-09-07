package com.aitrujobs.controller;

import com.aitrujobs.entity.Job;
import com.aitrujobs.entity.User;
import com.aitrujobs.repository.JobRepository;
import com.aitrujobs.repository.UserRepository;
import com.aitrujobs.repository.ApplicationRepository;
import com.aitrujobs.service.OpenAIService;
import com.aitrujobs.service.JobMatchingService;
import com.aitrujobs.service.NotificationService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@CrossOrigin(origins = "*")
public class JobController {

    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private OpenAIService openAIService;

    @Autowired
    private JobMatchingService jobMatchingService;

    @Autowired
    private NotificationService notificationService;

    @GetMapping
    public ResponseEntity<Page<Job>> getAllJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String jobType,
            @RequestParam(required = false) String skills) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Job> jobs;
        
        if (search != null && !search.isEmpty()) {
            jobs = jobRepository.searchJobs(search, pageable);
        } else if (location != null || skills != null || jobType != null) {
            jobs = jobRepository.findJobsWithFilters(location, skills, jobType, pageable);
        } else {
            jobs = jobRepository.findByIsPublishedTrue(pageable);
        }
        
        return ResponseEntity.ok(jobs);
    }

    // Compatibility endpoint for clients using /jobs/search
    @GetMapping("/search")
    public ResponseEntity<Page<Job>> searchJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String jobType,
            @RequestParam(required = false) String skills) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        // If any filter provided, use same logic as getAllJobs
        if (keyword != null && !keyword.isEmpty()) {
            return ResponseEntity.ok(jobRepository.searchJobs(keyword, pageable));
        }
        if (location != null || skills != null || jobType != null) {
            return ResponseEntity.ok(jobRepository.findJobsWithFilters(location, skills, jobType, pageable));
        }
        // Default to published jobs
        return ResponseEntity.ok(jobRepository.findByIsPublishedTrue(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Job> getJobById(@PathVariable Long id) {
        Optional<Job> jobOpt = jobRepository.findById(id);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Job job = jobOpt.get();
        // Hide unpublished jobs from non-owners
        if (Boolean.FALSE.equals(job.getIsPublished())) {
            String email = getAuthenticatedUserEmail().orElse(null);
            if (email == null || (!job.getCreatedBy().getEmail().equals(email))) {
                return ResponseEntity.notFound().build();
            }
        }
        return ResponseEntity.ok(job);
    }

    @PostMapping
    public ResponseEntity<?> createJob(@Valid @RequestBody CreateJobRequest request) {

        String email = getAuthenticatedUserEmail().orElse(null);
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized: No authenticated user"));
        }

        Optional<User> recruiterOpt = userRepository.findByEmail(email);
        if (recruiterOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
        }
        User recruiter = recruiterOpt.get();
        if (!recruiter.getRole().equals(User.Role.RECRUITER)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only recruiters can create jobs"));
        }

        Job job = new Job();
        job.setTitle(request.getTitle());
        job.setDescription(request.getDescription());
        job.setRequirements(request.getRequirements());
        job.setSkills(request.getSkills());
        job.setLocation(request.getLocation());
        job.setSalaryRange(request.getSalaryRange());
        job.setJobType(request.getJobType());
        job.setIsPublished(request.getIsPublished());
        job.setCreatedBy(recruiter);

        Job savedJob = jobRepository.save(job);

        // Generate embedding asynchronously (optional)
        if (request.getDescription() != null && openAIService.isConfigured()) {
            openAIService.generateEmbedding(request.getDescription())
                .subscribe(
                    embedding -> {
                        try {
                            savedJob.setEmbeddingVector(embedding.toString());
                            jobRepository.save(savedJob);
                        } catch (Exception e) {
                            // swallow errors to avoid impacting API response
                        }
                    },
                    error -> {
                        // log only; do not propagate
                    }
                );
        }

        return ResponseEntity.ok(savedJob);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateJob(@PathVariable Long id, @Valid @RequestBody CreateJobRequest request) {
        Optional<Job> jobOpt = jobRepository.findById(id);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Job job = jobOpt.get();
        // Enforce ownership (or admin)
        String email = getAuthenticatedUserEmail().orElse(null);
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }
        User actor = userOpt.get();
        boolean isOwner = job.getCreatedBy().getEmail().equals(email);
        boolean isAdmin = actor.getRole() == User.Role.ADMIN;
        if (!isOwner && !isAdmin) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }
        
        job.setTitle(request.getTitle());
        job.setDescription(request.getDescription());
        job.setRequirements(request.getRequirements());
        job.setSkills(request.getSkills());
        job.setLocation(request.getLocation());
        job.setSalaryRange(request.getSalaryRange());
        job.setJobType(request.getJobType());
        
        // Publishing status is now handled by dedicated endpoints
        // job.setIsPublished(request.getIsPublished());

        Job updatedJob = jobRepository.save(job);
        return ResponseEntity.ok(updatedJob);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteJob(@PathVariable Long id) {
        Optional<Job> jobOpt = jobRepository.findById(id);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Enforce ownership (or admin)
        String email = getAuthenticatedUserEmail().orElse(null);
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }
        User actor = userOpt.get();
        boolean isOwner = jobOpt.get().getCreatedBy().getEmail().equals(email);
        boolean isAdmin = actor.getRole() == User.Role.ADMIN;
        if (!isOwner && !isAdmin) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        jobRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Job deleted successfully"));
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateJobDescription(@RequestBody GenerateJobRequest request) {
        try {
            Mono<String> descriptionMono = openAIService.generateJobDescription(
                    request.getJobTitle(),
                    request.getCompany(),
                    request.getRequirements()
            );

            String description = descriptionMono.block(); // Blocking for simplicity
            
            Map<String, String> response = new HashMap<>();
            response.put("description", description);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to generate job description"));
        }
    }

    @GetMapping("/recruiter/{recruiterId}")
    public ResponseEntity<Page<Job>> getJobsByRecruiter(
            @PathVariable Long recruiterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Optional<User> recruiterOpt = userRepository.findById(recruiterId);
        if (recruiterOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Job> jobs = jobRepository.findByCreatedBy(recruiterOpt.get(), pageable);
        
        return ResponseEntity.ok(jobs);
    }

    // New endpoint: uses authenticated recruiter (no path variable required)
    @GetMapping("/recruiter")
    public ResponseEntity<Page<Job>> getJobsForAuthenticatedRecruiter(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        // Extract authenticated principal email
        String email = getAuthenticatedUserEmail().orElse(null);
        if (email == null) {
            log.warn("Recruiter jobs request without authenticated principal");
            return ResponseEntity.status(401).build();
        }
        Optional<User> recruiterOpt = userRepository.findByEmail(email);
        if (recruiterOpt.isEmpty()) {
            log.warn("Recruiter jobs request - user not found for email {}", email);
            return ResponseEntity.status(404).build();
        }
        User recruiter = recruiterOpt.get();
        if (recruiter.getRole() != User.Role.RECRUITER && recruiter.getRole() != User.Role.ADMIN) {
            log.warn("Recruiter jobs request - user {} has role {} not authorized", email, recruiter.getRole());
            return ResponseEntity.status(403).build();
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        try {
            Page<Job> jobs = jobRepository.findByCreatedBy(recruiter, pageable);
            log.debug("Returning {} jobs for recruiter {}", jobs.getNumberOfElements(), email);
            return ResponseEntity.ok(jobs);
        } catch (Exception ex) {
            log.error("Error retrieving jobs for recruiter {}: {}", email, ex.getMessage(), ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/{id}/publish")
    public ResponseEntity<?> publishJob(@PathVariable Long id) {
        try {
            String email = getAuthenticatedUserEmail().orElse(null);
            if (email == null) {
                log.error("publishJob: Unauthorized - no authenticated user");
                return ResponseEntity.status(401).body(Map.of("error", "Unauthorized: No authenticated user"));
            }

            Optional<Job> jobOpt = jobRepository.findByIdWithCreatedBy(id);
            if (jobOpt.isEmpty()) {
                log.error("publishJob: Job not found for id {}", id);
                return ResponseEntity.status(404).body(Map.of("error", "Job not found"));
            }
            Job job = jobOpt.get();

            Optional<User> actorOpt = userRepository.findByEmail(email);
            if (actorOpt.isEmpty()) {
                log.error("publishJob: User not found for email {}", email);
                return ResponseEntity.status(404).body(Map.of("error", "User not found"));
            }
            User actor = actorOpt.get();
            boolean isOwner = job.getCreatedBy().getEmail().equals(email);
            boolean isAdmin = actor.getRole() == User.Role.ADMIN;
            if (!isOwner && !isAdmin) {
                log.error("publishJob: Forbidden - user {} is not owner or admin", email);
                return ResponseEntity.status(403).body(Map.of("error", "Forbidden: Not owner or admin"));
            }

            job.setIsPublished(true);
            jobRepository.save(job);

            // Send real-time notification about new job
            try {
                notificationService.notifyNewJob(job.getId(), job.getTitle(), job.getCompany(), job.getLocation());
            } catch (Exception notifyEx) {
                log.error("publishJob: Notification failed for job {}: {}", job.getId(), notifyEx.getMessage(), notifyEx);
            }

            return ResponseEntity.ok(job);
        } catch (Exception ex) {
            log.error("publishJob: Unexpected error for job id {}: {}", id, ex.getMessage(), ex);
            return ResponseEntity.status(500).body(Map.of("error", "Unexpected error: " + ex.getMessage()));
        }
    }

    @PutMapping("/{id}/unpublish")
    public ResponseEntity<?> unpublishJob(@PathVariable Long id) {
        Optional<Job> jobOpt = jobRepository.findById(id);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Job job = jobOpt.get();

        String email = getAuthenticatedUserEmail().orElse(null);
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        Optional<User> actorOpt = userRepository.findByEmail(email);
        if (actorOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }
        User actor = actorOpt.get();
        boolean isOwner = job.getCreatedBy().getEmail().equals(email);
        boolean isAdmin = actor.getRole() == User.Role.ADMIN;
        if (!isOwner && !isAdmin) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        job.setIsPublished(false);
        jobRepository.save(job);
        return ResponseEntity.ok(job);
    }

    /**
     * Get personalized job recommendations for the authenticated user
     */
    @GetMapping("/recommendations")
    public Mono<ResponseEntity<Page<JobMatchingService.JobMatch>>> getPersonalizedJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        String email = getAuthenticatedUserEmail().orElse(null);
        if (email == null) {
            return Mono.just(ResponseEntity.status(401).build());
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return Mono.just(ResponseEntity.status(404).build());
        }

        User user = userOpt.get();
        Pageable pageable = PageRequest.of(page, size);
        
        return jobMatchingService.getPersonalizedJobs(user, pageable)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.status(500).build());
    }

    /**
     * Get similar jobs based on a given job
     */
    @GetMapping("/{id}/similar")
    public Mono<ResponseEntity<List<Job>>> getSimilarJobs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "5") int limit) {
        
        return jobMatchingService.getSimilarJobs(id, limit)
                .map(jobs -> {
                    if (jobs.isEmpty()) {
                        return ResponseEntity.notFound().<List<Job>>build();
                    }
                    return ResponseEntity.ok(jobs);
                })
                .onErrorReturn(ResponseEntity.status(500).<List<Job>>build());
    }

    /**
     * Get job analytics for recruiters
     */
    @GetMapping("/{id}/analytics")
    public ResponseEntity<JobMatchingService.JobAnalytics> getJobAnalytics(@PathVariable Long id) {
        String email = getAuthenticatedUserEmail().orElse(null);
        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        Optional<Job> jobOpt = jobRepository.findById(id);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Job job = jobOpt.get();
        if (!job.getCreatedBy().getEmail().equals(email)) {
            return ResponseEntity.status(403).build();
        }

        JobMatchingService.JobAnalytics analytics = jobMatchingService.getJobAnalytics(id);
        if (analytics == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(analytics);
    }

    /**
     * Get dashboard statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<DashboardStats> getDashboardStats() {
        String email = getAuthenticatedUserEmail().orElse(null);
        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).build();
        }

        User user = userOpt.get();
        DashboardStats stats = new DashboardStats();

        if (user.getRole() == User.Role.CANDIDATE) {
            stats.setTotalApplications((int) applicationRepository.countByUser(user));
            stats.setActiveJobs(0); // Not applicable for candidates
            stats.setInterviews((int) applicationRepository.countByUserAndStatus(user, 
                com.aitrujobs.entity.Application.Status.SHORTLISTED));
            stats.setOffers((int) applicationRepository.countByUserAndStatus(user, 
                com.aitrujobs.entity.Application.Status.HIRED));
        } else if (user.getRole() == User.Role.RECRUITER) {
            stats.setActiveJobs((int) jobRepository.countByCreatedByAndIsPublishedTrue(user));
            stats.setTotalJobs((int) jobRepository.countByCreatedBy(user));
            stats.setTotalApplications((int) applicationRepository.countByJobUser(user));
            stats.setInterviews((int) applicationRepository.countByJobUserAndStatus(user, 
                com.aitrujobs.entity.Application.Status.SHORTLISTED));
            stats.setOffers((int) applicationRepository.countByJobUserAndStatus(user, 
                com.aitrujobs.entity.Application.Status.HIRED));
        } else if (user.getRole() == User.Role.ADMIN) {
            stats.setActiveJobs((int) jobRepository.countByIsPublishedTrue());
            stats.setTotalJobs((int) jobRepository.count());
            stats.setTotalApplications((int) applicationRepository.count());
            stats.setInterviews((int) applicationRepository.countByStatus(
                com.aitrujobs.entity.Application.Status.SHORTLISTED));
            stats.setOffers((int) applicationRepository.countByStatus(
                com.aitrujobs.entity.Application.Status.HIRED));
        }

        return ResponseEntity.ok(stats);
    }

    private Optional<String> getAuthenticatedUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return Optional.empty();
        }
        
        Object principal = authentication.getPrincipal();
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            return Optional.of(((org.springframework.security.core.userdetails.UserDetails) principal).getUsername());
        }
        if (principal instanceof String) {
            return Optional.of((String) principal);
        }
        
        return Optional.ofNullable(authentication.getName());
    }

    @Data
    static class DashboardStats {
        private int activeJobs;
        private int totalJobs;
        private int totalApplications;
        private int interviews;
        private int offers;
    }

    @Data
    static class CreateJobRequest {
        private String title;
        private String description;
        private String requirements;
        private String skills;
        private String location;
        private String salaryRange;
        private String jobType;
        private Boolean isPublished = false;
    }

    @Data
    static class GenerateJobRequest {
        private String jobTitle;
        private String company;
        private String requirements;
    }
}
