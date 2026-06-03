package com.demo.consent.dto;

import com.demo.consent.enums.ConsentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 동의/철회 요청 DTO
 */
public class ConsentRequest {

    @Getter
    @NoArgsConstructor
    public static class Agree {
        @NotNull(message = "userId는 필수입니다.")
        private Long userId;

        @NotNull(message = "termsId는 필수입니다.")
        private Long termsId;
    }

    @Getter
    @NoArgsConstructor
    public static class Withdraw {
        @NotNull(message = "userId는 필수입니다.")
        private Long userId;

        @NotNull(message = "termsId는 필수입니다.")
        private Long termsId;
    }
}
