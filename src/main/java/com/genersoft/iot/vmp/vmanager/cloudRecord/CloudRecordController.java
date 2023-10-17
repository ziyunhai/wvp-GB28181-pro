package com.genersoft.iot.vmp.vmanager.cloudRecord;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.genersoft.iot.vmp.conf.DynamicTask;
import com.genersoft.iot.vmp.conf.UserSetting;
import com.genersoft.iot.vmp.conf.exception.ControllerException;
import com.genersoft.iot.vmp.media.zlm.SendRtpPortManager;
import com.genersoft.iot.vmp.media.zlm.ZLMServerFactory;
import com.genersoft.iot.vmp.media.zlm.ZlmHttpHookSubscribe;
import com.genersoft.iot.vmp.media.zlm.dto.MediaServerItem;
import com.genersoft.iot.vmp.service.ICloudRecordService;
import com.genersoft.iot.vmp.service.IMediaServerService;
import com.genersoft.iot.vmp.service.bean.CloudRecordItem;
import com.genersoft.iot.vmp.vmanager.bean.ErrorCode;
import com.genersoft.iot.vmp.vmanager.bean.RecordFile;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

@SuppressWarnings("rawtypes")
@Tag(name = "云端录像接口")

@RestController
@RequestMapping("/api/cloud/record")
public class CloudRecordController {

    @Autowired
    private ZLMServerFactory zlmServerFactory;

    @Autowired
    private SendRtpPortManager sendRtpPortManager;

    private final static Logger logger = LoggerFactory.getLogger(CloudRecordController.class);

    @Autowired
    private ICloudRecordService cloudRecordService;

    @Autowired
    private IMediaServerService mediaServerService;

    @Autowired
    private UserSetting userSetting;

    @Autowired
    private DynamicTask dynamicTask;

    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    @ResponseBody
    @GetMapping("/date/list")
    @Operation(summary = "查询存在云端录像的日期")
    @Parameter(name = "app", description = "应用名", required = true)
    @Parameter(name = "stream", description = "流ID", required = true)
    @Parameter(name = "year", description = "年，置空则查询当年", required = false)
    @Parameter(name = "month", description = "月，置空则查询当月", required = false)
    @Parameter(name = "mediaServerId", description = "流媒体ID，置空则查询全部", required = false)
    public List<String> openRtpServer(
            @RequestParam String app,
            @RequestParam String stream,
            @RequestParam(required = false) int year,
            @RequestParam(required = false) int month,
            @RequestParam(required = false) String mediaServerId

    ) {
        logger.info("[云端录像] 查询存在云端录像的日期 app->{}, stream->{}, mediaServerId->{}, year->{}, month->{}",
                app, stream, mediaServerId, year, month);
        Calendar calendar = Calendar.getInstance();
        if (ObjectUtils.isEmpty(year)) {
            year = calendar.get(Calendar.YEAR);
        }
        if (ObjectUtils.isEmpty(month)) {
            month = calendar.get(Calendar.MONTH) + 1;
        }
        List<MediaServerItem> mediaServerItems;
        if (!ObjectUtils.isEmpty(mediaServerId)) {
            mediaServerItems = new ArrayList<>();
            MediaServerItem mediaServerItem = mediaServerService.getOne(mediaServerId);
            if (mediaServerItem == null) {
                throw new ControllerException(ErrorCode.ERROR100.getCode(), "未找到流媒体: " + mediaServerId);
            }
            mediaServerItems.add(mediaServerItem);
        } else {
            mediaServerItems = mediaServerService.getAll();
        }
        if (mediaServerItems.isEmpty()) {
            return new ArrayList<>();
        }

        return cloudRecordService.getDateList(app, stream, year, month, mediaServerItems);
    }

    @ResponseBody
    @GetMapping("/list")
    @Operation(summary = "分页查询云端录像")
    @Parameter(name = "app", description = "应用名", required = true)
    @Parameter(name = "stream", description = "流ID", required = true)
    @Parameter(name = "page", description = "当前页", required = false)
    @Parameter(name = "count", description = "每页查询数量", required = false)
    @Parameter(name = "startTime", description = "开始时间(yyyy-MM-dd HH:mm:ss)", required = true)
    @Parameter(name = "endTime", description = "结束时间(yyyy-MM-dd HH:mm:ss)", required = true)
    @Parameter(name = "mediaServerId", description = "流媒体ID，置空则查询全部流媒体", required = false)
    public PageInfo<CloudRecordItem> openRtpServer(
            @RequestParam String app,
            @RequestParam String stream,
            @RequestParam int page,
            @RequestParam int count,
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(required = false) String mediaServerId

    ) {
        logger.info("[云端录像] 查询 app->{}, stream->{}, mediaServerId->{}, page->{}, count->{}, startTime->{}, endTime->{}",
                app, stream, mediaServerId, page, count, startTime, endTime);

        List<MediaServerItem> mediaServerItems;
        if (!ObjectUtils.isEmpty(mediaServerId)) {
            mediaServerItems = new ArrayList<>();
            MediaServerItem mediaServerItem = mediaServerService.getOne(mediaServerId);
            if (mediaServerItem == null) {
                throw new ControllerException(ErrorCode.ERROR100.getCode(), "未找到流媒体: " + mediaServerId);
            }
            mediaServerItems.add(mediaServerItem);
        } else {
            mediaServerItems = mediaServerService.getAll();
        }
        if (mediaServerItems.isEmpty()) {
            throw new ControllerException(ErrorCode.ERROR100.getCode(), "当前无流媒体");
        }
        return cloudRecordService.getList(page, count, app, stream, startTime, endTime, mediaServerItems);
    }

    @ResponseBody
    @GetMapping("/task/add")
    @Operation(summary = "添加合并任务")
    @Parameter(name = "app", description = "应用名", required = true)
    @Parameter(name = "stream", description = "流ID", required = true)
    @Parameter(name = "mediaServerId", description = "流媒体ID", required = false)
    @Parameter(name = "startTime", description = "鉴权ID", required = false)
    @Parameter(name = "endTime", description = "鉴权ID", required = false)
    @Parameter(name = "callId", description = "鉴权ID", required = false)
    @Parameter(name = "remoteHost", description = "返回地址时的远程地址", required = false)
    public String addTask(
            @RequestParam(required = true) String app,
            @RequestParam(required = true) String stream,
            @RequestParam(required = false) String mediaServerId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String callId,
            @RequestParam(required = false) String remoteHost
    ){
        return cloudRecordService.addTask(app, stream, mediaServerId, startTime, endTime, callId, remoteHost);
    }

    @ResponseBody
    @GetMapping("/task/list")
    @Operation(summary = "查询合并任务")
    @Parameter(name = "taskId", description = "任务Id", required = false)
    @Parameter(name = "mediaServerId", description = "流媒体ID", required = false)
    @Parameter(name = "isEnd", description = "是否结束", required = false)
    public JSONArray queryTaskList(
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String mediaServerId,
            @RequestParam(required = false) Boolean isEnd
    ){
        return cloudRecordService.queryTask(taskId, mediaServerId, isEnd);
    }

    @ResponseBody
    @GetMapping("/collect/add")
    @Operation(summary = "添加收藏")
    @Parameter(name = "app", description = "应用名", required = true)
    @Parameter(name = "stream", description = "流ID", required = true)
    @Parameter(name = "mediaServerId", description = "流媒体ID", required = false)
    @Parameter(name = "startTime", description = "鉴权ID", required = false)
    @Parameter(name = "endTime", description = "鉴权ID", required = false)
    @Parameter(name = "callId", description = "鉴权ID", required = false)
    @Parameter(name = "collectType", description = "收藏类型", required = false)
    public JSONArray addCollect(
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String mediaServerId,
            @RequestParam(required = false) Boolean isEnd
    ){
        return cloudRecordService.queryTask(taskId, mediaServerId, isEnd);
    }



}
