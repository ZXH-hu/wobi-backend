package com.bizhil.springbootinit.manager;

import com.bizhil.springbootinit.common.ErrorCode;
import com.bizhil.springbootinit.exception.ThrowUtils;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;

@Service
public class RedisLimiterManager {
    @Resource
    private RedissonClient redissonClient;

    /**
     * 限流操作
     * 区分不同的限流器，如用户id应该分别统计
     * @param key
     */
    public void doRateLimiter(String key){
        // 创建一个限流器，限制每秒最多访问2次
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        // 初始化RateLimiter，每秒生成2个令牌
        rateLimiter.trySetRate(RateType.OVERALL, 2, 1, RateIntervalUnit.SECONDS);

        // 每当一个请求来了后，请求一个令牌
//        boolean result = rateLimiter.tryAcquire(1);
        // 请求两个令牌
        boolean result = rateLimiter.tryAcquire(1);
        ThrowUtils.throwIf(!result, ErrorCode.TOO_MANY_REQUEST);

        // 一般就单例情况下可以不用关闭
//        redissonClient.shutdown();
    }
}
