package com.demo.consent.controller;

import com.demo.consent.dto.ApiResponse;
import com.demo.consent.dto.ConsentRequest;
import com.demo.consent.dto.ConsentResponse;
import com.demo.consent.service.ConsentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 약관 동의 API 컨트롤러
 *
 * POST /consent/agree       - 약관 동의
 * POST /consent/withdraw    - 약관 철회
 * GET  /consent/history     - 동의 이력 조회
 * GET  /terms               - 현행 약관 목록 조회
 * GET  /terms/{termsId}/versions - 약관 버전 이력 조회
 */
@RestController
@RequiredArgsConstructor
public class ConsentController {

    private final ConsentService consentService;

    // ───────────────────────────────────────────────
    // 약관 조회
    // ───────────────────────────────────────────────

    /**
     * 현행 활성 약관 목록 조회
     * GET /terms
     */
    @GetMapping("/terms")
    public ResponseEntity<ApiResponse<List<ConsentResponse.TermsInfo>>> getActiveTerms() {
        return ResponseEntity.ok(ApiResponse.ok(consentService.getActiveTermsList()));
    }

    /**
     * 특정 약관의 버전 이력 조회
     * GET /terms/{termsId}/versions
     */
    @GetMapping("/terms/{termsId}/versions")
    public ResponseEntity<ApiResponse<List<ConsentResponse.TermsInfo>>> getTermsVersionHistory(
            @PathVariable Long termsId) {
        return ResponseEntity.ok(ApiResponse.ok(consentService.getTermsVersionHistory(termsId)));
    }

    // ───────────────────────────────────────────────
    // 동의 처리
    // ───────────────────────────────────────────────

    /**
     * 약관 동의 (최초 동의 / 재동의 모두 이 API 사용)
     * POST /consent
     */
    @PostMapping("/consent")
    public ResponseEntity<ApiResponse<ConsentResponse.Result>> agree(
            @Valid @RequestBody ConsentRequest.Agree request) {
        ConsentResponse.Result result = consentService.agree(request);
        return ResponseEntity.ok(ApiResponse.ok("약관에 동의하였습니다.", result));
    }

    /**
     * 약관 동의 철회
     * POST /consent/withdraw
     */
    @PostMapping("/consent/withdraw")
    public ResponseEntity<ApiResponse<ConsentResponse.Result>> withdraw(
            @Valid @RequestBody ConsentRequest.Withdraw request) {
        ConsentResponse.Result result = consentService.withdraw(request);
        return ResponseEntity.ok(ApiResponse.ok("약관 동의를 철회하였습니다.", result));
    }

    // ───────────────────────────────────────────────
    // 이력 조회
    // ───────────────────────────────────────────────

    /**
     * 사용자 동의 이력 조회
     * GET /consent/history?userId={userId}
     */
    @GetMapping("/consent/history")
    public ResponseEntity<ApiResponse<ConsentResponse.HistoryList>> getConsentHistory(
            @RequestParam Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(consentService.getConsentHistory(userId)));
    }
}
