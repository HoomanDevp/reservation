package com.azki.reservation.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = false)
@Data
@Entity
public class Reservation extends Auditable{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private User user;

    @OneToOne(optional = false)
    private TimeSlot timeSlot;

    private LocalDateTime reservedAt;
}
