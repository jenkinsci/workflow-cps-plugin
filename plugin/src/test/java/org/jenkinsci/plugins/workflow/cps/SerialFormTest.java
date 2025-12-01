/*
 * The MIT License
 *
 * Copyright 2021 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.cps;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import hudson.model.Node;
import hudson.model.Slave;
import hudson.slaves.SlaveComputer;
import java.net.URL;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.pickles.Pickle;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RealJenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Test compatibility of the format of {@code program.dat} as well as various {@link Pickle}s.
 */
public class SerialFormTest {

    private static final String AGENT_NAME = "remote";
    private static final String AGENT_SECRET = "defebc83e8736659464a172801f43de376f751891e69cb6ece89e856d6cc3b48";

    private static GenericContainer<?> agentContainer;

    @BeforeClass
    public static void dockerCheck() throws Exception {
        try {
            DockerClientFactory.instance().client();
        } catch (Exception x) {
            Assume.assumeNoException("does not look like Docker is available", x);
        }
    }

    @AfterClass
    public static void terminateContainer() throws Exception {
        if (agentContainer != null && agentContainer.isRunning()) {
            agentContainer.stop();
        }
    }

    @Rule
    public RealJenkinsRule rr = new RealJenkinsRule();

    @LocalData
    @Test
    public void persistence() throws Throwable {
        rr.startJenkins();
        URL u = rr.getUrl();
        Testcontainers.exposeHostPorts(u.getPort());
        agentContainer = new GenericContainer<>("jenkins/inbound-agent")
                .withEnv(
                        "JENKINS_URL",
                        new URL(u.getProtocol(), "host.testcontainers.internal", u.getPort(), u.getFile()).toString())
                .withEnv("JENKINS_AGENT_NAME", AGENT_NAME)
                .withEnv("JENKINS_SECRET", AGENT_SECRET)
                .withEnv("JENKINS_WEB_SOCKET", "true");
        agentContainer.start();
        agentContainer.copyFileToContainer(
                MountableFile.forClasspathResource(
                        SerialFormTest.class.getName().replace('.', '/') + "/persistence-workspace"),
                "/home/jenkins/agent/workspace");
        rr.runRemotely(SerialFormTest::_persistence);
    }

    private static void _persistence(JenkinsRule r) throws Throwable {
        Node agent = r.jenkins.getNode(AGENT_NAME);
        assertThat(agent, notNullValue());
        assertThat(((SlaveComputer) agent.toComputer()).getJnlpMac(), is(AGENT_SECRET));
        r.waitOnline((Slave) agent);
        WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
        WorkflowRun b = p.getBuildByNumber(1);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
    }
}
