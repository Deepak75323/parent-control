package com.parentalcontrol.auth.service;

import com.parentalcontrol.auth.domain.RefreshToken;
import com.parentalcontrol.auth.dto.AuthResponse;
import com.parentalcontrol.auth.dto.LoginRequest;
import com.parentalcontrol.auth.dto.RefreshTokenRequest;
import com.parentalcontrol.auth.dto.RegisterRequest;
import com.parentalcontrol.auth.repository.RefreshTokenRepository;
import com.parentalcontrol.common.domain.Role;
import com.parentalcontrol.common.domain.TokenType;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.JwtProperties;
import com.parentalcontrol.common.security.JwtTokenProvider;
import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.domain.User;
import com.parentalcontrol.user.dto.ParentProfileResponse;
import com.parentalcontrol.user.repository.ParentProfileRepository;
import com.parentalcontrol.billing.service.SubscriptionService;
import com.parentalcontrol.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final ParentProfileRepository parentProfileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final SubscriptionService subscriptionService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (!"true".equalsIgnoreCase(request.getConsentAccepted())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Consent must be accepted");
        }

        String normalizedEmail = normalizeEmail(request.getEmail());
        log.info("[AUTH REGISTER] incoming email='{}' normalized='{}'", request.getEmail(), normalizedEmail);

        boolean exists = userRepository.existsByEmailIgnoreCase(normalizedEmail);
        log.info("[AUTH REGISTER] existsByEmailIgnoreCase('{}') => {}", normalizedEmail, exists);
        if (exists) {
            throw new BusinessException(ErrorCode.USER_EXISTS, "Email already registered");
        }

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.ROLE_PARENT)
                .consentAccepted(true)
                .build();
        user = userRepository.save(user);
        log.info("[AUTH REGISTER] created userId={} email='{}'", user.getId(), user.getEmail());

        ParentProfile profile = ParentProfile.builder()
                .user(user)
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .phone(request.getPhone())
                .build();
        profile = parentProfileRepository.save(profile);
        subscriptionService.startTrial(profile.getId());

        return buildAuthResponse(user, profile);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        log.info("[AUTH LOGIN] incoming email='{}' normalized='{}'", request.getEmail(), normalizedEmail);

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        ParentProfile profile = parentProfileRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Parent profile not found"));

        return buildAuthResponse(user, profile);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String hash = hashToken(request.getRefreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHashAndRevokedFalse(hash)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid refresh token"));

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Refresh token expired");
        }

        if (!jwtTokenProvider.validateToken(request.getRefreshToken())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid refresh token");
        }

        Claims claims = jwtTokenProvider.parseClaims(request.getRefreshToken());
        if (jwtTokenProvider.getTokenType(claims) != TokenType.REFRESH) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid token type");
        }

        UUID userId = jwtTokenProvider.getUserId(claims);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        ParentProfile profile = parentProfileRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        return buildAuthResponse(user, profile);
    }

    private AuthResponse buildAuthResponse(User user, ParentProfile profile) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), user.getEmail(), user.getRole());
        persistRefreshToken(user.getId(), refreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresInSeconds(jwtProperties.getAccessTokenExpirationMs() / 1000)
                .profile(toProfileResponse(user, profile))
                .build();
    }

    private void persistRefreshToken(UUID userId, String rawToken) {
        RefreshToken entity = RefreshToken.builder()
                .userId(userId)
                .tokenHash(hashToken(rawToken))
                .expiresAt(Instant.now().plusMillis(jwtProperties.getRefreshTokenExpirationMs()))
                .build();
        refreshTokenRepository.save(entity);
    }

    private ParentProfileResponse toProfileResponse(User user, ParentProfile profile) {
        return ParentProfileResponse.builder()
                .id(profile.getId())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .email(user.getEmail())
                .phone(profile.getPhone())
                .timezone(profile.getTimezone())
                .build();
    }

    static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase();
    }
}
