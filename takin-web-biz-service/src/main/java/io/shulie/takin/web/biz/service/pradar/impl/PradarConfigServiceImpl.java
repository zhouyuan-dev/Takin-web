package io.shulie.takin.web.biz.service.pradar.impl;

import cn.hutool.core.bean.BeanUtil;
import io.shulie.takin.common.beans.page.PagingList;
import io.shulie.takin.web.biz.pojo.request.pradar.PradarZkConfigCreateRequest;
import io.shulie.takin.web.biz.pojo.request.pradar.PradarZkConfigDeleteRequest;
import io.shulie.takin.web.biz.pojo.request.pradar.PradarZKConfigQueryRequest;
import io.shulie.takin.web.biz.pojo.request.pradar.PradarZkConfigUpdateRequest;
import io.shulie.takin.web.biz.pojo.response.pradar.PradarZKConfigResponse;
import io.shulie.takin.web.biz.service.pradar.PradarConfigService;
import io.shulie.takin.web.biz.utils.ZkHelper;
import io.shulie.takin.web.common.util.CommonUtil;
import io.shulie.takin.web.data.dao.pradar.PradarZkConfigDAO;
import io.shulie.takin.web.data.param.pradarconfig.PradarConfigCreateParam;
import io.shulie.takin.web.data.result.pradarzkconfig.PradarZkConfigResult;
import io.shulie.takin.web.ext.util.WebPluginUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Slf4j
@Component
public class PradarConfigServiceImpl implements PradarConfigService {

    @Autowired
    private PradarZkConfigDAO pradarZkConfigDAO;

    @Autowired
    private ZkHelper zkHelper;

    @Override
    public void initZooKeeperData() {
        // 放入zk，只放入系统的
        for (PradarZkConfigResult config : pradarZkConfigDAO.list()) {
            if (!zkHelper.isNodeExists(config.getZkPath())) {
                zkHelper.addPersistentNode(config.getZkPath(), config.getValue());
            }
        }
    }

    @Override
    public PagingList<PradarZKConfigResponse> page(PradarZKConfigQueryRequest queryRequest) {
        PagingList<PradarZkConfigResult> page = pradarZkConfigDAO.page(WebPluginUtils.SYS_DEFAULT_TENANT_ID,
            WebPluginUtils.SYS_DEFAULT_ENV_CODE, queryRequest);
        if (page.getTotal() == 0) {
            return PagingList.empty();
        }
        return PagingList.of(CommonUtil.list2list(page.getList(), PradarZKConfigResponse.class), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void updateConfig(PradarZkConfigUpdateRequest updateRequest) {
        PradarConfigCreateParam updateParam = BeanUtil.copyProperties(updateRequest, PradarConfigCreateParam.class);
        PradarZkConfigResult config = pradarZkConfigDAO.getById(updateRequest.getId());
        Assert.notNull(config, "配置不存在！");
        if (pradarZkConfigDAO.update(updateParam)) {
            zkHelper.updateNode(config.getZkPath(), updateRequest.getValue());
        }
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void addConfig(PradarZkConfigCreateRequest createRequest) {
        String zkPath = createRequest.getZkPath();
        Assert.isTrue(zkPath.startsWith("/"), "zkPath必须以'/'开头");
        // 判断path是否存在
        PradarZkConfigResult pradarZkConfig = pradarZkConfigDAO.getByZkPath(zkPath);
        Assert.isNull(pradarZkConfig, "zkPath已存在！");
        PradarConfigCreateParam createParam = BeanUtil.copyProperties(createRequest, PradarConfigCreateParam.class);
        if (pradarZkConfigDAO.insert(createParam)) {
            zkHelper.addPersistentNode(zkPath, createParam.getValue());
        }
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void deleteConfig(PradarZkConfigDeleteRequest deleteRequest) {
        PradarZkConfigResult config = pradarZkConfigDAO.getById(deleteRequest.getId());
        Assert.notNull(config, "配置不存在！");
        if (pradarZkConfigDAO.deleteById(deleteRequest.getId())) {
            zkHelper.deleteNode(config.getZkPath());
        }
    }

}
