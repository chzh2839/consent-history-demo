package com.demo.consent.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 약관 마스터 엔티티
 *
 * 약관의 종류(코드/이름)를 관리한다.
 * 실제 내용과 버전은 TermsVersion에서 관리한다.
 */
@Entity
@Table(name = "terms")
@Getter
@NoArgsConstructor
public class Terms {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 약관 식별 코드 (예: TERMS_OF_SERVICE, PRIVACY_POLICY)
     */
    @Column(name = "terms_code", nullable = false, unique = true, length = 50)
    private String termsCode;

    /**
     * 약관 이름 (예: 서비스 이용약관)
     */
    @Column(name = "terms_name", nullable = false, length = 100)
    private String termsName;

    /**
     * 필수 동의 여부
     */
    @Column(name = "is_mandatory", nullable = false)
    private boolean isMandatory;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "terms", fetch = FetchType.LAZY)
    private List<TermsVersion> versions = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
