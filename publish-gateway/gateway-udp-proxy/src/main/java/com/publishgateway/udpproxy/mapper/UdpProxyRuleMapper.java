package com.publishgateway.udpproxy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.publishgateway.udpproxy.entity.UdpProxyRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * UDP代理规则Mapper
 */
@Mapper
public interface UdpProxyRuleMapper extends BaseMapper<UdpProxyRule> {
}
