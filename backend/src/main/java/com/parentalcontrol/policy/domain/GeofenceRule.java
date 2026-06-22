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

import java.util.UUID;

@Entity
@Table(name = "geofence_rules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeofenceRule extends BaseEntity {

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "radius_m", nullable = false)
    private Double radiusM;

    @Builder.Default
    @Column(name = "alert_on_enter", nullable = false)
    private Boolean alertOnEnter = true;

    @Builder.Default
    @Column(name = "alert_on_exit", nullable = false)
    private Boolean alertOnExit = true;
}
