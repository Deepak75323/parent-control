package com.parentalcontrol.policy.domain;

import com.parentalcontrol.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "app_block_rules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppBlockRule extends BaseEntity {

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "package_name", nullable = false)
    private String packageName;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false)
    @Builder.Default
    private ScheduleType scheduleType = ScheduleType.ALWAYS;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schedule_json")
    @Builder.Default
    private Map<String, Object> scheduleJson = Map.of();
}
