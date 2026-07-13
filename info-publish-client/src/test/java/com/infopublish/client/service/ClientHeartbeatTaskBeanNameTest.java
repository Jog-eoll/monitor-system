package com.infopublish.client.service;

import java.beans.Introspector;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ClientHeartbeatTaskBeanNameTest {

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern CLASS_PATTERN = Pattern.compile("(?m)\\bclass\\s+(\\w+)\\b");
    private static final Pattern SPRING_STEREOTYPE_PATTERN = Pattern.compile(
            "@(Component|Service|Controller|RestController|Repository|Configuration)\\s*(?:\\(\\s*\"([^\"]+)\"\\s*\\))?");

    public static void main(String[] args) throws Exception {
        new ClientHeartbeatTaskBeanNameTest().springComponentBeanNamesAreUnique();
    }

    public void springComponentBeanNamesAreUnique() throws Exception {
        Path sourceRoot = resolveSourceRoot();
        Map<String, List<String>> beanNames = new LinkedHashMap<>();

        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".java"))
                    .forEach(path -> collectComponentBeanName(path, beanNames));
        }

        Map<String, List<String>> duplicates = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : beanNames.entrySet()) {
            if (entry.getValue().size() > 1) {
                duplicates.put(entry.getKey(), entry.getValue());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new AssertionError("Expected unique Spring component bean names, duplicates=" + duplicates);
        }
    }

    private static Path resolveSourceRoot() {
        Path moduleRoot = Paths.get("src", "main", "java");
        if (Files.isDirectory(moduleRoot)) {
            return moduleRoot;
        }
        return Paths.get("info-publish-client", "src", "main", "java");
    }

    private static void collectComponentBeanName(Path path, Map<String, List<String>> beanNames) {
        try {
            String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            String packageName = firstGroup(PACKAGE_PATTERN, source);
            String className = firstGroup(CLASS_PATTERN, source);
            if (className == null) {
                return;
            }

            Matcher stereotype = SPRING_STEREOTYPE_PATTERN.matcher(source);
            if (!stereotype.find()) {
                return;
            }
            String beanName = stereotype.group(2);
            if (beanName == null || beanName.trim().isEmpty()) {
                beanName = Introspector.decapitalize(className);
            }

            String fqcn = packageName == null ? className : packageName + "." + className;
            if (!beanNames.containsKey(beanName)) {
                beanNames.put(beanName, new ArrayList<String>());
            }
            beanNames.get(beanName).add(fqcn);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to inspect " + path, e);
        }
    }

    private static String firstGroup(Pattern pattern, String source) {
        Matcher matcher = pattern.matcher(source);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }
}
