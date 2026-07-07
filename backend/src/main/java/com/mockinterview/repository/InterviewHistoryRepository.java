package com.mockinterview.repository;

import com.mockinterview.entity.InterviewHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewHistoryRepository extends JpaRepository<InterviewHistory, Long> {
    List<InterviewHistory> findByUserIdOrderByCreatedAtDesc(Long userId);
    org.springframework.data.domain.Page<InterviewHistory> findByUserId(Long userId, org.springframework.data.domain.Pageable pageable);
    Optional<InterviewHistory> findByInterviewId(Long interviewId);
    void deleteByUserId(Long userId);
}
