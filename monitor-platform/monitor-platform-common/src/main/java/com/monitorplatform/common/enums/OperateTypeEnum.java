package com.monitorplatform.common.enums;

import com.monitorplatform.common.annotation.OperateLog;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 操作日志的操作类型
 *
 * @author ruoyi
 */
@Getter
@AllArgsConstructor
public enum OperateTypeEnum {

    /**
     * 查询
     * <p>
     * 绝大多数情况下，不会记录查询动作，因为过于大量显得没有意义。
     * 在有需要的时候，通过声明 {@link OperateLog} 注解来记录
     */
    GET("query"),
    /**
     * 新增
     */
    CREATE("add"),
    /**
     * 修改
     */
    UPDATE("update"),
    /**
     * 删除
     */
    DELETE("delete"),
    /**
     * 导出
     */
    EXPORT("export"),
    /**
     * 导入
     */
    IMPORT("import"),
    /**
     * 其它
     * <p>
     * 在无法归类时，可以选择使用其它。因为还有操作名可以进一步标识
     */
    OTHER("other"),


    LOGIN("login"),

    LOGOUT("logout"),

    /**
     * 签名
     */
    SIGN("sign"),

    /**
     * 验签
     */
    VERIFY("verify"),

    /**
     * 发节目
     */
    PUBLISH("publish"),   // 发节目
    ;


    /**
     * 类型
     */
    private final String type;

}
