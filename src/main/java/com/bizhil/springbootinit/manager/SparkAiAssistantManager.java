package com.bizhil.springbootinit.manager;

import com.bizhil.springbootinit.model.vo.AiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.redisson.api.RedissonClient;

public class SparkAiAssistantManager {
    private final RedissonClient redissonClient;

    public SparkAiAssistantManager(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }
    public AiResponse getAns(long aiAssistantId, String question) throws JsonProcessingException {
        BigModelChar bigModelChar = new BigModelChar(aiAssistantId,redissonClient);
        bigModelChar.getResult(question);
        String aReturn = bigModelChar.getReturn();
        String onAnalysis = "服务错误";
        if(aReturn.contains("【回答内容】："))
            onAnalysis = aReturn.substring(aReturn.indexOf("：") + 1);
        AiResponse aiResponse = new AiResponse();
        aiResponse.setGenResult(onAnalysis);
        return aiResponse;
    }
}
