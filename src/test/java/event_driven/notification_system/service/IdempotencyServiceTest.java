package event_driven.notification_system.service;

import event_driven.notification_system.service.IMPL.IdempotencyServiceImpl; // Adjust case if your package is .impl
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    // Use the interface as reference, but instantiate the concrete implementation
    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // FIX: Instantiate the concrete class, not the interface
        idempotencyService = new IdempotencyServiceImpl(redisTemplate);
    }

    @Test
    @DisplayName("Should return true when key is locked successfully (first time request)")
    void testLockKey_Success() {
        // Arrange
        String key = "test_key_123";
        String expectedRedisKey = "idemp:" + key;
        when(valueOperations.setIfAbsent(eq(expectedRedisKey), eq("LOCKED"), any(Duration.class)))
                .thenReturn(Boolean.TRUE);


        boolean result = idempotencyService.lockKey(key);


        assertTrue(result, "Expected lockKey to return true for a new unique key");
    }

    @Test
    @DisplayName("Should return false when key already exists (duplicate request)")
    void testLockKey_DuplicateKey_ReturnsFalse() {
        // Arrange
        String key = "duplicate_key";
        String expectedRedisKey = "idemp:" + key;
        when(valueOperations.setIfAbsent(eq(expectedRedisKey), eq("LOCKED"), any(Duration.class)))
                .thenReturn(Boolean.FALSE);


        boolean result = idempotencyService.lockKey(key);

        assertFalse(result, "Expected lockKey to return false when key is already taken");
    }
}