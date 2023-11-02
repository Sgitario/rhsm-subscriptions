package org.candlepin.integration.tests.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.FIELD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface Kafka {
    String image() default "quay.io/strimzi/kafka:latest-kafka-3.1.0";
    String[] command() default { "sh", "/init_kafka.sh" };
    String expectedLog() default "";
    int[] ports() default 9092;
}
