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
@Table(name = "screen_time_rules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenTimeRule extends BaseEntity {

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "package_name")
    private String packageName;

    @Column(name = "app_category")
    private String appCategory;

    @Column(name = "daily_limit_minutes", nullable = false)
    private Integer dailyLimitMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_on_limit", nullable = false)
    @Builder.Default
    private ScreenTimeAction actionOnLimit = ScreenTimeAction.BLOCK_APP;

    @Builder.Default
    @Column(name = "warn_at_percent", nullable = false)
    private Integer warnAtPercent = 80;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "weekly_schedule_json")
    @Builder.Default
    private Map<String, Object> weeklyScheduleJson = Map.of();
}
