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

import static org.junit.jupiter.api.Assertions.fail;

import io.jeasyarch.api.DatabaseService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class RhsmSubscriptionsDatabaseService extends DatabaseService {

  @Override
  public String getJdbcUrl() {
    return super.getJdbcUrl() + "?reWriteBatchedInserts=true&stringtype=unspecified";
  }

  public int countEvents() {
    AtomicInteger count = new AtomicInteger();
    openStatement(
        statement -> {
          try {
            ResultSet rs = statement.executeQuery("select count(*) from events");
            while (rs.next()) {
              count.set(rs.getInt(1));
            }
          } catch (SQLException e) {
            fail("Error running the query. Cause: " + e.getMessage());
          }
        });
    return count.get();
  }

  public List<UUID> getEventIdFromEvents() {
    List<UUID> eventIds = new ArrayList<>();
    openStatement(
        statement -> {
          try {
            ResultSet rs = statement.executeQuery("select event_id from events");
            while (rs.next()) {
              eventIds.add(UUID.fromString(rs.getString(1)));
            }
          } catch (SQLException e) {
            fail("Error running the query. Cause: " + e.getMessage());
          }
        });
    return eventIds;
  }

  public boolean existsEvent(
      String orgId,
      String eventSource,
      String eventType,
      String instanceId,
      OffsetDateTime timestamp) {
    AtomicBoolean result = new AtomicBoolean();
    openStatement(
        statement -> {
          try {
            ResultSet rs =
                statement.executeQuery(
                    String.format(
                        "select org_id "
                            + "from events "
                            + "where org_id='%s' "
                            + "and event_source='%s' "
                            + "and event_type='%s' "
                            + "and instance_id='%s' "
                            + "and timestamp='%s'",
                        orgId, eventSource, eventType, instanceId, timestamp));
            result.set(rs.next());
          } catch (SQLException e) {
            fail("Error running the query. Cause: " + e.getMessage());
          }
        });

    return result.get();
  }
}
