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
package org.candlepin.metrics.telemeter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.candlepin.integration.tests.utils.Constants.PROMETHEUS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.redhat.swatch.configuration.registry.Metric;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import io.jeasyarch.api.DatabaseService;
import io.jeasyarch.api.DefaultService;
import io.jeasyarch.api.JEasyArch;
import io.jeasyarch.api.RestService;
import io.jeasyarch.api.Spring;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.candlepin.integration.tests.api.InsightsDatabase;
import org.candlepin.integration.tests.api.Kafka;
import org.candlepin.integration.tests.api.Prometheus;
import org.candlepin.integration.tests.api.PrometheusWiremockService;
import org.candlepin.integration.tests.api.RhsmSubscriptionsDatabase;
import org.candlepin.integration.tests.api.RhsmSubscriptionsDatabaseService;
import org.candlepin.integration.tests.api.TallyService;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.prometheus.model.QueryResult;
import org.candlepin.subscriptions.prometheus.model.QueryResultData;
import org.candlepin.subscriptions.prometheus.model.QueryResultDataResultInner;
import org.candlepin.subscriptions.prometheus.model.ResultType;
import org.candlepin.subscriptions.prometheus.model.StatusType;
import org.junit.jupiter.api.Test;

@JEasyArch
class MeteringMetricsFromPrometheusToDatabaseIT {

  private static final int NUM_METRICS_TO_SEND = 5;
  private static final Duration TIMEOUT_TO_WAIT_FOR_METRICS = Duration.ofSeconds(5);
  private static final String PRODUCT_TAG = "rosa";
  private static final MetricId CORES = MetricId.fromString("Cores");
  private static final String ORG_ID = "1111";

  @RhsmSubscriptionsDatabase
  static final RhsmSubscriptionsDatabaseService rhsmDatabase =
      new RhsmSubscriptionsDatabaseService();

  @InsightsDatabase static final DatabaseService insightsDatabase = new DatabaseService();

  @Kafka static final DefaultService kafka = new DefaultService();

  @Prometheus static final PrometheusWiremockService prometheus = new PrometheusWiremockService();

  @Spring(location = "../")
  static final TallyService tally =
      TallyService.ofWorkerProfile()
          .withKafka(kafka)
          .withDataSource(rhsmDatabase)
          .withInventoryDatasource(insightsDatabase);

  @Spring(location = "../")
  static final RestService metrics =
      TallyService.ofTelemeterMetricsProfile(prometheus)
          .enableSyncOperations()
          .withKafka(kafka)
          .withDataSource(rhsmDatabase);

  @Test
  void testCreateNewMetricsAndUpdateExistingEvents() {
    OffsetDateTime timestampForEvents = OffsetDateTime.now();

    givenMetricsInPrometheusWithUsage(timestampForEvents, Event.Usage.DEVELOPMENT_TEST);

    // all insert
    whenCollectMetrics();
    verifyAllEventsAreStoredInDatabaseWithUsage(Event.Usage.DEVELOPMENT_TEST);
    // store existing events to assert that event ID didn't change.
    List<UUID> snapshotOfExistingEvents = rhsmDatabase.getEventIdFromEvents();

    // all update
    givenMetricsInPrometheusWithUsage(timestampForEvents, Event.Usage.PRODUCTION);
    whenCollectMetrics();
    verifyAllEventsAreStoredInDatabaseWithUsage(Event.Usage.PRODUCTION);
    verifyAllEventsUseEventSourcePrometheus();
    assertThat(snapshotOfExistingEvents)
        .containsExactlyInAnyOrderElementsOf(rhsmDatabase.getEventIdFromEvents());
  }

  private void givenMetricsInPrometheusWithUsage(OffsetDateTime timestamp, Event.Usage usage) {
    prometheus.resetScenario();

    SubscriptionDefinition product =
        SubscriptionDefinition.lookupSubscriptionByTag(PRODUCT_TAG)
            .orElseThrow(() -> new RuntimeException("Product '" + PRODUCT_TAG + "' not found!"));

    Metric metric =
        product
            .getMetric(CORES.getValue())
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "Metric '" + CORES.getValue() + "' not found in product!"));
    ;

    QueryResult expectedResult = new QueryResult();
    expectedResult.status(StatusType.SUCCESS);
    QueryResultData expectedData = new QueryResultData();
    expectedData.resultType(ResultType.MATRIX);
    List<QueryResultDataResultInner> metricsList = new ArrayList<>();
    for (int i = 0; i < NUM_METRICS_TO_SEND; i++) {
      QueryResultDataResultInner data = new QueryResultDataResultInner();
      data.values(
          List.of(
              List.of(
                  new BigDecimal(timestamp.plusSeconds(100).toEpochSecond()), new BigDecimal(1))));
      Map<String, String> labels = new HashMap<>();
      labels.put(metric.getPrometheus().getQueryParams().get("instanceKey"), "id" + i);
      labels.put("product", "ocp");
      labels.put("support", "Premium");
      labels.put("usage", usage.value());
      data.metric(labels);
      metricsList.add(data);
    }
    expectedData.result(metricsList);
    expectedResult.data(expectedData);

    prometheus.stubQueryRange(
        metric.getPrometheus().getQueryParams().get("metric"), expectedResult);
  }

  private void whenCollectMetrics() {
    metrics
        .given()
        .queryParam("orgId", ORG_ID)
        .header("Origin", "console.redhat.com")
        .header("x-rh-swatch-synchronous-request", "true")
        .header("x-rh-swatch-psk", "placeholder")
        .header("x-rh-identity", identity())
        .post("/api/rhsm-subscriptions/v1/internal/metering/" + PRODUCT_TAG)
        .then()
        .statusCode(HttpStatus.SC_NO_CONTENT);
  }

  private void verifyAllEventsAreStoredInDatabaseWithUsage(Event.Usage expectedUsage) {
    await()
        .atMost(TIMEOUT_TO_WAIT_FOR_METRICS)
        .untilAsserted(
            () -> {
              assertEquals(NUM_METRICS_TO_SEND, rhsmDatabase.countEvents());
            });
  }

  private void verifyAllEventsUseEventSourcePrometheus() {
    rhsmDatabase.openStatement(
        statement -> {
          try {
            ResultSet rs = statement.executeQuery("select event_source from events");
            while (rs.next()) {
              assertEquals(PROMETHEUS, rs.getString(1));
            }
          } catch (SQLException e) {
            fail("Error running the query. Cause: " + e.getMessage());
          }
        });
  }

  private String identity() {
    String identity =
        "{\"identity\":{\"account_number\":\"\","
            + "\"type\":\"User\","
            + "\"user\":{\"is_org_admin\":true},"
            + "\"internal\":{\"org_id\":\""
            + ORG_ID
            + "\"}}}";
    return Base64.getEncoder().encodeToString(identity.getBytes(StandardCharsets.UTF_8));
  }
}
