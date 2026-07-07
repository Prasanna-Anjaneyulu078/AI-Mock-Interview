package com.mockinterview.service;

import com.mockinterview.dto.HistoryEntryDTO;
import com.mockinterview.entity.Interview;
import com.mockinterview.entity.InterviewHistory;
import com.mockinterview.exception.ResourceNotFoundException;
import com.mockinterview.repository.InterviewHistoryRepository;
import com.mockinterview.repository.InterviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HistoryService {

    private final InterviewRepository interviewRepository;
    private final InterviewHistoryRepository historyRepository;

    public HistoryService(InterviewRepository interviewRepository, InterviewHistoryRepository historyRepository) {
        this.interviewRepository = interviewRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getUserHistory(Long userId, int page, int limit) {
        PageRequest pageRequest = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<InterviewHistory> historyPage = historyRepository.findByUserId(userId, pageRequest);

        // Map to DTOs inside the transaction to avoid LazyInitializationException
        // and Jackson circular serialization (InterviewHistory -> User -> InterviewHistory)
        List<HistoryEntryDTO> entries = historyPage.getContent()
                .stream()
                .map(HistoryEntryDTO::from)
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("entries", entries);
        result.put("totalEntries", historyPage.getTotalElements());
        result.put("totalPages", historyPage.getTotalPages());
        result.put("currentPage", page);
        return result;
    }

    @Transactional(readOnly = true)
    public Interview getHistoryEntry(Long entryId, Long userId) {
        // Node returns the Interview, Spring might return Interview or InterviewHistory. Let's return Interview for parity
        return interviewRepository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found"));
    }

    @Transactional
    public void deleteHistoryEntry(Long interviewId, Long userId) {
        Interview interview = interviewRepository.findByIdAndUserId(interviewId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found"));
        
        // Remove referencing history record first to avoid SQL constraint violations
        historyRepository.findByInterviewId(interviewId).ifPresent(historyRepository::delete);
        
        // Delete interview
        interviewRepository.delete(interview);
    }

    @Transactional
    public Map<String, Object> clearUserHistory(Long userId) {
        // Delete InterviewHistory records first to satisfy FK constraints,
        // then delete the parent Interview records.
        historyRepository.deleteByUserId(userId);
        long deletedCount = interviewRepository.deleteByUserId(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("message", "All history cleared");
        result.put("deletedCount", deletedCount);
        return result;
    }
}
