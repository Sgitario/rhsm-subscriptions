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
package org.candlepin.subscriptions.db;

import java.util.stream.Stream;
import org.candlepin.subscriptions.db.model.config.OrgConfig;
import org.candlepin.subscriptions.tally.OrgListSourceException;
import org.springframework.stereotype.Component;

/**
 * Pulls the list of orgs to sync from a database table.
 *
 * <p>See {@link OrgConfig}.
 */
@Component
public class OrgListSource {

  private final AccountConfigRepository accountRepo;

  public OrgListSource(AccountConfigRepository accountRepo) {
    this.accountRepo = accountRepo;
  }

  public Stream<String> getAllOrgToSync() throws OrgListSourceException {
    try {
      return accountRepo.findSyncEnabledOrgs();
    } catch (Exception e) {
      throw new OrgListSourceException("Unable to get org sync list!", e);
    }
  }
}
