package io.shulie.takin.web.entrypoint.controller.agentupgradeonline;

import io.shulie.takin.common.beans.page.PagingList;
import io.shulie.takin.web.biz.pojo.request.agentupgradeonline.AgentLibraryCreateRequest;
import io.shulie.takin.web.biz.pojo.request.agentupgradeonline.PluginLibraryListQueryRequest;
import io.shulie.takin.web.biz.pojo.response.agentupgradeonline.AgentPluginUploadResponse;
import io.shulie.takin.web.biz.pojo.response.agentupgradeonline.PluginInfo;
import io.shulie.takin.web.biz.pojo.response.agentupgradeonline.PluginLibraryListResponse;
import io.shulie.takin.web.biz.service.agentupgradeonline.PluginLibraryService;
import io.shulie.takin.web.biz.utils.fastagentaccess.ResponseFileUtil;
import io.shulie.takin.web.common.constant.APIUrls;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 插件版本库(PluginLibrary)controller
 *
 * @author ocean_wll
 * @date 2021-11-09 20:23:17
 */
@RestController
@RequestMapping(APIUrls.TAKIN_API_URL + "pluginLibrary")
@Api(tags = "接口：agent插件管理")
@Validated
@Slf4j
public class PluginLibraryController {

    @Resource
    private PluginLibraryService pluginLibraryService;

    @ApiOperation("|_ 上传插件包，三种类型0、modules包，1、simulator包，2、agent包")
    @PutMapping("/upload")
    public AgentPluginUploadResponse uploadFile(@NotNull(message = "文件不能为空") MultipartFile file) {
        return pluginLibraryService.upload(file);
    }

    @ApiOperation("|_ 发布新版本")
    @PostMapping("/release")
    public void release(@Validated @RequestBody AgentLibraryCreateRequest createRequest) {
        pluginLibraryService.release(createRequest);
    }

    @ApiOperation("|_ 下载插件")
    @GetMapping("/download")
    public void getProjectFile(@RequestParam("pluginId") Long pluginId, HttpServletResponse response) {
        ResponseFileUtil.transfer(pluginLibraryService.getPluginFile(pluginId), false, "simulator.zip", true, response);
    }

    @ApiOperation("|_ 当前插件所有的历史版本")
    @GetMapping("/query/history")
    public List<PluginInfo> queryHistory(@NotBlank(message = "插件名不能为空") @RequestParam("pluginName") String pluginName) {
        return pluginLibraryService.queryByPluginName(pluginName);
    }

    @ApiOperation("|_ 查询所有的插件名(下拉框使用)")
    @GetMapping("/query/all/pluginNames")
    public List<String> queryAllPluginNames() {
        return pluginLibraryService.queryAllPluginNames();
    }

    @ApiOperation("|_ 列表分页查询")
    @GetMapping("/list")
    public PagingList<PluginLibraryListResponse> pluginList(PluginLibraryListQueryRequest queryRequest) {
        return pluginLibraryService.list(queryRequest);
    }


}
