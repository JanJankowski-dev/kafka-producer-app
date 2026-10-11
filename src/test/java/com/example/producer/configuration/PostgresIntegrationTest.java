package com.example.producer.configuration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "app.outbox.enabled=false"
})
@Import(PostgresTestConfiguration.class)
@ActiveProfiles("synthetic-demo")
public @interface PostgresIntegrationTest {
}
