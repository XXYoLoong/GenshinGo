package com.zyf.deepseek.config;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "deepseek", name = "launch-browser", havingValue = "true")
public class LaunchBrowserOnReady implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(LaunchBrowserOnReady.class);

    private final Environment environment;

    public LaunchBrowserOnReady(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        String url = buildHomeUrl();
        if (tryWindowsDefaultBrowser(url)) {
            return;
        }
        if (!GraphicsEnvironment.isHeadless()
                && Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(new URI(url));
                return;
            } catch (Exception e) {
                log.warn("无法自动打开浏览器: {}", e.getMessage());
            }
        }
        log.warn("无法自动打开浏览器，请手动访问: {}", url);
    }

    private String buildHomeUrl() {
        Integer port = environment.getProperty("local.server.port", Integer.class);
        if (port == null) {
            port = environment.getProperty("server.port", Integer.class, 8080);
        }
        return "http://localhost:" + port + "/";
    }

    /** Windows：不依赖 AWT，headless 下也可用 {@code start} 调起默认浏览器 */
    private static boolean tryWindowsDefaultBrowser(String url) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            return false;
        }
        try {
            new ProcessBuilder("cmd", "/c", "start", "", url)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return true;
        } catch (IOException e) {
            log.warn("调用系统浏览器失败: {}", e.getMessage());
            return false;
        }
    }
}
