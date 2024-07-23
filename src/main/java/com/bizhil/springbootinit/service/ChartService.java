package com.bizhil.springbootinit.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.bizhil.springbootinit.model.entity.Chart;

/**
* @author 小赵
* @description 针对表【chart(图表信息表)】的数据库操作Service
* @createDate 2024-06-30 16:34:35
*/
public interface ChartService extends IService<Chart> {

    void handleChartUpdateError(long chartId, String execMessage);
}
