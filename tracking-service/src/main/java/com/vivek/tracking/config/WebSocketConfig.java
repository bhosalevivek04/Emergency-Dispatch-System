package com.vivek.tracking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		// Native WebSocket endpoint (no SockJS)
		registry.addEndpoint("/ws")
			.setAllowedOriginPatterns(
				"https://mobile-driver-app.onrender.com", 
				"http://localhost:*",
				"http://127.0.0.1:*"
			);
		
		// SockJS fallback endpoint
		registry.addEndpoint("/ws-sockjs")
			.setAllowedOriginPatterns(
				"https://mobile-driver-app.onrender.com", 
				"http://localhost:*",
				"http://127.0.0.1:*"
			)
			.withSockJS()
			.setSessionCookieNeeded(false);  // Important!
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic");
		registry.setApplicationDestinationPrefixes("/app");
	}
}
