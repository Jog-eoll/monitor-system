package com.infopublish.client.utils.log;

import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.yaml.snakeyaml.Yaml;

/**
 * Installs reversible console log encoding for packaged client logs.
 */
public final class LogEncodingSupport {

    private static final String ENABLED_PROPERTY = "client.log.encode.enabled";

    private static volatile boolean installed;

    private LogEncodingSupport() {
    }

    public static void installIfEnabled() {
        installIfEnabled(null);
    }

    public static void installIfEnabled(String[] args) {
        if (!isEnabled(args)) {
            return;
        }
        if (installed) {
            return;
        }
        synchronized (LogEncodingSupport.class) {
            if (installed) {
                return;
            }
            System.setOut(newEncodedPrintStream(System.out));
            System.setErr(newEncodedPrintStream(System.err));
            installed = true;
        }
    }

    private static boolean isEnabled(String[] args) {
        String enabledText = System.getProperty(ENABLED_PROPERTY);
        if (hasText(enabledText)) {
            return parseBoolean(enabledText, true);
        }

        Boolean configEnabled = readConfigEnabled(args);
        return configEnabled == null || configEnabled.booleanValue();
    }

    private static Boolean readConfigEnabled(String[] args) {
        Set<Path> configPaths = resolveConfigPaths(args);
        for (Path configPath : configPaths) {
            Boolean enabled = readConfigEnabled(configPath);
            if (enabled != null) {
                return enabled;
            }
        }
        return null;
    }

    private static Set<Path> resolveConfigPaths(String[] args) {
        Set<Path> paths = new LinkedHashSet<Path>();
        addConfigLocation(paths, System.getProperty("spring.config.location"));
        addConfigLocation(paths, System.getenv("SPRING_CONFIG_LOCATION"));
        if (args != null) {
            for (int index = 0; index < args.length; index++) {
                String arg = args[index];
                if (arg == null) {
                    continue;
                }
                if (arg.startsWith("--spring.config.location=")) {
                    addConfigLocation(paths, arg.substring("--spring.config.location=".length()));
                } else if ("--spring.config.location".equals(arg) && index + 1 < args.length) {
                    addConfigLocation(paths, args[index + 1]);
                    index++;
                }
            }
        }

        Path userDir = Paths.get(System.getProperty("user.dir", "."));
        paths.add(userDir.resolve("config").resolve("application.yml").toAbsolutePath().normalize());
        paths.add(userDir.resolve("config").resolve("application.yaml").toAbsolutePath().normalize());
        paths.add(userDir.resolve("application.yml").toAbsolutePath().normalize());
        paths.add(userDir.resolve("application.yaml").toAbsolutePath().normalize());
        return paths;
    }

    private static void addConfigLocation(Set<Path> paths, String rawLocation) {
        if (!hasText(rawLocation)) {
            return;
        }

        String[] locations = rawLocation.split(",");
        for (String location : locations) {
            Path path = resolveConfigLocation(location);
            if (path == null) {
                continue;
            }
            if (Files.isDirectory(path) || location.endsWith("/") || location.endsWith("\\")) {
                paths.add(path.resolve("application.yml").toAbsolutePath().normalize());
                paths.add(path.resolve("application.yaml").toAbsolutePath().normalize());
            } else {
                paths.add(path.toAbsolutePath().normalize());
            }
        }
    }

    private static Path resolveConfigLocation(String rawLocation) {
        if (!hasText(rawLocation)) {
            return null;
        }

        String location = rawLocation.trim();
        while (location.startsWith("optional:")) {
            location = location.substring("optional:".length());
        }
        if (location.startsWith("classpath:")) {
            return null;
        }

        if (location.startsWith("file:")) {
            try {
                return Paths.get(new URI(location));
            } catch (Exception ignored) {
                location = location.substring("file:".length());
                if (location.startsWith("/") && location.length() > 2 && location.charAt(2) == ':') {
                    location = location.substring(1);
                }
            }
        }

        return Paths.get(location);
    }

    private static Boolean readConfigEnabled(Path configPath) {
        if (configPath == null || !Files.isRegularFile(configPath)) {
            return null;
        }

        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            Object loaded = new Yaml().load(reader);
            if (!(loaded instanceof Map)) {
                return null;
            }

            Map<?, ?> root = (Map<?, ?>) loaded;
            Object value = root.get(ENABLED_PROPERTY);
            if (value == null) {
                value = findNestedValue(root, ENABLED_PROPERTY);
            }
            return parseBooleanValue(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object findNestedValue(Map<?, ?> root, String propertyName) {
        Object current = root;
        String[] parts = propertyName.split("\\.");
        for (String part : parts) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<?, ?>) current).get(part);
        }
        return current;
    }

    private static Boolean parseBooleanValue(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        if (value instanceof String) {
            return Boolean.valueOf(parseBoolean((String) value, true));
        }
        return null;
    }

    private static boolean parseBoolean(String value, boolean defaultValue) {
        if (!hasText(value)) {
            return defaultValue;
        }

        String normalized = value.trim().toLowerCase();
        if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized) || "off".equals(normalized)) {
            return false;
        }
        if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized) || "on".equals(normalized)) {
            return true;
        }
        return defaultValue;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static PrintStream newEncodedPrintStream(OutputStream delegate) {
        try {
            return new PrintStream(new EncodedLogOutputStream(delegate), true, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is not supported", e);
        }
    }
}
