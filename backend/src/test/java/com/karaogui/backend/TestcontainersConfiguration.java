package com.karaogui.backend;

import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:16"));
	}

	@Bean
	WebSocketStompClient webSocketStompClient() {
		WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
		client.setMessageConverter(new AbstractMessageConverter(
				MimeTypeUtils.TEXT_PLAIN,
				MimeTypeUtils.APPLICATION_JSON,
				MimeTypeUtils.parseMimeType("application/json;charset=UTF-8")) {
			@Override
			protected boolean supports(Class<?> clazz) {
				return String.class.isAssignableFrom(clazz);
			}
			@Override
			protected Object convertFromInternal(org.springframework.messaging.Message<?> message,
					Class<?> targetClass, Object conversionHint) {
				Object payload = message.getPayload();
				if (payload instanceof String s) return s;
				if (payload instanceof byte[] bytes) return new String(bytes);
				return payload.toString();
			}
			@Override
			protected Object convertToInternal(Object payload, MessageHeaders headers,
					Object conversionHint) {
				return payload.toString().getBytes();
			}
		});
		return client;
	}

}
