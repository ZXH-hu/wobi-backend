package com.bizhil.springbootinit.model.vo;

import lombok.Data;

/**
 * AI回答返回的结果
 */

@Data
public class AiResponse {

    private String genResult;

    private Long aiAssistantId;
}
