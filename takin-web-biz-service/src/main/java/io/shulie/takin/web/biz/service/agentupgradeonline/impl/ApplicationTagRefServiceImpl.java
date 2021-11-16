package io.shulie.takin.web.biz.service.agentupgradeonline.impl;

import cn.hutool.core.collection.CollectionUtil;
import io.shulie.takin.common.beans.component.SelectVO;
import io.shulie.takin.web.biz.service.agentupgradeonline.ApplicationTagRefService;
import io.shulie.takin.web.data.dao.agentupgradeonline.ApplicationTagRefDAO;
import io.shulie.takin.web.data.result.application.ApplicationTagRefDetailResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 应用标签表(ApplicationTagRef)service
 *
 * @author ocean_wll
 * @date 2021-11-09 20:09:44
 */
@Service
public class ApplicationTagRefServiceImpl implements ApplicationTagRefService {

    @Autowired
    private ApplicationTagRefDAO refDAO;


    @Override
    public List<SelectVO> getListByTenant() {
        List<ApplicationTagRefDetailResult> listByTenant = refDAO.getListByTenant();
        if(CollectionUtil.isEmpty(listByTenant)){
            return Collections.emptyList();
        }
        return listByTenant.stream().map(
                tagInfo -> new SelectVO(tagInfo.getTagName(),String.valueOf(tagInfo.getTagId()))
        ).collect(Collectors.toList());
    }

    @Override
    public List<ApplicationTagRefDetailResult> getList(List<Long> applicationIds) {
        if(CollectionUtil.isEmpty(applicationIds)){
            return Collections.emptyList();
        }
        return refDAO.getList(applicationIds);
    }

    @Override
    public List<ApplicationTagRefDetailResult> getList(Long tagId) {
        if(Objects.isNull(tagId)){
            return Collections.emptyList();
        }
        return refDAO.getList(tagId);
    }


}
