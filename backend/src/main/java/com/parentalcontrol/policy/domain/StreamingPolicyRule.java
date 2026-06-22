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
@Table(name = "streaming_policy_rules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamingPolicyRule extends BaseEntity {

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Builder.Default
    @Column(name = "screen_share_allowed", nullable = false)
    private Boolean screenShareAllowed = true;

    @Builder.Default
    @Column(name = "camera_allowed", nullable = false)
    private Boolean cameraAllowed = true;

    @Builder.Default
    @Column(name = "audio_allowed", nullable = false)
    private Boolean audioAllowed = true;
}
