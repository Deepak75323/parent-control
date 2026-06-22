package com.parentalcontrol.monitoring.service;

import com.parentalcontrol.monitoring.dto.IceConnectivityReportRequest;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TurnObservabilityService {

    private final MeterRegistry meterRegistry;

    public void recordIceReport(IceConnectivityReportRequest report) {
        String state = normalize(report.getIceConnectionState());
        String candidate = normalize(report.getSelectedCandidateType());
        String stream = normalize(report.getStreamType());

        meterRegistry.counter("webrtc.ice.reports", "state", state, "stream", stream).increment();
        if (candidate != null) {
            meterRegistry.counter("webrtc.ice.candidate_type", "type", candidate, "stream", stream).increment();
        }
        if (Boolean.TRUE.equals(report.getTurnUsed())) {
            meterRegistry.counter("webrtc.turn.used", "stream", stream).increment();
        }
        if ("failed".equals(state) || "disconnected".equals(state)) {
            meterRegistry.counter("webrtc.ice.failures", "stream", stream).increment();
            log.info(
                    "ICE failure stream={} session={} candidate={} turnUsed={} reason={}",
                    stream,
                    report.getSessionId(),
                    candidate,
                    report.getTurnUsed(),
                    report.getFailureReason()
            );
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.toLowerCase();
    }
}
