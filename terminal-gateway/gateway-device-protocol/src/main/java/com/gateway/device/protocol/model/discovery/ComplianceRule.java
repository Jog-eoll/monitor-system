package com.gateway.device.protocol.model.discovery;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备合规校验规则 —— 字段名 + 期望值列表（命中任一即可）。
 *
 * <p>同字段内多个期望值为 OR 关系，多字段之间为 AND 关系。</p>
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceRule {
    /**
     * 字段名
     */
    private String field;
    /**
     * 期望值
     */
    private List<String> expected = new ArrayList<>();

    @Override
    public String toString() {
        return field + " IN " + expected;
    }
}
