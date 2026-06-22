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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "policy_violation_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyViolationEvent extends BaseEntity {

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "policy_id")
    private UUID policyId;

    @Column(name = "violation_type", nullable = false)
    private String violationType;

    @Column(name = "package_name")
    private String packageName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details_json")
    private Map<String, Object> detailsJson;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
