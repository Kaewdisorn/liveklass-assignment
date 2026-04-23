package com.example.liveklass.course.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCourseRequest(
        @NotBlank(message = "title is required") @Size(max = 100, message = "title must be 100 characters or less") String title,

        @Size(max = 5000, message = "description must be 5000 characters or less") String description,

        @NotNull(message = "price is required") @DecimalMin(value = "0.00", message = "price must be zero or greater") BigDecimal price,

        @NotNull(message = "capacity is required") @Min(value = 1, message = "capacity must be at least 1") Integer capacity,

        @NotNull(message = "startDate is required") LocalDate startDate,

        @NotNull(message = "endDate is required") LocalDate endDate) {

    @AssertTrue(message = "startDate must be on or before endDate")
    public boolean isDateRangeValid() {
        if (startDate == null || endDate == null) {
            return true;
        }
        return !startDate.isAfter(endDate);
    }
}