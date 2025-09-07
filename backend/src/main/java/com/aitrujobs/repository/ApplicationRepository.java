package com.aitrujobs.repository;

import com.aitrujobs.entity.Application;
import com.aitrujobs.entity.Job;
import com.aitrujobs.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {
    
    @Query("SELECT a FROM Application a JOIN FETCH a.job WHERE a.user = :user")
    Page<Application> findByUser(@Param("user") User user, Pageable pageable);
    
    Page<Application> findByJob(Job job, Pageable pageable);
    
    Optional<Application> findByUserAndJob(User user, Job job);
    
    boolean existsByUserAndJob(User user, Job job);
    

    @Query("SELECT a FROM Application a JOIN FETCH a.job j JOIN FETCH a.user u LEFT JOIN FETCH a.resume r WHERE j.createdBy = :recruiter ORDER BY a.createdAt DESC")
    Page<Application> findByJobUser(@Param("recruiter") User recruiter, Pageable pageable);

    @Query("SELECT a FROM Application a JOIN FETCH a.job j JOIN FETCH a.user u LEFT JOIN FETCH a.resume r WHERE j.createdBy = :recruiter AND a.status = :status ORDER BY a.createdAt DESC")
    Page<Application> findByJobUserAndStatus(@Param("recruiter") User recruiter, @Param("status") Application.Status status, Pageable pageable);

    @Query("SELECT a FROM Application a JOIN FETCH a.job j JOIN FETCH a.user u LEFT JOIN FETCH a.resume r WHERE j.createdBy = :recruiter ORDER BY a.createdAt DESC")
    Page<Application> findApplicationsForRecruiter(@Param("recruiter") User recruiter, Pageable pageable);
    
    @Query("SELECT a FROM Application a WHERE a.status = :status ORDER BY a.atsScore DESC")
    Page<Application> findByStatusOrderByAtsScoreDesc(@Param("status") Application.Status status, Pageable pageable);
    
    // Count methods for dashboard statistics
    long countByUser(User user);
    
    long countByUserAndStatus(User user, Application.Status status);
    
    @Query("SELECT COUNT(a) FROM Application a WHERE a.job.createdBy = :recruiter")
    long countByJobUser(@Param("recruiter") User recruiter);
    
    @Query("SELECT COUNT(a) FROM Application a WHERE a.job.createdBy = :recruiter AND a.status = :status")
    long countByJobUserAndStatus(@Param("recruiter") User recruiter, @Param("status") Application.Status status);
    
    @Query("SELECT COUNT(a) FROM Application a WHERE a.status = :status")
    long countByStatus(@Param("status") Application.Status status);

    @Query("SELECT a FROM Application a JOIN FETCH a.job j JOIN FETCH j.createdBy JOIN FETCH a.user u LEFT JOIN FETCH a.resume r WHERE a.id = :id")
    Optional<Application> findByIdWithAssociations(@Param("id") Long id);
}
