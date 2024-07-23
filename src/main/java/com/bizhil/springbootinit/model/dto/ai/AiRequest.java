package com.bizhil.springbootinit.model.dto.ai;

import lombok.Data;
import java.io.Serializable;

@Data
public class AiRequest implements Serializable {
    /**
     * 提交的问题
     */
    private String issue;

    /**
     *
     */
    private static final long serialVersionUID = 1L;
}
