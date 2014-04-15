/**
 * Copyright 2010-2014 Axel Fontaine and the many contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.flywaydb.core.command;

import org.flywaydb.core.api.FlywayCallback;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.dbsupport.Schema;
import org.flywaydb.core.metadatatable.MetaDataTable;
import org.flywaydb.core.util.StopWatch;
import org.flywaydb.core.util.TimeFormat;
import org.flywaydb.core.util.jdbc.TransactionCallback;
import org.flywaydb.core.util.jdbc.TransactionTemplate;
import org.flywaydb.core.util.logging.Log;
import org.flywaydb.core.util.logging.LogFactory;
import java.sql.Connection;

/**
 * Main workflow for cleaning the database.
 */
public class DbClean {
    private static final Log LOG = LogFactory.getLog(DbClean.class);

    /**
     * The connection to use.
     */
    private final Connection connection;

    /**
     * The metadata table.
     */
    private final MetaDataTable metaDataTable;

    /**
     * The schemas to clean.
     */
    private final Schema[] schemas;

    /**
     * This is a list of callbacks that fire before or after the validate task is executed.  
     * You can add as many callbacks as you want.  These should be set on the Flyway class
     * by the end user as Flyway will set them automatically for you here.
     */
    private FlywayCallback[] callbacks = new FlywayCallback[0];

    /**
     * Creates a new database cleaner.
     *
     * @param connection    The connection to use.
     * @param metaDataTable The metadata table.
     * @param schemas       The schemas to clean.
     */
    public DbClean(Connection connection, MetaDataTable metaDataTable, Schema[] schemas, FlywayCallback[] callbacks) {
        this.connection = connection;
        this.metaDataTable = metaDataTable;
        this.schemas = schemas;
        this.callbacks = callbacks;
    }

    /**
     * Cleans the schemas of all objects.
     *
     * @throws FlywayException when clean failed.
     */
    public void clean() throws FlywayException {
		for (FlywayCallback callback: callbacks) {
			callback.beforeClean(connection);
		}

		boolean dropSchemas = false;
        try {
            dropSchemas = metaDataTable.hasSchemasMarker();
        } catch (Exception e) {
            LOG.error("Error while checking whether the schemas should be dropped", e);
        }

        for (Schema schema : schemas) {
            if (dropSchemas) {
                dropSchema(schema);
            } else {
                cleanSchema(schema);
            }
        }

		for (FlywayCallback callback: callbacks) {
			callback.afterClean(connection);
		}
    }

    /**
     * Drops this schema.
     *
     * @param schema The schema to drop.
     * @throws FlywayException when the drop failed.
     */
    private void dropSchema(final Schema schema) {
        LOG.debug("Dropping schema " + schema + " ...");
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        new TransactionTemplate(connection).execute(new TransactionCallback<Void>() {
            public Void doInTransaction() {
                schema.drop();
                return null;
            }
        });
        stopWatch.stop();
        LOG.info(String.format("Dropped schema %s (execution time %s)",
                schema, TimeFormat.format(stopWatch.getTotalTimeMillis())));
    }

    /**
     * Cleans this schema of all objects.
     *
     * @param schema The schema to clean.
     * @throws FlywayException when clean failed.
     */
    private void cleanSchema(final Schema schema) {
        LOG.debug("Cleaning schema " + schema + " ...");
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        new TransactionTemplate(connection).execute(new TransactionCallback<Void>() {
            public Void doInTransaction() {
                schema.clean();
                return null;
            }
        });
        stopWatch.stop();
        LOG.info(String.format("Cleaned schema %s (execution time %s)",
                schema, TimeFormat.format(stopWatch.getTotalTimeMillis())));
    }
}
