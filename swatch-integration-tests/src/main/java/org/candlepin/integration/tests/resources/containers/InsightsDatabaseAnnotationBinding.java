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

import io.jeasyarch.api.DatabaseService;
import io.jeasyarch.api.Service;
import io.jeasyarch.core.JEasyArchContext;
import io.jeasyarch.core.ManagedResource;
import io.jeasyarch.resources.containers.ContainerAnnotationBinding;
import java.lang.annotation.Annotation;
import org.candlepin.integration.tests.api.InsightsDatabase;

public class InsightsDatabaseAnnotationBinding extends ContainerAnnotationBinding {

  @Override
  public boolean isFor(Annotation... annotations) {
    return findAnnotation(annotations, InsightsDatabase.class).isPresent();
  }

  @Override
  public ManagedResource getManagedResource(
      JEasyArchContext context, Service service, Annotation... annotations) {
    InsightsDatabase metadata = findAnnotation(annotations, InsightsDatabase.class).get();

    if (!(service instanceof DatabaseService)) {
      throw new IllegalStateException(
          "@InsightsDatabase can only be used with DatabaseService service");
    }

    DatabaseService databaseService = (DatabaseService) service;
    databaseService.withJdbcName(metadata.jdbcName());
    databaseService.withDatabaseNameProperty(metadata.databaseNameProperty());
    databaseService.withUserProperty(metadata.userProperty());
    databaseService.withPasswordProperty(metadata.passwordProperty());
    databaseService.with(metadata.user(), metadata.password(), metadata.database());

    return doInit(
        context,
        service,
        metadata.image(),
        metadata.expectedLog(),
        metadata.command(),
        metadata.ports());
  }
}
