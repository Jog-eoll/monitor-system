package com.publishgateway.udpproxy.forward;

import com.publishgateway.udpproxy.entity.UdpProxyRule;
import com.publishgateway.udpproxy.service.CryptoPacketStore;
import com.publishgateway.udpproxy.service.CryptoService;
import com.publishgateway.udpproxy.service.DataReportService;
import com.publishgateway.udpproxy.service.TranscodeService;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全端口透明 TCP 代理（发布网关版本）
 *
 * <pre>
 * 解决问题：
 *   Nova 大屏通过 16602/16606 控制通道动态协商随机 TCP 端口（如 43592, 44993）传输文件。
 *   这些端口无法预知，需要在网络层拦截所有发往大屏 IP 的未知 TCP 端口并加密转发。
 *
 * 工作原理（iptables REDIRECT + SO_ORIGINAL_DST）：
 *   1. iptables nat/PREROUTING REDIRECT 将发往大屏 IP 的所有未知 TCP 端口重定向到本代理
 *   2. 通过 JNA getsockopt(SO_ORIGINAL_DST) 获取连接的原始目标端口
 *   3. 加密后转发到终端网关的 CatchAll 端口，先发 2 字节端口号握手
 *
 * 数据流：
 *   PC:random → [iptables REDIRECT] → 本代理(catch-all:19999)
 *       → [2B端口+加密数据] → 终端网关 CatchAll → 大屏:originalPort
 *
 * 隔离保证：
 *   - 仅当 dynamicPortProxyEnabled=true 时启动（只有 Nova 设备）
 *   - iptables 规则绑定大屏 IP，不影响其他厂商（Sigma 等）
 *
 * 与 TPROXY 方案的区别（为什么选 REDIRECT）：
 *   - REDIRECT 仅需 nat 表，无需策略路由（ip rule/ip route），配置简单
 *   - REDIRECT 使用标准 NIO（无需 Epoll 原生传输 / IP_TRANSPARENT socket option）
 *   - REDIRECT 后通过 SO_ORIGINAL_DST（Linux socket option 80）恢复原始目标端口
 *   - 在 Docker (network_mode:host, privileged:true) 环境中兼容性更好
 * </pre>
 */
@Slf4j
public class CatchAllTcpProxyServer {

    /**
     * CatchAll 加密开关（默认关闭）
     *
     * ★ 为什么默认关闭：
     *   动态端口传输文件数据时，每个 ~1460B 的 TCP 段都要 encrypt/decrypt，
     *   SM4/AES 每次调用有密码器初始化+块填充的固定开销，数百个包累积后
     *   将文件传输时间从秒级拉长到分钟级，导致屏精灵超时报错。
     *   控制通道 16606 已有 TLS 保护敏感指令，动态端口仅传图片文件数据。
     *
     * ★ 不影响 Sigma：
     *   Sigma 走 TcpProxyServer（静态端口代理），不经过 CatchAll。
     *   CatchAll 仅当 dynamicPortProxyEnabled=true 时启动（只有 Nova 设备）。
     *
     * 如需恢复加密，改为 true 即可（但文件传输速度会显著下降）。
     */
    private static final boolean CATCHALL_ENCRYPT_ENABLED = true;
    private static final int MAX_CONTENT_CAPTURE_BYTES = 200 * 1024 * 1024;

    private final UdpProxyRule rule;
    private final CryptoService cryptoService;
    private final CryptoPacketStore cryptoPacketStore;
    private final DataReportService dataReportService;
    private final boolean transcodeEnabled;
    private final TranscodeService transcodeService;

    /** catch-all 监听端口（默认 19999） */
    private final int catchAllPort;

    /** 终端网关 catch-all 端口（与本端口相同） */
    private final int terminalCatchAllPort;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private volatile boolean running = false;

    private final Map<String, Channel> outboundChannels = new ConcurrentHashMap<>();

    /** iptables 规则已排除的静态端口（16606, 16602 等） */
    private String excludedPorts;

    /**
     * 检测到的 iptables 命令（iptables-legacy 或 iptables）
     *
     * ★ 核心问题说明：
     *   Docker 使用 iptables-legacy (xtables) 后端管理 nat 规则。
     *   Debian Buster+ 容器内的 iptables 默认指向 iptables-nft (nftables) 后端。
     *   iptables-legacy 和 iptables-nft 在内核中是两个独立的规则后端，互不可见。
     *   如果通过 iptables-nft 添加 REDIRECT 规则，虽然命令返回成功（exit 0），
     *   但规则写入了 nft 后端，而数据包走的是 Docker 所在的 legacy 后端处理链路，
     *   导致 REDIRECT 永远不会被命中。
     *   必须使用 iptables-legacy 确保规则与 Docker 在同一后端。
     */
    private String iptablesCmd;

    // ── JNA getsockopt(SO_ORIGINAL_DST) ─────────────────────────────

    /**
     * JNA C 库接口：调用 getsockopt 获取 iptables REDIRECT 前的原始目标地址
     *
     * 技术依据：iptables REDIRECT 修改了目标地址（变为 localhost:catchAllPort），
     * 但内核 conntrack 仍记录原始目标地址。通过 getsockopt(SOL_IP, SO_ORIGINAL_DST)
     * 可从已接受的 socket fd 上恢复原始目标的 sockaddr_in。
     */
    private interface CLib extends Library {
        CLib INSTANCE = Native.load("c", CLib.class);
        int getsockopt(int sockfd, int level, int optname, byte[] optval, IntByReference optlen);
    }

    /** SOL_IP = 0 (Linux, include/uapi/linux/in.h) */
    private static final int SOL_IP = 0;
    /** SO_ORIGINAL_DST = 80 (Linux, include/uapi/linux/netfilter_ipv4.h) */
    private static final int SO_ORIGINAL_DST = 80;

    public CatchAllTcpProxyServer(UdpProxyRule rule, CryptoService cryptoService,
                                   CryptoPacketStore cryptoPacketStore,
                                   DataReportService dataReportService,
                                   boolean transcodeEnabled, TranscodeService transcodeService,
                                   int catchAllPort) {
        this.rule = rule;
        this.cryptoService = cryptoService;
        this.cryptoPacketStore = cryptoPacketStore;
        this.dataReportService = dataReportService;
        this.transcodeEnabled = transcodeEnabled;
        this.transcodeService = transcodeService;
        this.catchAllPort = catchAllPort;
        this.terminalCatchAllPort = catchAllPort;
    }

    /**
     * 启动 CatchAll 透明代理
     *
     * 必要性：Nova 大屏使用随机动态端口传输文件，iptables REDIRECT 在内核层拦截
     * 所有未知 TCP 端口并重定向到本代理，通过 SO_ORIGINAL_DST 恢复原始端口号。
     */
    public void start() {
        if (running) {
            log.warn("CatchAll TCP 代理已在运行，规则ID: {}", rule.getRuleId());
            return;
        }

        // 使用标准 NIO（REDIRECT 模式不需要 Epoll 的 IP_TRANSPARENT）
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 256)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new CatchAllProxyHandler());
                        }
                    });

            ChannelFuture future = b.bind(catchAllPort).sync();
            serverChannel = future.channel();
            running = true;

            // 设置 iptables REDIRECT 规则
            setupIptables();

            log.info("==========================================");
            log.info("  CatchAll TCP 透明代理启动成功（发布网关）");
            log.info("  规则ID: {}", rule.getRuleId());
            log.info("  catch-all 端口: {}", catchAllPort);
            log.info("  拦截目标IP: {}", rule.getListenIp());
            log.info("  终端网关: {}:{}", rule.getTerminalGatewayIp(), terminalCatchAllPort);
            log.info("  排除端口: {}", excludedPorts);
            log.info("==========================================");

        } catch (Throwable t) {
            log.error("CatchAll TCP 代理启动失败，规则ID: {}", rule.getRuleId(), t);
            stop();
        }
    }

    /**
     * 停止 CatchAll 透明代理并清理 iptables 规则
     */
    public void stop() {
        if (!running) return;
        running = false;

        // 先清理 iptables 规则
        cleanupIptables();

        // 关闭所有出站连接
        outboundChannels.values().forEach(ch -> {
            if (ch != null && ch.isActive()) {
                try { ch.close().sync(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        outboundChannels.clear();

        if (serverChannel != null) {
            try { serverChannel.close().sync(); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (workerGroup != null) {
            try { workerGroup.shutdownGracefully().sync(); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (bossGroup != null) {
            try { bossGroup.shutdownGracefully().sync(); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        log.info("==========================================");
        log.info("  CatchAll TCP 透明代理已停止");
        log.info("  规则ID: {}", rule.getRuleId());
        log.info("==========================================");
    }

    public boolean isRunning() {
        return running;
    }

    // ── iptables REDIRECT 管理 ─────────────────────────────────────

    /**
     * 检测正确的 iptables 命令
     *
     * 检测逻辑：
     *   1. 优先使用 iptables-legacy（Docker 使用 legacy 后端，nft 后端的规则无法拦截数据包）
     *   2. 若 iptables-legacy 不存在，退回使用 iptables（可能是 nft 或 legacy）
     *   3. 记录检测到的命令及版本信息，便于排查
     */
    private String resolveIptablesCmd() {
        if (iptablesCmd != null) return iptablesCmd;

        // 优先检测 iptables-legacy
        if (isCommandAvailable("iptables-legacy")) {
            iptablesCmd = "iptables-legacy";
        } else if (isCommandAvailable("iptables")) {
            iptablesCmd = "iptables";
        } else {
            iptablesCmd = "iptables"; // 最终回退
            log.error("CatchAll: 未找到任何 iptables 命令！");
            return iptablesCmd;
        }

        // 记录版本信息
        String version = getCommandOutput(iptablesCmd + " --version");
        log.info("CatchAll: 检测到 iptables 命令: {}, 版本: {}", iptablesCmd, version);
        return iptablesCmd;
    }

    private boolean isCommandAvailable(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", "which " + cmd + " 2>/dev/null"});
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String getCommandOutput(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd + " 2>&1"});
            p.waitFor();
            java.io.InputStream is = p.getInputStream();
            byte[] buf = new byte[1024];
            int len = is.read(buf);
            return len > 0 ? new String(buf, 0, len).trim() : "(无输出)";
        } catch (Exception e) {
            return "(执行失败: " + e.getMessage() + ")";
        }
    }

    /**
     * 设置 iptables REDIRECT 规则（nat 表）
     *
     * 技术依据：
     *   REDIRECT 在 nat/PREROUTING 链将匹配的数据包目标端口改为 catch-all 端口，
     *   原始目标地址由内核 conntrack 记录，可通过 SO_ORIGINAL_DST 恢复。
     *   相比 TPROXY：无需 mangle 表、无需策略路由、无需 IP_TRANSPARENT。
     *
     * 规则逻辑：
     *   所有目标为大屏 IP 的 TCP 连接，排除已知静态端口（16606, 16602, 19999），
     *   其余全部 REDIRECT 到 catch-all 端口（19999）。
     *
     * 关键注意事项：
     *   1. 使用 -I PREROUTING 1（插入链首），避免被 Docker 的 -j DOCKER 规则抢先匹配
     *   2. 使用 iptables-legacy（而非默认的 iptables-nft），确保规则与 Docker 在同一后端
     */
    private void setupIptables() {
        String displayIp = rule.getListenIp();
        if (displayIp == null || displayIp.isEmpty()) {
            log.warn("CatchAll: listenIp 为空，跳过 iptables 配置");
            return;
        }

        // 构建排除端口列表（主端口 + 附加端口 + CatchAll端口自身）
        StringBuilder excludePorts = new StringBuilder();
        excludePorts.append(rule.getListenPort()); // 如 16606
        if (rule.getAdditionalTcpPorts() != null && !rule.getAdditionalTcpPorts().isEmpty()) {
            excludePorts.append(",").append(rule.getAdditionalTcpPorts()); // 如 16602
        }
        excludePorts.append(",").append(catchAllPort); // 排除 CatchAll 端口自身
        this.excludedPorts = excludePorts.toString();

        // 检测正确的 iptables 命令（iptables-legacy 优先）
        String ipt = resolveIptablesCmd();

        // 使用 -I PREROUTING 1 插入到链首
        String redirectCmd = String.format(
                "%s -t nat -I PREROUTING 1 -d %s -p tcp -m multiport ! --dports %s -j REDIRECT --to-ports %d",
                ipt, displayIp, excludedPorts, catchAllPort);

        int exitCode = executeCommand(redirectCmd);
        if (exitCode == 0) {
            log.info("CatchAll: iptables REDIRECT 规则已通过 {} 插入到 PREROUTING 链首", ipt);
            log.info("CatchAll: displayIp={}, excludePorts={}, catchAllPort={}", displayIp, excludedPorts, catchAllPort);
        } else if (exitCode == 127) {
            log.error("==========================================");
            log.error("  ⚠️  {} 命令不存在！CatchAll 流量重定向无法生效！", ipt);
            log.error("  请在宿主机手动执行以下命令：");
            log.error("  {}", redirectCmd);
            log.error("==========================================");
        } else {
            log.warn("CatchAll: iptables REDIRECT 规则设置失败（exitCode={}），命令: {}", exitCode, redirectCmd);
        }

        // 诊断：同时列出 legacy 和 nft 两个后端的规则，帮助定位后端不匹配问题
        logIptablesRules();
    }

    /**
     * 诊断辅助：同时列出 iptables-legacy 和 iptables(nft) 两个后端的 PREROUTING 规则
     *
     * 目的：
     *   1. 确认 REDIRECT 规则是否在正确的后端（legacy）
     *   2. 确认 REDIRECT 规则是否在链首
     *   3. 如果规则只出现在 nft 后端而不在 legacy 后端，说明后端不匹配
     */
    private void logIptablesRules() {
        String[] variants = {"iptables-legacy", "iptables"};
        for (String variant : variants) {
            try {
                String cmd = variant + " -t nat -S PREROUTING 2>&1";
                Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
                process.waitFor();
                java.io.InputStream is = process.getInputStream();
                byte[] output = new byte[8192];
                int len = is.read(output);
                if (len > 0) {
                    String rules = new String(output, 0, len).trim();
                    log.info("CatchAll: [{}] nat/PREROUTING 链规则:\n{}", variant, rules);
                } else {
                    log.info("CatchAll: [{}] nat/PREROUTING 链规则: (无输出或命令不存在)", variant);
                }
            } catch (Exception e) {
                log.info("CatchAll: [{}] 不可用: {}", variant, e.getMessage());
            }
        }
    }

    /**
     * 清理 iptables REDIRECT 规则
     */
    private void cleanupIptables() {
        String displayIp = rule.getListenIp();
        if (displayIp == null || displayIp.isEmpty()) return;
        if (excludedPorts == null || excludedPorts.isEmpty()) return;

        String ipt = resolveIptablesCmd();
        String cmd = String.format(
                "%s -t nat -D PREROUTING -d %s -p tcp -m multiport ! --dports %s -j REDIRECT --to-ports %d 2>/dev/null || true",
                ipt, displayIp, excludedPorts, catchAllPort);
        executeCommand(cmd);
        log.info("CatchAll: iptables REDIRECT 规则已通过 {} 清理", ipt);
    }

    /**
     * 执行 shell 命令
     * @return 命令退出码，-1 表示执行异常
     */
    private int executeCommand(String command) {
        try {
            log.info("CatchAll: 执行命令: {}", command);
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("CatchAll: 命令执行返回非零状态: {}，命令: {}", exitCode, command);
            }
            return exitCode;
        } catch (Exception e) {
            log.error("CatchAll: 命令执行失败: {}", command, e);
            return -1;
        }
    }

    // ── SO_ORIGINAL_DST 获取原始目标端口 ────────────────────────────

    /**
     * 通过 getsockopt(SO_ORIGINAL_DST) 获取 iptables REDIRECT 前的原始目标端口
     *
     * 技术依据：
     *   iptables REDIRECT 将数据包的目标地址改为 localhost:catchAllPort，
     *   但内核 conntrack 模块仍保留原始目标地址（如 192.168.113.60:43592）。
     *   通过 getsockopt(SOL_IP=0, SO_ORIGINAL_DST=80) 可从已接受的 socket 上
     *   读取 struct sockaddr_in 格式的原始目标地址。
     *
     * sockaddr_in 结构（16 字节）：
     *   [0-1] sin_family  (AF_INET=2)
     *   [2-3] sin_port    (网络字节序，大端)
     *   [4-7] sin_addr    (IP 地址)
     *   [8-15] sin_zero   (填充)
     *
     * @param channel Netty NioSocketChannel（已接受的入站连接）
     * @return 原始目标端口号，失败返回 -1
     */
    private int getOriginalDstPort(Channel channel) {
        try {
            int fd = getChannelFd(channel);
            if (fd < 0) {
                log.error("【CatchAll发布网关】无法获取 socket fd");
                return -1;
            }

            byte[] optval = new byte[16];
            IntByReference optlen = new IntByReference(16);
            int ret = CLib.INSTANCE.getsockopt(fd, SOL_IP, SO_ORIGINAL_DST, optval, optlen);
            if (ret != 0) {
                log.error("【CatchAll发布网关】getsockopt(SO_ORIGINAL_DST) 失败，返回: {}", ret);
                return -1;
            }

            // 从 sockaddr_in 解析端口号（字节 2-3，网络字节序/大端序）
            int port = ((optval[2] & 0xFF) << 8) | (optval[3] & 0xFF);

            // 解析原始目标 IP（字节 4-7）用于日志
            String originalIp = String.format("%d.%d.%d.%d",
                    optval[4] & 0xFF, optval[5] & 0xFF, optval[6] & 0xFF, optval[7] & 0xFF);
            log.info("【CatchAll发布网关】SO_ORIGINAL_DST 获取原始目标: {}:{}", originalIp, port);

            return port;
        } catch (Throwable t) {
            log.error("【CatchAll发布网关】获取原始目标端口失败", t);
            return -1;
        }
    }

    /**
     * 通过反射获取 Netty NioSocketChannel 底层的 Linux socket fd
     *
     * 反射路径：
     *   NioSocketChannel → AbstractNioChannel.javaChannel() → java.nio.channels.SocketChannel
     *   → sun.nio.ch.SocketChannelImpl.fdVal (int 类型的 fd 缓存)
     *
     * @return Linux socket fd，失败返回 -1
     */
    private static int getChannelFd(Channel channel) {
        try {
            // 步骤1：通过反射调用 AbstractNioChannel.javaChannel() 获取 Java NIO SocketChannel
            Method javaChannelMethod = null;
            Class<?> clazz = channel.getClass();
            while (clazz != null) {
                try {
                    javaChannelMethod = clazz.getDeclaredMethod("javaChannel");
                    break;
                } catch (NoSuchMethodException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            if (javaChannelMethod == null) {
                log.error("【CatchAll发布网关】无法找到 javaChannel() 方法");
                return -1;
            }
            javaChannelMethod.setAccessible(true);
            Object javaChannel = javaChannelMethod.invoke(channel);

            // 步骤2：从 sun.nio.ch.SocketChannelImpl 获取 fdVal 字段
            Field fdValField = javaChannel.getClass().getDeclaredField("fdVal");
            fdValField.setAccessible(true);
            return fdValField.getInt(javaChannel);
        } catch (Exception e) {
            log.error("【CatchAll发布网关】反射获取 fd 失败", e);
            return -1;
        }
    }

    // ── 代理处理器 ─────────────────────────────────────────────

    /**
     * CatchAll 代理处理器
     *
     * 每条入站连接（PC → iptables REDIRECT → 本代理）对应一条出站连接（本代理 → 终端网关 catch-all 端口）。
     * 连接建立时：
     *   1. 通过 SO_ORIGINAL_DST 获取 iptables REDIRECT 前的原始目标端口
     *   2. 建立到终端网关 catch-all 端口的 TCP 连接
     *   3. 发送 2 字节大端序端口号作为握手
     *   4. 后续数据按 [4B密文长度][密文块] 格式加密转发
     */
    private class CatchAllProxyHandler extends ChannelInboundHandlerAdapter {

        private volatile Channel outboundChannel;
        private int originalPort;
        private final ByteArrayOutputStream contentCaptureBuffer = new ByteArrayOutputStream(64 * 1024);
        private int contentCaptureBytes;
        private boolean contentCaptureOverflow;
        private String sourceIp;

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            // 通过 SO_ORIGINAL_DST 获取原始目标端口（iptables REDIRECT 前的真实目标）
            originalPort = getOriginalDstPort(ctx.channel());
            if (originalPort <= 0) {
                log.error("【CatchAll发布网关】无法获取原始目标端口，关闭连接");
                ctx.close();
                return;
            }
            // 安全检查：如果原始端口就是 catch-all 端口，说明不是 REDIRECT 流量
            if (originalPort == catchAllPort) {
                log.warn("【CatchAll发布网关】原始端口等于 catch-all 端口（{}），非 REDIRECT 流量，关闭", catchAllPort);
                ctx.close();
                return;
            }

            InetSocketAddress sender = (InetSocketAddress) ctx.channel().remoteAddress();
            if (sender != null && sender.getAddress() != null) {
                sourceIp = sender.getAddress().getHostAddress();
            }
            log.info("【CatchAll发布网关】新连接 - 来源: {}, 原始目标端口: {}", sender, originalPort);

            // 暂停读取，等出站连接建立后再读
            ctx.channel().config().setAutoRead(false);

            // 确定终端网关地址
            String targetIp = rule.getTerminalGatewayIp();
            int targetPort = terminalCatchAllPort;

            // 建立到终端网关 catch-all 端口的出站连接（使用标准 NIO）
            Bootstrap outBoot = new Bootstrap();
            outBoot.group(ctx.channel().eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            // 加密模式：终端网关响应使用 [4B长度][密文] 格式，需拆帧+解密
                            if (CATCHALL_ENCRYPT_ENABLED && Boolean.TRUE.equals(rule.getEncryptEnabled())) {
                                ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(
                                        10 * 1024 * 1024, 0, 4, 0, 4));
                            }
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx2, Object msg) {
                                    ByteBuf buf = (ByteBuf) msg;
                                    try {
                                        if (CATCHALL_ENCRYPT_ENABLED && Boolean.TRUE.equals(rule.getEncryptEnabled())) {
                                            // 加密模式：解密终端网关的响应后回传给 PC
                                            byte[] responseData = new byte[buf.readableBytes()];
                                            buf.readBytes(responseData);
                                            byte[] decrypted = cryptoService.decrypt(responseData);
                                            if (decrypted != null) {
                                                ctx.channel().writeAndFlush(Unpooled.copiedBuffer(decrypted));
                                            } else {
                                                log.error("【CatchAll发布网关】响应解密失败，丢弃");
                                            }
                                        } else {
                                            // 透明模式：零拷贝直接回传给 PC
                                            if (ctx.channel().isActive()) {
                                                ctx.channel().writeAndFlush(buf.retain());
                                            }
                                        }
                                    } finally {
                                        buf.release();
                                    }
                                }

                                @Override
                                public void channelInactive(ChannelHandlerContext ctx2) {
                                    ctx.close();
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx2, Throwable cause) {
                                    log.error("【CatchAll发布网关】出站通道异常", cause);
                                    ctx2.close();
                                }
                            });
                        }
                    });

            outBoot.connect(targetIp, targetPort).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    outboundChannel = future.channel();
                    outboundChannels.put(ctx.channel().id().asShortText(), outboundChannel);

                    // 发送 2 字节握手：告知终端网关原始目标端口
                    ByteBuf handshake = Unpooled.buffer(2);
                    handshake.writeShort(originalPort);
                    outboundChannel.writeAndFlush(handshake).addListener((ChannelFutureListener) hf -> {
                        if (hf.isSuccess()) {
                            log.info("【CatchAll发布网关】握手已发送 - 原始端口: {}, 终端网关: {}:{}",
                                    originalPort, targetIp, targetPort);
                            // 握手完成，恢复读取入站数据
                            ctx.channel().config().setAutoRead(true);
                        } else {
                            log.error("【CatchAll发布网关】握手发送失败", hf.cause());
                            ctx.close();
                        }
                    });
                } else {
                    log.error("【CatchAll发布网关】连接终端网关失败 - {}:{}", targetIp, targetPort, future.cause());
                    ctx.close();
                }
            });
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buf = (ByteBuf) msg;
            try {
                if (outboundChannel == null || !outboundChannel.isActive()) {
                    log.warn("【CatchAll发布网关】出站通道不可用，丢弃数据");
                    return;
                }

                byte[] data = new byte[buf.readableBytes()];
                buf.readBytes(data);
                captureContent(data);

                if (!cryptoService.isSvacModuleReady()) {
                    log.warn("publish gateway SVAC module unavailable, drop CatchAll packet: source={}, status={}",
                            sourceIp, cryptoService.getSvacModuleStatus());
                    return;
                }

                if (CATCHALL_ENCRYPT_ENABLED && Boolean.TRUE.equals(rule.getEncryptEnabled())) {
                    // 加密模式：加密后以 [4B密文长度][密文块] 格式发送
                    byte[] encrypted = cryptoService.encrypt(data);
                    if (encrypted != null) {
                        if (cryptoPacketStore != null) {
                            cryptoPacketStore.saveRawTcp(
                                    rule.getRuleId(), rule.getChainId(), sourceIp,
                                    rule.getTerminalGatewayIp(), terminalCatchAllPort,
                                    data, encrypted);
                        }
                        ByteBuf sendBuf = Unpooled.buffer(4 + encrypted.length);
                        sendBuf.writeInt(encrypted.length);
                        sendBuf.writeBytes(encrypted);
                        outboundChannel.writeAndFlush(sendBuf);
                    } else {
                        log.warn("【CatchAll发布网关】加密失败，丢弃本包");
                    }
                } else {
                    // 透明模式：零拷贝直接转发原始字节（默认，文件传输高性能）
                    outboundChannel.writeAndFlush(Unpooled.copiedBuffer(data));
                }
            } finally {
                buf.release();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            String channelId = ctx.channel().id().asShortText();
            outboundChannels.remove(channelId);
            if (outboundChannel != null) {
                outboundChannel.close();
            }
            reportCapturedContent();
            log.info("【CatchAll发布网关】连接断开 - 端口: {}", originalPort);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("【CatchAll发布网关】处理异常 - 端口: {}", originalPort, cause);
            ctx.close();
        }

        private void captureContent(byte[] data) {
            if (data == null || data.length == 0 || contentCaptureOverflow) {
                return;
            }
            if (contentCaptureBytes + data.length > MAX_CONTENT_CAPTURE_BYTES) {
                contentCaptureOverflow = true;
                contentCaptureBuffer.reset();
                log.warn("[CatchAll-Content] capture overflow, skip content report: ruleId={}, originalPort={}, limitBytes={}",
                        rule.getRuleId(), originalPort, MAX_CONTENT_CAPTURE_BYTES);
                return;
            }
            contentCaptureBuffer.write(data, 0, data.length);
            contentCaptureBytes += data.length;
        }

        private void reportCapturedContent() {
            if (dataReportService == null || contentCaptureBytes <= 0 || contentCaptureOverflow) {
                return;
            }
            byte[] captured = contentCaptureBuffer.toByteArray();
            if (captured.length == 0) {
                return;
            }
            log.info("[CatchAll-Content] report captured stream: ruleId={}, sourceIp={}, originalPort={}, bytes={}",
                    rule.getRuleId(), sourceIp, originalPort, captured.length);
            dataReportService.reportAsync(rule.getRuleId(), rule.getChainId(), captured,
                    sourceIp, rule.getManufacturer(), rule.getTargetIp(), rule.getTargetPort());
        }
    }
}
