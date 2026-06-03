package com.demo.consent.dto;

import com.demo.consent.entity.ConsentHistory;
import com.demo.consent.entity.Terms;
import com.demo.consent.entity.TermsVersion;
import com.demo.consent.enums.ConsentStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

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
     * 사용자 동의 이력 목록
     */
    @Getter
    @Builder
    public static class HistoryList {
        private Long userId;
        private List<HistoryItem> histories;
        private int totalCount;
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
