package io.github.blam135.connectfour.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("connectfour")
public record ConnectFourProperties(WebSocket websocket) {

    public record WebSocket(List<String> allowedOriginPatterns) {}
}
