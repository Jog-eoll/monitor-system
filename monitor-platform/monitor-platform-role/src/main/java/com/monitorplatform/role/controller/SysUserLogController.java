package com.monitorplatform.role.controller;


import com.alibaba.cloud.commons.lang.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.common.annotation.OperateLog;
import com.monitorplatform.common.entity.Result;
import com.monitorplatform.common.entity.SysUserLogVO;
import com.monitorplatform.common.util.IPUtil;
import com.monitorplatform.role.entity.SysUserLog;
import com.monitorplatform.role.entity.dto.PageSysLogDTO;
import com.monitorplatform.role.service.ISysUserLogService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

/**
 * <p>
 * 系统管理-操作日志表 前端控制器
 * </p>
 *
 * @author suweiming
 * @since 2022-07-13
 */
@RestController
@RequestMapping("/sysUserLog")
@Api(tags = "操作日志表")
public class SysUserLogController  {

    @Autowired
    private ISysUserLogService sysUserLogService;

    /**
     * 查询全部
     */
    @GetMapping(value = "/list")
    @ApiOperation("查询全部")
    public Result<?> list() {
        return Result.data(sysUserLogService.list());
    }

    /**
     * 新增
     */
    @PostMapping(value = "/add")
    @ApiOperation("新增日志")
    @OperateLog(enable = false)
    public Result<?> add(@RequestBody SysUserLog item, HttpServletRequest request) {
        if (StringUtils.isNotBlank(item.getClientIp())){
            item.setClientIp(IPUtil.getRealRequestIp(request));
        }
        sysUserLogService.save(item);
        return Result.data(item.getId());
    }

    /**
     * 删除
     */
    @PostMapping(value = "/delete/{id}")
    @ApiOperation("删除日志")
    @OperateLog(enable = false)
    public Result<String> delete(@PathVariable("id") String id) {
        sysUserLogService.removeById(id);
        return Result.success("成功");
    }

    /**
     * 修改
     */
    @PostMapping(value = "/update")
    @ApiOperation("修改日志")
    @OperateLog(enable = false)
    public Result<?> update(@RequestBody SysUserLog item) {
        sysUserLogService.updateById(item);
        return Result.success("成功");
    }

    /**
     * 获取单个信息
     */
    @GetMapping(value = "/get/{id}")
    @ApiOperation("获取单个信息")
    public Result<?> get(@PathVariable("id") String id) {
        return Result.data(sysUserLogService.getById(id));
    }


    /**
     * 新增(openfeign)
     */
    @PostMapping(value = "/insert")
    @OperateLog(enable = false)
    public String insert(@RequestBody SysUserLogVO item) {
        return sysUserLogService.insert(item);
    }

//
//    /**
//     * 联动日志数
//     *
//     * @return
//     */
//    @ApiImplicitParams({
//            @ApiImplicitParam(paramType = "query", dataType = "string", name = "startTime", value = "", required = true),
//            @ApiImplicitParam(paramType = "query", dataType = "string", name = "endTime", value = "", required = true),
//    })
//    @ApiOperation(value = "系统日志数", notes = "系统日志数", httpMethod = "POST")
//    @PostMapping("/sysLogCount")
//    @OperateLog(enable = false)
//    public Result<List<CountVO>> sysLogCount(String startTime, String endTime, boolean isLogin) {
//        List<SysUserLog> list = this.sysUserLogService.list(new LambdaQueryWrapper<SysUserLog>().eq(isLogin, SysUserLog::getActType, "login").apply("create_time between CONCAT('" + startTime + "',' 00:00:00') and CONCAT('" + endTime + "',' 23:59:59')"));
//        list.stream().peek(sysUserLog -> sysUserLog.setTime(sysUserLog.getCreateTime().toLocalDate())).collect(Collectors.toList());
//        Map<LocalDate, Long> collect = list.stream().collect(Collectors.groupingBy(SysUserLog::getTime, Collectors.counting()));
//        List<CountVO> countVOS = new ArrayList<>();
//        collect.forEach((key, value) -> {
//            CountVO countVO = new CountVO();
//            countVO.setDate(key);
//            countVO.setCount(value);
//            countVOS.add(countVO);
//        });
//        return Result.data(countVOS);
//    }


    /**
     * 分页
     *
     * @param dto 入参
     * @return 数据
     */
    @ApiOperation(value = "分页", notes = "分页", httpMethod = "POST")
    @PostMapping("/page")
    @OperateLog(enable = false)
    public Result<IPage<SysUserLog>> page(@RequestBody PageSysLogDTO dto) {
        IPage<SysUserLog> page = new Page<>(dto.getCurrent(), dto.getSize());
        this.sysUserLogService.page(page, new LambdaQueryWrapper<SysUserLog>()
                .eq(StringUtils.isNotBlank(dto.getType()), SysUserLog::getType, dto.getType())
                .eq(StringUtils.isNotBlank(dto.getActType()), SysUserLog::getActType, dto.getActType())
                .ge(dto.getStartTime() != null, SysUserLog::getCreateTime, dto.getStartTime())
                .le(dto.getEndTime() != null, SysUserLog::getCreateTime, dto.getEndTime())
                .and(StringUtils.isNotBlank(dto.getKeyword()), wrapper ->
                        wrapper.like(SysUserLog::getActAction, dto.getKeyword())
                                .or().like(SysUserLog::getClientIp, dto.getKeyword())
                                .or().like(SysUserLog::getActorName, dto.getKeyword())
                )
                .orderByDesc(SysUserLog::getCreateTime));
        return Result.data(page);
    }


//    /**
//     * 日志类型/操作日志占比
//     *
//     * @return
//     */
//    @ApiImplicitParams({
//            @ApiImplicitParam(paramType = "query", dataType = "string", name = "startTime", value = "", required = true),
//            @ApiImplicitParam(paramType = "query", dataType = "string", name = "endTime", value = "", required = true)
//    })
//    @ApiOperation(value = "日志类型/操作日志占比", notes = "日志类型/操作日志占比", httpMethod = "POST")
//    @PostMapping("/logCountByActType")
//    @OperateLog(enable = false)
//    public Result<List<LogCount>> logCountByActType(String startTime, String endTime) {
//        List<SysUserLog> logs = this.sysUserLogService.list(new LambdaQueryWrapper<SysUserLog>().
//                apply("create_time between concat('" + startTime + "', ' 00:00:00') and concat('" + endTime + "',' 23:59:59')"));
//        Map<LocalDate, Map<String, Long>> collect = logs.stream().collect(Collectors.groupingBy(log -> log.getCreateTime().toLocalDate(), Collectors.groupingBy(SysUserLog::getActType, Collectors.counting())));
//        List<LogCount> logCounts = new ArrayList<>();
//        collect.forEach((date, map) -> {
//            map.forEach((actType, count) -> {
//                LogCount logCount = new LogCount();
//                logCount.setDate(date);
//                logCount.setActType(LogActTypeEnum.getByCode(actType).getName());
//                logCount.setCount(count);
//                logCounts.add(logCount);
//            });
//        });
//        return Result.data(logCounts);
//    }


//    /**
//     * 导出
//     *
//     * @param response
//     * @param dto
//     */
//    @ApiOperation(value = "导出系统日志", notes = "导出", httpMethod = "POST")
//    @PostMapping("/export")
//    public void export(HttpServletResponse response, @RequestBody PageSysLogDTO dto) {
//        // 安全限制
//        final int MAX_EXPORT = 100_000;
//        final Long PAGE_SIZE = 1000L;
//        // 获取总数
//        dto.setSize(1L);
//        dto.setCurrent(1L);
//        Result<IPage<SysUserLog>> countPage = this.page(dto);
//        long total = Math.min(countPage.getData().getTotal(), MAX_EXPORT);
//
//        if (total == 0) {
//            ExportUtil.export(response, SysUserLog.class, Collections.emptyList(), "系统日志");
//            return;
//        }
//
//        // 数据提供器：给定页码，返回该页数据（已转换 actType）
//        java.util.function.Function<Long, List<?>> dataProvider = pageNum -> {
//            PageSysLogDTO pageDto = new PageSysLogDTO();
//            pageDto.setCurrent(pageNum);
//            pageDto.setSize(PAGE_SIZE);
//            pageDto.setType(dto.getType());
//            pageDto.setActType(dto.getActType());
//            pageDto.setStartTime(dto.getStartTime());
//            pageDto.setEndTime(dto.getEndTime());
//            pageDto.setKeyword(dto.getKeyword());
//
//            Result<IPage<SysUserLog>> result = this.page(pageDto);
//            List<SysUserLog> list = result.getData().getRecords();
//            // 转换枚举
//            list.forEach(log -> {
//                LogActTypeEnum typeEnum = LogActTypeEnum.getByCode(log.getActType());
//                log.setActType(typeEnum != null ? typeEnum.getName() : log.getActType());
//            });
//            return list;
//        };
//
//        ExportUtil.exportByPage(response, SysUserLog.class, "系统日志", dataProvider, PAGE_SIZE, total);
//    }

}

