/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.util.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.TracerProvider;
import java.io.IOException;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.cloud.SolrCloudTestCase;
import org.apache.solr.common.util.SuppressForbidden;
import org.apache.solr.core.SolrCore;
import org.apache.solr.logging.MDCLoggingContext;
import org.apache.solr.update.processor.LogUpdateProcessorFactory;
import org.apache.solr.util.LogListener;
import org.junit.BeforeClass;
import org.junit.Test;

@SuppressForbidden(reason = "We need to use log4J2 classes directly to test MDC impacts")
public class TestSimplePropagatorDistributedTracing extends SolrCloudTestCase {

  private static final String COLLECTION = "collection1";

  @BeforeClass
  public static void setupCluster() throws Exception {
    configureCluster(4).addConfig("conf", configset("cloud-minimal")).configure();

    // tracer should be disabled
    assertEquals(
        "Expecting noop otel (propagating only)",
        TracerProvider.noop(),
        GlobalOpenTelemetry.get().getTracerProvider());

    CollectionAdminRequest.createCollection(COLLECTION, "conf", 2, 2)
        .process(cluster.getSolrClient());
    cluster.waitForActiveCollection(COLLECTION, 2, 4);
  }

  @Test
  public void test() throws IOException, SolrServerException {
    CloudSolrClient cloudClient = cluster.getSolrClient();

    // Indexing has trace ids
    try (LogListener reqLog = LogListener.info(LogUpdateProcessorFactory.class.getName())) {
      // verify all indexing events have trace id present
      cloudClient.add(COLLECTION, sdoc("id", "1"));
      cloudClient.add(COLLECTION, sdoc("id", "2"));
      cloudClient.add(COLLECTION, sdoc("id", "3"));
      var queue = reqLog.getQueue();
      assertFalse(queue.isEmpty());
      while (!queue.isEmpty()) {
        var reqEvent = queue.poll();
        String evTraceId = reqEvent.getContextData().getValue(MDCLoggingContext.TRACE_ID);
        assertNotNull(evTraceId);
      }

      // TODO this doesn't work due to solr client creating the UpdateRequest without headers
      //      // verify all events have the same 'custom' traceid
      //      String traceId = "tidTestSimplePropagatorDistributedTracing0";
      //      var doc = sdoc("id", "4");
      //      UpdateRequest u = new UpdateRequest();
      //      u.add(doc);
      //      u.addHeader(SimplePropagator.TRACE_ID, traceId);
      //      var r1 = u.process(cloudClient, COLLECTION);
      //      assertEquals(0, r1.getStatus());
      //      assertSameTraceId(reqLog, traceId);
    }

    // Searching has trace ids
    try (LogListener reqLog = LogListener.info(SolrCore.class.getName() + ".Request")) {
      // verify all query events have the same auto-generated traceid
      var r1 = cloudClient.query(COLLECTION, new SolrQuery("*:*"));
      assertEquals(0, r1.getStatus());
      assertSameTraceId(reqLog, null);

      // verify all query events have the same 'custom' traceid
      String traceId = "tidTestSimplePropagatorDistributedTracing1";
      var q = new QueryRequest(new SolrQuery("*:*"));
      q.addHeader(SimplePropagator.TRACE_ID, traceId);
      var r2 = q.process(cloudClient, COLLECTION);
      assertEquals(0, r2.getStatus());
      assertSameTraceId(reqLog, traceId);
    }
  }

  private void assertSameTraceId(LogListener reqLog, String traceId) {
    var queue = reqLog.getQueue();
    assertFalse(queue.isEmpty());
    if (traceId == null) {
      traceId = queue.poll().getContextData().getValue(MDCLoggingContext.TRACE_ID);
      assertNotNull(traceId);
    }
    while (!queue.isEmpty()) {
      var reqEvent = queue.poll();
      String evTraceId = reqEvent.getContextData().getValue(MDCLoggingContext.TRACE_ID);
      assertEquals(traceId, evTraceId);
    }
  }
}
