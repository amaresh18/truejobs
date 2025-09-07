package com.aitrujobs.service;

import com.aitrujobs.entity.Job;
import com.aitrujobs.entity.Resume;
import com.aitrujobs.entity.User;
import com.aitrujobs.repository.JobRepository;
import com.aitrujobs.repository.ResumeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class JobMatchingService {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private OpenAIService openAIService;

    /**
     * Get personalized job recommendations for a user
     */
    public Mono<Page<JobMatch>> getPersonalizedJobs(User user, Pageable pageable) {
        if (user.getRole() != User.Role.CANDIDATE) {
            return Mono.just(new PageImpl<>(Collections.emptyList()));
        }

        // Get user's latest resume
        Optional<Resume> latestResumeOpt = resumeRepository.findFirstByUserOrderByCreatedAtDesc(user);
        if (latestResumeOpt.isEmpty()) {
            // If no resume, return regular job list
            Page<Job> jobs = jobRepository.findByIsPublishedTrue(pageable);
            List<JobMatch> jobMatches = jobs.getContent().stream()
                    .map(job -> new JobMatch(job, 0.0, Collections.emptyList(), "No resume uploaded"))
                    .collect(Collectors.toList());
            return Mono.just(new PageImpl<>(jobMatches, pageable, jobs.getTotalElements()));
        }

        Resume userResume = latestResumeOpt.get();
        Page<Job> availableJobs = jobRepository.findByIsPublishedTrue(pageable);

        if (userResume.getExtractedText() == null || userResume.getExtractedText().isEmpty()) {
            // If resume has no text, return jobs without scores
            List<JobMatch> jobMatches = availableJobs.getContent().stream()
                    .map(job -> new JobMatch(job, 0.0, Collections.emptyList(), "Resume text not extracted"))
                    .collect(Collectors.toList());
            return Mono.just(new PageImpl<>(jobMatches, pageable, availableJobs.getTotalElements()));
        }

        // Calculate ATS scores for all jobs
        return calculateJobMatches(userResume, availableJobs.getContent())
                .map(jobMatches -> {
                    // Sort by ATS score descending
                    List<JobMatch> sortedMatches = jobMatches.stream()
                            .sorted((a, b) -> Double.compare(b.getAtsScore(), a.getAtsScore()))
                            .collect(Collectors.toList());
                    
                    return new PageImpl<>(sortedMatches, pageable, availableJobs.getTotalElements());
                });
    }

    /**
     * Calculate detailed job matches with ATS scores and explanations
     */
    private Mono<List<JobMatch>> calculateJobMatches(Resume resume, List<Job> jobs) {
        if (!openAIService.isConfigured()) {
            List<JobMatch> fallbackMatches = jobs.stream()
                    .map(job -> new JobMatch(job, 0.0, Collections.emptyList(), "AI service not configured"))
                    .collect(Collectors.toList());
            return Mono.just(fallbackMatches);
        }

        List<Mono<JobMatch>> matchMonos = jobs.stream()
                .map(job -> calculateSingleJobMatch(resume, job))
                .collect(Collectors.toList());

        return Mono.zip(matchMonos, objects -> 
                Arrays.stream(objects)
                        .map(obj -> (JobMatch) obj)
                        .collect(Collectors.toList())
        );
    }

    /**
     * Calculate ATS score and explanation for a single job
     */
    private Mono<JobMatch> calculateSingleJobMatch(Resume resume, Job job) {
        String jobContent = String.format("%s %s %s %s", 
                job.getTitle(), 
                job.getDescription() != null ? job.getDescription() : "", 
                job.getRequirements() != null ? job.getRequirements() : "",
                job.getSkills() != null ? job.getSkills() : "");

        return openAIService.calculateATSScore(resume.getExtractedText(), jobContent)
                .flatMap(score -> 
                    generateMatchExplanation(resume.getExtractedText(), jobContent, score)
                            .map(explanation -> {
                                List<String> skillMatches = extractSkillMatches(resume.getExtractedText(), job.getSkills());
                                return new JobMatch(job, score, skillMatches, explanation);
                            })
                )
                .onErrorReturn(new JobMatch(job, 0.0, Collections.emptyList(), "Error calculating match"));
    }

    /**
     * Generate detailed explanation for job match
     */
    private Mono<String> generateMatchExplanation(String resumeText, String jobContent, double score) {
        String prompt = String.format(
            "Explain why this resume matches this job with a score of %.1f/100. " +
            "Provide specific reasons for the match/mismatch. " +
            "Keep it concise (2-3 sentences). " +
            "Resume: %s " +
            "Job: %s",
            score, 
            resumeText.length() > 1000 ? resumeText.substring(0, 1000) + "..." : resumeText,
            jobContent.length() > 1000 ? jobContent.substring(0, 1000) + "..." : jobContent
        );

        return openAIService.generateChatCompletion(prompt)
                .onErrorReturn("Match score calculated based on content similarity");
    }

    /**
     * Extract matching skills between resume and job requirements
     */
    private List<String> extractSkillMatches(String resumeText, String jobSkills) {
        if (jobSkills == null || jobSkills.isEmpty()) {
            return Collections.emptyList();
        }

        String resumeLower = resumeText.toLowerCase();
        return Arrays.stream(jobSkills.split(","))
                .map(String::trim)
                .filter(skill -> resumeLower.contains(skill.toLowerCase()))
                .limit(5) // Limit to top 5 matching skills
                .collect(Collectors.toList());
    }

    /**
     * Get similar jobs based on a given job
     */
    public Mono<List<Job>> getSimilarJobs(Long jobId, int limit) {
        Optional<Job> jobOpt = jobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            return Mono.just(Collections.emptyList());
        }

        Job targetJob = jobOpt.get();
        String jobContent = String.format("%s %s %s", 
                targetJob.getDescription() != null ? targetJob.getDescription() : "", 
                targetJob.getRequirements() != null ? targetJob.getRequirements() : "",
                targetJob.getSkills() != null ? targetJob.getSkills() : "");

        List<Job> allJobs = jobRepository.findByIsPublishedTrueAndIdNot(jobId);
        
        if (!openAIService.isConfigured() || allJobs.isEmpty()) {
            return Mono.just(allJobs.stream()
                    .limit(limit)
                    .collect(Collectors.toList()));
        }

        // Calculate similarity scores for all other jobs
        List<Mono<JobSimilarity>> similarityMonos = allJobs.stream()
                .map(job -> {
                    String otherJobContent = String.format("%s %s %s", 
                            job.getDescription() != null ? job.getDescription() : "", 
                            job.getRequirements() != null ? job.getRequirements() : "",
                            job.getSkills() != null ? job.getSkills() : "");
                    
                    return openAIService.calculateATSScore(jobContent, otherJobContent)
                            .map(score -> new JobSimilarity(job, score))
                            .onErrorReturn(new JobSimilarity(job, 0.0));
                })
                .collect(Collectors.toList());

        return Mono.zip(similarityMonos, objects -> 
                Arrays.stream(objects)
                        .map(obj -> (JobSimilarity) obj)
                        .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                        .limit(limit)
                        .map(JobSimilarity::getJob)
                        .collect(Collectors.toList())
        );
    }

    /**
     * Get job statistics for analytics
     */
    public JobAnalytics getJobAnalytics(Long jobId) {
        Optional<Job> jobOpt = jobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            return null;
        }

        Job job = jobOpt.get();
        
        // These would be implemented with proper repository queries
        int totalApplications = 0; // applicationRepository.countByJob(job);
        int shortlistedCount = 0; // applicationRepository.countByJobAndStatus(job, Application.Status.SHORTLISTED);
        int rejectedCount = 0; // applicationRepository.countByJobAndStatus(job, Application.Status.REJECTED);
        double averageScore = 0.0; // applicationRepository.getAverageATSScoreByJob(job);

        return new JobAnalytics(
                jobId,
                totalApplications,
                shortlistedCount,
                rejectedCount,
                averageScore,
                job.getCreatedAt()
        );
    }

    // Inner classes for data transfer
    public static class JobMatch {
        private final Job job;
        private final double atsScore;
        private final List<String> matchingSkills;
        private final String explanation;

        public JobMatch(Job job, double atsScore, List<String> matchingSkills, String explanation) {
            this.job = job;
            this.atsScore = atsScore;
            this.matchingSkills = matchingSkills;
            this.explanation = explanation;
        }

        // Getters
        public Job getJob() { return job; }
        public double getAtsScore() { return atsScore; }
        public List<String> getMatchingSkills() { return matchingSkills; }
        public String getExplanation() { return explanation; }
    }

    private static class JobSimilarity {
        private final Job job;
        private final double score;

        public JobSimilarity(Job job, double score) {
            this.job = job;
            this.score = score;
        }

        public Job getJob() { return job; }
        public double getScore() { return score; }
    }

    public static class JobAnalytics {
        private final Long jobId;
        private final int totalApplications;
        private final int shortlistedCount;
        private final int rejectedCount;
        private final double averageScore;
        private final java.time.LocalDateTime createdAt;

        public JobAnalytics(Long jobId, int totalApplications, int shortlistedCount, 
                           int rejectedCount, double averageScore, java.time.LocalDateTime createdAt) {
            this.jobId = jobId;
            this.totalApplications = totalApplications;
            this.shortlistedCount = shortlistedCount;
            this.rejectedCount = rejectedCount;
            this.averageScore = averageScore;
            this.createdAt = createdAt;
        }

        // Getters
        public Long getJobId() { return jobId; }
        public int getTotalApplications() { return totalApplications; }
        public int getShortlistedCount() { return shortlistedCount; }
        public int getRejectedCount() { return rejectedCount; }
        public double getAverageScore() { return averageScore; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    }
}
