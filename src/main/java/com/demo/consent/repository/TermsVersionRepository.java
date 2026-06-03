package com.demo.consent.repository;

import com.demo.consent.entity.Terms;
import com.demo.consent.entity.TermsVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TermsVersionRepository extends JpaRepository<TermsVersion, Long> {

    /**
     * 특정 약관의 현행 활성 버전 조회
     */
    Optional<TermsVersion> findByTermsAndIsActiveTrue(Terms terms);

    /**
     * 특정 약관의 모든 버전 이력 조회 (최신순)
     */
    @Query("SELECT tv FROM TermsVersion tv WHERE tv.terms.id = :termsId ORDER BY tv.effectiveDate DESC")
    List<TermsVersion> findAllByTermsIdOrderByEffectiveDateDesc(@Param("termsId") Long termsId);

    /**
     * 모든 약관의 현행 활성 버전 목록 조회
     */
    List<TermsVersion> findAllByIsActiveTrue();
}
