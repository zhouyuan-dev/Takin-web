package io.shulie.takin.web.biz.job;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

import com.dangdang.ddframe.job.api.ShardingContext;
import com.dangdang.ddframe.job.api.simple.SimpleJob;
import io.shulie.takin.job.annotation.ElasticSchedulerJob;
import io.shulie.takin.utils.json.JsonHelper;
import io.shulie.takin.web.biz.service.report.ReportService;
import io.shulie.takin.web.biz.service.report.ReportTaskService;
import io.shulie.takin.web.ext.entity.tenant.TenantCommonExt;
import io.shulie.takin.web.ext.entity.tenant.TenantInfoExt;
import io.shulie.takin.web.ext.util.WebPluginUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @author 无涯
 * @date 2021/7/13 23:10
 */
@Component
@ElasticSchedulerJob(jobName = "calcTpsTargetJob",
    // 分片序列号和参数用等号分隔 不需要参数可以不加
    //shardingItemParameters = "0=0,1=1,2=2",
    isSharding = true,
    cron = "*/10 * * * * ?",
    description = "获取tps指标图")
@Slf4j
public class CalcTpsTargetJob implements SimpleJob {
    @Autowired
    private ReportTaskService reportTaskService;
    @Autowired
    private ReportService reportService;

    @Value("${open.report.task: true}")
    private boolean openReportTask;

    @Autowired
    @Qualifier("jobThreadPool")
    private ThreadPoolExecutor jobThreadPool;

    @Autowired
    @Qualifier("fastDebugThreadPool")
    private ThreadPoolExecutor fastDebugThreadPool;

    @Override
    public void execute(ShardingContext shardingContext) {
        if (!openReportTask) {
            return;
        }
        long start = System.currentTimeMillis();
        List<TenantInfoExt> tenantInfoExts = WebPluginUtils.getTenantInfoList();
        if(CollectionUtils.isEmpty(tenantInfoExts)) {
            // 私有化 + 开源 根据 报告id进行分片
            List<Long> reportIds =  reportTaskService.getRunningReport(null);
            log.info("获取正在压测中的报告:{}", JsonHelper.bean2Json(reportIds));
            for (Long reportId : reportIds) {
                // 开始数据层分片
                if (reportId % shardingContext.getShardingTotalCount() == shardingContext.getShardingItem()) {
                    fastDebugThreadPool.execute(() ->reportTaskService.calcTpsTarget(reportId));
                }
            }
        }else {
            // saas 根据租户进行分片
            for (TenantInfoExt ext : tenantInfoExts) {
                // 开始数据层分片
                if (ext.getTenantId() % shardingContext.getShardingTotalCount() == shardingContext.getShardingItem()) {
                    // 根据环境 分线程
                    ext.getEnvs().forEach(e ->
                        jobThreadPool.execute(() ->  this.calcTpsTarget(new TenantCommonExt(ext.getTenantId(),ext.getUserAppKey(),e.getEnvCode()))));
                }
            }
        }
        log.info("calcTpsTargetJob 执行时间:{}", System.currentTimeMillis() - start);
    }

    private void calcTpsTarget(TenantCommonExt ext) {
        List<Long> reportIds =  reportService.queryListRunningReport(ext);
        log.info("获取租户【{}】【{}】正在压测中的报告:{}", ext.getTenantId(), ext.getEnvCode(), JsonHelper.bean2Json(reportIds));
        for (Long reportId : reportIds) {
            // 开始数据层分片
            fastDebugThreadPool.execute(() ->reportTaskService.calcTpsTarget(reportId));
        }
    }
}
