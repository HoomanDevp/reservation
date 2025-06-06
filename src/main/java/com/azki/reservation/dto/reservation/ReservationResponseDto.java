package com.azki.reservation.dto.reservation;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReservationResponseDto {
    private String requestId;
    private String status;
}
