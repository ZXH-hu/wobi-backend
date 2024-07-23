package com.bizhil.springbootinit.controller;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bizhil.springbootinit.common.DeleteRequest;
import com.bizhil.springbootinit.common.ResultUtils;
import com.bizhil.springbootinit.model.dto.chart.*;
import com.bizhil.springbootinit.model.vo.BiResponse;
import com.bizhil.springbootinit.mqComponent.BiMessageProducer;
import com.bizhil.springbootinit.utils.ExcelUtils;
import com.bizhil.springbootinit.utils.SqlUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.bizhil.springbootinit.annotation.AuthCheck;
import com.bizhil.springbootinit.common.BaseResponse;
import com.bizhil.springbootinit.common.ErrorCode;
import com.bizhil.springbootinit.constant.CommonConstant;
import com.bizhil.springbootinit.constant.UserConstant;
import com.bizhil.springbootinit.exception.BusinessException;
import com.bizhil.springbootinit.exception.ThrowUtils;
import com.bizhil.springbootinit.manager.RedisLimiterManager;
import com.bizhil.springbootinit.manager.SparkAiManager;
import com.bizhil.springbootinit.model.entity.Chart;
import com.bizhil.springbootinit.model.entity.User;
import com.bizhil.springbootinit.service.ChartService;
import com.bizhil.springbootinit.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 帖子接口
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://bizhil.icu">编程导航知识星球</a>
 */
@RestController
@RequestMapping("/chart")
@Slf4j
public class ChartController {

    @Resource
    private ChartService chartService;

    @Resource
    private UserService userService;

    @Resource
    RedissonClient redissonClient;

    @Resource
    private RedisLimiterManager redisLimiterManager;

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;


    @Resource
    private BiMessageProducer biMessageProducer;


    /**
     * 创建
     *
     * @param chartAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addChart(@RequestBody ChartAddRequest chartAddRequest, HttpServletRequest request) {
        if (chartAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartAddRequest, chart);
        User loginUser = userService.getLoginUser(request);
        chart.setUserId(loginUser.getId());
        boolean result = chartService.save(chart);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        long newChartId = chart.getId();
        return ResultUtils.success(newChartId);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteChart(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldChart.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = chartService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新（仅管理员）
     *
     * @param chartUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateChart(@RequestBody ChartUpdateRequest chartUpdateRequest) {
        if (chartUpdateRequest == null || chartUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartUpdateRequest, chart);
        long id = chartUpdateRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get")
    public BaseResponse<Chart> getChartById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = chartService.getById(id);
        if (chart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(chart);
    }

    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page")
    public BaseResponse<Page<Chart>> listMyChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
            HttpServletRequest request) {
        if (chartQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        chartQueryRequest.setUserId(loginUser.getId());
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    @PostMapping("/list/page")
    public BaseResponse<Page<Chart>> listChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
                                                       HttpServletRequest request) {
        if (chartQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        chartQueryRequest.setUserId(loginUser.getId());
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    // endregion

    /**
     * 编辑（用户）
     *
     * @param chartEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editChart(@RequestBody ChartEditRequest chartEditRequest, HttpServletRequest request) {
        if (chartEditRequest == null || chartEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartEditRequest, chart);
        User loginUser = userService.getLoginUser(request);
        long id = chartEditRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldChart.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }


    /**
     * 智能分析（同步）
     *
     * @param multipartFile
     * @param
     * @param request
     * @return
     */
    @PostMapping("/gen")
    public BaseResponse<BiResponse> genChartByAi(@RequestPart("file") MultipartFile multipartFile,
                                                 GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) throws JsonProcessingException {
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();
        //校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        //校验文件
        //判断大小是否超过1MB
        final long ONE_MB = 1024 * 1024L;
        long size = multipartFile.getSize();
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件过大");

        //判断文件类型
        String originalFilename = multipartFile.getOriginalFilename();
        String suffix = FileUtil.getSuffix(originalFilename);
        ThrowUtils.throwIf(StringUtils.isBlank(suffix), ErrorCode.PARAMS_ERROR, "文件名异常");
        boolean isExcel = suffix.equals("xlsx") || suffix.equals("xls");
        ThrowUtils.throwIf(!isExcel, ErrorCode.PARAMS_ERROR, "文件类型错误");

        // 获取用户信息
        User loginUser = userService.getLoginUser(request);
        // 用户限流处理
        redisLimiterManager.doRateLimiter("genChartByAi_" + loginUser.getId());

        // 用户输入
        String csvData = ExcelUtils.excelToCSV(multipartFile);
        //根据用户上传的数据，压缩ai提问语
        StringBuffer res = new StringBuffer();
        res.append("你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容：");
        res.append("\n").append("分析需求：").append("\n").append("{").append(goal).append("}").append("\n");
        res.append("原始数据:").append("\n").append(csvData);
        res.append("请根据这两部分内容，按照先生成结论，再生成代码的顺序，下面输出格式如：\n【原始数据分析结论】： \n...结论...\n【代码部分】：\n...代码部分...\n" +
                "\n以下为具体生成内容要求：" +
                "\n原始数据分析结论：根据提供的原始数据分析做详细文字总结。\n" +
                "代码部分：{可视化图表类型为" + chartType+ ";" + "只生成前端 Echarts V5 的 option 配置对象纯JSON代码，代码中不要使用函数等无效JSON语法，" +
                "合理的将数据进行可视化并且在代码toolbox中开启图表格式转换magicType、" +
                "保存图片saveAsImage功能。注意！代码部分需要给出```json这种标识");
        res.append("最后的代码部分不要生成任何多余的总结内容，不要注释}");

        //将拆分出来的数据保存到数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setUserId(loginUser.getId());
        boolean saveResult = chartService.save(chart);


        // 调用AI
        SparkAiManager sparkAiManager = new SparkAiManager(redissonClient);
        BiResponse ans = sparkAiManager.getAns(chart.getId(), res.toString());
        // 将AI返回的结果保存到数据库
        chart.setGenChart(ans.getGenChart());
        chart.setGenResult(ans.getGenResult());
        boolean hasError = "服务错误".equals(chart.getGenResult()) && "服务错误".equals(chart.getGenChart()) || "服务错误".equals(chart.getGenChart());
        if (hasError) {
            chart.setStatus("failed");
        }else {
            chart.setStatus("succeed");
        }
        boolean save = chartService.updateById(chart);
        ThrowUtils.throwIf(!save, ErrorCode.SYSTEM_ERROR, "图表状态更新失败");
        ans.setChartId(chart.getId());
        return ResultUtils.success(ans);
    }

    /**
     * 智能分析（异步）
     *
     * @param multipartFile
     * @param
     * @param request
     * @return
     */
    @PostMapping("/gen/async")
    public BaseResponse<BiResponse> genChartByAiAsync(@RequestPart("file") MultipartFile multipartFile,
                                                 GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) throws JsonProcessingException {
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();
        //校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        //校验文件
        //判断大小是否超过1MB
        final long ONE_MB = 1024 * 1024L;
        long size = multipartFile.getSize();
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件过大");

        //判断文件类型
        String originalFilename = multipartFile.getOriginalFilename();
        String suffix = FileUtil.getSuffix(originalFilename);
        ThrowUtils.throwIf(StringUtils.isBlank(suffix), ErrorCode.PARAMS_ERROR, "文件名异常");
        boolean isExcel = suffix.equals("xlsx") || suffix.equals("xls");
        ThrowUtils.throwIf(!isExcel, ErrorCode.PARAMS_ERROR, "文件类型错误");

        // 获取用户信息
        User loginUser = userService.getLoginUser(request);
        // 用户限流处理
        redisLimiterManager.doRateLimiter("genChartByAi_" + loginUser.getId());

        // 用户输入
        String csvData = ExcelUtils.excelToCSV(multipartFile);
        //根据用户上传的数据，压缩ai提问语
        StringBuffer res = new StringBuffer();
        res.append("你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容：");
        res.append("\n").append("分析需求：").append("\n").append("{").append(goal).append("}").append("\n");
        res.append("原始数据:").append("\n").append(csvData);
        res.append("请根据这两部分内容，按照先生成结论，再生成代码的顺序，下面输出格式如：\n【原始数据分析结论】： \n...结论...\n【代码部分】：\n...代码部分...\n" +
                "\n以下为具体生成内容要求：" +
                "\n原始数据分析结论：根据提供的原始数据分析做详细文字总结。\n" +
                "代码部分：{可视化图表类型为" + chartType+ ";" + "只生成前端 Echarts V5 的 option 配置对象纯JSON代码，代码中不要使用函数等无效JSON语法，" +
                "合理的将数据进行可视化并且在代码toolbox中开启图表格式转换magicType、" +
                "保存图片saveAsImage功能。注意！代码部分需要给出```json这种标识");
        res.append("最后的代码部分不要生成任何多余的总结内容，不要注释}");

        //将拆分出来的数据保存到数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setStatus("wait");
        chart.setUserId(loginUser.getId());
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");

        //使用线程池执行任务
        //在最终的返回结果提交一个任务
        //todo 处理任务队列满了后抛异常
        CompletableFuture.runAsync(() -> {
            //先修改图表任务状态为 “执行中”。等执行成功后，修改为 “已完成”、保存执行结果；执行失败后，状态修改为 “失败”，
            // 记录任务失败信息。(为了防止同一个任务被多次执行)
            Chart updateChart = new Chart();
            updateChart.setId(chart.getId());
            updateChart.setStatus("running");
            boolean b = chartService.updateById(updateChart);
            // 如果提交失败(一般情况下,更新失败可能意味着你的数据库出问题了)
            if (!b){
                chartService.handleChartUpdateError(chart.getId(), "更新图表运行中状态失败");
                return;
            }
            // 调用AI
            SparkAiManager sparkAiManager = new SparkAiManager(redissonClient);
            BiResponse ans = null;
            try {
                ans = sparkAiManager.getAns(chart.getId(), res.toString());
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            // 拿到AI结果后将状态更新为成功
            Chart updateChartResult = new Chart();
            updateChartResult.setId(chart.getId());
            updateChartResult.setGenChart(ans.getGenChart());
            updateChartResult.setGenResult(ans.getGenResult());
            boolean hasError = "服务错误".equals(updateChartResult.getGenResult()) && "服务错误".equals(updateChartResult.getGenChart()) || "服务错误".equals(updateChartResult.getGenChart());
            if (hasError) {
                updateChartResult.setStatus("failed");
            }else {
                updateChartResult.setStatus("succeed");
            }
            boolean b1 = chartService.updateById(updateChartResult);
            if (!b1){
                chartService.handleChartUpdateError(chart.getId(), "更新图表成功状态失败");
            }
        }, threadPoolExecutor);

        BiResponse ans = new BiResponse();
        ans.setChartId(chart.getId());
        return ResultUtils.success(ans);
    }


    /**
     * 智能分析（消息队列）
     *
     * @param multipartFile
     * @param
     * @param request
     * @return
     */
    @PostMapping("/gen/async/mq")
    public BaseResponse<BiResponse> genChartByAiAsyncMq(@RequestPart("file") MultipartFile multipartFile,
                                                      GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) throws JsonProcessingException {
        // 获取用户信息（必须登录才能使用）
        User loginUser = userService.getLoginUser(request);
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();
        //校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        //校验文件
        //判断大小是否超过1MB
        final long ONE_MB = 1024 * 1024L;
        long size = multipartFile.getSize();
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件过大");

        //判断文件类型
        String originalFilename = multipartFile.getOriginalFilename();
        String suffix = FileUtil.getSuffix(originalFilename);
        ThrowUtils.throwIf(StringUtils.isBlank(suffix), ErrorCode.PARAMS_ERROR, "文件名异常");
        boolean isExcel = suffix.equals("xlsx") || suffix.equals("xls");
        ThrowUtils.throwIf(!isExcel, ErrorCode.PARAMS_ERROR, "文件类型错误");

        // 压缩后的数据
        String csvData = ExcelUtils.excelToCSV(multipartFile);
        //将拆分出来的数据保存到数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        // 设置任务状态为wait
        chart.setStatus("wait");
        chart.setUserId(loginUser.getId());
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");

        // 使用消息队列取发送任务
        try {
            String message = chart.getId() + "," + loginUser.getId();
            biMessageProducer.sendMessage(message);
        } catch (Exception e) {
            chartService.handleChartUpdateError(chart.getId(), "Ai生成图表失败" + e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Ai生成图表失败");
        }

        BiResponse ans = new BiResponse();
        ans.setChartId(chart.getId());
        return ResultUtils.success(ans);
    }


    /**
     * 获取查询包装类
     *
     * @param chartQueryRequest
     * @return
     */
    private QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest) {
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        if (chartQueryRequest == null) {
            return queryWrapper;
        }

        Long id = chartQueryRequest.getId();
        String name = chartQueryRequest.getName();
        Long userId = chartQueryRequest.getUserId();
        String goal = chartQueryRequest.getGoal();
        String chartType = chartQueryRequest.getChartType();
        String sortField = chartQueryRequest.getSortField();
        String sortOrder = chartQueryRequest.getSortOrder();

        queryWrapper.eq(id != null && id > 0,"id", id);
        queryWrapper.eq(StringUtils.isNotBlank(goal),"goal", goal);
        queryWrapper.like(StringUtils.isNotBlank(name),"name", name);
        queryWrapper.eq(StringUtils.isNotBlank(chartType),"chartType", chartType);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq("isDelete",false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

}
