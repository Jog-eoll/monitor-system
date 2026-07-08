package com.gateway.device.transport.netty.common;

import com.gateway.device.transport.netty.NettyTransportConfig;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

/**
 * Netty 共享资源管理器 —— 统一 EventLoopGroup + 超时调度 + TCP 响应等待线程池的生命周期。
 *
 * <p>UDP/TCP/HTTP 三种传输实现共享同一个 EventLoopGroup，避免线程资源冗余。</p>
 */
@Slf4j
public class EventLoopResources implements AutoCloseable {

    private final EventLoopGroup eventLoopGroup;
    private final ScheduledExecutorService timeoutScheduler;
    private final ExecutorService responseExecutor;

    public EventLoopResources(NettyTransportConfig config) {
        int workers = config.getWorkerThreads() > 0
                ? config.getWorkerThreads()
                : Runtime.getRuntime().availableProcessors() * 2;
        this.eventLoopGroup = new NioEventLoopGroup(workers);

        this.timeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "transport-timeout");
            t.setDaemon(true);
            return t;
        });

        int core = config.getResponsePoolCore() > 0
                ? config.getResponsePoolCore()
                : Runtime.getRuntime().availableProcessors();
        this.responseExecutor = new ThreadPoolExecutor(
                core,
                config.getResponsePoolMax(),
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(config.getResponsePoolQueue()),
                r -> {
                    Thread t = new Thread(r, "netty-tcp-response");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());

        log.info("Netty 共享资源已初始化: workerThreads={} timeoutScheduler=daemon responsePool={}/{}",
                workers, core, config.getResponsePoolMax());
    }

    public EventLoopGroup eventLoopGroup() {
        return eventLoopGroup;
    }

    public ScheduledExecutorService timeoutScheduler() {
        return timeoutScheduler;
    }

    /**
     * TCP 响应阻塞等待线程池 —— 仅 TcpTransport 使用，HTTP/UDP 不需要。
     */
    public ExecutorService responseExecutor() {
        return responseExecutor;
    }

    @Override
    public void close() {
        log.info("关闭 Netty 共享资源...");
        timeoutScheduler.shutdown();
        responseExecutor.shutdown();
        eventLoopGroup.shutdownGracefully();
    }
}
