package com.mockinterview.repository;

import com.mockinterview.entity.QuestionBank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionBankRepository extends JpaRepository<QuestionBank, Long> {
    
    @Query(value = "SELECT * FROM question_bank WHERE role = :role AND difficulty = :difficulty AND category = :category AND is_active = true ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<QuestionBank> findRandomQuestions(@Param("role") String role, 
                                           @Param("difficulty") String difficulty, 
                                           @Param("category") String category, 
                                           @Param("limit") int limit);
}
