package com.azki.reservation.dto.reservation;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReservationResponseDto {
    private Long reservationId;
    private String startTime;
    private String endTime;
}
