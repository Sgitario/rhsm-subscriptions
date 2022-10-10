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
package org.candlepin.subscriptions.tally.job;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.db.AccountConfigRepository;
import org.candlepin.subscriptions.db.AccountListSource;
import org.candlepin.subscriptions.db.OrgListSource;
import org.candlepin.subscriptions.tally.OrgListSourceException;
import org.candlepin.subscriptions.task.TaskDescriptor;
import org.candlepin.subscriptions.task.TaskManagerException;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.task.TaskType;
import org.candlepin.subscriptions.task.queue.inmemory.ExecutorTaskQueue;
import org.candlepin.subscriptions.task.queue.inmemory.ExecutorTaskQueueConsumerFactory;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.util.DateRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(classes = FixedClockConfiguration.class)
@ActiveProfiles({"worker", "test"})
class CaptureSnapshotsTaskManagerTest {

  @MockBean ExecutorTaskQueue queue;

  @MockBean ExecutorTaskQueueConsumerFactory consumerFactory;

  @Autowired private CaptureSnapshotsTaskManager manager;

  @MockBean private AccountListSource accountListSource;

  @Autowired private TaskQueueProperties taskQueueProperties;

  @Autowired private ApplicationProperties appProperties;

  @Autowired ApplicationClock applicationClock;

  @MockBean private AccountConfigRepository accountRepo;

  @MockBean private OrgListSource orgList;

  public static final String ORG_ID = "org123";
  public static final String ACCOUNT = "foo123";

  // Deprecate this test once accountNumber to orgId is finished.
  @Test
  void testUpdateForSingleAccount_WhenOrgPresent() {
    when(accountRepo.findOrgByAccountNumber(ACCOUNT)).thenReturn(ORG_ID);
    manager.updateAccountSnapshots(ACCOUNT);
    verify(queue).enqueue(createDescriptorOrg(ORG_ID));
  }

  // Deprecate this test once accountNumber to orgId is finished.
  @Test
  void testUpdateForSingleAccount_WhenOrgAbsent() {
    when(accountRepo.findOrgByAccountNumber(ACCOUNT)).thenReturn(null);
    manager.updateAccountSnapshots(ACCOUNT);
    verify(queue).enqueue(createDescriptorAccount(ACCOUNT));
  }

  @Test
  void testUpdateForSingleOrg() {
    manager.updateOrgSnapshots(ORG_ID);
    verify(queue).enqueue(createDescriptorOrg(ORG_ID));
  }

  @Test
  void ensureUpdateIsRunForEachOrg() throws Exception {
    List<String> expectedOrgList = Arrays.asList("o1", "o2");
    when(orgList.getAllOrgToSync()).thenReturn(expectedOrgList.stream());

    manager.updateSnapshotsForAllOrg();

    verify(queue, times(1)).enqueue(createDescriptorOrg(expectedOrgList));
  }

  @Test
  void ensureOrgListIsPartitionedWhenSendingTaskMessages() throws Exception {
    List<String> expectedOrgList = Arrays.asList("o1", "o2", "o3", "o4");
    when(orgList.getAllOrgToSync()).thenReturn(expectedOrgList.stream());

    manager.updateSnapshotsForAllOrg();

    // NOTE: Partition size is defined in test.properties
    verify(queue, times(1)).enqueue(createDescriptorOrg(Arrays.asList("o1", "o2")));
    verify(queue, times(1)).enqueue(createDescriptorOrg(Arrays.asList("o3", "o4")));
  }

  @Test
  void ensureLastOrgListPartitionIsIncludedWhenSendingTaskMessages() throws Exception {
    List<String> expectedOrgList = Arrays.asList("o1", "o2", "o3", "o4", "o5");
    when(orgList.getAllOrgToSync()).thenReturn(expectedOrgList.stream());

    manager.updateSnapshotsForAllOrg();

    // NOTE: Partition size is defined in test.properties
    verify(queue, times(1)).enqueue(createDescriptorOrg(List.of("o1", "o2")));
    verify(queue, times(1)).enqueue(createDescriptorOrg(List.of("o3", "o4")));
    verify(queue, times(1)).enqueue(createDescriptorOrg(List.of("o5")));
  }

  @Test
  void ensureErrorOnUpdateContinuesWithoutFailure() throws Exception {
    List<String> expectedOrgList = Arrays.asList("o1", "o2", "o3", "o4", "o5", "o6");
    when(orgList.getAllOrgToSync()).thenReturn(expectedOrgList.stream());

    doThrow(new RuntimeException("Forced!"))
        .when(queue)
        .enqueue(createDescriptorAccount(Arrays.asList("o3", "o4")));

    manager.updateSnapshotsForAllOrg();

    verify(queue, times(1)).enqueue(createDescriptorOrg(Arrays.asList("o1", "o2")));
    verify(queue, times(1)).enqueue(createDescriptorOrg(Arrays.asList("o3", "o4")));
    // Even though o3,o4 throws exception, o5,o6 should be enqueued.
    verify(queue, times(1)).enqueue(createDescriptorOrg(Arrays.asList("o5", "o6")));
  }

  @Test
  void ensureNoUpdatesWhenOrgListCanNotBeRetreived() throws Exception {
    doThrow(new OrgListSourceException("Forced!", new RuntimeException()))
        .when(orgList)
        .getAllOrgToSync();

    assertThrows(
        TaskManagerException.class,
        () -> {
          manager.updateSnapshotsForAllOrg();
        });

    verify(queue, never()).enqueue(any());
  }

  /**
   * Test hourly snapshots using the minutes offset. The offset is designed to ensure the snapshot
   * includes as many finished tallies as possible, and allows an additional hour for them to
   * finish.
   *
   * @throws Exception
   */
  @Test
  void testHourlySnapshotTallyOffset() throws Exception {
    List<String> expectedAccounts = Arrays.asList("a1", "a2");
    when(accountListSource.syncableAccounts()).thenReturn(expectedAccounts.stream());

    Duration metricRange = appProperties.getMetricLookupRangeDuration();
    Duration prometheusLatencyDuration = appProperties.getPrometheusLatencyDuration();
    Duration hourlyTallyOffsetMinutes = appProperties.getHourlyTallyOffset();

    OffsetDateTime endDateTime =
        adjustTimeForLatency(
            applicationClock.now().minus(hourlyTallyOffsetMinutes).truncatedTo(ChronoUnit.HOURS),
            prometheusLatencyDuration);
    OffsetDateTime startDateTime = endDateTime.minus(metricRange);

    manager.updateHourlySnapshotsForAllAccounts(Optional.empty());

    expectedAccounts.forEach(
        accountNumber -> {
          verify(queue, times(1))
              .enqueue(
                  TaskDescriptor.builder(
                          TaskType.UPDATE_HOURLY_SNAPSHOTS, taskQueueProperties.getTopic())
                      .setSingleValuedArg("accountNumber", accountNumber)
                      // 2019-05-24T12:35Z truncated to top of the hour - 1 hour tally range
                      .setSingleValuedArg("startDateTime", "2019-05-24T10:00:00Z")
                      .setSingleValuedArg("endDateTime", "2019-05-24T11:00:00Z")
                      .build());
        });
  }

  @Test
  void testHourlySnapshotForAllAccountsForDateRange() throws Exception {
    List<String> expectedAccounts = Arrays.asList("a1", "a2");
    when(accountListSource.syncableAccounts()).thenReturn(expectedAccounts.stream());

    DateRange range =
        new DateRange(applicationClock.now().minusDays(10L), applicationClock.now().minusDays(5L));

    manager.updateHourlySnapshotsForAllAccounts(Optional.of(range));

    expectedAccounts.forEach(
        accountNumber -> {
          verify(queue, times(1))
              .enqueue(
                  TaskDescriptor.builder(
                          TaskType.UPDATE_HOURLY_SNAPSHOTS, taskQueueProperties.getTopic())
                      .setSingleValuedArg("accountNumber", accountNumber)
                      // 10 days less than the test clock at the top of the hour.
                      .setSingleValuedArg("startDateTime", "2019-05-14T12:00:00Z")
                      // 5 days less than the test clock at the top of the hour.
                      .setSingleValuedArg("endDateTime", "2019-05-19T12:00:00Z")
                      .build());
        });
  }

  private TaskDescriptor createDescriptorAccount(String account) {
    return createDescriptorAccount(List.of(account));
  }

  private TaskDescriptor createDescriptorAccount(List<String> accounts) {
    return TaskDescriptor.builder(TaskType.UPDATE_SNAPSHOTS, taskQueueProperties.getTopic())
        .setArg("accounts", accounts)
        .build();
  }

  private TaskDescriptor createDescriptorOrg(String org) {
    return createDescriptorOrg(List.of(org));
  }

  private TaskDescriptor createDescriptorOrg(List<String> orgs) {
    return TaskDescriptor.builder(TaskType.UPDATE_SNAPSHOTS, taskQueueProperties.getTopic())
        .setArg("orgs", orgs)
        .build();
  }

  protected OffsetDateTime adjustTimeForLatency(
      OffsetDateTime dateTime, Duration adjustmentAmount) {
    return dateTime.toZonedDateTime().minus(adjustmentAmount).toOffsetDateTime();
  }

  @ParameterizedTest(name = "testAdjustTimeForLatency[{index}] {arguments}")
  @CsvSource({
    "2021-02-01T00:00:00Z, PT0H, 2021-02-01T00:00:00Z",
    "2021-02-01T00:00:00Z, PT1H, 2021-01-31T23:00:00Z",
    "2021-02-01T00:00:00Z, PT25H, 2021-01-30T23:00:00Z",
    "2021-02-01T00:00:00Z, PT-1H, 2021-02-01T01:00:00Z",
    "2021-02-01T00:00:00Z, PT1M, 2021-01-31T23:59:00Z",
    "2021-02-01T00:00:00Z, P1D, 2021-01-31T00:00:00Z"
  })
  void testAdjustTimeForLatency(
      OffsetDateTime originalDateTime, Duration latencyDuration, OffsetDateTime adjustedDateTime) {

    OffsetDateTime actual = manager.adjustTimeForLatency(originalDateTime, latencyDuration);

    assertEquals(adjustedDateTime, actual);
  }
}
