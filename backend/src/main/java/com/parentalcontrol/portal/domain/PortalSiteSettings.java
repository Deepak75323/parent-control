package com.parentalcontrol.portal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "portal_site_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalSiteSettings {

  public static final short SINGLETON_ID = 1;

  @Id
  @Column(nullable = false)
  private Short id;

  @Column(nullable = false)
  @Builder.Default
  private boolean disabled = false;

  @Column(name = "shared_with_everyone", nullable = false)
  @Builder.Default
  private boolean sharedWithEveryone = false;

  @Column(name = "updated_at", nullable = false)
  @Builder.Default
  private Instant updatedAt = Instant.now();

  @Column(name = "updated_by")
  private UUID updatedBy;
}
