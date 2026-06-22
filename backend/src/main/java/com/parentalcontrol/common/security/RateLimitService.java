package com.parentalcontrol.common.security;

import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.config.RateLimitProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;

    public RateLimitService(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public void checkAuth(String clientKey) {
        check("ratelimit:auth:" + clientKey, properties.getAuthRequestsPerMinute());
    }

    public void checkPairing(String clientKey) {
        check("ratelimit:pair:" + clientKey, properties.getAuthRequestsPerMinute());
    }

    public void checkDeviceIngest(String deviceId) {
        check("ratelimit:device:" + deviceId, properties.getDeviceIngestRequestsPerMinute());
    }

    private void check(String key, int maxPerMinute) {
        if (!properties.isEnabled() || redisTemplate == null) {
            return;
        }
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofMinutes(1));
            }
            if (count != null && count > maxPerMinute) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "Rate limit exceeded");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception ignored) {
            // Redis unavailable — do not block traffic in dev
        }
    }
}
