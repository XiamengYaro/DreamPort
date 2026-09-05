package cn.xmcraft.dreamport.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 部署配置初始化（Minecraft 插件式体验）：
 * 1. 首次启动在工作目录生成 config.yml（带中文注释的完整部署模板）
 * 2. 把 config.yml 作为高优先级属性源加载 —— 同一次启动即生效
 * 用户只需：启动 → 编辑 config.yml → 重启，无需环境变量。
 * 环境变量仍可覆盖（Docker/CI 场景），命令行参数优先级最高。
 */
public class DreamPortConfigInitializer implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DreamPortConfigInitializer.class);
    static final String CONFIG_FILE = "config.yml";
    static final String PROPERTY_SOURCE_NAME = "dreamportConfigFile";

    @Override
    public int getOrder() {
        // 在 ConfigData（application.yml 解析）之后执行
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        Path file = Path.of(CONFIG_FILE);
        if (!Files.exists(file)) {
            generateTemplate(file);
            log.warn("已生成部署配置 {} —— 修改后重启生效（生产请完成 [必改] 项）", file.toAbsolutePath());
        }
        try {
            Map<String, Object> source = new Yaml().load(Files.readString(file, StandardCharsets.UTF_8));
            if (source != null && !source.isEmpty()) {
                // 插到命令行参数之后（存在时），保证 CLI > 配置文件 > 打包默认值
                if (environment.getPropertySources().contains(
                        org.springframework.core.env.CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME)) {
                    environment.getPropertySources().addAfter(
                            org.springframework.core.env.CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME,
                            new MapPropertySource(PROPERTY_SOURCE_NAME, flatten(source)));
                } else {
                    environment.getPropertySources().addFirst(
                            new MapPropertySource(PROPERTY_SOURCE_NAME, flatten(source)));
                }
                log.info("已加载部署配置 {}", file.toAbsolutePath());
            }
        } catch (Exception e) {
            log.error("config.yml 解析失败（将使用默认配置）: {}", e.getMessage());
        }
    }

    private void generateTemplate(Path file) {
        try {
            var in = new ClassPathResource("install/config-template.yml").getInputStream();
            Files.writeString(file, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("config.yml 模板生成失败: {}", e.getMessage());
        }
    }

    /** 扁平化嵌套 map（a.b.c 形式），确保属性源可被正确解析 */
    @SuppressWarnings("unchecked")
    static Map<String, Object> flatten(Map<String, Object> nested) {
        Map<String, Object> flat = new java.util.LinkedHashMap<>();
        flattenInto("", nested, flat);
        return flat;
    }

    @SuppressWarnings("unchecked")
    private static void flattenInto(String prefix, Map<String, Object> nested, Map<String, Object> flat) {
        for (Map.Entry<String, Object> e : nested.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            if (e.getValue() instanceof Map<?, ?> child && !(child instanceof java.util.List)) {
                flattenInto(key, (Map<String, Object>) child, flat);
            } else {
                flat.put(key, e.getValue());
            }
        }
    }
}
