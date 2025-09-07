package com.aitrujobs.config;

import com.aitrujobs.entity.Job;
import com.aitrujobs.entity.Resume;
import com.aitrujobs.entity.Application;
import com.aitrujobs.entity.User;
import com.aitrujobs.repository.ApplicationRepository;
import com.aitrujobs.repository.JobRepository;
import com.aitrujobs.repository.ResumeRepository;
import com.aitrujobs.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile({"default"})
public class DevDataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JobRepository jobRepository;
    private final ResumeRepository resumeRepository;
    private final ApplicationRepository applicationRepository;

    public DevDataLoader(UserRepository userRepository, PasswordEncoder passwordEncoder, JobRepository jobRepository,
                         ResumeRepository resumeRepository, ApplicationRepository applicationRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jobRepository = jobRepository;
        this.resumeRepository = resumeRepository;
        this.applicationRepository = applicationRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        // Clean up orphaned files from previous sessions (since we use in-memory DB)
        try {
            java.nio.file.Path uploadDir = java.nio.file.Paths.get("uploads", "resumes");
            if (java.nio.file.Files.exists(uploadDir)) {
                // Delete all files except the ones we'll create
                java.nio.file.Files.list(uploadDir)
                    .filter(path -> !path.getFileName().toString().equals("sample-default.pdf"))
                    .forEach(path -> {
                        try {
                            java.nio.file.Files.deleteIfExists(path);
                            System.out.println("Cleaned up orphaned file: " + path.getFileName());
                        } catch (Exception e) {
                            System.err.println("Failed to clean up file: " + path.getFileName());
                        }
                    });
            }
        } catch (Exception e) {
            System.err.println("Failed to clean up uploads directory: " + e.getMessage());
        }

        // Seed a default recruiter and candidate if repository is empty
        if (userRepository.count() == 0) {
            User recruiter = new User();
            recruiter.setName("Default Recruiter");
            recruiter.setEmail("recruiter@example.com");
            recruiter.setPassword(passwordEncoder.encode("password123"));
            recruiter.setRole(User.Role.RECRUITER);
            recruiter = userRepository.save(recruiter);

            User candidate = new User();
            candidate.setName("Default Candidate");
            candidate.setEmail("candidate@example.com");
            candidate.setPassword(passwordEncoder.encode("password123"));
            candidate.setRole(User.Role.CANDIDATE);
            candidate = userRepository.save(candidate);

            // Seed at least one published job for listings/tests
            Job job = new Job();
            job.setTitle("Senior Frontend Developer");
            job.setCompany("TechCorp Inc.");
            job.setDescription("Build amazing user experiences with React and TypeScript.");
            job.setRequirements("5+ years experience in frontend, React, TypeScript.");
            job.setSkills("React,TypeScript,Next.js,Tailwind");
            job.setLocation("Remote");
            job.setSalaryRange("$120k - $160k");
            job.setJobType("Full-time");
            job.setIsPublished(true);
            job.setCreatedBy(recruiter);
            job = jobRepository.save(job);

            // Seed a sample resume for the candidate
            Resume resume = new Resume();
            resume.setUser(candidate);
            resume.setTitle("Default Candidate Resume");
            // store a placeholder path inside uploads/resumes
            resume.setFilePath("resumes/sample-default.pdf");
            resume = resumeRepository.save(resume);

            // Ensure the placeholder file exists to satisfy download operations
            try {
                java.nio.file.Path uploadDir = java.nio.file.Paths.get("uploads", "resumes");
                java.nio.file.Files.createDirectories(uploadDir);
                java.nio.file.Path sample = uploadDir.resolve("sample-default.pdf");
                if (!java.nio.file.Files.exists(sample)) {
                    // Create a minimal valid PDF file
                    String pdfContent = "%PDF-1.4\n" +
                        "1 0 obj\n" +
                        "<<\n" +
                        "/Type /Catalog\n" +
                        "/Pages 2 0 R\n" +
                        ">>\n" +
                        "endobj\n" +
                        "\n" +
                        "2 0 obj\n" +
                        "<<\n" +
                        "/Type /Pages\n" +
                        "/Kids [3 0 R]\n" +
                        "/Count 1\n" +
                        ">>\n" +
                        "endobj\n" +
                        "\n" +
                        "3 0 obj\n" +
                        "<<\n" +
                        "/Type /Page\n" +
                        "/Parent 2 0 R\n" +
                        "/MediaBox [0 0 612 792]\n" +
                        "/Contents 4 0 R\n" +
                        ">>\n" +
                        "endobj\n" +
                        "\n" +
                        "4 0 obj\n" +
                        "<<\n" +
                        "/Length 44\n" +
                        ">>\n" +
                        "stream\n" +
                        "BT\n" +
                        "/F1 12 Tf\n" +
                        "72 720 Td\n" +
                        "(Sample Resume) Tj\n" +
                        "ET\n" +
                        "endstream\n" +
                        "endobj\n" +
                        "\n" +
                        "xref\n" +
                        "0 5\n" +
                        "0000000000 65535 f \n" +
                        "0000000009 00000 n \n" +
                        "0000000074 00000 n \n" +
                        "0000000120 00000 n \n" +
                        "0000000179 00000 n \n" +
                        "trailer\n" +
                        "<<\n" +
                        "/Size 5\n" +
                        "/Root 1 0 R\n" +
                        ">>\n" +
                        "startxref\n" +
                        "238\n" +
                        "%%EOF";
                    java.nio.file.Files.writeString(sample, pdfContent);
                }
            } catch (Exception ignored) {}

            // Seed a default application so recruiter dashboard has data
            Application application = new Application();
            application.setUser(candidate);
            application.setJob(job);
            application.setResume(resume);
            application.setStatus(Application.Status.PENDING);
            application.setAtsScore(87.0);
            applicationRepository.save(application);
        } else if (jobRepository.count() == 0) {
            // If users exist but no jobs, create one under any recruiter
            User recruiter = userRepository.findAll().stream()
                    .filter(u -> u.getRole() == User.Role.RECRUITER)
                    .findFirst()
                    .orElse(null);
            if (recruiter != null) {
                Job job = new Job();
                job.setTitle("Full Stack Engineer");
                job.setCompany("StartupXYZ");
                job.setDescription("Scale our platform to millions of users.");
                job.setRequirements("Node.js, React, PostgreSQL, AWS");
                job.setSkills("Node.js,React,PostgreSQL,AWS");
                job.setLocation("Remote");
                job.setSalaryRange("$100k - $140k");
                job.setJobType("Full-time");
                job.setIsPublished(true);
                job.setCreatedBy(recruiter);
                job = jobRepository.save(job);

                // If there's any candidate, ensure at least one has a resume and application
                User candidate = userRepository.findAll().stream()
                        .filter(u -> u.getRole() == User.Role.CANDIDATE)
                        .findFirst().orElse(null);
                if (candidate != null) {
                    Resume resume = resumeRepository.findByUser(candidate).stream().findFirst().orElse(null);
                    if (resume == null) {
                        resume = new Resume();
                        resume.setUser(candidate);
                        resume.setTitle("Sample Resume");
                        resume.setFilePath("resumes/sample-default.pdf");
                        resume = resumeRepository.save(resume);
                        try {
                            java.nio.file.Path uploadDir = java.nio.file.Paths.get("uploads", "resumes");
                            java.nio.file.Files.createDirectories(uploadDir);
                            java.nio.file.Path sample = uploadDir.resolve("sample-default.pdf");
                            if (!java.nio.file.Files.exists(sample)) {
                                // Create a proper minimal PDF file
                                String pdfContent = "%PDF-1.4\n" +
                                    "1 0 obj\n" +
                                    "<<\n" +
                                    "/Type /Catalog\n" +
                                    "/Pages 2 0 R\n" +
                                    ">>\n" +
                                    "endobj\n" +
                                    "2 0 obj\n" +
                                    "<<\n" +
                                    "/Type /Pages\n" +
                                    "/Kids [3 0 R]\n" +
                                    "/Count 1\n" +
                                    ">>\n" +
                                    "endobj\n" +
                                    "3 0 obj\n" +
                                    "<<\n" +
                                    "/Type /Page\n" +
                                    "/Parent 2 0 R\n" +
                                    "/MediaBox [0 0 612 792]\n" +
                                    "/Contents 4 0 R\n" +
                                    ">>\n" +
                                    "endobj\n" +
                                    "4 0 obj\n" +
                                    "<<\n" +
                                    "/Length 44\n" +
                                    ">>\n" +
                                    "stream\n" +
                                    "BT\n" +
                                    "/F1 12 Tf\n" +
                                    "100 700 Td\n" +
                                    "(Sample Resume) Tj\n" +
                                    "ET\n" +
                                    "endstream\n" +
                                    "endobj\n" +
                                    "xref\n" +
                                    "0 5\n" +
                                    "0000000000 65535 f \n" +
                                    "0000000009 65535 n \n" +
                                    "0000000074 65535 n \n" +
                                    "0000000131 65535 n \n" +
                                    "0000000227 65535 n \n" +
                                    "trailer\n" +
                                    "<<\n" +
                                    "/Size 5\n" +
                                    "/Root 1 0 R\n" +
                                    ">>\n" +
                                    "startxref\n" +
                                    "320\n" +
                                    "%%EOF";
                                java.nio.file.Files.write(sample, pdfContent.getBytes());
                            }
                        } catch (Exception e) {
                            System.err.println("Failed to create sample PDF: " + e.getMessage());
                        }
                    }

                    boolean exists = applicationRepository.existsByUserAndJob(candidate, job);
                    if (!exists) {
                        Application application = new Application();
                        application.setUser(candidate);
                        application.setJob(job);
                        application.setResume(resume);
                        application.setStatus(Application.Status.PENDING);
                        application.setAtsScore(82.0);
                        applicationRepository.save(application);
                    }
                }
            }
        }
    }
}
