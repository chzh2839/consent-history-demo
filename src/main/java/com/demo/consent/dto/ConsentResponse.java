package com.demo.consent.dto;

import com.demo.consent.entity.ConsentHistory;
import com.demo.consent.entity.Terms;
import com.demo.consent.entity.TermsVersion;
import com.demo.consent.enums.ConsentStatus;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 동의 관련 응답 DTO
 */
public class ConsentResponse {

    /**
     * 동의/철회 처리 결과
     */
    @Getter
    @Builder
    public static class Result {
        private Long consentHistoryId;
        private Long userId;
        private Long termsId;
        private String termsName;
        private String version;
        private ConsentStatus status;
        private LocalDateTime consentedAt;

        public static Result from(ConsentHistory history) {
            TermsVersion tv = history.getTermsVersion();
            return Result.builder()
                    .consentHistoryId(history.getId())
                    .userId(history.getUser().getId())
                    .termsId(tv.getTerms().getId())
                    .termsName(tv.getTerms().getTermsName())
                    .version(tv.getVersion())
                    .status(history.getStatus())
                    .consentedAt(history.getConsentedAt())
                    .build();
        }
    }

    /**
     * 동의 이력 단건
     */
    @Getter
    @Builder
    public static class HistoryItem {
        private Long historyId;
        private Long termsId;
        private String termsCode;
        private String termsName;
        private boolean mandatory;
        private String version;
        private ConsentStatus status;
        private LocalDateTime consentedAt;

        public static HistoryItem from(ConsentHistory history) {
            TermsVersion tv = history.getTermsVersion();
            Terms terms = tv.getTerms();
            return HistoryItem.builder()
                    .historyId(history.getId())
                    .termsId(terms.getId())
                    .termsCode(terms.getTermsCode())
                    .termsName(terms.getTermsName())
                    .mandatory(terms.isMandatory())
                    .version(tv.getVersion())
                    .status(history.getStatus())
                    .consentedAt(history.getConsentedAt())
                    .build();
        }
    }

    /**
     * 사용자 동의 이력 목록 (페이지네이션)
     */
    @Getter
    @Builder
    public static class HistoryList {
        private Long userId;
        private List<HistoryItem> histories;
        private int page;            // 현재 페이지 (0-base)
        private int size;            // 페이지 크기
        private long totalElements;  // 전체 이력 건수
        private int totalPages;      // 전체 페이지 수
        private boolean hasNext;     // 다음 페이지 존재 여부

        public static HistoryList from(Long userId, Page<ConsentHistory> page) {
            List<HistoryItem> items = page.getContent().stream()
                    .map(HistoryItem::from)
                    .collect(Collectors.toList());
            return HistoryList.builder()
                    .userId(userId)
                    .histories(items)
                    .page(page.getNumber())
                    .size(page.getSize())
                    .totalElements(page.getTotalElements())
                    .totalPages(page.getTotalPages())
                    .hasNext(page.hasNext())
                    .build();
        }
    }

    /**
     * 약관 목록 (현행 버전 기준)
     */
    @Getter
    @Builder
    public static class TermsInfo {
        private Long termsId;
        private String termsCode;
        private String termsName;
        private boolean mandatory;
        private String currentVersion;
        private String content;
        private LocalDateTime effectiveDate;

        public static TermsInfo from(TermsVersion tv) {
            Terms terms = tv.getTerms();
            return TermsInfo.builder()
                    .termsId(terms.getId())
                    .termsCode(terms.getTermsCode())
                    .termsName(terms.getTermsName())
                    .mandatory(terms.isMandatory())
                    .currentVersion(tv.getVersion())
                    .content(tv.getContent())
                    .effectiveDate(tv.getEffectiveDate())
                    .build();
        }
    }
}
