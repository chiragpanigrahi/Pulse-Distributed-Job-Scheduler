package com.example.scheduler.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * A minimal distributed lock built directly on Redis SETNX + TTL.
 *
 * This is what lets us run this application as N replicas without N
 * duplicate dispatches of the same due job: every replica's poller tries to
 * acquire the same lock key on every tick, only one wins, and it releases
 * the lock (via a Lua script checking the owner token, to avoid releasing
 * a lock some other instance now owns after expiry) once it's done
 * dispatching that batch.
 */
@Component
@RequiredArgsConstructor
public class DistributedLockService {

    private final StringRedisTemplate redisTemplate;

    private static final String RELEASE_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('del', KEYS[1]) " +
            "else " +
            "  return 0 " +
            "end";

    public boolean tryLock(String key, String ownerToken, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, ownerToken, ttl);
        return Boolean.TRUE.equals(acquired);
    }

    public void unlock(String key, String ownerToken) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(RELEASE_SCRIPT, Long.class);
        redisTemplate.execute(script, Collections.singletonList(key), ownerToken);
    }
}
