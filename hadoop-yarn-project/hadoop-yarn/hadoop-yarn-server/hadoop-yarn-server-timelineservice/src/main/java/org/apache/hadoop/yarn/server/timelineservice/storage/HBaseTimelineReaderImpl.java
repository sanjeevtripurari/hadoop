/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.yarn.server.timelineservice.storage;


import java.io.IOException;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.service.AbstractService;
import org.apache.hadoop.yarn.api.records.timelineservice.TimelineEntity;
import org.apache.hadoop.yarn.server.timelineservice.reader.TimelineDataToRetrieve;
import org.apache.hadoop.yarn.server.timelineservice.reader.TimelineEntityFilters;
import org.apache.hadoop.yarn.server.timelineservice.reader.TimelineReaderContext;
import org.apache.hadoop.yarn.server.timelineservice.storage.reader.TimelineEntityReader;
import org.apache.hadoop.yarn.server.timelineservice.storage.reader.TimelineEntityReaderFactory;

/**
 * HBase based implementation for {@link TimelineReader}.
 */
public class HBaseTimelineReaderImpl
    extends AbstractService implements TimelineReader {

  private static final Log LOG = LogFactory
      .getLog(HBaseTimelineReaderImpl.class);

  private Configuration hbaseConf = null;
  private Connection conn;

  public HBaseTimelineReaderImpl() {
    super(HBaseTimelineReaderImpl.class.getName());
  }

  @Override
  public void serviceInit(Configuration conf) throws Exception {
    super.serviceInit(conf);
    hbaseConf = HBaseConfiguration.create(conf);
    conn = ConnectionFactory.createConnection(hbaseConf);
  }

  @Override
  protected void serviceStop() throws Exception {
    if (conn != null) {
      LOG.info("closing the hbase Connection");
      conn.close();
    }
    super.serviceStop();
  }

  @Override
  public TimelineEntity getEntity(TimelineReaderContext context,
      TimelineDataToRetrieve dataToRetrieve) throws IOException {
    TimelineEntityReader reader =
        TimelineEntityReaderFactory.createSingleEntityReader(context,
            dataToRetrieve);
    return reader.readEntity(hbaseConf, conn);
  }

  @Override
  public Set<TimelineEntity> getEntities(TimelineReaderContext context,
      TimelineEntityFilters filters, TimelineDataToRetrieve dataToRetrieve)
      throws IOException {
    TimelineEntityReader reader =
        TimelineEntityReaderFactory.createMultipleEntitiesReader(context,
            filters, dataToRetrieve);
    return reader.readEntities(hbaseConf, conn);
  }
}
