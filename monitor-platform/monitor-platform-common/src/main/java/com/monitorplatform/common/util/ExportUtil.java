package com.monitorplatform.common.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.util.MapUtils;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.alibaba.fastjson2.JSON;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * @Description 导出工具类
 * @Author: zqr
 * @Date: 2023/3/14 18:10:03
 */
@Slf4j
public class ExportUtil {

    @SneakyThrows
    public static void export(HttpServletResponse response, Class<?> clazz, Collection<?> data, String fileName) {
        try {
            //使用swagger会导致各种问题，请直接用浏览器或者用postman
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            // 这里URLEncoder.encode可以防止中文乱码
            String urlName = URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");
            response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + urlName + ".xlsx");
            EasyExcel.write(response.getOutputStream(), clazz)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy()).sheet(fileName).doWrite(data);
        } catch (IOException e) {
            response.reset();
            response.setContentType("application/json");
            response.setCharacterEncoding("utf-8");
            Map<String, String> map = MapUtils.newHashMap();
            map.put("status", "failure");
            map.put("message", "下载文件失败" + e.getMessage());
            response.getWriter().println(JSON.toJSONString(map));
            log.info("导出{}失败:{}", fileName, e.getMessage());
        }
    }

    @SneakyThrows
    public static void export(HttpServletResponse response, String fileName, List<List<String>> head, List<List<String>> data) {
        try {
            //使用swagger会导致各种问题，请直接用浏览器或者用postman
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            // 这里URLEncoder.encode可以防止中文乱码
            String urlName = URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");
            response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + urlName + ".xlsx");
            EasyExcel.write(response.getOutputStream())
                    .head(head)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet(fileName)
                    .doWrite(data);
        } catch (IOException e) {
            response.reset();
            response.setContentType("application/json");
            response.setCharacterEncoding("utf-8");
            Map<String, String> map = MapUtils.newHashMap();
            map.put("status", "failure");
            map.put("message", "下载文件失败" + e.getMessage());
            response.getWriter().println(JSON.toJSONString(map));
            log.info("导出{}失败:{}", fileName, e.getMessage());
        }
    }

    @SneakyThrows
    public static void export(HttpServletResponse response, String fileName, String value) {
        export(response, fileName, value.getBytes(StandardCharsets.UTF_8));
    }

    @SneakyThrows
    public static void export(HttpServletResponse response, String fileName, byte[] bytes) {
        String filename = URLEncoder.encode(fileName, "UTF-8");
        response.setContentType("application/x-download");
        response.setHeader("Content-Disposition", "attachment;filename=" + filename);
        ServletOutputStream outputStream = response.getOutputStream();
        outputStream.write(bytes);
    }


    @SneakyThrows
    public static void exportByPage(
            HttpServletResponse response,
            Class<?> clazz,
            String fileName,
            Function<Long, List<?>> dataProvider,
            Long pageSize,
            long total) {

        if (total <= 0) {
            // 无数据也导出空表
            export(response, clazz, java.util.Collections.emptyList(), fileName);
            return;
        }

        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString()).replaceAll("\\+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + encodedFileName + ".xlsx");

        ExcelWriter excelWriter = null;
        try (ServletOutputStream outputStream = response.getOutputStream()) {
            ExcelWriterBuilder builder = EasyExcel.write(outputStream, clazz)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .autoCloseStream(false); // 由我们控制 finish

            excelWriter = builder.build();
            WriteSheet writeSheet = EasyExcel.writerSheet(fileName).build();

            long exported = 0;
            long currentPage = 1L;

            while (exported < total) {
                List<?> pageData = dataProvider.apply(currentPage);
                if (pageData == null || pageData.isEmpty()) {
                    break;
                }

                excelWriter.write(pageData, writeSheet);
                exported += pageData.size();
                currentPage++;

                // 安全兜底：防止死循环
                if (currentPage > 100_000) break;
            }

            excelWriter.finish();
        } catch (Exception e) {
            log.error("流式导出失败: {}", fileName, e);
            response.reset();
            response.setContentType("application/json;charset=utf-8");
            Map<String, String> error = MapUtils.newHashMap();
            error.put("status", "failure");
            error.put("message", "导出失败: " + e.getMessage());
            response.getWriter().write(JSON.toJSONString(error));
        } finally {
            if (excelWriter != null) {
                try {
                    excelWriter.finish();
                } catch (Exception ignored) {
                }
            }
        }
    }

}
