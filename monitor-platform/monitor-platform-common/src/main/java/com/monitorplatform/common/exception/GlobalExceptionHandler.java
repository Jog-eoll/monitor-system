package com.monitorplatform.common.exception;

import com.monitorplatform.common.entity.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;


import javax.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 
 * 统一处理所有 Controller 层异常，消除重复的 try-catch 代码
 * 
 * @author monitor-platform
 * @date 2026-05-21
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * 处理业务异常
     * 
     * 用于 Service 层抛出的可预期业务异常
     * 例如：用户不存在、权限不足、数据已存在等
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常: uri={}, code={}, msg={}", request.getRequestURI(), e.getCode(), e.getMsg());
        return Result.fail(e.getCode(), e.getMsg());
    }
    
    /**
     * 处理参数校验异常（@Validated/@Valid）
     * 
     * 自动提取所有校验失败的字段信息，返回友好提示
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        String errorMsg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        
        log.warn("参数校验失败: uri={}, errors={}", request.getRequestURI(), errorMsg);
        return Result.fail(400, "参数校验失败: " + errorMsg);
    }
    
    /**
     * 处理缺少请求参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleMissingParamException(MissingServletRequestParameterException e, HttpServletRequest request) {
        log.warn("缺少请求参数: uri={}, param={}", request.getRequestURI(), e.getParameterName());
        return Result.fail(400, "缺少必要参数: " + e.getParameterName());
    }
    
    /**
     * 处理请求方法不支持异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleTypeMismatchException(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        log.warn("请求参数类型错误: uri={}, param={}, value={}", request.getRequestURI(), e.getName(), e.getValue());
        return Result.fail(400, "参数类型错误: " + e.getName());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<?> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        log.warn("请求方法不支持: uri={}, method={}", request.getRequestURI(), e.getMethod());
        return Result.fail(405, "不支持的请求方法: " + e.getMethod());
    }
    
    /**
     * 处理资源未找到异常（404）
     */
//    @ExceptionHandler(NoResourceFoundException.class)
//    @ResponseStatus(HttpStatus.NOT_FOUND)
//    public Result<?> handleNoResourceFoundException(NoResourceFoundException e, HttpServletRequest request) {
//        log.warn("资源未找到: uri={}", request.getRequestURI());
//        return Result.fail(404, "请求的资源不存在");
//    }
    
    /**
     * 处理通用异常
     * 
     * 捕获所有未预期的异常，返回统一的 500 错误
     * 注意：生产环境不应暴露详细异常信息给前端
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常: uri={}", request.getRequestURI(), e);
        return Result.fail(500, "系统异常，请联系管理员");
    }
}
