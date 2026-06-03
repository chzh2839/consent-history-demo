package com.demo.consent.service;

import com.demo.consent.dto.ConsentRequest;
import com.demo.consent.dto.ConsentResponse;
import com.demo.consent.entity.ConsentHistory;
import com.demo.consent.entity.Terms;
import com.demo.consent.entity.TermsVersion;
import com.demo.consent.entity.User;
import com.demo.consent.enums.ConsentStatus;
import com.demo.consent.exception.ConsentException;
import com.demo.consent.exception.ResourceNotFoundException;
import com.demo.consent.repository.ConsentHistoryRepository;
import com.demo.consent.repository.TermsRepository;
import com.demo.consent.repository.TermsVersionRepository;
import com.demo.consent.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentService {

    private final UserRepository userRepository;
    private final TermsRepository termsRepository;
    private final TermsVersionRepository termsVersionRepository;
    private final ConsentHistoryRepository consentHistoryRepository;

    // ───────────────────────────────────────────────
    // 약관 조회
    // ───────────────────────────────────────────────

    /**
     * 현행 활성 약관 목록 전체 조회
     */
    @Transactional(readOnly = true)
    public List<ConsentResponse.TermsInfo> getActiveTermsList() {
        return termsVersionRepository.findAllByIsActiveTrue()
                .stream()
                .map(ConsentResponse.TermsInfo::from)
                .collect(Collectors.toList());
    }

    /**
     * 특정 약관의 버전 이력 조회
     */
    @Transactional(readOnly = true)
    public List<ConsentResponse.TermsInfo> getTermsVersionHistory(Long termsId) {
        termsRepository.findById(termsId)
                .orElseThrow(() -> new ResourceNotFoundException("약관을 찾을 수 없습니다. id=" + termsId));

        return termsVersionRepository.findAllByTermsIdOrderByEffectiveDateDesc(termsId)
                .stream()
                .map(ConsentResponse.TermsInfo::from)
                .collect(Collectors.toList());
    }

    // ───────────────────────────────────────────────
    // 동의 (AGREED)
    // ───────────────────────────────────────────────

    /**
     * 약관 동의
     *
     * 비즈니스 규칙:
     * 1. 현행 활성 버전이 존재해야 동의 가능
     * 2. 이미 현행 버전에 동의 상태(AGREED)인 경우 중복 동의 불가
     *    → 단, 이전 버전에 동의했거나 철회 후 재동의는 허용 (재동의 시나리오)
     */
    @Transactional
    public ConsentResponse.Result agree(ConsentRequest.Agree request) {
        User user = findUser(request.getUserId());
        Terms terms = findTerms(request.getTermsId());
        TermsVersion activeVersion = findActiveVersion(terms);

        // 현재 상태 확인: 최신 이력이 이미 현행 버전 AGREED 이면 중복 방지
        Optional<ConsentHistory> latestHistory =
                consentHistoryRepository.findLatestByUserIdAndTermsId(user.getId(), terms.getId());

        if (latestHistory.isPresent()) {
            ConsentHistory latest = latestHistory.get();
            boolean isSameVersion = latest.getTermsVersion().getId().equals(activeVersion.getId());
            boolean isAlreadyAgreed = latest.getStatus() == ConsentStatus.AGREED;

            if (isSameVersion && isAlreadyAgreed) {
                throw new ConsentException(
                        String.format("이미 '%s' 약관 %s 버전에 동의하셨습니다.",
                                terms.getTermsName(), activeVersion.getVersion())
                );
            }
        }

        ConsentHistory history = ConsentHistory.builder()
                .user(user)
                .termsVersion(activeVersion)
                .status(ConsentStatus.AGREED)
                .build();

        ConsentHistory saved = consentHistoryRepository.save(history);
        log.info("동의 완료 - userId={}, termsId={}, version={}", user.getId(), terms.getId(), activeVersion.getVersion());

        return ConsentResponse.Result.from(saved);
    }

    // ───────────────────────────────────────────────
    // 철회 (WITHDRAWN)
    // ───────────────────────────────────────────────

    /**
     * 약관 동의 철회
     *
     * 비즈니스 규칙:
     * 1. 필수 약관은 철회 불가
     * 2. 현재 AGREED 상태가 아니면 철회 불가
     */
    @Transactional
    public ConsentResponse.Result withdraw(ConsentRequest.Withdraw request) {
        User user = findUser(request.getUserId());
        Terms terms = findTerms(request.getTermsId());

        // 필수 약관 철회 불가
        if (terms.isMandatory()) {
            throw new ConsentException(
                    String.format("'%s'은 필수 약관으로 철회할 수 없습니다.", terms.getTermsName())
            );
        }

        // 현재 동의 상태 확인
        ConsentHistory latestHistory =
                consentHistoryRepository.findLatestByUserIdAndTermsId(user.getId(), terms.getId())
                        .orElseThrow(() -> new ConsentException(
                                String.format("'%s' 약관에 동의한 이력이 없습니다.", terms.getTermsName())
                        ));

        if (latestHistory.getStatus() == ConsentStatus.WITHDRAWN) {
            throw new ConsentException(
                    String.format("'%s' 약관은 이미 철회 상태입니다.", terms.getTermsName())
            );
        }

        TermsVersion activeVersion = findActiveVersion(terms);

        ConsentHistory withdrawHistory = ConsentHistory.builder()
                .user(user)
                .termsVersion(activeVersion)
                .status(ConsentStatus.WITHDRAWN)
                .build();

        ConsentHistory saved = consentHistoryRepository.save(withdrawHistory);
        log.info("철회 완료 - userId={}, termsId={}", user.getId(), terms.getId());

        return ConsentResponse.Result.from(saved);
    }

    // ───────────────────────────────────────────────
    // 동의 이력 조회
    // ───────────────────────────────────────────────

    /**
     * 특정 사용자의 전체 동의 이력 조회
     */
    @Transactional(readOnly = true)
    public ConsentResponse.HistoryList getConsentHistory(Long userId) {
        User user = findUser(userId);

        List<ConsentHistory> histories =
                consentHistoryRepository.findAllByUserIdOrderByConsentedAtDesc(user.getId());

        List<ConsentResponse.HistoryItem> items = histories.stream()
                .map(ConsentResponse.HistoryItem::from)
                .collect(Collectors.toList());

        return ConsentResponse.HistoryList.builder()
                .userId(userId)
                .histories(items)
                .totalCount(items.size())
                .build();
    }

    // ───────────────────────────────────────────────
    // Private helpers
    // ───────────────────────────────────────────────

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다. id=" + userId));
    }

    private Terms findTerms(Long termsId) {
        return termsRepository.findById(termsId)
                .orElseThrow(() -> new ResourceNotFoundException("약관을 찾을 수 없습니다. id=" + termsId));
    }

    private TermsVersion findActiveVersion(Terms terms) {
        return termsVersionRepository.findByTermsAndIsActiveTrue(terms)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("'%s' 약관의 활성 버전이 존재하지 않습니다.", terms.getTermsName())
                ));
    }
}
