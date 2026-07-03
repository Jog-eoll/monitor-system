package com.infopublish.client.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.infopublish.client.entity.UdpProxyRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * UDP代理规则Mapper（与 publish-gateway 一致）
 */
@Mapper
public interface UdpProxyRuleMapper extends BaseMapper<UdpProxyRule> {
}
