package io.shulie.takin.web.data.dao.pradar;

import java.util.List;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.shulie.takin.common.beans.page.PagingList;
import io.shulie.takin.web.common.pojo.dto.PageBaseDTO;
import io.shulie.takin.web.data.param.pradarconfig.PagePradarZkConfigParam;
import io.shulie.takin.web.data.param.pradarconfig.PradarConfigCreateParam;
import io.shulie.takin.web.data.param.pradarconfig.PradarConfigQueryParam;
import io.shulie.takin.web.data.result.pradarzkconfig.PradarZKConfigResult;
import org.apache.ibatis.annotations.Param;

/**
 * @author junshao
 * @date 2021/07/08 2:35 下午
 */
public interface PradarZkConfigDAO {

    /**
     * 分页查询zk配置列表
     *
     * @param queryParam
     * @return
     */
    @InterceptorIgnore(tenantLine = "true")
    PagingList<PradarZKConfigResult> selectPage(@Param("queryParam") PradarConfigQueryParam queryParam);

    int insert(PradarConfigCreateParam createParam);

    int update(PradarConfigCreateParam updateParam);

    int delete(PradarConfigCreateParam deleteParam);

    /**
     * 系统配置的列表
     *
     * @return pradarConfig 系统配置的列表
     */
    @InterceptorIgnore(tenantLine = "true")
    List<PradarZKConfigResult> listSystemConfig();

    PradarZKConfigResult selectById(Long id);

    /**
     * 通过 zk path 获得配置
     * 如果自己的存在， 就使用自己的， 不存在， 就使用
     *
     * @param zkPath zk路径
     * @return 配置
     */
    PradarZKConfigResult getByZkPath(String zkPath);

    /**
     * pradarZkConfig 分页列表
     * 先找租户自己的，没有就找系统的
     *
     * @param pageBaseDTO 分页参数
     * @param param 筛选条件
     * @return 分页列表
     */
    @InterceptorIgnore(tenantLine = "true")
    IPage<PradarZKConfigResult> page(PagePradarZkConfigParam param, PageBaseDTO pageBaseDTO);

}
