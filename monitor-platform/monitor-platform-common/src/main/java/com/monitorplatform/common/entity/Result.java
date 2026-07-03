package com.monitorplatform.common.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.monitorplatform.common.constant.IocConstant;
import com.monitorplatform.common.protocol.IResultCode;
import com.monitorplatform.common.protocol.ResultCode;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.Getter;

import java.io.Serializable;


/**
 * 返回结果类
 * @author LIQIU
 */
@Data
@Getter
@ApiModel(value = "Result",description = "统一响应消息报文")
public class Result<T> implements Serializable {
	
	private static final long serialVersionUID = 1L;

//    // 返回是否成功
//    private Boolean success = false;


    @ApiModelProperty(value = "消息内容", required = true)
	private String msg= "操作成功";

	@ApiModelProperty(value = "状态码", required = true)
	private int code;
    
    @ApiModelProperty(value = "时间戳", required = true)
	private long time;

	@ApiModelProperty(value = "业务数据")
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private T data;


    private Result() {
		this.time = System.currentTimeMillis();
	}

	private Result(IResultCode resultCode) {
		this(resultCode, null, resultCode.getMsg());
	}

	private Result(IResultCode resultCode, String msg) {
		this(resultCode, null, msg);
	}

	private Result(IResultCode resultCode, T data) {
		this(resultCode, data, resultCode.getMsg());
	}

	private Result(IResultCode resultCode, T data, String msg) {
		this(resultCode.getCode(), data, msg);
	}

	private Result(int code, T data, String msg) {
		this.code = code;
		this.data = data;
		this.msg = msg;
		this.time = System.currentTimeMillis();
	}

//    private Result(Boolean success, String msg, Integer code, T data) {
//        this.success = success;
//        this.msg = msg;
//        this.code = code;
//        this.data = data;
//    }

	/**
	 * 返回状态码
	 *
	 * @param resultCode 状态码
	 * @param <T>        泛型标识
	 * @return ApiResult
	 */
	public static <T> Result<T> success(IResultCode resultCode) {
		return new Result<>(resultCode);
	}

	public static <T> Result<T> success() {
		return success("success");
	}

	public static <T> Result<T> success(String msg) {
		return new Result<>(ResultCode.SUCCESS, msg);
	}

	public static <T> Result<T> success(IResultCode resultCode, String msg) {
		return new Result<>(resultCode, msg);
	}

	public static <T> Result<T> data(T data) {
		return data(data, IocConstant.DEFAULT_SUCCESS_MESSAGE);
	}

	public static <T> Result<T> data(T data, String msg) {
		return data(ResultCode.SUCCESS.getCode(), data, msg);
	}

	public static <T> Result<T> data(int code, T data, String msg) {
		return new Result<>(code, data, data == null ? IocConstant.DEFAULT_NULL_MESSAGE : msg);
	}
	
	public static <T> Result<T> fail() {
		return new Result<>(ResultCode.FAILURE, ResultCode.FAILURE.getMsg());
	}

	public static <T> Result<T> fail(String msg) {
		return new Result<>(ResultCode.FAILURE, msg);
	}

	public static <T> Result<T> fail(int code, String msg) {
		return new Result<>(code, null, msg);
	}

	public static <T> Result<T> fail(IResultCode resultCode) {
		return new Result<>(resultCode);
	}

	public static <T> Result<T> fail(IResultCode resultCode, String msg) {
		return new Result<>(resultCode, msg);
	}

	public static <T> Result<T> condition(boolean flag) {
		return flag ? success(IocConstant.DEFAULT_SUCCESS_MESSAGE) : fail(IocConstant.DEFAULT_FAIL_MESSAGE);
	}

	// ==================== 向下兼容方法（兼容旧的 util.Result API）====================

	/**
	 * 兼容旧的 Result.success(msg, data) 方法
	 * @deprecated 建议使用 Result.data(data, msg)
	 */
	@Deprecated
	public static <T> Result<T> success(String msg, T data) {
		return data(data, msg);
	}

	/**
	 * 兼容旧的 Result.success(data) 方法（返回 Map 时的用法）
	 * @deprecated 建议使用 Result.data(data)
	 */
	@Deprecated
	public static <T> Result<T> success(T data) {
		return data(data);
	}

	/**
	 * 兼容旧的 Result.error(msg) 方法
	 * @deprecated 建议使用 Result.fail(msg)
	 */
	@Deprecated
	public static <T> Result<T> error(String msg) {
		return fail(msg);
	}

	/**
	 * 兼容旧的 Result.error(msg, exception) 方法
	 * 注意：此方法会记录日志并返回失败结果
	 * @deprecated 建议使用全局异常处理器，不再需要手动捕获异常
	 */
	@Deprecated
	public static <T> Result<T> error(String msg, Exception e) {
		// 记录日志（虽然全局异常处理器也会记录，但这里保留以兼容旧代码）
		org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Result.class);
		log.error(msg, e);
		return fail(msg + ": " + e.getMessage());
	}

	/**
	 * 兼容旧的 Result.build(code, msg, data) 方法
	 * @deprecated 建议使用 Result.data(code, data, msg) 或 Result.fail(code, msg)
	 */
	@Deprecated
	public static <T> Result<T> build(int code, String msg, T data) {
		return new Result<>(code, data, msg);
	}

	/**
	 * 兼容旧的 Result.buildData() 方法
	 * @deprecated 建议直接使用 new HashMap<>()
	 */
	@Deprecated
	public static java.util.Map<String, Object> buildData() {
		return new java.util.HashMap<>();
	}

	/**
	 * 兼容旧的 Result.buildData(key, value) 方法
	 * @deprecated 建议直接使用 Map.of() 或手动创建 HashMap
	 */
	@Deprecated
	public static java.util.Map<String, Object> buildData(String key, Object value) {
		java.util.Map<String, Object> data = new java.util.HashMap<>();
		data.put(key, value);
		return data;
	}

//    /**
//     * 构建返回结果
//     * @param success
//     * @param msg
//     * @param code
//     * @param data
//     * @return
//     */
//	public static Result build(Boolean success, String msg, Integer code,Object data){
//        return new Result(success,msg,code,data);
//    }
//
//    /**
//     * 构建返回结果，code默认值为0
//     * @param success
//     * @param msg
//     * @param data
//     * @return
//     */
//    public static Result build(Boolean success,String msg,Object data){
//        return build(success,msg,0,data);
//    }
//
//    /**
//     *构建成功结果
//     * @param msg
//     * @param data
//     * @return
//     */
//    public static Result buildSuccess(String msg,Object data) {
//        return build(Boolean.TRUE,msg,data );
//    }
//
//    /**
//     * 构建失败结果
//     * @param msg
//     * @param data
//     * @return
//     */
//    public static Result buildFailure(Integer code,String msg,Object data){
//        return build(Boolean.FALSE,msg ,code,data);
//    }
//
//    /**
//     * 构建失败结果
//     * @param msg
//     * @param data
//     * @return
//     */
//    public static Result buildFailure(String msg,Object data){
//        return build(Boolean.FALSE,msg ,data);
//    }
//
//    /**
//     * 构建成功结果带信息
//     * @param msg
//     * @return
//     */
//    public static Result buildSuccess(String msg){
//        return buildSuccess(msg,null);
//    }
//
//    /**
//     * 构建成功结果待数据
//     * @param data
//     * @return
//     */
//    public static Result buildSuccess(Object data){
//        return buildSuccess(null,data);
//    }
//
//    /**
//     * 构建失败结果待数据
//     * @param msg
//     * @return
//     */
//    public static Result buildFailure(String msg){
//        return buildFailure(msg,null);
//    }
//
//    /**
//     * 构建失败结果待数据
//     * @param code
//     * @param msg
//     * @return
//     */
//    public static Result buildFailure(Integer code,String msg){
//        return build(Boolean.FALSE,msg,code,null);
//    }
//
//    /**
//     * 构建失败结果待数据
//     * @param status
//     * @return
//     */
//    public static Result buildFailure(ErrorStatus status){
//        return buildFailure(status.value(),status.getMessage());
//    }
//
//    /**
//     * 构建失败结果待数据
//     * @param status
//     * @return
//     */
//    public static Result buildFailure(ErrorStatus status,Object data){
//        return buildFailure(status.value(),status.getMessage(),data);
//    }
//
//    /**
//     * 构建失败结果带数据
//     * @param data
//     * @return
//     */
//    public static Result buildFailure(Object data){
//        return buildFailure("",data);
//    }
}

