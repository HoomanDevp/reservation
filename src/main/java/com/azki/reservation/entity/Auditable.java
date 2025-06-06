package com.azki.reservation.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@SuperBuilder
@MappedSuperclass
@Accessors(chain = true)
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@EntityListeners(AuditingEntityListener.class)
public abstract class Auditable {
    @CreatedBy
    @Setter(AccessLevel.NONE)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @CreatedDate
    @Setter(AccessLevel.NONE)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    @Setter(AccessLevel.NONE)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "last_modified_date")
    private LocalDateTime lastModifiedDate;

    @LastModifiedBy
    @Setter(AccessLevel.NONE)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "last_modified_by")
    private String lastModifiedBy;

    @Version
    @Setter(AccessLevel.NONE)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "version")
    private int version;

    @PrePersist
    protected void prePersist() {
        // Do Not Remove
    }

    @PreUpdate
    protected void preUpdate() {
        // Do Not Remove
    }
}
