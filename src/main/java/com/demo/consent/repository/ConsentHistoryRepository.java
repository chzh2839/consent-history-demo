package com.demo.consent.repository;

import com.demo.consent.entity.ConsentHistory;
import com.demo.consent.enums.ConsentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ConsentHistoryRepository extends JpaRepository<ConsentHistory, Long> {

    /**
     * 특정 사용자의 전체 동의 이력 조회 (최신순, 페이지네이션 없음)
     * 이력을 전량 조회해야 하는 내부 용도(테스트 정리 등)에 사용한다.
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
     * 특정 사용자의 동의 이력을 조건별로 검색 (페이지 단위)
     * termsId/status/from/to는 모두 선택 값이며, null이면 해당 조건은 무시된다.
     * JOIN FETCH가 있는 쿼리는 count 쿼리를 자동 유도하기 어려워 countQuery를 명시한다.
     * 정렬은 Pageable의 Sort(컨트롤러 기본값: consentedAt DESC)를 그대로 따른다.
     */
    @Query(
        value = """
            SELECT ch FROM ConsentHistory ch
            JOIN FETCH ch.termsVersion tv
            JOIN FETCH tv.terms t
            WHERE ch.user.id = :userId
              AND (:termsId IS NULL OR t.id = :termsId)
              AND (:status IS NULL OR ch.status = :status)
              AND (:from IS NULL OR ch.consentedAt >= :from)
              AND (:to IS NULL OR ch.consentedAt <= :to)
        """,
        countQuery = """
            SELECT COUNT(ch) FROM ConsentHistory ch
            WHERE ch.user.id = :userId
              AND (:termsId IS NULL OR ch.termsVersion.terms.id = :termsId)
              AND (:status IS NULL OR ch.status = :status)
              AND (:from IS NULL OR ch.consentedAt >= :from)
              AND (:to IS NULL OR ch.consentedAt <= :to)
        """
    )
    Page<ConsentHistory> search(
            @Param("userId") Long userId,
            @Param("termsId") Long termsId,
            @Param("status") ConsentStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

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
