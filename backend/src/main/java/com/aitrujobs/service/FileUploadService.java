package com.aitrujobs.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.aitrujobs.exception.ResourceNotFoundException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileUploadService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final String[] ALLOWED_EXTENSIONS = {".pdf", ".doc", ".docx"};

    public String uploadFile(MultipartFile file, String subDirectory) {
        validateFile(file);
        
        try {
            // Create directory if it doesn't exist
            Path uploadPath = Paths.get(uploadDir, subDirectory);
            Files.createDirectories(uploadPath);
            
            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename);
            String uniqueFilename = UUID.randomUUID().toString() + extension;
            
            // Save file
            Path filePath = uploadPath.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            
            return subDirectory + "/" + uniqueFilename;
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + e.getMessage());
        }
    }

    public byte[] downloadFile(String filePath) {
        try {
            Path file = Paths.get(uploadDir, filePath);
            System.out.println("Attempting to download from: " + file.toString());
            System.out.println("Upload directory: " + uploadDir);
            System.out.println("File path parameter: " + filePath);
            System.out.println("File exists: " + Files.exists(file));
            
            if (!Files.exists(file)) {
                System.err.println("File not found at: " + file.toString());
                throw new ResourceNotFoundException("File not found: " + filePath);
            }
            return Files.readAllBytes(file);
        } catch (IOException e) {
            System.err.println("IO Error reading file: " + filePath + ", Error: " + e.getMessage());
            throw new RuntimeException("Failed to read file: " + e.getMessage());
        }
    }

    public void deleteFile(String filePath) {
        try {
            Path file = Paths.get(uploadDir, filePath);
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file: " + e.getMessage());
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }
        
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum limit of 10MB");
        }
        
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("File must have a name");
        }
        
        String extension = getFileExtension(filename).toLowerCase();
        boolean isValidExtension = false;
        for (String allowedExt : ALLOWED_EXTENSIONS) {
            if (extension.equals(allowedExt)) {
                isValidExtension = true;
                break;
            }
        }
        
        if (!isValidExtension) {
            throw new IllegalArgumentException("File type not supported. Allowed types: PDF, DOC, DOCX");
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex >= 0 ? filename.substring(lastDotIndex) : "";
    }
}
