package com.demo.consent;

import com.demo.consent.dto.ConsentRequest;
import com.demo.consent.dto.ConsentResponse;
import com.demo.consent.enums.ConsentStatus;
import com.demo.consent.exception.ConsentException;
import com.demo.consent.service.ConsentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class ConsentServiceTest {

    @Autowired
    ConsentService consentService;

    @Test
    @DisplayName("현행 활성 약관 목록을 조회할 수 있다")
    void getActiveTermsList() {
        List<ConsentResponse.TermsInfo> terms = consentService.getActiveTermsList();
        assertThat(terms).isNotEmpty();
        assertThat(terms).allMatch(t -> t.getCurrentVersion() != null);
    }

    @Test
    @DisplayName("약관에 동의할 수 있다")
    void agreeTerms() {
        ConsentRequest.Agree request = new ConsentRequest.Agree();
        setField(request, "userId", 1L);
        setField(request, "termsId", 3L); // 마케팅 (선택)

        ConsentResponse.Result result = consentService.agree(request);

        assertThat(result.getStatus()).isEqualTo(ConsentStatus.AGREED);
        assertThat(result.getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("동일 약관에 중복 동의하면 예외가 발생한다")
    void duplicateAgree() {
        ConsentRequest.Agree request = new ConsentRequest.Agree();
        setField(request, "userId", 1L);
        setField(request, "termsId", 3L);

        consentService.agree(request); // 최초 동의

        assertThatThrownBy(() -> consentService.agree(request))
                .isInstanceOf(ConsentException.class)
                .hasMessageContaining("이미");
    }

    @Test
    @DisplayName("필수 약관은 철회할 수 없다")
    void cannotWithdrawMandatoryTerms() {
        // 먼저 동의
        ConsentRequest.Agree agreeReq = new ConsentRequest.Agree();
        setField(agreeReq, "userId", 1L);
        setField(agreeReq, "termsId", 1L); // 필수 약관
        consentService.agree(agreeReq);

        // 철회 시도
        ConsentRequest.Withdraw withdrawReq = new ConsentRequest.Withdraw();
        setField(withdrawReq, "userId", 1L);
        setField(withdrawReq, "termsId", 1L);

        assertThatThrownBy(() -> consentService.withdraw(withdrawReq))
                .isInstanceOf(ConsentException.class)
                .hasMessageContaining("필수 약관");
    }

    @Test
    @DisplayName("선택 약관을 철회할 수 있다")
    void withdrawOptionalTerms() {
        // 동의 후
        ConsentRequest.Agree agreeReq = new ConsentRequest.Agree();
        setField(agreeReq, "userId", 1L);
        setField(agreeReq, "termsId", 3L);
        consentService.agree(agreeReq);

        // 철회
        ConsentRequest.Withdraw withdrawReq = new ConsentRequest.Withdraw();
        setField(withdrawReq, "userId", 1L);
        setField(withdrawReq, "termsId", 3L);
        ConsentResponse.Result result = consentService.withdraw(withdrawReq);

        assertThat(result.getStatus()).isEqualTo(ConsentStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("철회 후 재동의할 수 있다")
    void reAgreeAfterWithdraw() {
        ConsentRequest.Agree agreeReq = new ConsentRequest.Agree();
        setField(agreeReq, "userId", 1L);
        setField(agreeReq, "termsId", 3L);

        ConsentRequest.Withdraw withdrawReq = new ConsentRequest.Withdraw();
        setField(withdrawReq, "userId", 1L);
        setField(withdrawReq, "termsId", 3L);

        consentService.agree(agreeReq);    // 동의
        consentService.withdraw(withdrawReq); // 철회
        ConsentResponse.Result result = consentService.agree(agreeReq); // 재동의

        assertThat(result.getStatus()).isEqualTo(ConsentStatus.AGREED);
    }

    @Test
    @DisplayName("사용자의 동의 이력 전체를 조회할 수 있다")
    void getConsentHistory() {
        // 여러 약관에 동의
        for (long termsId = 1; termsId <= 3; termsId++) {
            ConsentRequest.Agree req = new ConsentRequest.Agree();
            setField(req, "userId", 1L);
            setField(req, "termsId", termsId);
            consentService.agree(req);
        }

        ConsentResponse.HistoryList history = consentService.getConsentHistory(1L);

        assertThat(history.getTotalCount()).isGreaterThanOrEqualTo(3);
        assertThat(history.getUserId()).isEqualTo(1L);
    }

    // Reflection helper (Lombok @NoArgsConstructor 대응)
    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
