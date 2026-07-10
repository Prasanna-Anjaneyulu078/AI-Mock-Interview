package com.mockinterview.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * A single auto-grading test case for a code question. Test cases are executed by
 * Judge0: the candidate's submitted source is run with {@code input} as stdin and the
 * trimmed stdout is compared against {@code expectedOutput}. Visible cases are shown to
 * the candidate; hidden cases are graded but not revealed.
 */
@Entity
@Table(name = "test_cases", indexes = @Index(name = "idx_testcase_question", columnList = "question_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Question question;

    @Column(columnDefinition = "TEXT")
    private String input;

    @Column(columnDefinition = "TEXT")
    private String expectedOutput;

    private boolean hidden;

    private String name;
}
