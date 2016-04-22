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

package org.apache.hadoop.yarn.server.timelineservice.collector;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.classification.InterfaceAudience.Private;
import org.apache.hadoop.classification.InterfaceStability.Unstable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.service.CompositeService;
import org.apache.hadoop.yarn.api.records.timelineservice.TimelineMetricOperation;
import org.apache.hadoop.yarn.api.records.timelineservice.TimelineEntities;
import org.apache.hadoop.yarn.api.records.timelineservice.TimelineEntity;
import org.apache.hadoop.yarn.api.records.timelineservice.TimelineMetric;
import org.apache.hadoop.yarn.api.records.timelineservice.TimelineWriteResponse;
import org.apache.hadoop.yarn.server.timelineservice.storage.TimelineWriter;

/**
 * Service that handles writes to the timeline service and writes them to the
 * backing storage.
 *
 * Classes that extend this can add their own lifecycle management or
 * customization of request handling.
 */
@Private
@Unstable
public abstract class TimelineCollector extends CompositeService {

  private static final Log LOG = LogFactory.getLog(TimelineCollector.class);
  public static final String SEPARATOR = "_";

  private TimelineWriter writer;
  private ConcurrentMap<String, AggregationStatusTable> aggregationGroups
      = new ConcurrentHashMap<>();
  private static Set<String> entityTypesSkipAggregation
      = new HashSet<>();

  public TimelineCollector(String name) {
    super(name);
  }

  @Override
  protected void serviceInit(Configuration conf) throws Exception {
    super.serviceInit(conf);
  }

  @Override
  protected void serviceStart() throws Exception {
    super.serviceStart();
  }

  @Override
  protected void serviceStop() throws Exception {
    super.serviceStop();
  }

  protected void setWriter(TimelineWriter w) {
    this.writer = w;
  }

  protected TimelineWriter getWriter() {
    return writer;
  }

  protected Map<String, AggregationStatusTable> getAggregationGroups() {
    return aggregationGroups;
  }

  /**
   * Method to decide the set of timeline entity types the collector should
   * skip on aggregations. Subclasses may want to override this method to
   * customize their own behaviors.
   *
   * @return A set of strings consists of all types the collector should skip.
   */
  protected Set<String> getEntityTypesSkipAggregation() {
    return entityTypesSkipAggregation;
  }

  public abstract TimelineCollectorContext getTimelineEntityContext();


  /**
   * Handles entity writes. These writes are synchronous and are written to the
   * backing storage without buffering/batching. If any entity already exists,
   * it results in an update of the entity.
   *
   * This method should be reserved for selected critical entities and events.
   * For normal voluminous writes one should use the async method
   * {@link #putEntitiesAsync(TimelineEntities, UserGroupInformation)}.
   *
   * @param entities entities to post
   * @param callerUgi the caller UGI
   * @return the response that contains the result of the post.
   * @throws IOException if there is any exception encountered while putting
   *     entities.
   */
  public TimelineWriteResponse putEntities(TimelineEntities entities,
      UserGroupInformation callerUgi) throws IOException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("SUCCESS - TIMELINE V2 PROTOTYPE");
      LOG.debug("putEntities(entities=" + entities + ", callerUgi="
          + callerUgi + ")");
    }
    TimelineCollectorContext context = getTimelineEntityContext();

    // Update application metrics for aggregation
    updateAggregateStatus(entities, aggregationGroups,
        getEntityTypesSkipAggregation());

    return writer.write(context.getClusterId(), context.getUserId(),
        context.getFlowName(), context.getFlowVersion(), context.getFlowRunId(),
        context.getAppId(), entities);
  }

  /**
   * Handles entity writes in an asynchronous manner. The method returns as soon
   * as validation is done. No promises are made on how quickly it will be
   * written to the backing storage or if it will always be written to the
   * backing storage. Multiple writes to the same entities may be batched and
   * appropriate values updated and result in fewer writes to the backing
   * storage.
   *
   * @param entities entities to post
   * @param callerUgi the caller UGI
   */
  public void putEntitiesAsync(TimelineEntities entities,
      UserGroupInformation callerUgi) {
    // TODO implement
    if (LOG.isDebugEnabled()) {
      LOG.debug("putEntitiesAsync(entities=" + entities + ", callerUgi=" +
          callerUgi + ")");
    }
  }

  /**
   * Aggregate all metrics in given timeline entities with no predefined states.
   *
   * @param entities Entities to aggregate
   * @param resultEntityId Id of the result entity
   * @param resultEntityType Type of the result entity
   * @param needsGroupIdInResult Marks if we want the aggregation group id in
   *                             each aggregated metrics.
   * @return A timeline entity that contains all aggregated TimelineMetric.
   */
  public static TimelineEntity aggregateEntities(
      TimelineEntities entities, String resultEntityId,
      String resultEntityType, boolean needsGroupIdInResult) {
    ConcurrentMap<String, AggregationStatusTable> aggregationGroups
        = new ConcurrentHashMap<>();
    updateAggregateStatus(entities, aggregationGroups, null);
    if (needsGroupIdInResult) {
      return aggregate(aggregationGroups, resultEntityId, resultEntityType);
    } else {
      return aggregateWithoutGroupId(
          aggregationGroups, resultEntityId, resultEntityType);
    }
  }

  /**
   * Update the aggregation status table for a timeline collector.
   *
   * @param entities Entities to update
   * @param aggregationGroups Aggregation status table
   * @param typesToSkip Entity types that we can safely assume to skip updating
   */
  static void updateAggregateStatus(
      TimelineEntities entities,
      ConcurrentMap<String, AggregationStatusTable> aggregationGroups,
      Set<String> typesToSkip) {
    for (TimelineEntity e : entities.getEntities()) {
      if ((typesToSkip != null && typesToSkip.contains(e.getType()))
          || e.getMetrics().isEmpty()) {
        continue;
      }
      AggregationStatusTable aggrTable = aggregationGroups.get(e.getType());
      if (aggrTable == null) {
        AggregationStatusTable table = new AggregationStatusTable();
        aggrTable = aggregationGroups.putIfAbsent(e.getType(),
            table);
        if (aggrTable == null) {
          aggrTable = table;
        }
      }
      aggrTable.update(e);
    }
  }

  /**
   * Aggregate internal status and generate timeline entities for the
   * aggregation results.
   *
   * @param aggregationGroups Aggregation status table
   * @param resultEntityId Id of the result entity
   * @param resultEntityType Type of the result entity
   * @return A timeline entity that contains all aggregated TimelineMetric.
   */
  static TimelineEntity aggregate(
      Map<String, AggregationStatusTable> aggregationGroups,
      String resultEntityId, String resultEntityType) {
    TimelineEntity result = new TimelineEntity();
    result.setId(resultEntityId);
    result.setType(resultEntityType);
    for (Map.Entry<String, AggregationStatusTable> entry
        : aggregationGroups.entrySet()) {
      entry.getValue().aggregateAllTo(result, entry.getKey());
    }
    return result;
  }

  /**
   * Aggregate internal status and generate timeline entities for the
   * aggregation results. The result metrics will not have aggregation group
   * information.
   *
   * @param aggregationGroups Aggregation status table
   * @param resultEntityId Id of the result entity
   * @param resultEntityType Type of the result entity
   * @return A timeline entity that contains all aggregated TimelineMetric.
   */
  static TimelineEntity aggregateWithoutGroupId(
      Map<String, AggregationStatusTable> aggregationGroups,
      String resultEntityId, String resultEntityType) {
    TimelineEntity result = new TimelineEntity();
    result.setId(resultEntityId);
    result.setType(resultEntityType);
    for (Map.Entry<String, AggregationStatusTable> entry
        : aggregationGroups.entrySet()) {
      entry.getValue().aggregateAllTo(result, "");
    }
    return result;
  }

  // Note: In memory aggregation is performed in an eventually consistent
  // fashion.
  private static class AggregationStatusTable {
    // On aggregation, for each metric, aggregate all per-entity accumulated
    // metrics. We only use the id and type for TimelineMetrics in the key set
    // of this table.
    private ConcurrentMap<TimelineMetric, Map<String, TimelineMetric>>
        aggregateTable;

    public AggregationStatusTable() {
      aggregateTable = new ConcurrentHashMap<>();
    }

    public void update(TimelineEntity incoming) {
      String entityId = incoming.getId();
      for (TimelineMetric m : incoming.getMetrics()) {
        // Skip if the metric does not need aggregation
        if (m.getRealtimeAggregationOp() == TimelineMetricOperation.NOP) {
          continue;
        }
        // Update aggregateTable
        Map<String, TimelineMetric> aggrRow = aggregateTable.get(m);
        if (aggrRow == null) {
          Map<String, TimelineMetric> tempRow = new ConcurrentHashMap<>();
          aggrRow = aggregateTable.putIfAbsent(m, tempRow);
          if (aggrRow == null) {
            aggrRow = tempRow;
          }
        }
        aggrRow.put(entityId, m);
      }
    }

    public TimelineEntity aggregateTo(TimelineMetric metric, TimelineEntity e,
        String aggregationGroupId) {
      if (metric.getRealtimeAggregationOp() == TimelineMetricOperation.NOP) {
        return e;
      }
      Map<String, TimelineMetric> aggrRow = aggregateTable.get(metric);
      if (aggrRow != null) {
        TimelineMetric aggrMetric = new TimelineMetric();
        if (aggregationGroupId.length() > 0) {
          aggrMetric.setId(metric.getId() + SEPARATOR + aggregationGroupId);
        } else {
          aggrMetric.setId(metric.getId());
        }
        aggrMetric.setRealtimeAggregationOp(TimelineMetricOperation.NOP);
        Map<Object, Object> status = new HashMap<>();
        for (TimelineMetric m : aggrRow.values()) {
          TimelineMetric.aggregateTo(m, aggrMetric, status);
          // getRealtimeAggregationOp returns an enum so we can directly
          // compare with "!=".
          if (m.getRealtimeAggregationOp()
              != aggrMetric.getRealtimeAggregationOp()) {
            aggrMetric.setRealtimeAggregationOp(m.getRealtimeAggregationOp());
          }
        }
        Set<TimelineMetric> metrics = e.getMetrics();
        metrics.remove(aggrMetric);
        metrics.add(aggrMetric);
      }
      return e;
    }

    public TimelineEntity aggregateAllTo(TimelineEntity e,
        String aggregationGroupId) {
      for (TimelineMetric m : aggregateTable.keySet()) {
        aggregateTo(m, e, aggregationGroupId);
      }
      return e;
    }
  }
}
