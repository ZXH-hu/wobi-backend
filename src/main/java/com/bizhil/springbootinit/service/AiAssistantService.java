package com.bizhil.springbootinit.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bizhil.springbootinit.model.entity.AiAssistant;

public interface AiAssistantService extends IService<AiAssistant> {
    void handleChartUpdateError(long aiAssistantId, String execMessage);
}
