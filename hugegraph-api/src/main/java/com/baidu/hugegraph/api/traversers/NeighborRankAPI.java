/*
 * Copyright 2017 HugeGraph Authors
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.baidu.hugegraph.api.traversers;

import static com.baidu.hugegraph.traversal.algorithm.HugeTraverser.DEFAULT_CAPACITY;
import static com.baidu.hugegraph.traversal.algorithm.HugeTraverser.DEFAULT_DEGREE;
import static com.baidu.hugegraph.traversal.algorithm.HugeTraverser.DEFAULT_PATHS_LIMIT;
import static com.baidu.hugegraph.traversal.algorithm.HugeTraverser.NO_LIMIT;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Singleton;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;

import org.slf4j.Logger;

import com.baidu.hugegraph.HugeGraph;
import com.baidu.hugegraph.api.API;
import com.baidu.hugegraph.api.graph.VertexAPI;
import com.baidu.hugegraph.backend.id.Id;
import com.baidu.hugegraph.core.GraphManager;
import com.baidu.hugegraph.schema.EdgeLabel;
import com.baidu.hugegraph.server.RestServer;
import com.baidu.hugegraph.traversal.algorithm.RankTraverser;
import com.baidu.hugegraph.type.define.Directions;
import com.baidu.hugegraph.util.E;
import com.baidu.hugegraph.util.JsonUtil;
import com.baidu.hugegraph.util.Log;
import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.annotation.JsonProperty;

@Path("graphs/{graph}/traversers/neighborrank")
@Singleton
public class NeighborRankAPI extends API {

    private static final Logger LOG = Log.logger(RestServer.class);

    @POST
    @Timed
    @Produces(APPLICATION_JSON_WITH_CHARSET)
    public String neighborRank(@Context GraphManager manager,
                               @PathParam("graph") String graph,
                               RankRequest request) {
        LOG.debug("Graph [{}] get neighbor rank from '{}' with steps '{}', " +
                  "alpha '{}', and sorted '{}'",
                  graph, request.source, request.steps, request.alpha);

        E.checkArgumentNotNull(request, "The rank request body can't be null");
        E.checkArgumentNotNull(request.source,
                               "The sources of rank request can't be null");
        if (request.steps != null) {
            E.checkArgument(!request.steps.isEmpty(),
                            "The steps of rank request are either not " +
                            "specified or as null, but can't be specified " +
                            "as empty");
        }
        E.checkArgument(request.alpha >= 0.0 && request.alpha <= 1.0,
                        "The alpha of rank request must between [0, 1], " +
                        "but got '%s'", request.alpha);

        Id sourceId = VertexAPI.checkAndParseVertexId(request.source);
        HugeGraph g = graph(manager, graph);

        List<RankTraverser.Step> steps = step(g, request);

        RankTraverser traverser = new RankTraverser(g, request.alpha);
        List<Map<Id, Double>> ranks = traverser.neighborRank(sourceId, steps,
                                                       request.capacity,
                                                       request.limit);
        return JsonUtil.toJson(ranks);
    }

    private static List<RankTraverser.Step> step(HugeGraph graph,
                                                 RankRequest req) {
        List<RankTraverser.Step> steps = new ArrayList<>(req.steps.size());
        for (Step step : req.steps) {
            steps.add(step.jsonToStep(graph));
        }
        return steps;
    }

    private static class RankRequest {

        @JsonProperty("source")
        private String source;
        @JsonProperty("steps")
        private List<Step> steps;
        @JsonProperty("alpha")
        private double alpha;
        @JsonProperty("capacity")
        public long capacity = Long.valueOf(DEFAULT_CAPACITY);
        @JsonProperty("limit")
        public long limit = Long.valueOf(DEFAULT_PATHS_LIMIT);

        @Override
        public String toString() {
            return String.format("RankRequest{source=%s,steps=%s," +
                                 "alpha=%s}",
                                 this.source, this.steps, this.alpha);
        }
    }

    private static class Step {

        private static int MAX_NUMBER = 1000;

        @JsonProperty("direction")
        public Directions direction;
        @JsonProperty("labels")
        public List<String> labels;
        @JsonProperty("properties")
        public Map<String, Object> properties;
        @JsonProperty("degree")
        public long degree = Long.valueOf(DEFAULT_DEGREE);
        @JsonProperty("number")
        public int number = MAX_NUMBER;

        @Override
        public String toString() {
            return String.format("Step{direction=%s,labels=%s,properties=%s," +
                                 "degree=%s}", this.direction, this.labels,
                                 this.properties, this.degree);
        }

        private RankTraverser.Step jsonToStep(HugeGraph graph) {
            E.checkArgument(this.degree > 0 || this.degree == NO_LIMIT,
                            "The degree must be > 0, but got: %s",
                            this.degree);
            E.checkArgument(this.degree == NO_LIMIT,
                            "Degree must be greater than or equal to sample, " +
                            "but got degree %s", degree);
            E.checkArgument(this.number > 0 && this.number <= 1000,
                            "The recommended number of each layer cannot " +
                            "exceed '%s'", MAX_NUMBER);
            Map<Id, String> labelIds = new HashMap<>();
            if (this.labels != null) {
                for (String label : this.labels) {
                    EdgeLabel el = graph.edgeLabel(label);
                    labelIds.put(el.id(), label);
                }
            }
            return new RankTraverser.Step(this.direction, labelIds,
                                          this.properties, this.degree,
                                          this.number);
        }
    }
}
