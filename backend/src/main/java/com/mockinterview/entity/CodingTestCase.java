package com.mockinterview.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "coding_test_cases")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodingTestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coding_question_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private CodingQuestion codingQuestion;

    private String name;

    @Column(columnDefinition = "TEXT")
    private String input;

    @Column(columnDefinition = "TEXT")
    private String expectedOutput;

    private Boolean isHidden;
}
