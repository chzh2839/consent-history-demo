package com.demo.consent.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 약관 버전 엔티티
 *
 * 약관은 개정될 때마다 새로운 버전 레코드가 추가된다.
 * 현행 버전은 is_active = true 인 레코드 하나만 존재한다.
 *
 * 핵심 설계 포인트:
 * - 약관 개정 시 기존 레코드를 수정하지 않고 새 버전 레코드를 INSERT한다. (불변성)
 * - 사용자가 어떤 버전에 동의했는지 ConsentHistory에 버전 ID로 추적한다.
 */
@Entity
@Table(
    name = "terms_version",
    uniqueConstraints = @UniqueConstraint(columnNames = {"terms_id", "version"})
)
@Getter
@NoArgsConstructor
public class TermsVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terms_id", nullable = false)
    private Terms terms;

    /**
     * 버전 식별자 (예: v1.0, v1.1, v2.0)
     */
    @Column(nullable = false, length = 20)
    private String version;

    /**
     * 약관 본문 내용
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 현행 활성 버전 여부
     * - 한 약관에 is_active=true 레코드는 반드시 1개만 존재
     */
    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    /**
     * 해당 버전의 시행일
     */
    @Column(name = "effective_date", nullable = false)
    private LocalDateTime effectiveDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void deactivate() {
        this.isActive = false;
    }
}
