package com.demo.consent;

import com.demo.consent.dto.ConsentRequest;
import com.demo.consent.entity.ConsentHistory;
import com.demo.consent.enums.ConsentStatus;
import com.demo.consent.repository.ConsentHistoryRepository;
import com.demo.consent.service.ConsentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동일 유저 + 동일 약관에 대한 동시 요청이 실제로 직렬화되는지 검증한다.
 * 스레드별로 별도 트랜잭션/커넥션이 필요하므로 @Transactional 롤백 방식은 쓸 수 없고,
 * 테스트가 직접 삽입한 데이터를 @AfterEach에서 정리한다.
 */
@SpringBootTest
class ConsentConcurrencyTest {

    private static final Long USER_ID = 2L;      // user_bob
    private static final Long TERMS_ID = 3L;     // 마케팅 수신 동의 (선택 약관)

    @Autowired
    ConsentService consentService;

    @Autowired
    ConsentHistoryRepository consentHistoryRepository;

    @AfterEach
    void cleanUp() {
        List<ConsentHistory> histories =
                consentHistoryRepository.findAllByUserIdOrderByConsentedAtDesc(USER_ID);
        consentHistoryRepository.deleteAll(histories);
    }

    @Test
    @DisplayName("같은 유저가 같은 약관에 동시에 동의 요청을 보내도 AGREED 이력은 하나만 쌓인다")
    void concurrentAgree_onlyOneAgreedHistoryIsSaved() throws InterruptedException {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ConsentRequest.Agree request = new ConsentRequest.Agree();
                setField(request, "userId", USER_ID);
                setField(request, "termsId", TERMS_ID);
                try {
                    readyLatch.countDown();
                    startLatch.await();
                    consentService.agree(request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown(); // 두 스레드를 동시에 출발시킨다
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);

        List<ConsentHistory> histories =
                consentHistoryRepository.findAllByUserIdOrderByConsentedAtDesc(USER_ID);
        long agreedCount = histories.stream()
                .filter(h -> h.getStatus() == ConsentStatus.AGREED)
                .count();
        assertThat(agreedCount).isEqualTo(1);
    }

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
