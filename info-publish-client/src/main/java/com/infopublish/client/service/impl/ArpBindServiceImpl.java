package com.infopublish.client.service.impl;

import com.infopublish.client.service.ArpBindService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ARP 静态绑定服务实现
 *
 * <p>通过 netsh 命令将"情报板IP"静态绑定到"发布网关MAC"，
 * 使本机将发往情报板的流量实际送到发布网关（ARP欺骗/重定向）。
 */
@Slf4j
@Service
public class ArpBindServiceImpl implements ArpBindService {

    /** 记录当前已绑定状态（内存，重启后重新判断） */
    private volatile boolean bound = false;
    private volatile String lastBoundIp  = "";
    private volatile String lastBoundMac = "";
    private volatile int    lastIfIdx    = -1;
    private volatile String lastOutput   = "";

    @Override
    public String bind(String targetIp, String gatewayMac) {
        String mac = normalizeMac(gatewayMac);
        if (mac == null) {
            return "MAC地址格式错误，支持格式: 00-30-b4-01-62-98 或 00:30:b4:01:62:98";
        }

        int ifIdx = findInterfaceIndex(targetIp);
        if (ifIdx < 0) {
            return "未能找到可达 " + targetIp + " 的网卡，请检查网络连接";
        }
        log.info("[ARP] 使用网卡索引 idx={} 绑定 {} -> {}", ifIdx, targetIp, mac);

        runNetsh(buildDelArgs(ifIdx, targetIp));
        runCmd(new String[]{"arp", "-d", targetIp});

        String[] addArgs = buildAddArgs(ifIdx, targetIp, mac);
        String result = runNetsh(addArgs);

        if (isArpBound(targetIp, mac)) {
            bound = true;
            lastBoundIp  = targetIp;
            lastBoundMac = mac;
            lastIfIdx    = ifIdx;
            lastOutput   = "绑定成功";
            log.info("[ARP] 静态绑定成功: {} -> {}", targetIp, mac);
            return "绑定成功：" + targetIp + " -> " + mac + "（网卡idx=" + ifIdx + "）";
        } else {
            lastOutput = "绑定命令已执行，但ARP表未验证到条目，输出: " + result;
            log.warn("[ARP] 绑定后验证失败，命令输出: {}", result);
            return lastOutput;
        }
    }

    @Override
    public String unbind(String targetIp) {
        int idx = (lastIfIdx > 0) ? lastIfIdx : findInterfaceIndex(targetIp);
        if (idx < 0) {
            return "未能找到网卡索引，解绑失败";
        }

        String result = runNetsh(buildDelArgs(idx, targetIp));
        log.info("[ARP] 静态ARP删除: {} (idx={}), 输出: {}", targetIp, idx, result);

        if (result.contains("提升") || result.contains("管理员") ||
                result.toLowerCase().contains("elevation") ||
                result.toLowerCase().contains("administrator")) {
            lastOutput = "解绑失败（需要管理员权限）: " + result;
            log.error("[ARP] 解绑失败，程序未以管理员身份运行: {}", result);
            return lastOutput;
        }

        runCmd(new String[]{"arp", "-d", targetIp});

        String fakeMac = generateRandomMac();
        String addResult = runNetsh(buildAddArgs(idx, targetIp, fakeMac));
        log.info("[ARP] 占位静态ARP已添加: {} -> {} (idx={}), 输出: {}",
                targetIp, fakeMac, idx, addResult);

        if (isArpBound(targetIp, fakeMac)) {
            bound = false;
            lastOutput = "解绑成功，已用随机MAC占位";
            log.info("[ARP] 解绑成功: {} -> {} (占位，流量将被丢弃)", targetIp, fakeMac);
            return "解绑成功：" + targetIp + " -> " + fakeMac + "（占位，网卡idx=" + idx + "）";
        } else {
            runCmd(new String[]{"arp", "-d", targetIp});
            bound = false;
            lastOutput = "解绑完成，但占位失败，动态ARP可能重建";
            log.warn("[ARP] 占位失败，流量可能触发动态ARP重建");
            return lastOutput;
        }
    }

    @Override
    public ArpStatus getStatus() {
        ArpStatus s = new ArpStatus();
        s.setBound(bound);
        s.setTargetIp(lastBoundIp);
        s.setGatewayMac(lastBoundMac);
        s.setIfIdx(lastIfIdx);
        s.setLastOutput(lastOutput);
        if (bound && !lastBoundIp.isEmpty() && !lastBoundMac.isEmpty()) {
            s.setVerified(isArpBound(lastBoundIp, lastBoundMac));
        }
        return s;
    }

    @Override
    public List<InterfaceInfo> listInterfaces() {
        return parseInterfaces(runCmd(new String[]{"netsh", "i", "i", "show", "in"}));
    }

    @Override
    public int findInterfaceIndex() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    if (inetAddress.isLoopbackAddress()) {
                        continue;
                    }
                    if (inetAddress instanceof Inet4Address) {
                        log.info("[ARP] Interface idx: {}, Name: {}, IP: {}",
                                networkInterface.getIndex(), networkInterface.getName(), inetAddress.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            log.error("[ARP] 查询网卡索引异常: {}", e.getMessage());
        }
        return -1;
    }

    // ========== 内部实现 ==========

    private int findInterfaceIndex(String targetIp) {
        String targetPrefix = targetIp.substring(0, targetIp.lastIndexOf('.'));
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    if (inetAddress.isLoopbackAddress()) {
                        continue;
                    }
                    if (inetAddress instanceof Inet4Address) {
                        String ifIp = inetAddress.getHostAddress();
                        String ifPrefix = ifIp.substring(0, ifIp.lastIndexOf('.'));
                        if (ifPrefix.equals(targetPrefix)) {
                            log.info("[ARP] 找到目标网卡: idx={}, name={}, ip={}, targetIp={}",
                                    networkInterface.getIndex(),
                                    networkInterface.getDisplayName(),
                                    ifIp, targetIp);
                            return networkInterface.getIndex();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("[ARP] 查找网卡索引异常: {}", e.getMessage());
        }
        log.error("[ARP] 未找到能到达 {} 的本地网卡", targetIp);
        return -1;
    }

    private String runNetsh(String[] args) {
        return runCmd(args);
    }

    private String runCmd(String[] args) {
        StringBuilder sb = new StringBuilder();
        try {
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), Charset.forName("GBK")))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            proc.waitFor();
        } catch (Exception e) {
            log.error("[ARP] 命令执行失败: {}", e.getMessage());
            sb.append("ERROR: ").append(e.getMessage());
        }
        return sb.toString().trim();
    }

    private List<InterfaceInfo> parseInterfaces(String output) {
        List<InterfaceInfo> list = new ArrayList<>();
        Pattern p = Pattern.compile("^\\s*(\\d+)\\s+\\d+\\s+\\d+\\s+(\\S+)\\s+(.+)$");
        for (String line : output.split("\n")) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                InterfaceInfo info = new InterfaceInfo();
                info.setIdx(Integer.parseInt(m.group(1).trim()));
                info.setState(m.group(2).trim());
                info.setName(m.group(3).trim());
                list.add(info);
            }
        }
        return list;
    }

    private boolean isArpBound(String ip, String mac) {
        String output = runCmd(new String[]{"arp", "-a"});
        String normalMac  = mac.replace("-", "-").toLowerCase();
        String colonMac   = mac.replace("-", ":").toLowerCase();
        String out = output.toLowerCase();
        return (out.contains(ip) && (out.contains(normalMac) || out.contains(colonMac)));
    }

    private String[] buildAddArgs(int idx, String ip, String mac) {
        String cmd = String.format("netsh -c \"i i\" add neighbors %d %s %s", idx, ip, mac);
        return new String[]{"cmd", "/c", cmd};
    }

    private String[] buildDelArgs(int idx, String ip) {
        String cmd = String.format("netsh -c \"i i\" del neighbors %d %s", idx, ip);
        return new String[]{"cmd", "/c", cmd};
    }

    private static final Random RANDOM = new Random();

    private String generateRandomMac() {
        byte[] bytes = new byte[6];
        RANDOM.nextBytes(bytes);
        bytes[0] = (byte) ((bytes[0] & 0xFC) | 0x02);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (i > 0) sb.append("-");
            sb.append(String.format("%02x", bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    private String normalizeMac(String mac) {
        if (mac == null) return null;
        String raw = mac.replaceAll("[:\\-\\.]", "").trim();
        if (raw.length() != 12) return null;
        if (!raw.matches("[0-9a-fA-F]{12}")) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i += 2) {
            if (i > 0) sb.append("-");
            sb.append(raw.substring(i, i + 2).toLowerCase());
        }
        return sb.toString();
    }
}

