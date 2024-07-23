package com.bizhil.springbootinit.model.dto.ai;


import com.bizhil.springbootinit.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 查询请求
 *
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class AIAssistantQueryRequest extends PageRequest implements Serializable {

    private Long id;

    /**
     * 提交的问题
     */
    private String issue;

    /**
     * 用户ID
     */
    private Long userId;


    private static final long serialVersionUID = 1L;
}