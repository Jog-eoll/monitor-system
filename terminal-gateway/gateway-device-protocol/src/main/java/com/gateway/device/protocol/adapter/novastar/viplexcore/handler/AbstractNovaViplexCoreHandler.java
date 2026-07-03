package com.gateway.device.protocol.adapter.novastar.viplexcore.handler;

import com.gateway.device.protocol.api.CapabilityHandler;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexCoreChannel;
import com.gateway.device.protocol.base.novastar.viplexcore.builder.ViplexProgramPipeline;
import com.gateway.device.protocol.base.novastar.viplexcore.text.NovaViplexCoreTextStyle;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * ViplexCore SDK 能力处理器抽象基类 —— 纯基类，无模板方法。
 *
 * <p>子类通过构造器注入 SDK 通道、节目管线、文本样式，
 * 自行实现 {@link #execute(DeviceContext, CommandParams)} 完成具体能力。</p>
 *
 * @param <P> 本能力对应的参数类型
 */
@Slf4j
public abstract class AbstractNovaViplexCoreHandler<P extends CommandParams> implements CapabilityHandler<P> {

    private final ViplexCoreChannel channel;
    private final ViplexProgramPipeline pipeline;
    private final NovaViplexCoreTextStyle textStyle;

    protected AbstractNovaViplexCoreHandler(
            ViplexCoreChannel channel,
            ViplexProgramPipeline pipeline,
            NovaViplexCoreTextStyle textStyle) {
        this.channel = channel;
        this.pipeline = pipeline;
        this.textStyle = textStyle;
    }

    /**
     * SDK 通道（通过 Channel 抽象调用，无 JNA 依赖）
     */
    protected ViplexCoreChannel channel() {
        return channel;
    }

    /**
     * 节目管线
     */
    protected ViplexProgramPipeline pipeline() {
        return pipeline;
    }

    /**
     * 文本样式配置
     */
    protected NovaViplexCoreTextStyle textStyle() {
        return textStyle;
    }

    /**
     * 默认超时 10 秒
     */
    protected Duration getTimeout() {
        return Duration.ofSeconds(10);
    }

    @Override
    public abstract CommandResult execute(DeviceContext device, P params);
}
