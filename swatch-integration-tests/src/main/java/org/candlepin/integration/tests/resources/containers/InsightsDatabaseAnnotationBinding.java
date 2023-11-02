package org.candlepin.integration.tests.resources.containers;

import io.jeasyarch.api.DatabaseService;
import io.jeasyarch.api.Service;
import io.jeasyarch.core.JEasyArchContext;
import io.jeasyarch.core.ManagedResource;
import io.jeasyarch.resources.containers.ContainerAnnotationBinding;
import org.candlepin.integration.tests.api.InsightsDatabase;
import org.candlepin.integration.tests.api.RhsmSubscriptionsDatabase;

import java.lang.annotation.Annotation;

public class InsightsDatabaseAnnotationBinding extends ContainerAnnotationBinding {

    @Override
    public boolean isFor(Annotation... annotations) {
        return findAnnotation(annotations, InsightsDatabase.class).isPresent();
    }

    @Override
    public ManagedResource getManagedResource(JEasyArchContext context, Service service, Annotation... annotations) {
        InsightsDatabase metadata = findAnnotation(annotations, InsightsDatabase.class).get();

        if (!(service instanceof DatabaseService)) {
            throw new IllegalStateException("@InsightsDatabase can only be used with DatabaseService service");
        }

        DatabaseService databaseService = (DatabaseService) service;
        databaseService.withJdbcName(metadata.jdbcName());
        databaseService.withDatabaseNameProperty(metadata.databaseNameProperty());
        databaseService.withUserProperty(metadata.userProperty());
        databaseService.withPasswordProperty(metadata.passwordProperty());
        databaseService.with(metadata.user(), metadata.password(), metadata.database());

        return doInit(context, service, metadata.image(), metadata.expectedLog(), metadata.command(), metadata.ports());
    }
}
