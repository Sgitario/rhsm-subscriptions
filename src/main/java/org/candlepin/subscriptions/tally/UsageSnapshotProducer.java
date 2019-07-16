/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.subscriptions.tally;

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.exception.SnapshotProducerException;
import org.candlepin.subscriptions.files.AccountListSource;
import org.candlepin.subscriptions.inventory.db.InventoryRepository;
import org.candlepin.subscriptions.tally.facts.FactNormalizer;
import org.candlepin.subscriptions.tally.roller.DailySnapshotRoller;
import org.candlepin.subscriptions.tally.roller.MonthlySnapshotRoller;
import org.candlepin.subscriptions.tally.roller.QuarterlySnapshotRoller;
import org.candlepin.subscriptions.tally.roller.WeeklySnapshotRoller;
import org.candlepin.subscriptions.tally.roller.YearlySnapshotRoller;
import org.candlepin.subscriptions.util.ApplicationClock;

import com.google.common.collect.Iterables;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

/**
 * Produces usage snapshot for all configured accounts.
 */
@Component
public class UsageSnapshotProducer {

    private static final Logger log = LoggerFactory.getLogger(UsageSnapshotProducer.class);

    private static final String RHEL = "RHEL";

    private final AccountListSource accountListSource;
    private final int accountBatchSize;

    private final DailySnapshotRoller dailyRoller;
    private final WeeklySnapshotRoller weeklyRoller;
    private final MonthlySnapshotRoller monthlyRoller;
    private final YearlySnapshotRoller yearlyRoller;
    private final QuarterlySnapshotRoller quarterlyRoller;

    @Autowired
    public UsageSnapshotProducer(FactNormalizer factNormalizer, AccountListSource accountListSource,
        InventoryRepository inventoryRepository, TallySnapshotRepository tallyRepo, ApplicationClock clock,
        ApplicationProperties applicationProperties) {
        this.accountListSource = accountListSource;
        this.accountBatchSize = applicationProperties.getAccountBatchSize();

        dailyRoller = new DailySnapshotRoller(RHEL, inventoryRepository,
            tallyRepo, factNormalizer, clock);
        weeklyRoller = new WeeklySnapshotRoller(RHEL, tallyRepo, clock);
        monthlyRoller = new MonthlySnapshotRoller(RHEL, tallyRepo, clock);
        yearlyRoller = new YearlySnapshotRoller(RHEL, tallyRepo, clock);
        quarterlyRoller = new QuarterlySnapshotRoller(RHEL, tallyRepo, clock);
    }

    @Transactional
    public void produceSnapshots() {
        try {
            // Partition the account list to help reduce memory usage while performing
            // the calculations.
            log.info("Batch producing snapshots.");
            Iterables.partition(accountListSource.list(), accountBatchSize).forEach(accounts -> {
                log.info("Producing snapshots for the next {} accounts.", accounts.size());
                dailyRoller.rollSnapshots(accounts);
                weeklyRoller.rollSnapshots(accounts);
                monthlyRoller.rollSnapshots(accounts);
                yearlyRoller.rollSnapshots(accounts);
                quarterlyRoller.rollSnapshots(accounts);
            });
            log.info("Finished producing snapshots for all accounts.");

        }
        catch (IOException ioe) {
            throw new SnapshotProducerException(
                "Unable to read account listing while producing usage snapshots.", ioe);
        }
    }

}