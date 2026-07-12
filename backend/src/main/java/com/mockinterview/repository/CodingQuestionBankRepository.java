package com.mockinterview.repository;

import com.mockinterview.entity.CodingQuestionBank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CodingQuestionBankRepository extends JpaRepository<CodingQuestionBank, Long> {
    
    @Query(value = "SELECT * FROM coding_question_bank WHERE role = :role AND difficulty = :difficulty ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<CodingQuestionBank> findRandomCodingQuestions(@Param("role") String role, 
                                                       @Param("difficulty") String difficulty, 
                                                       @Param("limit") int limit);
}
