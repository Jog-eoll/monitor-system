package com.monitorplatform.alarm.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.alarm.entity.AlarmRecord;
import com.monitorplatform.alarm.entity.dto.AlarmHandleDTO;
import com.monitorplatform.alarm.entity.dto.AlarmQueryDTO;
import com.monitorplatform.alarm.entity.dto.AlarmReceiveDTO;
import com.monitorplatform.alarm.entity.dto.AlarmStatisticsDTO;
import com.monitorplatform.alarm.entity.dto.DashboardOverviewDTO;
import com.monitorplatform.alarm.entity.dto.DisconnectGatewayDTO;
import com.monitorplatform.alarm.entity.dto.FalseAlarmDTO;
import com.monitorplatform.alarm.entity.dto.IdBatchRequestDTO;
import com.monitorplatform.alarm.entity.vo.AlarmTodayStatsVO;
import com.monitorplatform.alarm.entity.vo.GatewayActionResultVO;
import com.monitorplatform.alarm.entity.vo.WeeklyAlarmChartVO;
import com.monitorplatform.alarm.service.AlarmService;
import com.monitorplatform.common.entity.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/alarm")
@CrossOrigin(origins = "*")
public class AlarmController {

    @Resource
    private AlarmService alarmService;

    @PostMapping("/receive")
    public Result<AlarmRecord> receiveAlarm(@Validated @RequestBody AlarmReceiveDTO dto) {
        try {
            AlarmRecord record = alarmService.receiveAlarm(dto);
            return Result.data(record, "告警接收成功");
        } catch (Exception e) {
            log.error("receive alarm failed", e);
            return Result.fail(500, e.getMessage());
        }
    }

    @PostMapping("/handle")
    public Result<Boolean> handleAlarm(@Validated @RequestBody AlarmHandleDTO dto) {
        try {
            boolean success = alarmService.handleAlarm(dto);
            return Result.data(success, "告警处理成功");
        } catch (Exception e) {
            log.error("handle alarm failed", e);
            return Result.fail(500, e.getMessage());
        }
    }

    @GetMapping("/page")
    public Result<Page<AlarmRecord>> pageQuery(AlarmQueryDTO dto) {
        try {
            return Result.data(alarmService.pageQuery(dto));
        } catch (Exception e) {
            log.error("query alarm page failed", e);
            return Result.fail(500, e.getMessage());
        }
    }

    @GetMapping("/detail/{id}")
    public Result<AlarmRecord> getDetail(@PathVariable Long id) {
        try {
            AlarmRecord record = alarmService.getById(id);
            if (record == null) {
                return Result.fail(404, "告警记录不存在");
            }
            return Result.data(record);
        } catch (Exception e) {
            log.error("query alarm detail failed", e);
            return Result.fail(500, e.getMessage());
        }
    }

    @GetMapping("/recent")
    public Result<List<AlarmRecord>> getRecentList(@RequestParam(defaultValue = "20") Integer limit) {
        try {
            return Result.data(alarmService.getRecentList(limit));
        } catch (Exception e) {
            log.error("query recent alarms failed", e);
            return Result.fail(500, e.getMessage());
        }
    }

    @GetMapping("/pending")
    public Result<List<AlarmRecord>> getPendingList(@RequestParam(defaultValue = "20") Integer limit) {
        try {
            return Result.data(alarmService.getPendingList(limit));
        } catch (Exception e) {
            log.error("query pending alarms failed", e);
            return Result.fail(500, e.getMessage());
        }
    }

    @GetMapping("/statistics")
    public Result<AlarmStatisticsDTO> statistics() {
        try {
            return Result.data(alarmService.statistics());
        } catch (Exception e) {
            log.error("alarm statistics failed", e);
            return Result.fail(500, e.getMessage());
        }
    }

    @GetMapping("/export")
    public void exportExcel(HttpServletResponse response, AlarmQueryDTO dto) {
        try {
            alarmService.exportExcel(response, dto);
        } catch (Exception e) {
            log.error("export alarms failed", e);
        }
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        try {
            boolean success = alarmService.delete(id);
            return Result.data(success, "删除成功");
        } catch (Exception e) {
            log.error("delete alarm failed", e);
            return Result.fail(500, e.getMessage());
        }
    }

    @PostMapping("/delete-batch")
    public Result<Integer> deleteBatch(@RequestBody IdBatchRequestDTO params) {
        try {
            if (params == null || params.getIds() == null || params.getIds().isEmpty()) {
                return Result.fail(400, "请选择要删除的记录");
            }
            int count = alarmService.deleteBatch(params.getIds());
            return Result.data(count, "成功删除 " + count + " 条记录");
        } catch (Exception e) {
            log.error("delete alarms in batch failed", e);
            return Result.fail(500, e.getMessage());
        }
    }

    @GetMapping("/dashboard")
    public Result<DashboardOverviewDTO> getDashboard() {
        try {
            return Result.data(alarmService.getDashboardOverview());
        } catch (Exception e) {
            log.error("query dashboard failed", e);
            return Result.fail(500, e.getMessage());
        }
    }

    @PostMapping("/disconnect-gateway")
    public Result<GatewayActionResultVO> disconnectGateway(@Validated @RequestBody DisconnectGatewayDTO dto) {
        try {
            GatewayActionResultVO result = alarmService.disconnectGatewayConnection(dto);
            if (Boolean.TRUE.equals(result.getSuccess())) {
                return Result.data(result, "黑屏指令已发送");
            }
            return Result.fail(500, result.getMessage() == null ? "黑屏处置失败" : result.getMessage());
        } catch (Exception e) {
            log.error("disconnect gateway failed", e);
            return Result.fail(500, "操作异常: " + e.getMessage());
        }
    }

    @PostMapping("/auto-black-screen")
    public Result<GatewayActionResultVO> autoBlackScreen(@Validated @RequestBody DisconnectGatewayDTO dto) {
        try {
            GatewayActionResultVO result = alarmService.sendAutoBlackScreenCommand(dto);
            if (Boolean.TRUE.equals(result.getSuccess())) {
                return Result.data(result, "自动黑屏指令已发送");
            }
            return Result.fail(500, result.getMessage() == null ? "自动黑屏失败" : result.getMessage());
        } catch (Exception e) {
            log.error("auto black screen failed", e);
            return Result.fail(500, "操作异常: " + e.getMessage());
        }
    }

    @PostMapping("/resume-gateway")
    public Result<GatewayActionResultVO> resumeGateway(@Validated @RequestBody DisconnectGatewayDTO dto) {
        try {
            GatewayActionResultVO result = alarmService.resumeGatewayConnection(dto);
            if (Boolean.TRUE.equals(result.getSuccess())) {
                return Result.data(result, "停止黑屏指令已发送");
            }
            return Result.fail(500, result.getMessage() == null ? "停止黑屏失败" : result.getMessage());
        } catch (Exception e) {
            log.error("resume gateway failed", e);
            return Result.fail(500, "操作异常: " + e.getMessage());
        }
    }

    @PostMapping("/weekly-chart")
    public Result<WeeklyAlarmChartVO> getWeeklyAlarmChart() {
        try {
            return Result.data(alarmService.getWeeklyAlarmChart());
        } catch (Exception e) {
            log.error("query weekly chart failed", e);
            return Result.fail(500, e.getMessage());
        }
    }

    @GetMapping("/has-pending-by-ip")
    public Result<Boolean> hasPendingAlarmByIp(@RequestParam("ip") String ip) {
        try {
            return Result.data(alarmService.hasPendingAlarmByIp(ip));
        } catch (Exception e) {
            log.error("query pending by ip failed, ip={}", ip, e);
            return Result.fail(500, e.getMessage());
        }
    }

    @PostMapping("/todayAlarm")
    public Result<AlarmTodayStatsVO> todayStats() {
        try {
            return Result.data(alarmService.getTodayStats());
        } catch (Exception e) {
            log.error("query today stats failed", e);
            return Result.fail(500, e.getMessage());
        }
    }

    @PostMapping("/markFalseAlarm")
    public Result<Boolean> markFalseAlarm(@Validated @RequestBody FalseAlarmDTO dto) {
        try {
            boolean success = alarmService.markFalseAlarm(dto);
            return Result.data(success, "已标记为误报");
        } catch (Exception e) {
            log.error("mark false alarm failed", e);
            return Result.fail(500, e.getMessage());
        }
    }
}
