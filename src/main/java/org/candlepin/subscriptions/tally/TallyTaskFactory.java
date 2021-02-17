/*
 * Copyright (c) 2019 - 2021 Red Hat, Inc.
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

import org.candlepin.subscriptions.tally.tasks.CaptureMetricsSnapshotTask;
import org.candlepin.subscriptions.tally.tasks.UpdateAccountSnapshotsTask;
import org.candlepin.subscriptions.task.Task;
import org.candlepin.subscriptions.task.TaskDescriptor;
import org.candlepin.subscriptions.task.TaskFactory;
import org.candlepin.subscriptions.task.TaskType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import java.time.OffsetDateTime;

/**
 * A class responsible for a TaskDescriptor into actual Task instances. Task instances are build via the
 * build(TaskDescriptor) method. The type of Task that will be built is determined by the descriptor's
 * TaskType property.
 */

public class TallyTaskFactory implements TaskFactory {

    @Autowired
    private TallySnapshotController snapshotController;

    /**
     * Builds a Task instance based on the specified TaskDescriptor.
     *
     * @param taskDescriptor the task descriptor that is used to customize the Task that is to be created.
     *
     * @return the Task defined by the descriptor.
     */
    @Override
    public Task build(TaskDescriptor taskDescriptor) {
        if (taskDescriptor.getTaskType() == TaskType.UPDATE_SNAPSHOTS) {
            return new UpdateAccountSnapshotsTask(snapshotController, taskDescriptor.getArg("accounts"));
        }

        if (taskDescriptor.getTaskType() == TaskType.UPDATE_HOURLY_SNAPSHOTS) {
            validateHourlySnapshotTaskArgs(taskDescriptor);

            String accountNumber = taskDescriptor.getArg("accountNumber").get(0);
            String startDateTime = taskDescriptor.getArg("startDateTime").get(0);
            String endDateTime = taskDescriptor.getArg("endDateTime").get(0);

            OffsetDateTime from = OffsetDateTime.parse(startDateTime);
            OffsetDateTime to = OffsetDateTime.parse(endDateTime);

            return new CaptureMetricsSnapshotTask(snapshotController, accountNumber, from, to);
        }

        throw new IllegalArgumentException("Could not build task. Unknown task type: " +
            taskDescriptor.getTaskType());
    }

    protected void validateHourlySnapshotTaskArgs(TaskDescriptor taskDescriptor) {
        if (CollectionUtils.isEmpty(taskDescriptor.getArg("accountNumber")) ||
            CollectionUtils.isEmpty(taskDescriptor.getArg("startDateTime")) ||
            CollectionUtils.isEmpty(taskDescriptor.getArg("endDateTime"))) {
            throw new IllegalArgumentException(String.format(
                "Could not build %s task. accountNumber, startDateTime, endDateTime are all required",
                TaskType.UPDATE_HOURLY_SNAPSHOTS));
        }
    }
}
