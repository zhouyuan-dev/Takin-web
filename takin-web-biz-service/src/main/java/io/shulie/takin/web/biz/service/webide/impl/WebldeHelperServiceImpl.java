package io.shulie.takin.web.biz.service.webide.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpGlobalConfig;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.Method;
import com.alibaba.fastjson.JSON;
import com.pamirs.takin.entity.domain.dto.linkmanage.ScriptJmxNode;
import com.pamirs.takin.entity.domain.dto.linkmanage.mapping.enums.fastdebug.RequestTypeEnum;
import io.shulie.takin.common.beans.page.PagingList;
import io.shulie.takin.web.biz.pojo.output.report.ReportDetailOutput;
import io.shulie.takin.web.biz.pojo.request.activity.ActivityResultQueryRequest;
import io.shulie.takin.web.biz.pojo.request.filemanage.FileManageUpdateRequest;
import io.shulie.takin.web.biz.pojo.request.linkmanage.BusinessFlowParseRequest;
import io.shulie.takin.web.biz.pojo.request.linkmanage.SceneLinkRelateRequest;
import io.shulie.takin.web.biz.pojo.request.scriptmanage.PageScriptDebugRequestRequest;
import io.shulie.takin.web.biz.pojo.request.scriptmanage.ScriptDebugDoDebugRequest;
import io.shulie.takin.web.biz.pojo.request.webide.WebIDESyncScriptRequest;
import io.shulie.takin.web.biz.pojo.response.activity.ActivityListResponse;
import io.shulie.takin.web.biz.pojo.response.linkmanage.BusinessFlowDetailResponse;
import io.shulie.takin.web.biz.pojo.response.linkmanage.BusinessFlowThreadResponse;
import io.shulie.takin.web.biz.pojo.response.scriptmanage.ScriptDebugDetailResponse;
import io.shulie.takin.web.biz.pojo.response.scriptmanage.ScriptDebugRequestListResponse;
import io.shulie.takin.web.biz.pojo.response.scriptmanage.ScriptDebugResponse;
import io.shulie.takin.web.biz.service.ActivityService;
import io.shulie.takin.web.biz.service.report.ReportService;
import io.shulie.takin.web.biz.service.scene.SceneService;
import io.shulie.takin.web.biz.service.scriptmanage.ScriptDebugService;
import io.shulie.takin.web.biz.service.webide.WebldeHelper;
import io.shulie.takin.web.common.enums.activity.BusinessTypeEnum;
import io.shulie.takin.web.common.enums.script.ScriptDebugStatusEnum;
import io.shulie.takin.web.data.dao.linkmanage.SceneDAO;
import io.shulie.takin.web.data.mapper.mysql.WebIdeSyncScriptMapper;
import io.shulie.takin.web.data.model.mysql.WebIdeSyncScriptEntity;
import io.shulie.takin.web.data.param.linkmanage.SceneUpdateParam;
import io.shulie.takin.web.data.result.linkmange.SceneResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * @Author: 南风
 * @Date: 2022/8/2 9:51 上午
 */
@Service
@Slf4j
public class WebldeHelperServiceImpl implements WebldeHelper {

    @Autowired
    private ScriptDebugService scriptDebugService;

    @Value("${file.upload.tmp.path:/tmp/takin/}")
    private String tmpFilePath;


    @Value("${takin.data.path}")
    private String baseNfsPath;

    @Autowired
    private ReportService reportService;

    @Resource
    private WebIdeSyncScriptMapper webIdeSyncScriptMapper;

    @Resource
    private ActivityService activityService;

    @Resource
    private SceneDAO sceneDAO;


    @Resource
    private ThreadPoolExecutor webIDESyncThreadPool;

    @Resource
    private SceneService sceneService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void actuatorScene(WebIDESyncScriptRequest request) {
        long startTime = System.currentTimeMillis();
        long createDebugTime = System.currentTimeMillis();

        List<ScriptDebugDoDebugRequest> scriptDeploys = new ArrayList<>();

        String url = request.getCallbackAddr();
        Long workRecordId = request.getWorkRecordId();
        boolean initData = false;
        WebIdeSyncScriptEntity entity = new WebIdeSyncScriptEntity();
        entity.setWorkRecordId(workRecordId);
        entity.setRequest(JSON.toJSONString(request));
        try {
            log.info("[webIDE同步开始] workRecordId:{}", workRecordId);
            List<WebIDESyncScriptRequest.ActivityFIle> flies = request.getFile();
            if (flies.size() > 0) {
                //todo 目前webIDE只会传jmx文件
                List<WebIDESyncScriptRequest.ActivityFIle> jmxs = flies.stream().
                        filter(t -> t.getType().equals(0))
                        .collect(Collectors.toList());

                jmxs.forEach(jmx -> {
                    //处理文件路径,改成控制台可用的路径
                    String path = baseNfsPath + "/" + jmx.getPath();
                    String uid = UUID.randomUUID().toString();
                    String sourcePath = tmpFilePath + "/" + uid + "/" + jmx.getName();
                    FileUtil.copy(path, sourcePath, false);

                    BusinessFlowParseRequest bus = new BusinessFlowParseRequest();
                    FileManageUpdateRequest file = new FileManageUpdateRequest();
                    file.setFileName(jmx.getName());
                    file.setFileType(jmx.getType());
                    file.setDownloadUrl(sourcePath);
                    file.setUploadId(uid);
                    file.setIsDeleted(0);
                    bus.setScriptFile(file);
                    //解析脚本
                    BusinessFlowDetailResponse parseScriptAndSave = sceneService.parseScriptAndSave(bus);
                    BusinessFlowDetailResponse detail = sceneService.getBusinessFlowDetail(parseScriptAndSave.getId());
                    if (Objects.nonNull(detail)) {
                        entity.setBusinessFlowId(parseScriptAndSave.getId());
                        entity.setScriptDeployId(detail.getScriptDeployId());
                        ScriptDebugDoDebugRequest scriptDebugDoDebug = new ScriptDebugDoDebugRequest();
                        scriptDebugDoDebug.setScriptDeployId(detail.getScriptDeployId());
                        scriptDebugDoDebug.setConcurrencyNum(request.getConcurrencyNum());
                        scriptDebugDoDebug.setRequestNum(request.getRequestNum());
                        scriptDeploys.add(scriptDebugDoDebug);

                        //todo 目前webIDE只会有一个脚本节点，后面多个脚本节点需要加入匹配逻辑
                        String xpathMd5 = detail.getScriptJmxNodeList().get(0).getValue();
                        BusinessFlowThreadResponse groupDetail = sceneService.getThreadGroupDetail(parseScriptAndSave.getId(),
                                xpathMd5);
                        if (Objects.nonNull(groupDetail)) {
                            List<ScriptJmxNode> threadScriptJmxNodes = groupDetail.getThreadScriptJmxNodes();
                            List<ScriptJmxNode> parseNodes = new ArrayList<>();
                            //递归解析出所有需要匹配的节点
                            parse(threadScriptJmxNodes, parseNodes);
                            if (parseNodes.size() > 0) {
                                //给节点匹配应用入口
                                List<WebIDESyncScriptRequest.ApplicationActivity> application = request.getApplication();
                                if (application.size() > 0) {
                                    //匹配
                                    List<SceneLinkRelateRequest> matchList = matchBuild(application, parseNodes,
                                            parseScriptAndSave.getId());

                                    if (matchList.size() > 0) {
                                        matchList.forEach(t -> sceneService.matchActivity(t));
                                    }
                                }
                            }
                        }
                    }
                });
            }
            initData = true;
        } catch (Exception e) {
            log.error("[创建业务场景失败] workRecordId:{},e", workRecordId, e);
            entity.setErrorMsg(e.toString());
            entity.setErrorStage("初始化数据阶段异常");
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } finally {
            log.info("[创建业务场景] 回调");
            String msg = initData ? "创建业务场景成功" : "创建业务场景失败";
            entity.setIsError(initData ? 0 : 1);
            String level = initData ? "INFO" : "FATAL";
            if (StringUtils.isNotBlank(entity.getErrorMsg())) {
                msg += ",{" + entity.getErrorMsg() + "}";
            }
            callback(url, msg, workRecordId, level);
        }
        long initializationTime = System.currentTimeMillis();

        //启动调试
        if (initData && scriptDeploys.size() > 0) {
            if (scriptDeploys.size() > 1) {
                //目前一次请求仅发起一次调试，多了不处理
                return;
            }
            Long debugId = 0L;
            boolean debugFlag = false;
            String errorMsg = "";
            try {
                ScriptDebugResponse debug = scriptDebugService.debug(scriptDeploys.get(0));
                if (debug.getScriptDebugId() != null) {
                    entity.setScriptDebugId(debug.getScriptDebugId());
                    debugId = debug.getScriptDebugId();
                    debugFlag = true;
                } else {
                    log.error("[启动调试失败] workRecordId:{},error:{}", workRecordId, debug.getErrorMessages().get(0));
                    entity.setErrorMsg(debug.getErrorMessages().get(0));
                    entity.setErrorStage("启动调试异常");
                    errorMsg = entity.getErrorMsg();
                }


            } catch (Exception e) {
                log.error("[启动调试失败] workRecordId:{},e", workRecordId, e);
                entity.setErrorMsg(e.toString());
                entity.setErrorStage("启动调试异常");
                errorMsg = e.getMessage();
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();

            } finally {
                String msg = debugFlag ? "启动调试成功" : "启动调试失败, 失败原因:{" + errorMsg + "}";
                log.info("[启动调试回调] workRecordId,:{},状态 :{}", workRecordId, msg);
                String level = debugFlag ? "INFO" : "FATAL";
                callback(url, msg, workRecordId, level);
                if (!debugFlag) {
                    delScene(entity.getBusinessFlowId());
                }
            }
            createDebugTime = System.currentTimeMillis();
            Long finalDebugId = debugId;

            // 虽然就一条数据，线程池不能去掉！！！去掉了上面172行会数据库死锁，不要问为什么
            webIDESyncThreadPool.execute(() -> {
                boolean loop = true;
                List<Integer> status = new ArrayList<>();
                do {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    ScriptDebugDetailResponse debugDetail = scriptDebugService.getById(finalDebugId);
                    log.info("[debug状态回调] workRecordId:{},debugId:{}", workRecordId, finalDebugId);
                    if (Objects.isNull(debugDetail)) {
                        break;
                    }
                    String level = "INFO";
                    String msg = ScriptDebugStatusEnum.getDesc(debugDetail.getStatus());

                    if (status.contains(debugDetail.getStatus())) {
                        //如果已经记录了当前状态,就不往下走了，不再触发callback
                        continue;
                    }
                    status.add(debugDetail.getStatus());

                    if (debugDetail.getStatus() == 4 || debugDetail.getStatus() == 5) {
                        loop = false;
                        if (debugDetail.getStatus() == 5) {
                            level = "ERROR";
                            //发送报告错误日志
                            Long cloudReportId = debugDetail.getCloudReportId();
                            ReportDetailOutput report = reportService.getReportByReportId(cloudReportId);
                            if (Objects.nonNull(report)) {
                                String resourceId = report.getResourceId();
                                Long jobId = report.getJobId();
                                String errorFilePath = tmpFilePath + "/ptl/" + resourceId + "/" + jobId;
                                if (FileUtil.exist(errorFilePath)) {
                                    String errorContext = FileUtil.readUtf8String(errorFilePath);
                                    log.info("[发送报告错误日志] workRecordId:{},resourceId:{},jobId:{}", workRecordId, resourceId, jobId);
                                    callback(url, errorContext, workRecordId, level);
                                }
                            }
                            msg += "，调试失败";
                        } else {
                            msg += ", 调试成功";
                        }

                        //获取调试详情
                        PageScriptDebugRequestRequest req = new PageScriptDebugRequestRequest();
                        req.setScriptDebugId(finalDebugId);
                        req.setCurrent(0);
                        req.setPageSize(10);
                        PagingList<ScriptDebugRequestListResponse> pageDetail = scriptDebugService.pageScriptDebugRequest(req);
                        if (pageDetail != null) {
                            List<ScriptDebugRequestListResponse> list = pageDetail.getList();
                            msg += ": {" + JSON.toJSONString(list) + "}";
                        }
                    }

                    callback(url, msg, workRecordId, level);
                } while (loop);
                delScene(entity.getBusinessFlowId());
            });

        }

        long debugTime = System.currentTimeMillis();
        long endTime = System.currentTimeMillis();

        //数据初始化耗时
        long initTime = initializationTime - startTime;
        //启动调试耗时
        long runTime = createDebugTime - initializationTime;
        //调试耗时
        long debugEndTime = debugTime - createDebugTime;
        //总耗时
        long time = endTime - startTime;

        log.info("[webIDE同步时间] startTime:{} ms,endTime:{} ms,总耗时:{} ms, 数据初始化耗时: {} ms，启动调试耗时:{} ms, 调试耗时:{} ms"
                , startTime, endTime, time, initTime, runTime, debugEndTime);
//        entity.setStartTime(startTime);
//        entity.setInitTime(initTime);
//        entity.setPrepareDebugTime(runTime);
//        entity.setDebugTime(debugEndTime);
//        entity.setTotalTime(time);
//        entity.setEndTime(endTime);
        webIDESyncThreadPool.execute(() -> saveSyncDetail(entity));
    }


    private void parse(List<ScriptJmxNode> threadScriptJmxNodes, List<ScriptJmxNode> list) {
        if (threadScriptJmxNodes.size() > 0) {
            threadScriptJmxNodes.forEach(item -> {
                if (item.getChildren() != null) {
                    parse(item.getChildren(), list);
                } else {
                    list.add(item);
                }
            });
        }
    }

    private List<SceneLinkRelateRequest> matchBuild(List<WebIDESyncScriptRequest.ApplicationActivity> activityInfo,
                                                    List<ScriptJmxNode> parseNodes, Long id) {
        List<SceneLinkRelateRequest> list = new ArrayList<>();
        for (WebIDESyncScriptRequest.ApplicationActivity activity : activityInfo) {
            for (ScriptJmxNode node : parseNodes) {
                if (node.getRequestPath().equals((activity.getMethod() + "|" + activity.getServiceName()))) {
                    SceneLinkRelateRequest request = new SceneLinkRelateRequest();
                    request.setBusinessFlowId(id);
                    request.setIdentification(node.getIdentification());
                    request.setXpathMd5(node.getXpathMd5());
                    request.setTestName(node.getTestName());
                    request.setApplicationName(activity.getApplicationName());
                    request.setEntrance(activity.getMethod() + "|" + activity.getServiceName() + "|" + activity.getRpcType());
                    request.setActivityName(activity.getActivityName());
                    request.setSamplerType(node.getSamplerType());
                    request.setBusinessType(BusinessTypeEnum.NORMAL_BUSINESS.getType());
                    ActivityResultQueryRequest activityQuery = new ActivityResultQueryRequest();
                    activityQuery.setApplicationName(request.getApplicationName());
                    activityQuery.setEntrancePath(request.getEntrance());

                    List<ActivityListResponse> responses = activityService.queryNormalActivities(activityQuery);
                    if (responses.size() > 0) {
                        request.setBusinessActivityId(responses.get(0).getActivityId());
                    }
                    list.add(request);
                }
            }
        }
        return list;
    }


    private void callback(String url, String msg, Long workRecordId, String level) {
        url = url + "?source=Takin控制台&level=" + level + "&work_record_id=" + workRecordId;
        new HttpRequest(url)
                .method(Method.POST)
                .contentType(RequestTypeEnum.TEXT.getDesc())
                .timeout(HttpGlobalConfig.getTimeout()).
                body(msg)
                .execute()
                .body();
    }

    private void delScene(Long businessFlowId) {
        SceneResult sceneDetail = sceneDAO.getSceneDetail(businessFlowId);
        if (Objects.isNull(sceneDetail)) {
            return;
        }
        SceneUpdateParam update = new SceneUpdateParam();
        update.setId(businessFlowId);
        update.setIsDeleted(1);
        sceneDAO.update(update);
    }

    private void saveSyncDetail(WebIdeSyncScriptEntity entity) {
        webIdeSyncScriptMapper.insert(entity);
    }
}
