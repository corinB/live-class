// 강의(Class) 초안 생성 요청 DTO — Bean Validation 으로 입력값을 검증한다.
package com.example.liveclass.web.clazz.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateClassRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @NotNull @PositiveOrZero BigDecimal priceAmount,
        @NotBlank @Size(min = 3, max = 3) String priceCurrency,
        @NotNull @Min(1) Integer capacity,
        @NotNull @FutureOrPresent LocalDate startDate,
        @NotNull LocalDate endDate
) {
}
