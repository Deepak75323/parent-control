package com.parentalcontrol.monitoring.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.parentalcontrol.monitoring.domain.ScreenSessionStatus;
import org.junit.jupiter.api.Test;

class StreamFailureReasonClassifierTest {

  @Test
  void classifiesMaxAttemptsWhilePendingAsChildNoOffer() {
    assertThat(
            StreamFailureReasonClassifier.classify(
                "Error: Connection failed after maximum attempts", ScreenSessionStatus.PENDING))
        .isEqualTo(StreamFailureReasonClassifier.CHILD_NO_OFFER);
  }

  @Test
  void classifiesOffer400AsSignalingRejected() {
    assertThat(
            StreamFailureReasonClassifier.classify(
                "Error: Failed to send offer: AxiosError: Request failed with status code 400",
                ScreenSessionStatus.ACTIVE))
        .isEqualTo(StreamFailureReasonClassifier.SIGNALING_REJECTED);
  }

  @Test
  void classifiesIceAsIceFailed() {
    assertThat(
            StreamFailureReasonClassifier.classify(
                "ICE_FINAL_FAILURE_failed", ScreenSessionStatus.ACTIVE))
        .isEqualTo(StreamFailureReasonClassifier.ICE_FAILED);
  }

  @Test
  void preservesStructuredChildCodes() {
    assertThat(
            StreamFailureReasonClassifier.classify(
                "CAMERA_FGS_FAILED", ScreenSessionStatus.PENDING))
        .isEqualTo("CAMERA_FGS_FAILED");
  }
}
