package com.yuegang.zhihui.tools;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public final class LocalServiceLauncher {
    private static final Map<String, String> MAIN_CLASSES = new LinkedHashMap<>();

    static {
        MAIN_CLASSES.put("gateway", "com.yuegang.zhihui.gateway.GatewayApplication");
        MAIN_CLASSES.put("system", "com.yuegang.zhihui.system.SystemApplication");
        MAIN_CLASSES.put("user", "com.yuegang.zhihui.user.UserApplication");
        MAIN_CLASSES.put("auth", "com.yuegang.zhihui.auth.AuthApplication");
        MAIN_CLASSES.put("product", "com.yuegang.zhihui.product.ProductApplication");
        MAIN_CLASSES.put("search", "com.yuegang.zhihui.search.SearchApplication");
        MAIN_CLASSES.put("knowledge", "com.yuegang.zhihui.knowledge.KnowledgeApplication");
        MAIN_CLASSES.put("inventory", "com.yuegang.zhihui.inventory.InventoryApplication");
        MAIN_CLASSES.put("order", "com.yuegang.zhihui.order.OrderApplication");
        MAIN_CLASSES.put("training", "com.yuegang.zhihui.training.TrainingApplication");
        MAIN_CLASSES.put("ai", "com.yuegang.zhihui.ai.AiApplication");
        MAIN_CLASSES.put("wallet", "com.yuegang.zhihui.wallet.WalletApplication");
        MAIN_CLASSES.put("notification", "com.yuegang.zhihui.notification.NotificationApplication");
        MAIN_CLASSES.put("admin", "com.yuegang.zhihui.admin.AdminApplication");
        MAIN_CLASSES.put("system-migration", "com.yuegang.zhihui.system.SystemMigrationApplication");
        MAIN_CLASSES.put("user-migration", "com.yuegang.zhihui.user.UserMigrationApplication");
        MAIN_CLASSES.put("auth-migration", "com.yuegang.zhihui.auth.AuthMigrationApplication");
    }

    private LocalServiceLauncher() {
    }

    public static void main(String[] args) throws Exception {
        Arguments parsed = Arguments.parse(args);
        if (parsed.help()) {
            printUsage();
            return;
        }

        Path envFile = parsed.envFile();
        if (Files.notExists(envFile)) {
            throw new IllegalArgumentException("Config file does not exist: " + envFile);
        }

        int loaded = loadConfiguration(envFile);
        if (parsed.checkOnly()) {
            System.out.println("OK: loaded " + loaded + " config entries from " + envFile);
            return;
        }

        String mainClassName = MAIN_CLASSES.get(parsed.service());
        if (mainClassName == null) {
            throw new IllegalArgumentException("Unknown service: " + parsed.service()
                    + ". Available: " + MAIN_CLASSES.keySet());
        }

        applyRuntimeDefaults(parsed.service());
        invokeMain(mainClassName, parsed.applicationArgs());
    }

    private static int loadConfiguration(Path file) throws IOException {
        String filename = file.getFileName().toString().toLowerCase();
        Map<String, String> values;
        if (filename.endsWith(".yaml") || filename.endsWith(".yml")) {
            values = loadYaml(file);
        } else {
            values = loadProperties(file);
        }

        int loaded = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (value != null && !value.isBlank() && System.getProperty(name) == null) {
                System.setProperty(name, value.trim());
                loaded++;
            }
        }
        return loaded;
    }

    private static Map<String, String> loadProperties(Path file) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) {
            values.put(name, properties.getProperty(name));
        }
        return values;
    }

    private static Map<String, String> loadYaml(Path file) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        int lineNumber = 0;
        for (String rawLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            lineNumber++;
            String line = stripYamlComment(rawLine).trim();
            if (line.isEmpty() || line.equals("---")) {
                continue;
            }
            if (Character.isWhitespace(rawLine.charAt(0))) {
                throw new IllegalArgumentException("Nested YAML is not supported in " + file
                        + " at line " + lineNumber + ": " + rawLine);
            }

            int separator = line.indexOf(':');
            if (separator <= 0) {
                throw new IllegalArgumentException("Expected 'KEY: value' in " + file
                        + " at line " + lineNumber + ": " + rawLine);
            }
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (key.isEmpty()) {
                throw new IllegalArgumentException("YAML key must not be blank in " + file
                        + " at line " + lineNumber);
            }
            values.put(key, unquoteYamlScalar(value));
        }
        return values;
    }

    private static String stripYamlComment(String rawLine) {
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        for (int i = 0; i < rawLine.length(); i++) {
            char current = rawLine.charAt(i);
            if (current == '\'' && !doubleQuoted) {
                singleQuoted = !singleQuoted;
            } else if (current == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
            } else if (current == '#' && !singleQuoted && !doubleQuoted) {
                return rawLine.substring(0, i);
            }
        }
        return rawLine;
    }

    private static String unquoteYamlScalar(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            String unquoted = value.substring(1, value.length() - 1);
            if (value.startsWith("\"")) {
                return unquoted.replace("\\\"", "\"").replace("\\\\", "\\");
            }
            return unquoted.replace("''", "'");
        }
        return value;
    }

    private static void applyRuntimeDefaults(String service) {
        System.setProperty("spring.main.banner-mode", System.getProperty("spring.main.banner-mode", "console"));
        if (!service.endsWith("-migration")) {
            System.setProperty("spring.flyway.enabled", System.getProperty("spring.flyway.enabled", "false"));
        }
    }

    private static void invokeMain(String mainClassName, String[] args) throws Exception {
        Class<?> mainClass = Class.forName(mainClassName);
        Method main = mainClass.getMethod("main", String[].class);
        try {
            main.invoke(null, (Object) args);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw failure;
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java -cp <runner-and-service-classpath> "
                + LocalServiceLauncher.class.getName()
                + " --env=<external-yaml-or-properties-file> [--check] <service>");
        System.out.println("Services: " + MAIN_CLASSES.keySet());
    }

    private record Arguments(String service, Path envFile, String[] applicationArgs, boolean help, boolean checkOnly) {
        static Arguments parse(String[] args) {
            if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
                return new Arguments("", Path.of(""), new String[0], true, false);
            }

            Path envFile = Path.of(System.getenv().getOrDefault("YGH_LOCAL_ENV_FILE",
                    "E:/ygh-secrets/env/ygh-core-services.yaml"));
            String service = null;
            int serviceIndex = -1;
            boolean checkOnly = false;

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg.startsWith("--env=")) {
                    envFile = Path.of(arg.substring("--env=".length()));
                } else if ("--env".equals(arg)) {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--env requires a file path");
                    }
                    envFile = Path.of(args[++i]);
                } else if ("--check".equals(arg)) {
                    checkOnly = true;
                } else {
                    service = arg;
                    serviceIndex = i;
                    break;
                }
            }

            if (service == null && !checkOnly) {
                throw new IllegalArgumentException("Missing service name");
            }

            String[] applicationArgs = serviceIndex < 0
                    ? new String[0]
                    : new String[Math.max(0, args.length - serviceIndex - 1)];
            if (applicationArgs.length > 0) {
                System.arraycopy(args, serviceIndex + 1, applicationArgs, 0, applicationArgs.length);
            }
            return new Arguments(service, envFile, applicationArgs, false, checkOnly);
        }
    }
}
