
package com.aitrujobs.repository;

import java.util.Optional;

import com.aitrujobs.entity.Job;
import com.aitrujobs.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {
    
    Page<Job> findByIsPublishedTrue(Pageable pageable);
    
    List<Job> findByIsPublishedTrueAndIdNot(Long excludeId);
    
    Page<Job> findByCreatedBy(User createdBy, Pageable pageable);
    
    @Query("SELECT j FROM Job j WHERE j.isPublished = true AND " +
           "(LOWER(j.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(j.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(j.skills) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Job> searchJobs(@Param("keyword") String keyword, Pageable pageable);
    
    @Query("SELECT j FROM Job j WHERE j.isPublished = true AND " +
           "(:location IS NULL OR LOWER(j.location) LIKE LOWER(CONCAT('%', :location, '%'))) AND " +
           "(:skills IS NULL OR LOWER(j.skills) LIKE LOWER(CONCAT('%', :skills, '%'))) AND " +
           "(:jobType IS NULL OR j.jobType = :jobType)")
    Page<Job> findJobsWithFilters(@Param("location") String location, 
                                  @Param("skills") String skills, 
                                  @Param("jobType") String jobType, 
                                  Pageable pageable);
    
    // Count methods for dashboard statistics
    long countByCreatedBy(User createdBy);
    long countByCreatedByAndIsPublishedTrue(User createdBy);
       long countByIsPublishedTrue();

       @Query("SELECT j FROM Job j JOIN FETCH j.createdBy WHERE j.id = :id")
       Optional<Job> findByIdWithCreatedBy(@Param("id") Long id);
}
