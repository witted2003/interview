// Directory structure:
// com.company.ratelimiter
// ├── models
// │   ├── RateLimitPolicy.java
// │   └── UserRequestMeta.java
// ├── strategy
// │   ├── RateLimitStrategy.java
// │   ├── SlidingWindowStrategy.java
// │   └── TokenBucketStrategy.java
// ├── service
// │   └── RateLimiterService.java
// ├── store
// │   └── InMemoryRequestStore.java
// ├── utils
// │   └── TimeUtils.java
// ├── exceptions
// │   └── RateLimitExceededException.java
// └── Main.java

// models/RateLimitPolicy.java
package models;

public class RateLimitPolicy {
    public final int maxRequests;
    public final int windowSeconds;
    public final double refillRate;

    public RateLimitPolicy(int maxRequests, int windowSeconds) {
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
        this.refillRate = (double) maxRequests / windowSeconds;
    }
}

// strategy/RateLimitStrategy.java
package strategy;

public interface RateLimitStrategy {
    boolean allowRequest(String userId);
}

// strategy/SlidingWindowStrategy.java
package strategy;

import models.RateLimitPolicy;

import java.util.*;
import java.util.concurrent.*;

public class SlidingWindowStrategy implements RateLimitStrategy {
    private final ConcurrentHashMap<String, Deque<Long>> userRequests = new ConcurrentHashMap<>();
    private final RateLimitPolicy policy;

    public SlidingWindowStrategy(RateLimitPolicy policy) {
        this.policy = policy;
    }

    @Override
    public boolean allowRequest(String userId) {
        long now = System.currentTimeMillis();
        long windowStart = now - policy.windowSeconds * 1000L;

        userRequests.putIfAbsent(userId, new ConcurrentLinkedDeque<>());
        Deque<Long> queue = userRequests.get(userId);

        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst() < windowStart) {
                queue.pollFirst();
            }

            if (queue.size() < policy.maxRequests) {
                queue.addLast(now);
                return true;
            }
            return false;
        }
    }
}

// strategy/TokenBucketStrategy.java
package strategy;

import models.RateLimitPolicy;

import java.util.concurrent.ConcurrentHashMap;

public class TokenBucketStrategy implements RateLimitStrategy {
    private static class Bucket {
        double tokens;
        long lastRefillTime;

        Bucket(double tokens, long lastRefillTime) {
            this.tokens = tokens;
            this.lastRefillTime = lastRefillTime;
        }
    }

    private final ConcurrentHashMap<String, Bucket> userBuckets = new ConcurrentHashMap<>();
    private final RateLimitPolicy policy;

    public TokenBucketStrategy(RateLimitPolicy policy) {
        this.policy = policy;
    }

    @Override
    public boolean allowRequest(String userId) {
        long now = System.currentTimeMillis();
        userBuckets.putIfAbsent(userId, new Bucket(policy.maxRequests, now));
        Bucket bucket = userBuckets.get(userId);

        synchronized (bucket) {
            long timeElapsed = now - bucket.lastRefillTime;
            double tokensToAdd = (timeElapsed / 1000.0) * policy.refillRate;
            bucket.tokens = Math.min(policy.maxRequests, bucket.tokens + tokensToAdd);
            bucket.lastRefillTime = now;

            if (bucket.tokens >= 1) {
                bucket.tokens -= 1;
                return true;
            }
            return false;
        }
    }
}

// service/RateLimiterService.java
package service;

import strategy.RateLimitStrategy;

public class RateLimiterService {
    private final RateLimitStrategy strategy;

    public RateLimiterService(RateLimitStrategy strategy) {
        this.strategy = strategy;
    }

    public boolean allowRequest(String userId) {
        return strategy.allowRequest(userId);
    }
}

// utils/TimeUtils.java
package utils;

public class TimeUtils {
    public static long now() {
        return System.currentTimeMillis();
    }
}

// exceptions/RateLimitExceededException.java
package exceptions;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}

// Main.java
import models.RateLimitPolicy;
import service.RateLimiterService;
import strategy.RateLimitStrategy;
import strategy.SlidingWindowStrategy;
import strategy.TokenBucketStrategy;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        RateLimitPolicy policy = new RateLimitPolicy(5, 10); // 5 reqs per 10 seconds

        RateLimitStrategy strategy = new SlidingWindowStrategy(policy);
        // Or use TokenBucketStrategy: new TokenBucketStrategy(policy);

        RateLimiterService service = new RateLimiterService(strategy);

        String user = "user1";
        for (int i = 0; i < 10; i++) {
            System.out.println("Request " + (i+1) + ": " + service.allowRequest(user));
            Thread.sleep(1000);
        }
    }
}
