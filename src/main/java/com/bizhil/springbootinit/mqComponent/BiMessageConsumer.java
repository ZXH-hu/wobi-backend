package com.bizhil.springbootinit.mqComponent;

import com.alibaba.excel.util.StringUtils;
import com.bizhil.springbootinit.model.vo.BiResponse;
import com.rabbitmq.client.Channel;
import com.bizhil.springbootinit.common.ErrorCode;
import com.bizhil.springbootinit.exception.BusinessException;
import com.bizhil.springbootinit.manager.RedisLimiterManager;
import com.bizhil.springbootinit.manager.SparkAiManager;
import com.bizhil.springbootinit.model.entity.Chart;
import com.bizhil.springbootinit.service.ChartService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * rabbitmq接收消息组件
 */

@Component
@Slf4j
public class BiMessageConsumer {

    @Resource
    private ChartService chartService;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RedisLimiterManager redisLimiterManager;

    // 监听消息队列code_queue
    @SneakyThrows
    @RabbitListener(queues = {BiMqConstant.BI_QUEUE_NAME}, ackMode = "MANUAL")
    public void receiveMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag){

        if (StringUtils.isBlank(message)) {
            log.error("信息为空");
            //空消息是没有价值的，直接确认
            try {
                channel.basicAck(deliveryTag, false);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return;
        }
        //解析消息:chart.getId() + "," + loginUser.getId() （图表信息，用户id）
        String msg[] = message.split(",");
        //从数据库查询当前这个图表
        Chart chart = chartService.getById(Long.valueOf(msg[0]));
        String goal = chart.getGoal();
        String data = chart.getChartData();
        String chartType = chart.getChartType();

        //根据用户上传的数据，压缩ai提问语
        StringBuffer res = new StringBuffer();
        res.append("你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容：");
        res.append("\n").append("分析需求：").append("\n").append("{").append(goal).append("}").append("\n");
        res.append("原始数据:").append("\n").append(data);
        res.append("请根据这两部分内容，按照先生成结论，再生成代码的顺序，下面输出格式如：\n【原始数据分析结论】： \n...结论...\n【代码部分】：\n...代码部分...\n" +
                "\n以下为具体生成内容要求：" +
                "\n原始数据分析结论：根据提供的原始数据分析做详细文字总结。\n" +
                "代码部分：{可视化图表类型为" + chartType+ ";" + "只生成前端 Echarts V5 的 option 配置对象纯JSON代码，代码中不要使用函数等无效JSON语法，" +
                "合理的将数据进行可视化并且在代码toolbox中开启图表格式转换magicType、" +
                "保存图片saveAsImage功能。注意！代码部分需要给出```json这种标识");
        res.append("最后的代码部分不要生成任何多余的总结内容，不要注释}");

        chart.setStatus("running");
        boolean update = chartService.updateById(chart);
        if (!update) {
            try {
                // 不确认消息
                channel.basicNack(deliveryTag, false, true);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            chartService.handleChartUpdateError(chart.getId(), "更新图表失败");
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新图表失败");
        }

        // 限流用户id
        redisLimiterManager.doRateLimiter("aiLimiter:" + msg[1]);
        // 调用AI
        SparkAiManager sparkAiManager = new SparkAiManager(redissonClient);
        BiResponse ans = sparkAiManager.getAns(chart.getId(), res.toString());
        String chartData = ans.getGenChart();
        String chartResult = ans.getGenResult();
        if (!chartData.equals("服务错误") && !chartResult.equals("服务错误") || !chartData.equals("服务错误")) {
            Chart succeedChart = new Chart();
            succeedChart.setId(chart.getId());
            succeedChart.setStatus("succeed");
            succeedChart.setGenChart(chartData);
            succeedChart.setGenResult(chartResult);
            boolean success = chartService.updateById(succeedChart);

            if (!success) {
                try {
                    channel.basicNack(deliveryTag, false, false);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                chartService.handleChartUpdateError(chart.getId(), "更新图表成功状态失败");
            }

            try {
                channel.basicAck(deliveryTag, false);
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else {
            chartService.handleChartUpdateError(chart.getId(), "图表生成异常！");
        }
    }
}
