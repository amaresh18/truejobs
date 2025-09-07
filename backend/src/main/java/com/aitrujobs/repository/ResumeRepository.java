package com.aitrujobs.repository;

import com.aitrujobs.entity.Resume;
import com.aitrujobs.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResumeRepository extends JpaRepository<Resume, Long> {
    
    List<Resume> findByUser(User user);
    
    List<Resume> findByUserOrderByCreatedAtDesc(User user);
    
    Optional<Resume> findFirstByUserOrderByCreatedAtDesc(User user);
}
