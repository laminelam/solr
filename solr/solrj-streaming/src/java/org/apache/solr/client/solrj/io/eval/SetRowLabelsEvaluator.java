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

package org.apache.solr.client.solrj.io.eval;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.solr.client.solrj.io.stream.expr.StreamExpression;
import org.apache.solr.client.solrj.io.stream.expr.StreamFactory;

public class SetRowLabelsEvaluator extends RecursiveObjectEvaluator implements TwoValueWorker {
  private static final long serialVersionUID = 1;

  public SetRowLabelsEvaluator(StreamExpression expression, StreamFactory factory)
      throws IOException {
    super(expression, factory);
  }

  @Override
  public Object doWork(Object value1, Object value2) throws IOException {
    if (!(value1 instanceof Matrix)) {
      throw new IOException(
          String.format(
              Locale.ROOT,
              "Invalid expression %s - found type %s for value, expecting a Matrix",
              toExpression(constructingFactory),
              value1.getClass().getSimpleName()));
    } else if (!(value2 instanceof List<?> rowlabels)) {
      throw new IOException(
          String.format(
              Locale.ROOT,
              "Invalid expression %s - found type %s for value, expecting an array of labels.",
              toExpression(constructingFactory),
              value2.getClass().getSimpleName()));
    } else {
      Matrix matrix = (Matrix) value1;

      // Convert numeric labels to strings.

      List<String> strLabels = new ArrayList<>(rowlabels.size());

      for (Object o : rowlabels) {
        strLabels.add(o.toString());
      }

      matrix.setRowLabels(strLabels);
      return matrix;
    }
  }
}
