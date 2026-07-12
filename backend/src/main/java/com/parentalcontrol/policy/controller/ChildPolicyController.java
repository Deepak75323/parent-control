package com.parentalcontrol.policy.controller;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.common.constant.ApiConstants;
import com.parentalcontrol.policy.dto.PolicyAckRequest;
import com.parentalcontrol.policy.dto.PolicyBundleResponse;
import com.parentalcontrol.policy.service.PolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.CHILD)
@RequiredArgsConstructor
@Tag(name = "Child Policies", description = "Child device policy sync")
@SecurityRequirement(name = "bearerAuth")
public class ChildPolicyController {

  private final PolicyService policyService;

  @GetMapping("/policies")
  @Operation(summary = "Fetch assigned policies for authenticated device")
  public ApiResponse<PolicyBundleResponse> getPolicies() {
    return ApiResponse.ok(policyService.getPoliciesForAuthenticatedDevice());
  }

  @PostMapping("/policies/ack")
  @Operation(summary = "Acknowledge policy sync version")
  public ApiResponse<Void> ack(@Valid @RequestBody PolicyAckRequest request) {
    policyService.acknowledgeSync(request);
    return ApiResponse.okMessage("Policy sync acknowledged");
  }
}
