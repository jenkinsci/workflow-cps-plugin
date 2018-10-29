/*
 * The MIT License
 *
 * Copyright 2014 Cloudbees Inc..
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

import groovy.lang.GroovyShell;
import hudson.model.*;
import hudson.tasks.ArtifactArchiver;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.workflow.cps.steps.ParallelStep;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.*;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStep;
import org.jenkinsci.plugins.workflow.support.steps.WorkspaceStep;
import org.jenkinsci.plugins.workflow.support.steps.build.BuildTriggerStep;
import org.jenkinsci.plugins.workflow.support.steps.input.InputStep;
import org.jenkinsci.plugins.workflow.testMetaStep.*;
import org.jenkinsci.plugins.workflow.testMetaStep.chemical.CarbonMonoxide;
import org.jenkinsci.plugins.workflow.testMetaStep.chemical.DetectionMetaStep;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.*;
import org.kohsuke.stapler.NoStaplerConstructorException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

//import org.jenkinsci.plugins.workflow.cps.steps.ParallelStep;

// TODO these tests would better be moved to the respective plugins

public class SnippetizerTest extends Snippetizer {

    @ClassRule
    public final static JenkinsRule r = new JenkinsRule();

    @ClassRule
    public static LoggerRule logging = new LoggerRule().record(DescribableModel.class, Level.ALL);

    private static SnippetizerTester st = new SnippetizerTester(r);

    @Test public void basics() throws Exception {
        st.assertRoundTrip(new EchoStep("hello world"), "echo 'hello world'");
        ReadFileStep s = new ReadFileStep("build.properties");
        st.assertRoundTrip(s, "readFile 'build.properties'");
        s.setEncoding("ISO-8859-1");
        st.assertRoundTrip(s, "readFile encoding: 'ISO-8859-1', file: 'build.properties'");
    }

    @Test public void collisionWithAnotherMetaStep() throws Exception {
        // neither should produce "CO()" because that would prevent disambiguation
        st.assertRoundTrip(new StateMetaStep(new Colorado()), "state CO()");
        st.assertRoundTrip(new DetectionMetaStep(new CarbonMonoxide()), "detect CO()");
    }


    @Test public void recursiveSymbolUse() throws Exception {
        Island hawaii = new Island(new Island(new Island(),null),new Island());
        st.assertRoundTrip(new StateMetaStep(new Hawaii(hawaii)), "hawaii island(lhs: island(lhs: island()), rhs: island())");
    }



    @Test
    public void coreStepDocs() throws Exception {
        SnippetizerTester.assertDocGeneration(CoreStep.class);
    }

    /**
     * An example of a step that will fail to generate docs correctly due to a lack of a {@link org.kohsuke.stapler.DataBoundConstructor}.
     */
    @Test(expected = NoStaplerConstructorException.class)
    public void parallelStepDocs() throws Exception {
        SnippetizerTester.assertDocGeneration(ParallelStep.class);
    }


}
