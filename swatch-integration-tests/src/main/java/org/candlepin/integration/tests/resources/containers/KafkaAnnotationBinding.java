package org.candlepin.integration.tests.resources.containers;

import io.jeasyarch.api.Service;
import io.jeasyarch.api.extensions.AnnotationBinding;
import io.jeasyarch.core.JEasyArchContext;
import io.jeasyarch.core.ManagedResource;
import io.jeasyarch.resources.containers.ContainerAnnotationBinding;
import org.candlepin.integration.tests.api.Kafka;

import java.lang.annotation.Annotation;

public class KafkaAnnotationBinding implements AnnotationBinding {

    @Override
    public boolean isFor(Annotation... annotations) {
        return findAnnotation(annotations, Kafka.class).isPresent();
    }

    @Override
    public ManagedResource getManagedResource(JEasyArchContext context, Service service, Annotation... annotations) {
        return new KafkaTestContainersManagedResource();
    }
}
