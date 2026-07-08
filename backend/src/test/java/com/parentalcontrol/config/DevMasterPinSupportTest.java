package com.parentalcontrol.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DevMasterPinSupportTest {

    @Test
    void acceptsMasterPinWhenEnabled() {
        TamperMasterPinProperties properties = new TamperMasterPinProperties();
        properties.setEnabled(true);
        properties.setPin("Deepak@123");
        DevMasterPinSupport support = new DevMasterPinSupport(properties);

        assertThat(support.isEnabled()).isTrue();
        assertThat(support.matches("Deepak@123")).isTrue();
        assertThat(support.matches(" Deepak@123 ")).isTrue();
        assertThat(support.matches("wrong")).isFalse();
    }

    @Test
    void rejectsMasterPinWhenDisabled() {
        TamperMasterPinProperties properties = new TamperMasterPinProperties();
        properties.setEnabled(false);
        properties.setPin("Deepak@123");
        DevMasterPinSupport support = new DevMasterPinSupport(properties);

        assertThat(support.isEnabled()).isFalse();
        assertThat(support.matches("Deepak@123")).isFalse();
    }
}
