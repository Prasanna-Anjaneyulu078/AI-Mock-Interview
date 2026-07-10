package com.mockinterview.repository;

import com.mockinterview.entity.CodingSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CodingSubmissionRepository extends JpaRepository<CodingSubmission, Long> {
    List<CodingSubmission> findByInterviewId(Long interviewId);
    List<CodingSubmission> findByCodingQuestionId(Long codingQuestionId);
}
