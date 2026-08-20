package com.parentalcontrol.analytics.repository;

import com.parentalcontrol.analytics.domain.ClientAnalyticsEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientAnalyticsEventRepository extends JpaRepository<ClientAnalyticsEvent, UUID> {}
