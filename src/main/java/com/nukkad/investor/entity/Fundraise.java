package com.nukkad.investor.entity;

import com.nukkad.startup.entity.StartupStage;
import com.nukkad.startup.entity.StartupStageConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

@Entity
@Table(name = "fundraises")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Fundraise {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "startup_id", nullable = false, unique = true, columnDefinition = "CHAR(36)")
    private String startupId;

    @Column(name = "target_amount", nullable = false)
    private long targetAmount;

    @Column(name = "amount_raised", nullable = false)
    @Builder.Default
    private long amountRaised = 0;

    @Convert(converter = StartupStageConverter.class)
    @Column(name = "funding_stage", nullable = false, length = 20)
    private StartupStage fundingStage;

    @Column(name = "use_of_funds", columnDefinition = "TEXT")
    private String useOfFunds;

    @Column(name = "minimum_ticket")
    private Long minimumTicket;

    @Convert(converter = FundraiseStatusConverter.class)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private FundraiseStatus status = FundraiseStatus.OPEN;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
