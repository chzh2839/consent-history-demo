package com.demo.consent.repository;

import com.demo.consent.entity.ConsentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConsentHistoryRepository extends JpaRepository<ConsentHistory, Long> {

    /**
     * 특정 사용자의 전체 동의 이력 조회 (최신순)
     */
    @Query("""
        SELECT ch FROM ConsentHistory ch
        JOIN FETCH ch.termsVersion tv
        JOIN FETCH tv.terms t
        WHERE ch.user.id = :userId
        ORDER BY ch.consentedAt DESC
    """)
    List<ConsentHistory> findAllByUserIdOrderByConsentedAtDesc(@Param("userId") Long userId);

    /**
     * 특정 사용자 + 특정 약관에 대한 최신 동의 이력 조회
     * → 현재 동의 상태 판단에 사용
     */
    @Query("""
        SELECT ch FROM ConsentHistory ch
        WHERE ch.user.id = :userId
          AND ch.termsVersion.terms.id = :termsId
        ORDER BY ch.consentedAt DESC
        LIMIT 1
    """)
    Optional<ConsentHistory> findLatestByUserIdAndTermsId(
            @Param("userId") Long userId,
            @Param("termsId") Long termsId
    );
}
