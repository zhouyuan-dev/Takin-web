package io.shulie.takin.web.biz.service.agentupgradeonline.impl;

import java.util.List;

import javax.annotation.Resource;

import com.pamirs.takin.entity.domain.entity.TApplicationMnt;
import io.shulie.takin.web.biz.pojo.bo.agentupgradeonline.AgentCommandBO;
import io.shulie.takin.web.biz.pojo.bo.agentupgradeonline.AgentHeartbeatBO;
import io.shulie.takin.web.biz.pojo.request.agentupgradeonline.AgentHeartbeatRequest;
import io.shulie.takin.web.biz.service.ApplicationService;
import io.shulie.takin.web.biz.service.agentupgradeonline.AgentHeartbeatService;
import io.shulie.takin.web.biz.service.agentupgradeonline.AgentReportService;
import io.shulie.takin.web.biz.service.agentupgradeonline.ApplicationPluginUpgradeService;
import io.shulie.takin.web.common.enums.agentupgradeonline.AgentUpgradeEnum;
import io.shulie.takin.web.common.enums.excel.BooleanEnum;
import io.shulie.takin.web.common.enums.fastagentaccess.AgentReportStatusEnum;
import io.shulie.takin.web.common.enums.fastagentaccess.AgentStatusEnum;
import io.shulie.takin.web.common.enums.fastagentaccess.ProbeStatusEnum;
import io.shulie.takin.web.common.exception.ExceptionCode;
import io.shulie.takin.web.common.exception.TakinWebException;
import io.shulie.takin.web.data.result.application.ApplicationPluginUpgradeDetailResult;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * @Description agent心跳处理器
 * @Author ocean_wll
 * @Date 2021/11/11 2:53 下午
 */
@Service
public class AgentHeartbeatServiceImpl implements AgentHeartbeatService {

    @Resource
    private AgentReportService agentReportService;

    @Resource
    private ApplicationService applicationService;

    @Resource
    private ApplicationPluginUpgradeService applicationPluginUpgradeService;

    public List<AgentCommandBO> process(AgentHeartbeatRequest commandRequest) {
        // TODO ocean_wll
        // 检测状态

        // 数据入库

        // 处理各种命令

        return null;
    }

    /**
     * 获取agent状态
     *
     * @param agentHeartBeatBO 心跳数据
     * @return AgentReportStatusEnum
     */
    private AgentReportStatusEnum getAgentReportStatus(AgentHeartbeatBO agentHeartBeatBO) {
        // 判断是否已卸载  探针上报已卸载
        if (BooleanEnum.TRUE.getCode().equals(agentHeartBeatBO.getUninstallStatus())) {
            return AgentReportStatusEnum.UNINSTALL;
        }

        // 判断是否已休眠 探针上报已休眠
        if (BooleanEnum.TRUE.getCode().equals(agentHeartBeatBO.getDormantStatus())) {
            return AgentReportStatusEnum.SLEEP;
        }

        // 判断是否为启动中，agent状态为成功，simulator状态为null或者空字符串
        if (AgentStatusEnum.INSTALLED.getCode().equals(agentHeartBeatBO.getAgentStatus()) && StringUtils.isEmpty(
            agentHeartBeatBO.getSimulatorStatus())) {
            return AgentReportStatusEnum.STARTING;
        }

        // 判断是否异常 agent状态异常 或 simulator状态不是安装成功
        if (AgentStatusEnum.INSTALL_FAILED.getCode().equals(agentHeartBeatBO.getAgentStatus())
            || !ProbeStatusEnum.INSTALLED.getCode().equals(agentHeartBeatBO.getSimulatorStatus())) {
            return AgentReportStatusEnum.ERROR;
        }

        // 查询当前应用的升级单批次
        if (AgentStatusEnum.INSTALLED.getCode().equals(agentHeartBeatBO.getAgentStatus())
            && ProbeStatusEnum.INSTALLED.getCode().equals(agentHeartBeatBO.getSimulatorStatus())) {

            // 查询最新的升级单
            ApplicationPluginUpgradeDetailResult pluginUpgradeDetailResult
                = applicationPluginUpgradeService.queryLatestUpgradeByAppIdAndStatus(
                agentHeartBeatBO.getApplicationId(), AgentUpgradeEnum.UPGRADE_SUCCESS.getVal());

            if (pluginUpgradeDetailResult == null
                || pluginUpgradeDetailResult.getUpgradeBatch().equals(agentHeartBeatBO.getCurUpgradeBatch())) {
                return AgentReportStatusEnum.RUNNING;
            } else {
                return AgentReportStatusEnum.WAIT_RESTART;
            }
        }

        return AgentReportStatusEnum.UNKNOWN;
    }

    /**
     * AgentCommandRequest -> AgentHeartBeatBO
     *
     * @param commandRequest AgentCommandRequest对象
     * @return AgentHeartBeatBO对象
     */
    private AgentHeartbeatBO buildAgentHeartBeatBO(AgentHeartbeatRequest commandRequest) {
        TApplicationMnt applicationMnt = applicationService.queryTApplicationMntByName(commandRequest.getProjectName());
        if (applicationMnt == null) {
            throw new TakinWebException(ExceptionCode.AGENT_REGISTER_ERROR, "应用名不存在");
        }
        AgentHeartbeatBO agentHeartBeatBO = new AgentHeartbeatBO();
        agentHeartBeatBO.setApplicationId(applicationMnt.getApplicationId());
        BeanUtils.copyProperties(commandRequest, agentHeartBeatBO);
        return agentHeartBeatBO;
    }
}
