/*
 * Copyright Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Red Hat trademarks are not licensed under GPLv3. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
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
    context.loadCustomConfiguration(
        ContainerServiceConfiguration.class, new ContainerServiceConfigurationBuilder());
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
