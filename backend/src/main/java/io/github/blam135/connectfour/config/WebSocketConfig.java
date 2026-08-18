package io.github.blam135.connectfour.config;

import io.github.blam135.connectfour.websocket.GameWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler gameHandler;
    private final ConnectFourProperties properties;

    public WebSocketConfig(GameWebSocketHandler gameHandler, ConnectFourProperties properties) {
        this.gameHandler = gameHandler;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gameHandler, "/ws/game")
                .setAllowedOriginPatterns(
                        properties.websocket().allowedOriginPatterns().toArray(String[]::new));
    }
}
