package event_driven.notification_system.service.IMPL;

import event_driven.notification_system.service.IdempotencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyServiceImpl implements IdempotencyService {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyServiceImpl.class);
    private final StringRedisTemplate redisTemplate;

    public IdempotencyServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Checks if key exists. If not, stores it with 24-hour TTL and returns true.
     * If key already exists, returns false (duplicate request).
     */
    public boolean lockKey(String idempotencyKey) {
        String redisKey = "idemp:" + idempotencyKey;
        try {
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(redisKey, "LOCKED", Duration.ofHours(24));
            return Boolean.TRUE.equals(isNew);
        } catch (Exception e) {
            log.warn("Redis connection unavailable ({}); bypassing Redis lock for key: {}", e.getMessage(), idempotencyKey);
            return true;
        }
    }
}
