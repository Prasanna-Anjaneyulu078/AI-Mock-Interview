package com.mockinterview.repository;

import com.mockinterview.entity.CodingResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CodingResultRepository extends JpaRepository<CodingResult, Long> {
    Optional<CodingResult> findBySubmissionId(Long submissionId);
}
