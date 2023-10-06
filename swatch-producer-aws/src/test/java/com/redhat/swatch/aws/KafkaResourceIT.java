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
package com.redhat.swatch.aws;

import static io.restassured.RestAssured.post;

import com.redhat.swatch.aws.openapi.model.KafkaSeekPosition;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import java.time.OffsetDateTime;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@QuarkusTest
@QuarkusTestResource(value = PostgresResource.class, restrictToAnnotatedClass = true)
@QuarkusTestResource(value = KafkaResource.class, restrictToAnnotatedClass = true)
@Tag("integration")
class KafkaResourceIT {

  @ParameterizedTest
  @EnumSource(KafkaSeekPosition.class)
  void testKafkaSeekPosition(KafkaSeekPosition position) {
    post(basePath() + "/kafka_seek_position?position=" + position)
        .then()
        .statusCode(HttpStatus.SC_NO_CONTENT);
  }

  @Test
  void testKafkaSeekTimestamp() {
    post(basePath() + "/kafka_seek_timestamp?timestamp=" + OffsetDateTime.now())
        .then()
        .statusCode(HttpStatus.SC_NO_CONTENT);
  }

  private String basePath() {
    return "/api/swatch-producer-aws/internal";
  }
}
