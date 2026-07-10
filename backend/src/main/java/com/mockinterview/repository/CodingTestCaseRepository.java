package com.mockinterview.repository;

import com.mockinterview.entity.CodingTestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CodingTestCaseRepository extends JpaRepository<CodingTestCase, Long> {
    List<CodingTestCase> findByCodingQuestionId(Long codingQuestionId);
}
