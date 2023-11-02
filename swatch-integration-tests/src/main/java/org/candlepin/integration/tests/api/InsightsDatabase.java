package org.candlepin.integration.tests.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.FIELD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface InsightsDatabase {
    String image() default "quay.io/centos7/postgresql-13-centos7";
    int[] ports() default 5432;
    String jdbcName() default "postgresql";
    String expectedLog() default "Starting server";
    String[] command() default {};
    String user() default "insights";
    String userProperty() default "POSTGRESQL_USER";
    String password() default "insights";
    String passwordProperty() default "POSTGRESQL_PASSWORD";
    String database() default "insights";
    String databaseNameProperty() default "POSTGRESQL_DATABASE";
}
