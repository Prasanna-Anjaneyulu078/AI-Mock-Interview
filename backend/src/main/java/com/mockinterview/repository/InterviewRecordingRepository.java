package com.mockinterview.repository;

import com.mockinterview.entity.InterviewRecording;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterviewRecordingRepository extends JpaRepository<InterviewRecording, Long> {
    List<InterviewRecording> findByInterviewId(Long interviewId);
    InterviewRecording findByQuestionId(Long questionId);
}
