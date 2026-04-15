package com.example.demo.collect.dart.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

//application.yml의 api.dart 하위 속성들을 자동으로 매핑합니다.
@ConfigurationProperties(prefix = "api.dart")
	public record DartProperties(String baseUrl, String key) {
}