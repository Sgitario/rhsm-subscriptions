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
package org.candlepin.integration.tests.api;

import static org.candlepin.integration.tests.utils.Constants.PROMETHEUS;

import io.jeasyarch.api.DatabaseService;
import io.jeasyarch.api.RestService;
import io.jeasyarch.core.BaseService;
import java.util.function.Supplier;

public class TallyService extends RestService {

  private static final String SPRING_PROFILES_ACTIVE = "spring.profiles.active";
  private static final String KAFKA_SERVER_PROPERTY = "spring.kafka.bootstrap-servers";

  private TallyService(String... profiles) {
    withProperty(SPRING_PROFILES_ACTIVE, String.join(",", profiles));
  }

  public TallyService withKafka(BaseService<?> kafka) {
    return withProperty(
        KAFKA_SERVER_PROPERTY, () -> kafka.getHost() + ":" + kafka.getFirstMappedPort());
  }

  public TallyService useStub() {
    withProperty("rhsm-subscriptions.subscription.use-stub", "true");
    withProperty("rhsm-subscriptions.user-service.use-stub", "true");
    withProperty("rhsm-subscriptions.rbac-service.use-stub", "true");
    return this;
  }

  public TallyService enableSyncOperations() {
    return withProperty("rhsm-subscriptions.enable-synchronous-operations", "true");
  }

  public TallyService withDataSource(DatabaseService database) {
    return withProperty("rhsm-subscriptions.datasource.url", database::getJdbcUrl);
  }

  public TallyService withInventoryDatasource(DatabaseService database) {
    return withProperty(
        "rhsm-subscriptions.inventory-service.datasource.url", database::getJdbcUrl);
  }

  @Override
  public TallyService withProperty(String key, String value) {
    super.withProperty(key, value);
    return this;
  }

  @Override
  public TallyService withProperty(String key, Supplier<String> value) {
    super.withProperty(key, value);
    return this;
  }

  public static TallyService ofWorkerProfile() {
    return new TallyService("worker");
  }

  public static TallyService ofTelemeterMetricsProfile(PrometheusWiremockService prometheus) {
    TallyService service = new TallyService("openshift-metering-worker", "kafka-queue");
    service.withProperty("rhsm-subscriptions.metering.prometheus.metric.event-source", PROMETHEUS);
    service.withProperty("rhsm-subscriptions.metering.prometheus.metric.max-attempts", "1");
    service.withProperty(
        "rhsm-subscriptions.metering.prometheus.client.url", prometheus::getClientUrl);
    return service;
  }
}
