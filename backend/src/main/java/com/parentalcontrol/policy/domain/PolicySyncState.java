package com.parentalcontrol.policy.domain;

import com.parentalcontrol.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "policy_sync_state")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicySyncState extends BaseEntity {

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "policy_version", nullable = false)
    private String policyVersion;

    @Column(name = "last_ack_at")
    private Instant lastAckAt;

    @Column(name = "last_sync_at", nullable = false)
    private Instant lastSyncAt;
}
