package com.bizhil.springbootinit.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bizhil.springbootinit.common.DeleteRequest;
import com.bizhil.springbootinit.common.ResultUtils;
import com.bizhil.springbootinit.model.dto.ai.AIAssistantQueryRequest;
import com.bizhil.springbootinit.model.dto.ai.AiRequest;
import com.bizhil.springbootinit.model.entity.AiAssistant;
import com.bizhil.springbootinit.model.vo.AiResponse;
import com.bizhil.springbootinit.utils.SqlUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.bizhil.springbootinit.common.BaseResponse;
import com.bizhil.springbootinit.common.ErrorCode;
import com.bizhil.springbootinit.constant.CommonConstant;
import com.bizhil.springbootinit.exception.BusinessException;
import com.bizhil.springbootinit.exception.ThrowUtils;
import com.bizhil.springbootinit.manager.RedisLimiterManager;
import com.bizhil.springbootinit.manager.SparkAiAssistantManager;
import com.bizhil.springbootinit.model.entity.User;
import com.bizhil.springbootinit.service.AiAssistantService;
import com.bizhil.springbootinit.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RedissonClient;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@Slf4j
@RequestMapping("/aide")
public class AiDialogueController {
    @Resource
    private UserService userService;

    @Resource
    private RedisLimiterManager redisLimiterManager;

    @Resource
    private AiAssistantService aiAssistantService;

    @Resource
    private RedissonClient redissonClient;


    /**
     * 向AI模型提问
     * @param aiRequest
     * @param request
     * @return
     * @throws JsonProcessingException
     */

    @PostMapping("/assistant")
    public BaseResponse<AiResponse> getAiDialogue(AiRequest aiRequest, HttpServletRequest request) throws JsonProcessingException {
        String goal = aiRequest.getIssue();
        //校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "问题为空");
        // 获取用户信息
        User loginUser = userService.getLoginUser(request);
        // 用户限流处理
        redisLimiterManager.doRateLimiter("genDialogueByAi_" + loginUser.getId());
        //根据用户上传的数据，压缩ai提问语
        StringBuffer res = new StringBuffer();
        res.append("你是一个很优秀的专家，接下来我会问你一些专业问题或我的想法：");
        res.append("\n").append("这是我向你的提问内容：").append("\n").append("{").append(goal).append("}").append("\n");
        res.append("请按照以下格式准确详细的回答我的问题，帮助我解决困难：\n【回答内容】： \n...内容...\n");

        // 保存内容到数据库
        AiAssistant aiAssistant = new AiAssistant();
        aiAssistant.setIssue(goal);
        aiAssistant.setStatus("wait");
        aiAssistant.setUserId(loginUser.getId());
        boolean save = aiAssistantService.save(aiAssistant);
        ThrowUtils.throwIf(!save, ErrorCode.SYSTEM_ERROR, "AI数据库表信息保存失败");

        // 调用AI
        SparkAiAssistantManager sparkAiAssistantManager = new SparkAiAssistantManager(redissonClient);
        AiResponse ans = sparkAiAssistantManager.getAns(aiAssistant.getId(), res.toString());
        // 将AI返回的结果保存到数据库
        aiAssistant.setGenResult(ans.getGenResult());
        boolean hasError = "服务错误".equals(aiAssistant.getGenResult());
        if (hasError) {
            aiAssistant.setStatus("failed");
        }else {
            aiAssistant.setStatus("succeed");
        }
        boolean saves = aiAssistantService.updateById(aiAssistant);
        ThrowUtils.throwIf(!saves, ErrorCode.SYSTEM_ERROR, "AI数据库表状态更新失败");
        ans.setAiAssistantId(aiAssistant.getId());
        return ResultUtils.success(ans);
    }


    /**
     * 查询AI历史对话记录
     * @param aiAssistantQueryRequest
     * @param request
     * @return
     */

    @PostMapping("/myai/list/page")
    public BaseResponse<Page<AiAssistant>> listMyAIAssistantByPage(@RequestBody AIAssistantQueryRequest aiAssistantQueryRequest,
                                                       HttpServletRequest request) {
        if (aiAssistantQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        aiAssistantQueryRequest.setUserId(loginUser.getId());
        long current = aiAssistantQueryRequest.getCurrent();
        long size = aiAssistantQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<AiAssistant> aiAssistantPage = aiAssistantService.page(new Page<>(current, size),
                getQueryWrapper(aiAssistantQueryRequest));
        return ResultUtils.success(aiAssistantPage);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteAi(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {

        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        AiAssistant oldChart = aiAssistantService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldChart.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = aiAssistantService.removeById(id);
        return ResultUtils.success(b);
    }


    /**
     * 获取查询包装类
     *
     * @param aiAssistantQueryRequest
     * @return
     */
    private QueryWrapper<AiAssistant> getQueryWrapper(AIAssistantQueryRequest aiAssistantQueryRequest) {
        QueryWrapper<AiAssistant> queryWrapper = new QueryWrapper<>();
        if (aiAssistantQueryRequest == null) {
            return queryWrapper;
        }

        Long id = aiAssistantQueryRequest.getId();
        Long userId = aiAssistantQueryRequest.getUserId();
        String issue = aiAssistantQueryRequest.getIssue();
        String sortField = aiAssistantQueryRequest.getSortField();
        String sortOrder = aiAssistantQueryRequest.getSortOrder();

        queryWrapper.eq(id != null && id > 0,"id", id);
        queryWrapper.eq(StringUtils.isNotBlank(issue),"issue", issue);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq("isDelete",false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

}
