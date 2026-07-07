package com.mockinterview.service;

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

        Map<String, Object> result = new HashMap<>();
        result.put("entries", historyPage.getContent());
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
    public void deleteHistoryEntry(Long entryId, Long userId) {
        Interview interview = interviewRepository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found"));
        interviewRepository.delete(interview);
    }

    @Transactional
    public Map<String, Object> clearUserHistory(Long userId) {
        // Since we cascaded delete, deleting Interviews will delete Histories. Wait, usually we'd delete history, but let's delete interviews.
        long deletedCount = interviewRepository.deleteByUserId(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("message", "All history cleared");
        result.put("deletedCount", deletedCount);
        return result;
    }
}
