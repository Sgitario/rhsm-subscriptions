package org.candlepin.integration.tests.resources.containers;

import io.jeasyarch.configuration.ContainerServiceConfiguration;
import io.jeasyarch.configuration.ContainerServiceConfigurationBuilder;
import io.jeasyarch.core.ManagedResource;
import io.jeasyarch.core.ServiceContext;
import io.jeasyarch.logging.LoggingHandler;
import io.jeasyarch.logging.TestContainersLoggingHandler;
import io.jeasyarch.resources.containers.local.ContainerJEasyArchNetwork;
import org.testcontainers.containers.KafkaContainer;

public class KafkaTestContainersManagedResource extends ManagedResource {

    private ContainerJEasyArchNetwork network;
    private KafkaContainer innerContainer;
    private LoggingHandler loggingHandler;

    @Override
    public String getDisplayName() {
        return context.getName();
    }

    @Override
    public void start() {
        if (isRunning()) {
            return;
        }

        network = ContainerJEasyArchNetwork.getOrCreate(context.getJEasyArchContext());
        network.attachService(context);

        innerContainer = new KafkaContainer();
        innerContainer.withNetwork(network);
        innerContainer.withNetworkAliases(context.getName());
        innerContainer.withStartupTimeout(context.getConfiguration().getStartupTimeout());

        // SMELL: Workaround for https://github.com/testcontainers/testcontainers-java/issues/7539
        // This is because testcontainers randomly fails to start a container when using Podman socket.
        innerContainer.withStartupAttempts(3);

        loggingHandler = new TestContainersLoggingHandler(context.getOwner(), innerContainer);
        loggingHandler.startWatching();

        doStart();
    }

    @Override
    public void stop() {
        if (loggingHandler != null) {
            loggingHandler.stopWatching();
        }

        if (isRunning()) {
            innerContainer.stop();
            innerContainer = null;
        }
    }

    @Override
    public String getHost() {
        return innerContainer.getHost();
    }

    @Override
    public int getFirstMappedPort() {
        return innerContainer.getFirstMappedPort();
    }

    @Override
    public int getMappedPort(int port) {
        return innerContainer.getMappedPort(port);
    }

    @Override
    public boolean isRunning() {
        return innerContainer != null && innerContainer.isRunning();
    }

    @Override
    protected LoggingHandler getLoggingHandler() {
        return loggingHandler;
    }

    @Override
    protected void init(ServiceContext context) {
        super.init(context);
        context.loadCustomConfiguration(ContainerServiceConfiguration.class,
                new ContainerServiceConfigurationBuilder());
    }

    private void doStart() {
        try {
            innerContainer.start();
        } catch (Exception ex) {
            stop();

            throw ex;
        }
    }
}
