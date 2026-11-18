package com.aimall.ai.shopping;

import com.aimall.ai.model.ShoppingSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class SessionStore {
    private static final Logger log = LoggerFactory.getLogger(SessionStore.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public SessionStore(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    /** key 中带 userId: 即使猜到别人的 conversationId 也读不到对方会话。 */
    private static String key(long userId, String conversationId) {
        return "ai:session:" + userId + ":" + conversationId;
    }

    public ShoppingSession load(long userId, String conversationId) {
        try {
            String json = redis.opsForValue().get(key(userId, conversationId));
            if (json != null) {
                return mapper.readValue(json, ShoppingSession.class);
            }
        } catch (Exception e) {
            log.warn("load session failed: {}", e.getMessage());
        }
        return new ShoppingSession(conversationId, userId);
    }

    public void save(ShoppingSession session) {
        try {
            redis.opsForValue().set(key(session.userId, session.conversationId),
                    mapper.writeValueAsString(session), Duration.ofHours(1));
        } catch (Exception e) {
            log.warn("save session failed: {}", e.getMessage());
        }
    }
}
