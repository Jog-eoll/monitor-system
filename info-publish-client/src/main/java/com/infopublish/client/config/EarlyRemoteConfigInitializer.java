package com.infopublish.client.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

public class EarlyRemoteConfigInitializer
        implements EnvironmentPostProcessor, Ordered {

    private static final Log log = LogFactory.getLog(EarlyRemoteConfigInitializer.class);
    private static final String PROPERTY_SOURCE_NAME = "earlyRemoteClientConfig";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getProperty("registry.client.enabled", Boolean.class, Boolean.TRUE)) {
            log.info("[EarlyRemoteConfig] registry client disabled, skip");
            return;
        }

        String clientId = ClientIdResolver.resolve(environment);
        String registryBaseUrl = buildRegistryBaseUrl(environment);
        String serviceName = environment.getProperty("registry.client.service-name",
                environment.getProperty("spring.application.name", "info-publish-client"));
        Integer port = parseInt(environment.getProperty("server.port"), 8080);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("monitor-platform.client-id", clientId);
        properties.put("registry.client.client-id", clientId);
        properties.put("client.remote-config.ready", Boolean.FALSE);

        Map<String, Object> config;
        String httpLoadError = null;
        try {
            registerClient(environment, registryBaseUrl, clientId, serviceName, port);
            config = pullConfig(registryBaseUrl, clientId);
            properties.put("client.remote-config.source", "registry-http");
        } catch (Exception e) {
            httpLoadError = "registry http failed: " + e.getMessage();
            log.warn("[EarlyRemoteConfig] registry http config unavailable, fallback to config db: clientId="
                    + clientId + ", error=" + e.getMessage());
            try {
                config = pullConfigFromDatabase(environment, clientId);
                properties.put("client.remote-config.source", "registry-config-db");
                log.info("[EarlyRemoteConfig] config loaded from registry config db: clientId=" + clientId);
            } catch (Exception dbException) {
                String error = httpLoadError + "; config db failed: " + dbException.getMessage();
                properties.put("client.remote-config.last-error", error);
                applyPropertySource(environment, properties);
                log.warn("[EarlyRemoteConfig] config preloading skipped; continue startup: clientId="
                        + clientId + ", error=" + error);
                failStartupIfDataSourceMissing(environment, properties, error);
                return;
            }
        }

        flatten("", config, properties);
        applyCompatibilityProperties(config, properties);
        properties.put("monitor-platform.client-id", clientId);
        properties.put("registry.client.client-id", clientId);

        String dataSourceError = validateDataSourceProperties(environment, properties);
        if (dataSourceError != null) {
            properties.put("client.remote-config.ready", Boolean.FALSE);
            properties.put("client.remote-config.last-error", dataSourceError);
            applyPropertySource(environment, properties);
            String error = "remote config datasource invalid: " + dataSourceError
                    + ", clientId=" + clientId
                    + ", registry=" + registryBaseUrl;
            log.error("[EarlyRemoteConfig] " + error);
            throw new IllegalStateException("[EarlyRemoteConfig] " + error);
        }

        String validationError = validateConfig(config);
        if (validationError == null) {
            properties.put("client.remote-config.ready", Boolean.TRUE);
            properties.remove("client.remote-config.last-error");
        } else {
            properties.put("client.remote-config.ready", Boolean.FALSE);
            properties.put("client.remote-config.last-error", validationError);
        }
        applyPropertySource(environment, properties);
        if (validationError == null) {
            log.info("[EarlyRemoteConfig] config applied before context refresh: clientId=" + clientId);
        } else {
            log.warn("[EarlyRemoteConfig] config applied with business validation warning: clientId="
                    + clientId + ", reason=" + validationError);
        }
    }

    private void applyPropertySource(ConfigurableEnvironment environment, Map<String, Object> properties) {
        MapPropertySource propertySource = new MapPropertySource(PROPERTY_SOURCE_NAME, properties);
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            environment.getPropertySources().replace(PROPERTY_SOURCE_NAME, propertySource);
            return;
        }
        environment.getPropertySources().addFirst(propertySource);
    }

    private void registerClient(ConfigurableEnvironment environment,
                                String registryBaseUrl,
                                String clientId,
                                String serviceName,
                                Integer port) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("clientId", clientId);
        request.put("serviceName", serviceName);
        request.put("instanceId", clientId);
        request.put("host", firstNonBlank(environment.getProperty("registry.client.host"), detectHost(environment)));
        request.put("port", port);
        request.put("macAddress", firstNonBlank(environment.getProperty("registry.client.mac-address"), detectMac()));
        request.put("deviceType", environment.getProperty("registry.client.device-type", "publish_server"));

        String body = httpRequest(registryBaseUrl + normalizePath(
                environment.getProperty("registry.client.register-path", "/register")), "POST", request);
        Map<String, Object> response = parseJson(body);
        if (!isSuccess(response)) {
            throw new IllegalStateException("register failed: " + body);
        }
        log.info("[EarlyRemoteConfig] registered before context refresh: clientId=" + clientId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> pullConfig(String registryBaseUrl, String clientId) throws Exception {
        String body = httpRequest(registryBaseUrl + "/client-config/" + clientId, "GET", null);
        Map<String, Object> response = parseJson(body);
        if (!isSuccess(response)) {
            throw new IllegalStateException("config not available: " + body);
        }
        Object dataObject = response.get("data");
        if (!(dataObject instanceof Map)) {
            throw new IllegalStateException("invalid config response");
        }
        Object configObject = firstValue((Map<String, Object>) dataObject,
                "config", "configContent", "config_content");
        return parseConfigContent(configObject);
    }

    private Map<String, Object> pullConfigFromDatabase(ConfigurableEnvironment environment,
                                                       String clientId) throws Exception {
        if (!environment.getProperty("registry.config-db.enabled", Boolean.class, Boolean.TRUE)) {
            throw new IllegalStateException("registry config db disabled");
        }
        if (isBlank(clientId)) {
            throw new IllegalStateException("clientId is blank");
        }

        String driverClassName = environment.getProperty("registry.config-db.driver-class-name",
                "com.mysql.jdbc.Driver");
        String url = environment.getProperty("registry.config-db.url");
        String username = environment.getProperty("registry.config-db.username");
        String password = environment.getProperty("registry.config-db.password");
        Integer queryTimeoutSeconds = parseInt(
                environment.getProperty("registry.config-db.query-timeout-seconds"), 5);

        if (isBlank(url)) {
            throw new IllegalStateException("registry.config-db.url is required");
        }
        if (isBlank(username)) {
            throw new IllegalStateException("registry.config-db.username is required");
        }

        Class.forName(driverClassName);
        String sql = "SELECT config_content FROM registry_client_config "
                + "WHERE client_id = ? AND enabled = 1 LIMIT 1";
        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, clientId);
            if (queryTimeoutSeconds != null && queryTimeoutSeconds > 0) {
                statement.setQueryTimeout(queryTimeoutSeconds);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("client config not found in registry_client_config: "
                            + clientId);
                }
                String configContent = resultSet.getString("config_content");
                try {
                    return parseConfigContent(configContent);
                } catch (Exception e) {
                    throw new IllegalStateException("invalid registry_client_config.config_content: "
                            + e.getMessage(), e);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfigContent(Object configObject) throws Exception {
        if (configObject instanceof Map) {
            return (Map<String, Object>) configObject;
        }
        if (configObject instanceof String) {
            String configText = String.valueOf(configObject).trim();
            if (isBlank(configText)) {
                throw new IllegalStateException("config content is empty");
            }
            return OBJECT_MAPPER.readValue(configText, new TypeReference<Map<String, Object>>() {});
        }
        throw new IllegalStateException("config content is empty");
    }

    private String httpRequest(String url, String method, Map<String, Object> body) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("Accept", "application/json");

            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(body);
                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(bytes);
                }
            }

            int status = connection.getResponseCode();
            String responseBody = readResponse(status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream());
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("http " + status + " from " + url + ": " + responseBody);
            }
            return responseBody;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readResponse(InputStream inputStream) throws Exception {
        if (inputStream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private Map<String, Object> parseJson(String body) throws Exception {
        if (isBlank(body)) {
            return new LinkedHashMap<>();
        }
        return OBJECT_MAPPER.readValue(body, new TypeReference<Map<String, Object>>() {});
    }

    private boolean isSuccess(Map<String, Object> response) {
        Object code = response == null ? null : response.get("code");
        return "200".equals(String.valueOf(code));
    }

    @SuppressWarnings("unchecked")
    private void flatten(String prefix, Map<String, Object> source, Map<String, Object> target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = normalizeConfigKey(entry.getKey());
            String propertyKey = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = entry.getValue();
            if (value instanceof Map) {
                flatten(propertyKey, (Map<String, Object>) value, target);
            } else if (value != null) {
                target.put(propertyKey, value);
            }
        }
    }

    private void applyCompatibilityProperties(Map<String, Object> config, Map<String, Object> target) {
        putIfPresent(config, target, "monitor-platform.url",
                "monitorPlatformUrl", "monitor-platform-url", "platformUrl", "platform_url");
        putIfPresent(config, target, "vauth.monitor-platform-url",
                "monitorPlatformUrl", "monitor-platform-url", "platformUrl", "platform_url");
        putIfPresent(config, target, "vauth.server-id", "serverId", "server-id");
        putIfPresent(config, target, "vauth.server-cert-path", "serverCertPath", "server-cert-path");
        putIfPresent(config, target, "vauth.client-cert-path", "clientCertPath", "client-cert-path");
        putIfPresent(config, target, "vauth.auth-id", "authId", "auth-id");
        putIfPresent(config, target, "vauth.password", "password");
        putIfPresent(config, target, "vauth.mock-mode", "mockMode", "mock-mode");
        applyDataSourceCompatibility(config, target);
    }

    private void putIfPresent(Map<String, Object> source, Map<String, Object> target,
                              String propertyName, String... sourceKeys) {
        Object value = firstValue(source, sourceKeys);
        if (value != null && !isBlank(String.valueOf(value))) {
            target.put(propertyName, value);
        }
    }

    @SuppressWarnings("unchecked")
    private void applyDataSourceCompatibility(Map<String, Object> config, Map<String, Object> target) {
        Map<String, Object> dataSource = null;
        Object springObject = firstValue(config, "spring");
        if (springObject instanceof Map) {
            Object dataSourceObject = firstValue((Map<String, Object>) springObject, "datasource", "dataSource");
            if (dataSourceObject instanceof Map) {
                dataSource = (Map<String, Object>) dataSourceObject;
            }
        }

        if (dataSource == null) {
            Object dataSourceObject = firstValue(config, "datasource", "dataSource", "database", "db");
            if (dataSourceObject instanceof Map) {
                dataSource = (Map<String, Object>) dataSourceObject;
            }
        }

        if (dataSource != null) {
            putIfPresent(dataSource, target, "spring.datasource.url",
                    "url", "jdbcUrl", "jdbc-url", "databaseUrl", "database-url");
            putIfPresent(dataSource, target, "spring.datasource.username",
                    "username", "user", "dbUsername", "db-username");
            putIfPresent(dataSource, target, "spring.datasource.password",
                    "password", "dbPassword", "db-password");
            putIfPresent(dataSource, target, "spring.datasource.driver-class-name",
                    "driverClassName", "driver-class-name", "driver", "driverClass", "driver-class");
        }

        putIfPresent(config, target, "spring.datasource.url",
                "spring.datasource.url", "spring.datasource.jdbc-url", "spring.datasource.jdbcUrl",
                "datasourceUrl", "datasource-url",
                "databaseUrl", "database-url", "dbUrl", "db-url", "jdbcUrl", "jdbc-url");
        putIfPresent(config, target, "spring.datasource.username",
                "spring.datasource.username", "datasourceUsername", "datasource-username",
                "databaseUsername", "database-username", "dbUsername", "db-username");
        putIfPresent(config, target, "spring.datasource.password",
                "spring.datasource.password", "datasourcePassword", "datasource-password",
                "databasePassword", "database-password", "dbPassword", "db-password");
        putIfPresent(config, target, "spring.datasource.driver-class-name",
                "spring.datasource.driver-class-name", "driverClassName", "driver-class-name",
                "datasourceDriverClassName", "datasource-driver-class-name",
                "databaseDriverClassName", "database-driver-class-name");

        Object url = target.get("spring.datasource.url");
        Object driverClassName = target.get("spring.datasource.driver-class-name");
        if ((driverClassName == null || isBlank(String.valueOf(driverClassName)))
                && url != null
                && String.valueOf(url).startsWith("jdbc:mysql:")) {
            target.put("spring.datasource.driver-class-name", "com.mysql.jdbc.Driver");
        }
    }

    private void failStartupIfDataSourceMissing(ConfigurableEnvironment environment,
                                                Map<String, Object> properties,
                                                String reason) {
        String dataSourceError = validateDataSourceProperties(environment, properties);
        if (dataSourceError == null) {
            return;
        }
        String error = "remote datasource config is unavailable: " + reason
                + "; " + dataSourceError
                + ". Check registry_client_config.config_content";
        log.error("[EarlyRemoteConfig] " + error);
        throw new IllegalStateException("[EarlyRemoteConfig] " + error);
    }

    private String validateDataSourceProperties(ConfigurableEnvironment environment,
                                                Map<String, Object> properties) {
        String url = resolveProperty(environment, properties,
                "spring.datasource.url", "spring.datasource.jdbc-url", "spring.datasource.jdbcUrl");
        if (isBlank(url)) {
            return "spring.datasource.url is required";
        }
        if (!url.trim().startsWith("jdbc:")) {
            return "spring.datasource.url must start with jdbc:";
        }

        if (url.trim().startsWith("jdbc:mysql:")) {
            String username = resolveProperty(environment, properties, "spring.datasource.username");
            if (isBlank(username)) {
                return "spring.datasource.username is required for MySQL datasource";
            }
        }
        return null;
    }

    private String resolveProperty(ConfigurableEnvironment environment,
                                   Map<String, Object> properties,
                                   String... names) {
        if (names == null) {
            return null;
        }
        for (String name : names) {
            if (properties != null) {
                Object value = properties.get(name);
                if (value != null && !isBlank(String.valueOf(value))) {
                    return String.valueOf(value).trim();
                }
            }
            String value = environment == null ? null : environment.getProperty(name);
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String normalizeConfigKey(String key) {
        if (key == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char ch = key.charAt(i);
            if (Character.isUpperCase(ch)) {
                if (i > 0) {
                    builder.append('-');
                }
                builder.append(Character.toLowerCase(ch));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private String validateConfig(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return "config content is empty";
        }

        String monitorPlatformUrl = firstString(config,
                "monitorPlatformUrl", "monitor-platform-url", "platformUrl", "platform_url");
        if (isBlank(monitorPlatformUrl)) {
            return "monitorPlatformUrl is required";
        }
        if (!monitorPlatformUrl.startsWith("http://") && !monitorPlatformUrl.startsWith("https://")) {
            return "monitorPlatformUrl must start with http:// or https://";
        }

        boolean mockMode = Boolean.parseBoolean(String.valueOf(firstValue(config, "mockMode", "mock-mode")));
        if (mockMode) {
            return null;
        }

        if (isBlank(firstString(config, "serverId", "server-id"))) {
            return "serverId is required";
        }
        if (isBlank(firstString(config, "authId", "auth-id"))) {
            return "authId is required";
        }
        if (isBlank(firstString(config, "password"))) {
            return "password is required";
        }
        return null;
    }

    private String buildRegistryBaseUrl(ConfigurableEnvironment environment) {
        String serverAddr = environment.getProperty("registry.client.server-addr", "localhost:8069");
        String baseUrl = serverAddr.startsWith("http://") || serverAddr.startsWith("https://")
                ? serverAddr : "http://" + serverAddr;
        String apiPrefix = environment.getProperty("registry.client.api-prefix", "/registry");
        return baseUrl + normalizePath(apiPrefix);
    }

    private String detectHost(ConfigurableEnvironment environment) {
        String preferredNetwork = environment.getProperty("registry.client.preferred-network");
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            String firstAddress = null;
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || networkInterface.isVirtual() || !networkInterface.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!(address instanceof Inet4Address) || address.isLoopbackAddress()) {
                        continue;
                    }
                    String hostAddress = address.getHostAddress();
                    if (firstAddress == null) {
                        firstAddress = hostAddress;
                    }
                    if (!isBlank(preferredNetwork) && hostAddress.startsWith(preferredNetwork)) {
                        return hostAddress;
                    }
                }
            }
            if (!isBlank(firstAddress)) {
                return firstAddress;
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.warn("[EarlyRemoteConfig] detect host failed: " + e.getMessage());
            return "127.0.0.1";
        }
    }

    private String detectMac() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || networkInterface.isVirtual() || !networkInterface.isUp()) {
                    continue;
                }
                byte[] mac = networkInterface.getHardwareAddress();
                if (mac == null || mac.length == 0) {
                    continue;
                }
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < mac.length; i++) {
                    builder.append(String.format("%02X", mac[i]));
                    if (i < mac.length - 1) {
                        builder.append("-");
                    }
                }
                return builder.toString();
            }
        } catch (Exception e) {
            log.warn("[EarlyRemoteConfig] detect mac failed: " + e.getMessage());
        }
        return null;
    }

    private String firstString(Map<String, Object> config, String... keys) {
        if (keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = config.get(key);
            if (value != null && !isBlank(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private Object firstValue(Map<String, Object> config, String firstKey, String secondKey) {
        Object value = config.get(firstKey);
        return value == null ? config.get(secondKey) : value;
    }

    private Object firstValue(Map<String, Object> config, String... keys) {
        if (keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = config.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Integer parseInt(String value, Integer defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String normalizePath(String path) {
        if (isBlank(path)) {
            return "";
        }
        String value = path.trim();
        return value.startsWith("/") ? value : "/" + value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
