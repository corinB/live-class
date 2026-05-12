// 강의 응답 DTO — Class Aggregate 의 외부 표현.
package com.example.liveclass.web.clazz.dto;

import com.example.liveclass.domain.clazz.Class;
import com.example.liveclass.domain.clazz.ClassStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ClassResponse(
        UUID id,
        String title,
        String description,
        BigDecimal priceAmount,
        String priceCurrency,
        int capacity,
        LocalDate startDate,
        LocalDate endDate,
        ClassStatus status,
        UUID creatorId,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
    public static ClassResponse from(Class clazz) {
        return new ClassResponse(
                clazz.getId(),
                clazz.getTitle(),
                clazz.getDescription(),
                clazz.getPrice().getAmount(),
                clazz.getPrice().getCurrency(),
                clazz.getCapacity().getValue(),
                clazz.getPeriod().getStartDate(),
                clazz.getPeriod().getEndDate(),
                clazz.getStatus(),
                clazz.getCreatorId(),
                clazz.getCreatedAt(),
                clazz.getUpdatedAt(),
                clazz.getVersion()
        );
    }
}
