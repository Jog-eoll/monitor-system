package com.publishgateway.udpproxy.mqtt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SystemNetworkChangeService {

    private static final Pattern IFACE_PATTERN = Pattern.compile("[A-Za-z0-9_.:-]{1,64}");
    private static final Pattern CHANGE_ID_PATTERN = Pattern.compile("[A-Za-z0-9_.:-]{1,96}");

    @Value("${system-network-change.enabled:false}")
    private boolean enabled;

    @Value("${system-network-change.allowed-interfaces:}")
    private String allowedInterfaces;

    @Value("${system-network-change.rollback-dir:/tmp}")
    private String rollbackDir;

    @Value("${system-network-change.command-timeout-ms:10000}")
    private long commandTimeoutMs;

    public Map<String, Object> changeIp(Map<String, Object> payload, String commandMessageId) {
        if (payload == null) {
            throw new IllegalArgumentException("payload is required");
        }

        String iface = requiredString(payload, "interfaceName", "iface", "networkInterface");
        String newIp = requiredString(payload, "newIp", "ip");
        int prefixLength = requiredInt(payload, "prefixLength", "prefix");
        String gateway = optionalString(payload, "gateway", "defaultGateway");
        boolean dryRun = optionalBoolean(payload, "dryRun", false);
        boolean applyDefaultRoute = optionalBoolean(payload, "applyDefaultRoute", false);
        boolean persist = optionalBoolean(payload, "persist", false);
        int rollbackSeconds = optionalInt(payload, 0, "rollbackSeconds");
        String changeId = optionalString(payload, "changeId");
        if (changeId == null || changeId.trim().isEmpty()) {
            changeId = commandMessageId;
        }

        validateChangeId(changeId);
        validateInterface(iface);
        validateAllowedInterface(iface);
        validateIpv4(newIp, "newIp");
        validatePrefix(prefixLength);
        if (gateway != null && !gateway.trim().isEmpty()) {
            validateIpv4(gateway, "gateway");
        }
        if (persist) {
            throw new IllegalArgumentException("persist=true is not supported from the container agent");
        }
        if (applyDefaultRoute && (gateway == null || gateway.trim().isEmpty())) {
            throw new IllegalArgumentException("gateway is required when applyDefaultRoute=true");
        }

        String cidr = newIp + "/" + prefixLength;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("changeId", changeId);
        result.put("interfaceName", iface);
        result.put("newCidr", cidr);
        result.put("dryRun", dryRun);
        result.put("applyDefaultRoute", applyDefaultRoute);
        result.put("rollbackSeconds", rollbackSeconds);
        result.put("systemNetworkChangeEnabled", enabled);

        String addCommand = "ip addr add " + sh(cidr) + " dev " + sh(iface);
        String routeCommand = applyDefaultRoute
                ? "ip route replace default via " + sh(gateway) + " dev " + sh(iface)
                : null;
        result.put("plannedAddCommand", addCommand);
        if (routeCommand != null) {
            result.put("plannedRouteCommand", routeCommand);
        }

        // dryRun=true 只做参数校验、接口白名单校验和计划生成，不执行任何系统命令，不要求 enabled=true
        if (dryRun) {
            result.put("success", true);
            result.put("message", "dry run passed");
            return result;
        }

        // dryRun=false 才进入真实权限、命令、网卡和安全开关检查
        if (!enabled) {
            throw new IllegalStateException("system network change is disabled");
        }
        CommandResult rootCheck = runRequired("id -u", "permission check");
        if (!"0".equals(rootCheck.stdout.trim())) {
            throw new IllegalStateException("network change requires root inside host network namespace");
        }
        runRequired("command -v ip", "ip command check");
        runRequired("ip link show dev " + sh(iface), "interface check");

        String beforeAddr = run("ip -o -4 addr show dev " + sh(iface)).stdout.trim();
        String beforeDefaultRoute = run("ip route show default | head -n 1").stdout.trim();
        result.put("beforeAddresses", beforeAddr);
        result.put("beforeDefaultRoute", beforeDefaultRoute);

        boolean alreadyPresent = addressExists(iface, cidr);
        result.put("alreadyPresent", alreadyPresent);
        if (!alreadyPresent) {
            runRequired(addCommand, "add ip address");
        }
        if (routeCommand != null) {
            runRequired(routeCommand, "replace default route");
        }
        if (rollbackSeconds > 0) {
            scheduleRollback(changeId, iface, cidr, beforeDefaultRoute, rollbackSeconds);
            result.put("rollbackActive", true);
            result.put("rollbackMarker", rollbackMarker(changeId));
        } else {
            result.put("rollbackActive", false);
        }

        result.put("afterAddresses", run("ip -o -4 addr show dev " + sh(iface)).stdout.trim());
        result.put("afterDefaultRoute", run("ip route show default | head -n 1").stdout.trim());
        result.put("success", true);
        result.put("message", "network change applied");
        return result;
    }

    public Map<String, Object> confirm(Map<String, Object> payload) {
        if (!enabled) {
            throw new IllegalStateException("system network change is disabled");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload is required");
        }
        String changeId = requiredString(payload, "changeId");
        validateChangeId(changeId);
        File marker = new File(rollbackMarker(changeId));
        boolean existed = marker.exists();
        if (existed && !marker.delete()) {
            throw new IllegalStateException("failed to delete rollback marker: " + marker.getAbsolutePath());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("changeId", changeId);
        result.put("rollbackCanceled", existed);
        result.put("success", true);
        result.put("message", existed ? "rollback canceled" : "rollback marker not found");
        return result;
    }

    private boolean addressExists(String iface, String cidr) {
        CommandResult res = run("ip -o -4 addr show dev " + sh(iface) + " | awk '{print $4}' | grep -Fx " + sh(cidr));
        return res.exitCode == 0;
    }

    private void scheduleRollback(String changeId, String iface, String cidr, String previousDefaultRoute, int rollbackSeconds) {
        String marker = rollbackMarker(changeId);
        String logFile = rollbackDir + "/publish-gateway-network-change-" + changeId + ".log";
        runRequired("mkdir -p " + sh(rollbackDir) + " && touch " + sh(marker), "create rollback marker");

        StringBuilder rollback = new StringBuilder();
        rollback.append("sleep ").append(rollbackSeconds).append("; ");
        rollback.append("if [ -f ").append(sh(marker)).append(" ]; then ");
        rollback.append("ip addr del ").append(sh(cidr)).append(" dev ").append(sh(iface)).append(" || true; ");
        if (previousDefaultRoute != null && !previousDefaultRoute.trim().isEmpty()) {
            rollback.append("ip route replace ").append(previousDefaultRoute).append(" || true; ");
        }
        rollback.append("rm -f ").append(sh(marker)).append("; fi");

        runRequired("nohup sh -c " + sh(rollback.toString()) + " > " + sh(logFile) + " 2>&1 &",
                "schedule rollback");
        log.warn("[NETWORK] scheduled rollback: changeId={}, seconds={}, marker={}", changeId, rollbackSeconds, marker);
    }

    private void validateInterface(String iface) {
        if (iface == null || !IFACE_PATTERN.matcher(iface).matches()) {
            throw new IllegalArgumentException("invalid interfaceName");
        }
        if ("lo".equals(iface)) {
            throw new IllegalArgumentException("loopback interface is not allowed");
        }
    }

    private void validateAllowedInterface(String iface) {
        Set<String> allowed = parseAllowedInterfaces();
        if (!allowed.isEmpty() && !allowed.contains(iface)) {
            throw new IllegalArgumentException("interface is not in allowed list: " + iface);
        }
    }

    private Set<String> parseAllowedInterfaces() {
        Set<String> result = new HashSet<>();
        if (allowedInterfaces == null || allowedInterfaces.trim().isEmpty()) {
            return result;
        }
        Arrays.stream(allowedInterfaces.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(result::add);
        return result;
    }

    private void validateIpv4(String value, String field) {
        try {
            InetAddress address = InetAddress.getByName(value);
            if (!(address instanceof Inet4Address) || address.isAnyLocalAddress()
                    || address.isLoopbackAddress() || address.isMulticastAddress()) {
                throw new IllegalArgumentException("invalid " + field);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid " + field + ": " + value);
        }
    }

    private void validatePrefix(int prefixLength) {
        if (prefixLength < 1 || prefixLength > 32) {
            throw new IllegalArgumentException("prefixLength must be 1-32");
        }
    }

    private void validateChangeId(String changeId) {
        if (changeId == null || !CHANGE_ID_PATTERN.matcher(changeId).matches()) {
            throw new IllegalArgumentException("invalid changeId");
        }
    }

    private String rollbackMarker(String changeId) {
        return rollbackDir + "/publish-gateway-network-change-" + changeId + ".rollback";
    }

    private String requiredString(Map<String, Object> payload, String... keys) {
        String value = optionalString(payload, keys);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(keys[0] + " is required");
        }
        return value.trim();
    }

    private String optionalString(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null && !value.toString().trim().isEmpty()) {
                return value.toString().trim();
            }
        }
        return null;
    }

    private int requiredInt(Map<String, Object> payload, String... keys) {
        int value = optionalInt(payload, -1, keys);
        if (value < 0) {
            throw new IllegalArgumentException(keys[0] + " is required");
        }
        return value;
    }

    private int optionalInt(Map<String, Object> payload, int defaultValue, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            if (value != null && !value.toString().trim().isEmpty()) {
                try {
                    return Integer.parseInt(value.toString().trim());
                } catch (Exception ignored) {
                    throw new IllegalArgumentException("invalid integer: " + key);
                }
            }
        }
        return defaultValue;
    }

    private boolean optionalBoolean(Map<String, Object> payload, String key, boolean defaultValue) {
        Object value = payload.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private CommandResult runRequired(String command, String label) {
        CommandResult result = run(command);
        if (result.exitCode != 0) {
            throw new IllegalStateException(label + " failed: " + result.stderr + " " + result.stdout);
        }
        return result;
    }

    private CommandResult run(String command) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder("sh", "-c", command);
            process = builder.start();
            boolean finished = process.waitFor(commandTimeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new CommandResult(124, "", "command timed out: " + command);
            }
            String stdout = read(process.getInputStream());
            String stderr = read(process.getErrorStream());
            return new CommandResult(process.exitValue(), stdout, stderr);
        } catch (Exception e) {
            return new CommandResult(1, "", e.getMessage());
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private String read(java.io.InputStream inputStream) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private String sh(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static class CommandResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        private CommandResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }
    }
}
