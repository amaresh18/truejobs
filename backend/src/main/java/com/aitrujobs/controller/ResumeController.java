package com.aitrujobs.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.aitrujobs.entity.Resume;
import com.aitrujobs.entity.User;
import com.aitrujobs.entity.Job;
import com.aitrujobs.exception.ResourceNotFoundException;
import com.aitrujobs.exception.UnauthorizedException;
import com.aitrujobs.repository.ResumeRepository;
import com.aitrujobs.repository.UserRepository;
import com.aitrujobs.repository.JobRepository;
import com.aitrujobs.service.FileUploadService;
import com.aitrujobs.service.OpenAIService;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/resumes")
@CrossOrigin(origins = "*")
public class ResumeController {

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private FileUploadService fileUploadService;

    @Autowired
    private OpenAIService openAIService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadResume(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title) {
        
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
        
        try {
            // Upload file
            String filePath = fileUploadService.uploadFile(file, "resumes");
            
            // Create resume record
            Resume resume = new Resume();
            resume.setTitle(title);
            resume.setFilePath(filePath);
            resume.setFileType(file.getContentType()); // Set the content type from uploaded file
            resume.setFileSize(file.getSize()); // Set file size
            resume.setUser(user);
            resume.setCreatedAt(LocalDateTime.now());
            resume.setUpdatedAt(LocalDateTime.now());
            
            Resume savedResume = resumeRepository.save(resume);
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", savedResume.getId());
            response.put("title", savedResume.getTitle());
            response.put("filePath", savedResume.getFilePath());
            response.put("createdAt", savedResume.getCreatedAt());
            response.put("message", "Resume uploaded successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to upload resume: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<Resume>> getUserResumes() {
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
        List<Resume> resumes = resumeRepository.findByUserOrderByCreatedAtDesc(user);
        
        return ResponseEntity.ok(resumes);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resume> getResume(@PathVariable Long id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("User not authenticated");
        }
        
        Optional<Resume> resumeOpt = resumeRepository.findById(id);
        if (resumeOpt.isEmpty()) {
            throw new ResourceNotFoundException("Resume not found");
        }
        
        Resume resume = resumeOpt.get();
        String email = authentication.getName();
        
        // Check if user owns this resume
        if (!resume.getUser().getEmail().equals(email)) {
            throw new UnauthorizedException("Access denied");
        }
        
        return ResponseEntity.ok(resume);
    }

    @GetMapping("/{id}/download")
    @Transactional
    public ResponseEntity<byte[]> downloadResume(@PathVariable Long id) {
        System.out.println("=== DOWNLOAD ENDPOINT REACHED ===");
        System.out.println("Resume ID requested: " + id);
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            System.err.println("Authentication failed - user not authenticated");
            throw new UnauthorizedException("User not authenticated");
        }
        
        System.out.println("Authentication successful for user: " + authentication.getName());
        
        Optional<Resume> resumeOpt = resumeRepository.findById(id);
        System.out.println("Resume query executed, found: " + resumeOpt.isPresent());
        
        if (resumeOpt.isEmpty()) {
            System.err.println("Resume not found in database for ID: " + id);
            throw new ResourceNotFoundException("Resume not found with ID: " + id);
        }
        
        Resume resume = resumeOpt.get();
        System.out.println("Resume object retrieved: " + resume.getTitle());
        System.out.println("Resume user: " + resume.getUser().getEmail());
        
        String email = authentication.getName();
        System.out.println("Authenticated user email: " + email);
        
        // Check if user owns this resume
        if (!resume.getUser().getEmail().equals(email)) {
            System.err.println("Access denied for user " + email + " to resume ID: " + id);
            throw new UnauthorizedException("Access denied to resume");
        }
        
        System.out.println("Authorization check passed, proceeding to file download");
        
        try {
            System.out.println("Attempting to download file: " + resume.getFilePath());
            System.out.println("Resume title: " + resume.getTitle());
            System.out.println("Resume file type: " + resume.getFileType());
            System.out.println("User email: " + email);
            
            byte[] fileContent = fileUploadService.downloadFile(resume.getFilePath());
            
            HttpHeaders headers = new HttpHeaders();
            
            // Set proper content type based on file type
            String contentType = "application/octet-stream"; // default
            if (resume.getFileType() != null) {
                contentType = resume.getFileType();
                headers.setContentType(MediaType.parseMediaType(contentType));
            } else {
                // Fallback: detect content type from file extension
                String fileExtension = resume.getFilePath().substring(resume.getFilePath().lastIndexOf('.') + 1).toLowerCase();
                switch (fileExtension) {
                    case "pdf":
                        contentType = "application/pdf";
                        break;
                    case "doc":
                        contentType = "application/msword";
                        break;
                    case "docx":
                        contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                        break;
                    default:
                        contentType = "application/octet-stream";
                }
                headers.setContentType(MediaType.parseMediaType(contentType));
            }
            
            // Get file extension from original file type or file path
            String fileExtension = "";
            if (resume.getFileType() != null) {
                if (resume.getFileType().contains("pdf")) {
                    fileExtension = ".pdf";
                } else if (resume.getFileType().contains("word") || resume.getFileType().contains("msword")) {
                    fileExtension = ".doc";
                } else if (resume.getFileType().contains("officedocument.wordprocessingml")) {
                    fileExtension = ".docx";
                } else {
                    // Extract extension from file path if available
                    String filePath = resume.getFilePath();
                    int lastDot = filePath.lastIndexOf('.');
                    if (lastDot > 0) {
                        fileExtension = filePath.substring(lastDot);
                    }
                }
            }
            
            String fileName = resume.getTitle() + fileExtension;
            headers.setContentDispositionFormData("attachment", fileName);
            
            System.out.println("Download response prepared with filename: " + fileName + " and content type: " + headers.getContentType());
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(fileContent);
                    
        } catch (ResourceNotFoundException e) {
            System.err.println("File not found for resume ID " + id + ": " + resume.getFilePath());
            throw new ResourceNotFoundException("Resume file not found. Please re-upload your resume.");
        } catch (Exception e) {
            System.err.println("Resume download failed for file: " + resume.getFilePath());
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to download resume: " + e.getMessage(), e);
        }
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteResume(@PathVariable Long id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("User not authenticated");
        }
        
        Optional<Resume> resumeOpt = resumeRepository.findById(id);
        if (resumeOpt.isEmpty()) {
            throw new ResourceNotFoundException("Resume not found");
        }
        
        Resume resume = resumeOpt.get();
        String email = authentication.getName();
        
        // Check if user owns this resume
        if (!resume.getUser().getEmail().equals(email)) {
            throw new UnauthorizedException("Access denied");
        }
        
        try {
            // Delete file from storage
            fileUploadService.deleteFile(resume.getFilePath());
            
            // Delete resume record
            resumeRepository.delete(resume);
            
            return ResponseEntity.ok(Map.of("message", "Resume deleted successfully"));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to delete resume: " + e.getMessage()));
        }
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeResume(@RequestBody Map<String, Object> request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("User not authenticated");
        }

        try {
            Long resumeId = Long.valueOf(request.get("resumeId").toString());
            Long jobId = Long.valueOf(request.get("jobId").toString());

            // Get resume
            Optional<Resume> resumeOpt = resumeRepository.findById(resumeId);
            if (resumeOpt.isEmpty()) {
                throw new ResourceNotFoundException("Resume not found");
            }

            // Get job
            Optional<Job> jobOpt = jobRepository.findById(jobId);
            if (jobOpt.isEmpty()) {
                throw new ResourceNotFoundException("Job not found");
            }

            Resume resume = resumeOpt.get();
            Job job = jobOpt.get();
            String email = authentication.getName();

            // Check if user owns this resume
            if (!resume.getUser().getEmail().equals(email)) {
                throw new UnauthorizedException("Access denied to resume");
            }

            // Check if resume has extracted text
            if (resume.getExtractedText() == null || resume.getExtractedText().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Resume text not extracted. Please re-upload your resume."));
            }

            // Check if job has description
            if (job.getDescription() == null || job.getDescription().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Job description not available"));
            }

            // Calculate ATS score
            String jobContent = String.format("%s %s %s %s", 
                    job.getTitle(), 
                    job.getDescription() != null ? job.getDescription() : "", 
                    job.getRequirements() != null ? job.getRequirements() : "",
                    job.getSkills() != null ? job.getSkills() : "");

            try {
                Double atsScore = openAIService.calculateATSScore(resume.getExtractedText(), jobContent)
                        .block(); // Block to get synchronous result

                if (atsScore == null) {
                    atsScore = 0.0;
                }

                Map<String, Object> result = new HashMap<>();
                result.put("score", atsScore);
                result.put("resumeTitle", resume.getTitle());
                result.put("jobTitle", job.getTitle());
                result.put("company", job.getCompany());

                return ResponseEntity.ok(result);

            } catch (Exception e) {
                System.err.println("Error calculating ATS score: " + e.getMessage());
                e.printStackTrace();
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Failed to calculate ATS score: " + e.getMessage()));
            }

        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid resume or job ID"));
        } catch (Exception e) {
            System.err.println("Error in analyze endpoint: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to analyze resume: " + e.getMessage()));
        }
    }
}
