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
    @Column(name = "created_by", updatable = false, nullable = false)
    protected String createdBy;

    @CreatedDate
    @Setter(AccessLevel.NONE)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Column(name = "created_date", updatable = false, nullable = false)
    protected LocalDateTime createdDate;

    @LastModifiedDate
    @Setter(AccessLevel.NONE)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Column(name = "last_modified_date")
    protected LocalDateTime lastModifiedDate;

    @LastModifiedBy
    @Setter(AccessLevel.NONE)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "last_modified_by")
    protected String lastModifiedBy;

    @Version
    @Builder.Default
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "version", nullable = false, columnDefinition = "INT DEFAULT 0")
    public Integer version = 0;

    @PrePersist
    protected void prePersist() {
        // Do Not Remove
    }

    @PreUpdate
    protected void preUpdate() {
        // Do Not Remove
    }
}
