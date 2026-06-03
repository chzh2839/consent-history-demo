package com.demo.consent.repository;

import com.demo.consent.entity.Terms;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TermsRepository extends JpaRepository<Terms, Long> {

    /**
     * 필수/선택 여부로 약관 목록 조회
     */
    List<Terms> findByIsMandatory(boolean isMandatory);
}
