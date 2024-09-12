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
package org.apache.jackrabbit.oak.plugins.index.elastic;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Network;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import eu.rekawek.toxiproxy.model.toxic.LimitData;
import org.apache.jackrabbit.oak.api.Tree;
import org.junit.After;
import org.junit.Test;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.apache.jackrabbit.oak.plugins.index.elastic.ElasticTestServer.checkIfDockerClientAvailable;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

public class ElasticReliabilityTest extends ElasticAbstractQueryTest {

    private static final DockerImageName TOXIPROXY_IMAGE = DockerImageName.parse("ghcr.io/shopify/toxiproxy:2.9.0");

    private ToxiproxyContainer toxiproxy;

    private Proxy proxy;

    @Override
    public void before() throws Exception {
        listContainers("before ToxiProxy start");
        toxiproxy = new ToxiproxyContainer(TOXIPROXY_IMAGE)
                .withNetwork(elasticRule.elastic.getNetwork())
                .withEnv("LOG_LEVEL", "trace")
                .dependsOn(elasticRule.elastic)
                .withNetworkAliases("toxiproxy");
        toxiproxy.start();
        Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(LOG).withSeparateOutputStreams();
        toxiproxy.followOutput(logConsumer);
        ToxiproxyClient toxiproxyClient = new ToxiproxyClient(toxiproxy.getHost(), toxiproxy.getControlPort());
        proxy = toxiproxyClient.createProxy("elastic", "0.0.0.0:8666", "elasticsearch:9200");
        listContainers("after ToxiProxy start");
        super.before();
    }

    private static void listContainers(String description) {
        DockerClient dockerClient = checkIfDockerClientAvailable();
        List<Container> containers = dockerClient.listContainersCmd().exec();
        List<Network> networks = dockerClient.listNetworksCmd().exec();
        LOG.info("List of containers - " + description);
        for (Container container : containers) {
//            LOG.info("name:" + container.getImage() + " status:" + container.getStatus() + " state:" + container.getState());
            LOG.info(container.toString());
            System.out.println(container);
        }
        LOG.info("List of networks - " + description);
        for (Network network : networks) {
            LOG.info(network.toString());
            System.out.println(network);
        }
    }

    @After
    @Override
    public void tearDown() throws IOException {
        listContainers("before tearDown");
        super.tearDown();
        if (toxiproxy.isRunning()) {
            toxiproxy.stop();
        }
    }

    @Override
    protected void createTestIndexNode() {
        setTraversalEnabled(true);
    }

    @Override
    protected ElasticConnection getElasticConnection() {
        return elasticRule.useDocker() ?
                elasticRule.getElasticConnectionForDocker(toxiproxy.getHost(), toxiproxy.getMappedPort(8666)) :
                elasticRule.getElasticConnectionFromString();
    }

    @Test
    public void connectionCutOnQuery() throws Exception {
        String indexName = UUID.randomUUID().toString();
        setIndex(indexName, createIndex("propa", "propb"));

        Tree test = root.getTree("/").addChild("test");
        test.addChild("a").setProperty("propa", "a");
        test.addChild("b").setProperty("propa", "c");
        test.addChild("c").setProperty("propb", "e");
        root.commit(Collections.singletonMap("sync-mode", "rt"));

        String query = "select [jcr:path] from [nt:base] where propa is not null";

        // simulate an upstream connection cut
        LimitData cutConnectionUpstream = proxy.toxics()
                .limitData("CUT_CONNECTION_UPSTREAM", ToxicDirection.UPSTREAM, 0L);

        // elastic is down, query should not use it
        assertThat(explain(query), not(containsString("elasticsearch:" + indexName)));

        // result set should be correct anyway since traversal is enabled
        assertQuery(query, Arrays.asList("/test/a", "/test/b"));

        // re-establish connection
        cutConnectionUpstream.remove();

        // result set should be the same as before but this time elastic should be used
        assertThat(explain(query), containsString("elasticsearch:" + indexName));
        assertQuery(query, Arrays.asList("/test/a", "/test/b"));
    }
}
