package com.parentalcontrol.chat.repository;

import com.parentalcontrol.chat.domain.ChatDecryptSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatDecryptSettingsRepository extends JpaRepository<ChatDecryptSettings, Short> {}
