package com.mockinterview.repository;

import com.mockinterview.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    @Query("SELECT q FROM Question q WHERE q.interview.id = :interviewId ORDER BY q.sequence ASC NULLS LAST")
    List<Question> findByInterviewId(@Param("interviewId") Long interviewId);

    @Query("SELECT q FROM Question q WHERE q.interview.user.id = :userId ORDER BY q.interview.id, q.sequence ASC NULLS LAST")
    List<Question> findByInterviewUserId(@Param("userId") Long userId);

    @Query("SELECT q FROM Question q WHERE q.interview.resumeId = :resumeId ORDER BY q.sequence ASC NULLS LAST")
    List<Question> findByInterviewResumeId(@Param("resumeId") Long resumeId);
}
