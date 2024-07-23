package com.bizhil.springbootinit.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bizhil.springbootinit.mapper.ChartMapper;
import com.bizhil.springbootinit.model.entity.Chart;
import com.bizhil.springbootinit.service.ChartService;
import org.springframework.stereotype.Service;

/**
* @author 小赵
* @description 针对表【chart(图表信息表)】的数据库操作Service实现
* @createDate 2024-06-30 16:34:35
*/
@Service
public class ChartServiceImpl extends ServiceImpl<ChartMapper, Chart> implements ChartService {


    @Override
    public void handleChartUpdateError(long chartId, String execMessage) {
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setStatus("failed");
        updateChartResult.setExecMessage(execMessage);
        boolean b = updateById(updateChartResult);
        if (!b){
            log.error("更新图表失败的状态失败" + chartId + "," + execMessage);
        }
    }
}




