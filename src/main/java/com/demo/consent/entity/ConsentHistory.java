package com.demo.consent.entity;

import com.demo.consent.enums.ConsentStatus;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 동의 이력 엔티티
 *
 * 핵심 설계 포인트:
 * - 동의/철회 모든 행위를 INSERT로 기록하며 절대 UPDATE/DELETE하지 않는다.
 * - 특정 시점의 동의 상태는 최신 레코드의 status 값으로 판단한다.
 * - 어떤 버전에 동의했는지 terms_version_id로 추적 가능하다.
 *
 * 이 불변 이력 설계로 감사(Audit), 규제 대응, 분쟁 해결에 활용할 수 있다.
 */
@Entity
@Table(name = "consent_history")
@Getter
@NoArgsConstructor
public class ConsentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terms_version_id", nullable = false)
    private TermsVersion termsVersion;

    /**
     * 동의 상태: AGREED(동의) / WITHDRAWN(철회)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConsentStatus status;

    /**
     * 동의/철회 처리 시각
     */
    @Column(name = "consented_at", nullable = false, updatable = false)
    private LocalDateTime consentedAt;

    @PrePersist
    protected void onCreate() {
        this.consentedAt = LocalDateTime.now();
    }

    @Builder
    public ConsentHistory(User user, TermsVersion termsVersion, ConsentStatus status) {
        this.user = user;
        this.termsVersion = termsVersion;
        this.status = status;
    }
}
