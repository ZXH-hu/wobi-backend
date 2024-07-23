package com.bizhil.springbootinit.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bizhil.springbootinit.mapper.AiAssistantMapper;
import com.bizhil.springbootinit.model.entity.AiAssistant;
import com.bizhil.springbootinit.service.AiAssistantService;
import org.springframework.stereotype.Service;

@Service
public class AiAssistantServiceImpl extends ServiceImpl<AiAssistantMapper, AiAssistant> implements AiAssistantService {
    @Override
    public void handleChartUpdateError(long aiAssistantId, String execMessage) {
        AiAssistant aiAssistant = new AiAssistant();
        aiAssistant.setId(aiAssistantId);
        aiAssistant.setStatus("failed");
        aiAssistant.setExecMessage(execMessage);
        boolean b = updateById(aiAssistant);
        if (!b){
            log.error("更新AI助手数据库表的状态失败" + aiAssistantId + "," + execMessage);
        }
    }
}
