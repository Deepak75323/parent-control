package com.parentalcontrol.device.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device_commands")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_type", nullable = false)
    private DeviceCommandType commandType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DeviceCommandStatus status = DeviceCommandStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message")
    private String errorMessage;

    /** Number of times this command entered DELIVERED via atomic claim. */
    @Column(name = "delivery_attempt_count", nullable = false)
    @Builder.Default
    private int deliveryAttemptCount = 0;

    /**
     * When the child device reported that it executed this command (before ACK).
     * Used to prevent stale reclaim from re-queuing already-executed commands.
     */
    @Column(name = "executed_at")
    private Instant executedAt;

    /** Outcome recorded at execution time; may auto-complete if ACK is delayed. */
    @Column(name = "execution_success")
    private Boolean executionSuccess;

    @Column(name = "execution_error")
    private String executionError;
}
