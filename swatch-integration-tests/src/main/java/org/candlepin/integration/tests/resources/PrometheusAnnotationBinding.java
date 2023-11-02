package org.candlepin.integration.tests.resources;

import io.jeasyarch.api.Service;
import io.jeasyarch.api.extensions.AnnotationBinding;
import io.jeasyarch.core.JEasyArchContext;
import io.jeasyarch.core.ManagedResource;
import io.jeasyarch.resources.containers.ContainerAnnotationBinding;
import io.jeasyarch.resources.containers.local.DefaultGenericContainerManagedResource;
import org.candlepin.integration.tests.api.Kafka;
import org.candlepin.integration.tests.api.Prometheus;
import org.candlepin.integration.tests.resources.wiremock.WiremockManagedResource;

import java.lang.annotation.Annotation;

public class PrometheusAnnotationBinding implements AnnotationBinding {

    @Override
    public boolean isFor(Annotation... annotations) {
        return findAnnotation(annotations, Prometheus.class).isPresent();
    }

    @Override
    public ManagedResource getManagedResource(JEasyArchContext context, Service service, Annotation... annotations) {
        Prometheus metadata = findAnnotation(annotations, Prometheus.class).get();

        return new WiremockManagedResource();
    }
}
