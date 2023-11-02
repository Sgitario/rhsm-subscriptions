package org.candlepin.integration.tests.resources.containers;

import io.jeasyarch.api.DatabaseService;
import io.jeasyarch.api.PostgresqlContainer;
import io.jeasyarch.api.Service;
import io.jeasyarch.core.JEasyArchContext;
import io.jeasyarch.core.ManagedResource;
import io.jeasyarch.resources.containers.ContainerAnnotationBinding;
import org.candlepin.integration.tests.api.RhsmSubscriptionsDatabase;
import org.candlepin.integration.tests.api.RhsmSubscriptionsDatabaseService;

import java.lang.annotation.Annotation;

public class RhsmSubscriptionsDatabaseAnnotationBinding extends ContainerAnnotationBinding {

    @Override
    public boolean isFor(Annotation... annotations) {
        return findAnnotation(annotations, RhsmSubscriptionsDatabase.class).isPresent();
    }

    @Override
    public Service getDefaultServiceImplementation() {
        return new RhsmSubscriptionsDatabaseService();
    }

    @Override
    public ManagedResource getManagedResource(JEasyArchContext context, Service service, Annotation... annotations) {
        RhsmSubscriptionsDatabase metadata = findAnnotation(annotations, RhsmSubscriptionsDatabase.class).get();

        if (!(service instanceof DatabaseService)) {
            throw new IllegalStateException("@RhsmSubscriptionsDatabase can only be used with DatabaseService service");
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
