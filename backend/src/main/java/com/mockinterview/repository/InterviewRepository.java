package com.mockinterview.repository;

import com.mockinterview.entity.Interview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewRepository extends JpaRepository<Interview, Long> {
    List<Interview> findByUserId(Long userId);
    Optional<Interview> findByIdAndUserId(Long id, Long userId);
    long deleteByUserId(Long userId);
}
