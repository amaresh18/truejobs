package com.aitrujobs.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OpenAIService {
    
    private final WebClient webClient;
    
    @Value("${openai.api.key}")
    private String apiKey;
    
    public OpenAIService(@Value("${openai.api.url}") String apiUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
    
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !"demo-key-replace-with-real".equals(apiKey);
    }
    
    /**
     * Generate embeddings for text using OpenAI's text-embedding-ada-002 model
     */
    public Mono<List<Double>> generateEmbedding(String text) {
        if (!isConfigured()) {
            log.warn("OpenAI API key not configured; returning empty embedding");
            return Mono.just(List.of());
        }
        Map<String, Object> request = Map.of(
                "model", "text-embedding-ada-002",
                "input", text
        );
        
        return webClient.post()
                .uri("/embeddings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
                    @SuppressWarnings("unchecked")
                    List<Double> embedding = (List<Double>) data.get(0).get("embedding");
                    return embedding;
                })
                .doOnError(error -> log.error("Error generating embedding: ", error));
    }
    
    /**
     * Generate job description using OpenAI's GPT model
     */
    public Mono<String> generateJobDescription(String jobTitle, String company, String requirements) {
        String prompt = String.format(
                "Create a professional job description for the position of %s at %s. " +
                "Requirements: %s. " +
                "Include job summary, key responsibilities, qualifications, and benefits. " +
                "Make it engaging and comprehensive.",
                jobTitle, company, requirements
        );
        
        return generateChatCompletion(prompt);
    }
    
    /**
     * Generate ATS feedback for a resume-job match
     */
    public Mono<String> generateATSFeedback(String resumeText, String jobDescription, double score) {
        String prompt = String.format(
                "Analyze this resume against the job description and provide constructive ATS feedback. " +
                "ATS Score: %.1f/100. " +
                "Resume: %s " +
                "Job Description: %s " +
                "Provide specific feedback on skills match, experience relevance, and improvement suggestions.",
                score, resumeText, jobDescription
        );
        
        return generateChatCompletion(prompt);
    }
    
    /**
     * Generate rejection reason
     */
    public Mono<String> generateRejectionReason(String resumeText, String jobDescription) {
        String prompt = String.format(
                "Generate a professional and constructive rejection reason for this candidate. " +
                "Resume: %s " +
                "Job Description: %s " +
                "Be specific about skills gaps but encouraging for future applications.",
                resumeText, jobDescription
        );
        
        return generateChatCompletion(prompt);
    }
    
    /**
     * Calculate ATS score based on resume and job description similarity
     */
    public Mono<Double> calculateATSScore(String resumeText, String jobDescription) {
        return Mono.zip(
                generateEmbedding(resumeText),
                generateEmbedding(jobDescription)
        ).map(tuple -> {
            List<Double> resumeEmbedding = tuple.getT1();
            List<Double> jobEmbedding = tuple.getT2();
            
            double similarity = calculateCosineSimilarity(resumeEmbedding, jobEmbedding);
            // Convert to 0-100 scale
            return Math.max(0, Math.min(100, similarity * 100));
        });
    }
    
    /**
     * Generic chat completion method
     */
    public Mono<String> generateChatCompletion(String prompt) {
        if (!isConfigured()) {
            log.warn("OpenAI API key not configured; returning placeholder chat response");
            return Mono.just("[AI disabled in dev]");
        }
        Map<String, Object> request = Map.of(
                "model", "gpt-3.5-turbo",
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                ),
                "max_tokens", 500,
                "temperature", 0.7
        );
        
        return webClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    return (String) message.get("content");
                })
                .doOnError(error -> log.error("Error generating chat completion: ", error));
    }
    
    /**
     * Calculate cosine similarity between two embedding vectors
     */
    public double calculateCosineSimilarity(List<Double> vectorA, List<Double> vectorB) {
        if (vectorA.size() != vectorB.size()) {
            throw new IllegalArgumentException("Vectors must have the same dimension");
        }
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < vectorA.size(); i++) {
            dotProduct += vectorA.get(i) * vectorB.get(i);
            normA += Math.pow(vectorA.get(i), 2);
            normB += Math.pow(vectorB.get(i), 2);
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
