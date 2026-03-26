package org.jenkinsci.plugins.workflow.cps.actions;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;
import hudson.EnvVars;
import hudson.Functions;
import hudson.XmlFile;
import hudson.model.Action;
import hudson.tasks.ArtifactArchiver;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Stream;
import org.apache.commons.lang3.RandomStringUtils;
import org.hamcrest.Matchers;
import org.hamcrest.collection.IsMapContaining;
import org.jenkinsci.plugins.credentialsbinding.impl.BindingStep;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction.NotStoredReason;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.cps.DescriptorMatchPredicate;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.FlowScanningUtils;
import org.jenkinsci.plugins.workflow.graphanalysis.ForkScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.LinearScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.NodeStepTypePredicate;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.EchoStep;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.StepExecutions;
import org.jenkinsci.plugins.workflow.support.storage.SimpleXStreamFlowNodeStorage;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.jenkinsci.plugins.workflow.testMetaStep.Curve;
import org.jenkinsci.plugins.workflow.testMetaStep.StateMetaStep;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Tests the input sanitization and step persistence here
 */
public class ArgumentsActionImplTest {
    // Run pipeline, with pauses for steps
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @Rule
    public LoggerRule logging = new LoggerRule();

    /** Helper function to test direct file deserialization for an execution */
    private void testDeserialize(FlowExecution execution) throws Exception {
        if (!(execution instanceof CpsFlowExecution)
                || !(((CpsFlowExecution) execution).getStorage() instanceof SimpleXStreamFlowNodeStorage)) {
            return; // Test is unfortunately coupled to the implementation -- otherwise it will simply hit caches
        }

        SimpleXStreamFlowNodeStorage storage =
                (SimpleXStreamFlowNodeStorage) (((CpsFlowExecution) execution).getStorage());
        Method getFileM = SimpleXStreamFlowNodeStorage.class.getDeclaredMethod("getNodeFile", String.class);
        getFileM.setAccessible(true);

        List<FlowNode> nodes = new DepthFirstScanner().allNodes(execution.getCurrentHeads());
        nodes.sort(FlowScanningUtils.ID_ORDER_COMPARATOR);

        Field nodeExecutionF = FlowNode.class.getDeclaredField("exec");
        nodeExecutionF.setAccessible(true);

        // Read each node via deserialization from storage,
        // and sanity check the node, the actions, and the ArgumentsAction read back right
        for (FlowNode f : nodes) {
            XmlFile file = (XmlFile) (getFileM.invoke(storage, f.getId()));
            Object tagObj = file.read();
            assertNotNull(tagObj);

            // Check actions & node in the Tag object, but without getting at the private Tag class
            Field actionField = tagObj.getClass().getDeclaredField("actions");
            Field nodeField = tagObj.getClass().getDeclaredField("node");

            actionField.setAccessible(true);
            nodeField.setAccessible(true);

            Action[] deserializedActions = (Action[]) actionField.get(tagObj);
            FlowNode deserializedNode = (FlowNode) (nodeField.get(tagObj));
            nodeExecutionF.set(deserializedNode, f.getExecution());

            assertNotNull(deserializedNode);
            if (f.getActions().size() > 0) {
                assertNotNull(deserializedActions);
                assertEquals(f.getActions().size(), deserializedActions.length);
            }

            ArgumentsAction expectedInfoAction = f.getPersistentAction(ArgumentsAction.class);
            if (expectedInfoAction != null) {
                Action deserializedInfoAction = Stream.of(deserializedActions)
                        .filter(ArgumentsAction.class::isInstance)
                        .findFirst()
                        .orElse(null);
                assertNotNull(deserializedInfoAction);
                ArgumentsAction ArgumentsAction = (ArgumentsAction) deserializedInfoAction;

                // Compare original and deserialized step arguments to see if they match
                assertEquals(
                        ArgumentsAction.getStepArgumentsAsString(f),
                        ArgumentsAction.getStepArgumentsAsString(deserializedNode));
                Map<String, Object> expectedParams = expectedInfoAction.getArguments();
                Map<String, Object> deserializedParams = ArgumentsAction.getArguments();
                assertEquals(expectedParams.size(), deserializedParams.size());
                for (String s : expectedParams.keySet()) {
                    Object expectedVal = expectedParams.get(s);
                    Object actualVal = deserializedParams.get(s);
                    if (expectedVal instanceof Comparable) {
                        assertEquals(actualVal, expectedVal);
                    }
                }
            }
        }
    }

    @Test
    public void testStringSafetyTest() throws Exception {
        String input = "I have a secret p4ssw0rd";
        HashMap<String, String> passwordBinding = new HashMap<>();
        passwordBinding.put("mypass", "p4ssw0rd");
        Set<String> sensitiveVariables = new HashSet<>();
        sensitiveVariables.add("mypass");
        assertThat(
                "Input with no variables is safe",
                ArgumentsActionImpl.replaceSensitiveVariables(input, new EnvVars(), sensitiveVariables),
                is(input));
        assertThat(
                "Input containing bound value is unsafe",
                ArgumentsActionImpl.replaceSensitiveVariables(input, new EnvVars(passwordBinding), sensitiveVariables),
                is("I have a secret ${mypass}"));
        assertThat(
                "EnvVars that do not occur are safe",
                ArgumentsActionImpl.replaceSensitiveVariables(
                        "I have no passwords", new EnvVars(passwordBinding), sensitiveVariables),
                is("I have no passwords"));
    }

    @Test
    public void testRecursiveSanitizationOfContent() {
        EnvVars env = new EnvVars();
        String secretUsername = "secretuser";
        env.put("USERVARIABLE", secretUsername); // assume secretuser is a bound credential

        Set<String> sensitiveVariables = new HashSet<>();
        sensitiveVariables.add("USERVARIABLE");

        int maxLen = ArgumentsActionImpl.getMaxRetainedLength();
        ArgumentsActionImpl impl = new ArgumentsActionImpl(sensitiveVariables);

        String oversizedString = generateStringOfSize(maxLen + 10);

        // Simplest masking of secret and oversized value
        assertEquals("${USERVARIABLE}", impl.sanitizeObjectAndRecordMutation(secretUsername, env));
        assertFalse(impl.isUnmodifiedArguments());
        impl.isUnmodifiedBySanitization = true;

        assertEquals(
                ArgumentsAction.NotStoredReason.OVERSIZE_VALUE,
                impl.sanitizeObjectAndRecordMutation(oversizedString, env));
        assertFalse(impl.isUnmodifiedArguments());
        impl.isUnmodifiedBySanitization = true;

        // Test explosion of Step & UninstantiatedDescribable objects
        Step mystep = new EchoStep("I have a " + secretUsername);
        Map<String, ?> singleSanitization = (Map<String, Object>) (impl.sanitizeObjectAndRecordMutation(mystep, env));
        assertEquals(1, singleSanitization.size());
        assertEquals("I have a ${USERVARIABLE}", singleSanitization.get("message"));
        assertFalse(impl.isUnmodifiedArguments());
        impl.isUnmodifiedBySanitization = true;
        singleSanitization = ((UninstantiatedDescribable) (impl.sanitizeObjectAndRecordMutation(
                        mystep.getDescriptor().uninstantiate(mystep), env)))
                .getArguments();
        assertEquals(1, singleSanitization.size());
        assertEquals("I have a ${USERVARIABLE}", singleSanitization.get("message"));
        assertFalse(impl.isUnmodifiedArguments());
        impl.isUnmodifiedBySanitization = true;

        // Maps
        HashMap<String, Object> dangerous = new HashMap<>();
        dangerous.put("name", secretUsername);
        Object sanitized = impl.sanitizeMapAndRecordMutation(dangerous, env);
        Assert.assertNotEquals(sanitized, dangerous);
        assertThat(sanitized, instanceOf(Map.class));
        Map<String, Object> sanitizedMap = (Map<String, Object>) sanitized;
        assertEquals("${USERVARIABLE}", sanitizedMap.get("name"));
        assertFalse(impl.isUnmodifiedArguments());
        impl.isUnmodifiedBySanitization = true;

        Object identical = impl.sanitizeMapAndRecordMutation(dangerous, new EnvVars()); // String is no longer dangerous
        assertEquals(identical, dangerous);
        assertTrue(impl.isUnmodifiedArguments());

        // Lists
        List unsanitizedList = Arrays.asList("cheese", null, secretUsername);
        Object sanitized2 = impl.sanitizeListAndRecordMutation(unsanitizedList, env);
        assertThat(sanitized2, instanceOf(List.class));
        List sanitizedList = (List) sanitized2;
        assertEquals(3, sanitizedList.size());
        assertFalse(impl.isUnmodifiedArguments());
        assertEquals("${USERVARIABLE}", sanitizedList.get(2));
        impl.isUnmodifiedBySanitization = true;

        assertEquals(unsanitizedList, impl.sanitizeObjectAndRecordMutation(unsanitizedList, new EnvVars()));
        assertEquals(unsanitizedList, impl.sanitizeListAndRecordMutation(unsanitizedList, new EnvVars()));
    }

    @Test
    @Issue("JENKINS-67380")
    public void oversizedMap() {
        {
            // a map with reasonable size should not be truncated
            ArgumentsActionImpl impl = new ArgumentsActionImpl(Collections.emptySet());
            Map<String, Object> smallMap = new HashMap<>();
            smallMap.put("key1", generateStringOfSize(ArgumentsActionImpl.getMaxRetainedLength() / 10));
            Object sanitizedSmallMap = impl.sanitizeMapAndRecordMutation(smallMap, null);
            Assert.assertEquals(sanitizedSmallMap, smallMap);
            Assert.assertTrue(impl.isUnmodifiedArguments());
            impl.isUnmodifiedBySanitization = true;
        }

        {
            // arguments map keys should be kept, but values should be truncated if too large
            Map<String, Object> bigMap = new HashMap<>();
            String bigString = generateStringOfSize(ArgumentsActionImpl.getMaxRetainedLength() + 10);
            bigMap.put("key1", bigString);
            ArgumentsActionImpl impl = new ArgumentsActionImpl(bigMap, null, Collections.emptySet());
            Assert.assertEquals(ArgumentsAction.NotStoredReason.OVERSIZE_VALUE, impl.getArgumentValueOrReason("key1"));
            Assert.assertFalse(impl.isUnmodifiedArguments());
        }

        {
            // an arbitrary map should be truncated if it is too large overall
            Map<String, Object> bigMap2 = new HashMap<>();
            String bigString2 = generateStringOfSize(ArgumentsActionImpl.getMaxRetainedLength());
            bigMap2.put("key1", bigString2);
            ArgumentsActionImpl impl = new ArgumentsActionImpl(Collections.emptySet());
            Object sanitizedBigMap2 = impl.sanitizeMapAndRecordMutation(bigMap2, null);
            Assert.assertNotEquals(sanitizedBigMap2, bigMap2);
            Assert.assertEquals(ArgumentsAction.NotStoredReason.OVERSIZE_VALUE, sanitizedBigMap2);
            Assert.assertFalse(impl.isUnmodifiedArguments());
            impl.isUnmodifiedBySanitization = true;
        }
    }

    @Test
    public void oversizedList() {
        ArgumentsActionImpl impl = new ArgumentsActionImpl(Collections.emptySet());
        List unsanitized = List.of(generateStringOfSize(ArgumentsActionImpl.getMaxRetainedLength()));
        Object sanitized = impl.sanitizeListAndRecordMutation(unsanitized, null);
        Assert.assertEquals(ArgumentsAction.NotStoredReason.OVERSIZE_VALUE, sanitized);
    }

    @Test
    public void oversizedArray() {
        ArgumentsActionImpl impl = new ArgumentsActionImpl(Collections.emptySet());
        String[] unsanitized = new String[] {generateStringOfSize(ArgumentsActionImpl.getMaxRetainedLength())};
        Object sanitized = impl.sanitizeArrayAndRecordMutation(unsanitized, null);
        Assert.assertEquals(ArgumentsAction.NotStoredReason.OVERSIZE_VALUE, sanitized);
    }

    private static String generateStringOfSize(int size) {
        char[] bigChars = new char[size];
        Arrays.fill(bigChars, 'a');
        return String.valueOf(bigChars);
    }

    @Test
    public void testArraySanitization() {
        EnvVars env = new EnvVars();
        String secretUsername = "IAmA";
        env.put("USERVARIABLE", secretUsername); // assume secretuser is a bound credential
        Set<String> sensitiveVariables = new HashSet<>();
        sensitiveVariables.add("USERVARIABLE");

        HashMap<String, Object> args = new HashMap<>();
        args.put("ints", new int[] {1, 2, 3});
        args.put("strings", new String[] {"heh", secretUsername, "lumberjack"});
        ArgumentsActionImpl filtered = new ArgumentsActionImpl(args, env, sensitiveVariables);

        Map<String, Object> filteredArgs = filtered.getArguments();
        assertEquals(2, filteredArgs.size());
        assertThat(filteredArgs, IsMapContaining.hasEntry("ints", ArgumentsAction.NotStoredReason.UNSERIALIZABLE));
        assertThat(filteredArgs, IsMapContaining.hasKey("strings"));
        Object[] contents = (Object[]) (filteredArgs.get("strings"));
        assertArrayEquals(
                new Object[] {"heh", "${USERVARIABLE}", "lumberjack"}, (Object[]) (filteredArgs.get("strings")));
    }

    @Test
    @Issue("JENKINS-48644")
    public void testMissingDescriptionInsideStage() throws Exception {
        Assume.assumeTrue(r.jenkins.getComputer("").isUnix()); // No need for windows-specific testing
        WorkflowJob j = r.createProject(WorkflowJob.class);
        j.setDefinition(new CpsFlowDefinition(
                "node{\n" + "   stage ('Build') {\n"
                        + "       sh \"echo 'Building'\"\n"
                        + "   }\n"
                        + "   stage ('Test') {\n"
                        + "       sh \"echo 'testing'\"\n"
                        + "   }\n"
                        + "    stage ('Deploy') {\n"
                        + "       sh \"echo 'deploy'\"\n"
                        + "   }\n"
                        + "}\n",
                true));
        WorkflowRun run = r.buildAndAssertSuccess(j);
        List<FlowNode> nodes = new LinearScanner().filteredNodes(run.getExecution(), new NodeStepTypePredicate("sh"));
        for (FlowNode f : nodes) {
            if (ArgumentsAction.getStepArgumentsAsString(f) == null) {
                fail("No arguments action for node: " + f.toString());
            }
        }
    }

    @Test
    public void testBasicCreateAndMask() throws Exception {
        HashMap<String, String> passwordBinding = new HashMap<>();
        passwordBinding.put("mypass", "p4ssw0rd");
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("message", "I have a secret p4ssw0rd");
        Set<String> sensitiveVariables = new HashSet<>();
        sensitiveVariables.add("mypass");

        Field maxSizeF = ArgumentsAction.class.getDeclaredField("MAX_RETAINED_LENGTH");
        maxSizeF.setAccessible(true);
        int maxSize = maxSizeF.getInt(null);

        // Same string, unsanitized
        ArgumentsActionImpl argumentsActionImpl = new ArgumentsActionImpl(arguments, new EnvVars(), sensitiveVariables);
        assertTrue(argumentsActionImpl.isUnmodifiedArguments());
        assertEquals(arguments.get("message"), argumentsActionImpl.getArgumentValueOrReason("message"));
        assertEquals(1, argumentsActionImpl.getArguments().size());
        assertEquals(
                "I have a secret p4ssw0rd", argumentsActionImpl.getArguments().get("message"));

        // Test sanitizing arguments now
        argumentsActionImpl = new ArgumentsActionImpl(arguments, new EnvVars(passwordBinding), sensitiveVariables);
        assertFalse(argumentsActionImpl.isUnmodifiedArguments());
        assertEquals("I have a secret ${mypass}", argumentsActionImpl.getArgumentValueOrReason("message"));
        assertEquals(1, argumentsActionImpl.getArguments().size());
        assertEquals(
                "I have a secret ${mypass}", argumentsActionImpl.getArguments().get("message"));

        // Mask oversized values
        arguments.clear();
        arguments.put("text", RandomStringUtils.random(maxSize + 1));
        argumentsActionImpl = new ArgumentsActionImpl(arguments);
        assertEquals(
                ArgumentsAction.NotStoredReason.OVERSIZE_VALUE, argumentsActionImpl.getArgumentValueOrReason("text"));
    }

    @Test
    @Issue("JENKINS-50752")
    public void testHandleUnserializableArguments() throws Exception {
        HashMap<String, Object> unserializable = new HashMap<>(3);
        Object me = new Object() {
            Object writeReplace() {
                throw new RuntimeException("Can't serialize me nyah nyah!");
            }
        };
        unserializable.put("ex", me);

        ArgumentsActionImpl impl = new ArgumentsActionImpl(unserializable);
        assertEquals(ArgumentsAction.NotStoredReason.UNSERIALIZABLE, impl.getArgumentValueOrReason("ex"));
        assertFalse("Should show argument removed by sanitization", impl.isUnmodifiedArguments());
    }

    static class SuperSpecialThing implements Serializable {
        int value = 5;
        String component = "heh";

        public SuperSpecialThing() {}

        public SuperSpecialThing(int value, String str) {
            this.value = value;
            this.component = str;
        }

        @Override
        public boolean equals(Object ob) {
            if (ob instanceof SuperSpecialThing) {
                SuperSpecialThing other = (SuperSpecialThing) ob;
                return this.value == other.value && this.component.equals(other.component);
            }
            return false;
        }
    }

    @Issue("JENKINS-54186")
    @Test
    public void fauxDescribable() throws Exception {
        logging.record(ArgumentsActionImpl.class, Level.FINE);
        ArgumentsActionImpl impl = new ArgumentsActionImpl(Map.of("curve", new Fractal()));
        Map<String, Object> args = impl.getArguments();
        assertThat(args, IsMapContaining.hasEntry("curve", ArgumentsAction.NotStoredReason.UNSERIALIZABLE));
    }

    public static final class Fractal extends Curve {
        @Override
        public String getDescription() {
            return "shape way too complex to describe";
        }
    }

    @Test
    @Issue("JENKINS-54032")
    public void testAvoidStoringSpecialTypes() throws Exception {
        HashMap<String, Object> testMap = new HashMap<>();
        testMap.put("safe", 5);
        testMap.put("maskme", new SuperSpecialThing());
        testMap.put("maskMyMapValue", Map.of("bob", new SuperSpecialThing(-5, "testing")));
        testMap.put(
                "maskAnElement",
                List.of("cheese", new SuperSpecialThing(5, "pi"), -8, List.of("nested", new SuperSpecialThing())));

        ArgumentsActionImpl argsAction = new ArgumentsActionImpl(testMap);
        Map<String, Object> maskedArgs = argsAction.getArguments();
        assertThat(maskedArgs, IsMapContaining.hasEntry("maskme", ArgumentsAction.NotStoredReason.UNSERIALIZABLE));
        assertThat(maskedArgs, IsMapContaining.hasEntry("safe", 5));

        // Sub-map sanitization
        Map<String, Object> subMap = (Map<String, Object>) (maskedArgs.get("maskMyMapValue"));
        assertThat(subMap, IsMapContaining.hasEntry("bob", ArgumentsAction.NotStoredReason.UNSERIALIZABLE));

        // Nested list masking too!
        List<Serializable> sublist = (List<Serializable>) (maskedArgs.get("maskAnElement"));
        assertThat(sublist, Matchers.hasItem("cheese"));
        assertThat(sublist, Matchers.hasItems("cheese", ArgumentsAction.NotStoredReason.UNSERIALIZABLE, -8));
        List<Serializable> subSubList = (List<Serializable>) (sublist.get(3));
        assertThat(subSubList, Matchers.contains("nested", ArgumentsAction.NotStoredReason.UNSERIALIZABLE));
    }

    @Test
    public void testBasicCredentials() throws Exception {
        String username = "bob";
        String password = "s3cr3t";
        UsernamePasswordCredentialsImpl c =
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "test", "sample", username, password);
        c.setUsernameSecret(true);
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), c);

        WorkflowJob job = r.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition(
                "node{ withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'test',\n"
                        + "                usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {\n"
                        + "    //available as an env variable, but will be masked if you try to print it out any which way\n"
                        + "    echo \"$PASSWORD'\" \n"
                        + "    echo \"${env.USERNAME}\"\n"
                        + "    echo \"bob\"\n"
                        + "} }\n"
                        + "withCredentials([usernamePassword(credentialsId: 'test', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {\n"
                        + "  echo \"${env.USERNAME} ${env.PASSWORD}\"\n"
                        + "}",
                false));
        WorkflowRun run = job.scheduleBuild2(0).getStartCondition().get();
        r.waitForCompletion(run);
        FlowExecution exec = run.getExecution();
        String log = JenkinsRule.getLog(run);
        ForkScanner scanner = new ForkScanner();
        List<FlowNode> filtered =
                scanner.filteredNodes(exec, new DescriptorMatchPredicate(BindingStep.DescriptorImpl.class));

        // Check the binding step is OK
        assertEquals(8, filtered.size());
        FlowNode node = Collections2.filter(filtered, FlowScanningUtils.hasActionPredicate(ArgumentsActionImpl.class))
                .iterator()
                .next();
        ArgumentsActionImpl act = node.getPersistentAction(ArgumentsActionImpl.class);
        assertNotNull(act.getArgumentValue("bindings"));
        assertNotNull(act.getArguments().get("bindings"));

        // Test that masking really does mask bound credentials appropriately
        filtered = scanner.filteredNodes(exec, new DescriptorMatchPredicate(EchoStep.DescriptorImpl.class));
        for (FlowNode f : filtered) {
            act = f.getPersistentAction(ArgumentsActionImpl.class);
            assertThat(
                    (String) act.getArguments().get("message"),
                    allOf(not(containsString("bob")), not(containsString("s3cr3t"))));
        }

        List<FlowNode> allStepped = scanner.filteredNodes(
                run.getExecution().getCurrentHeads(), FlowScanningUtils.hasActionPredicate(ArgumentsActionImpl.class));
        assertEquals(6, allStepped.size()); // One ArgumentsActionImpl per block or atomic step

        testDeserialize(exec);
    }

    /** Handling of Metasteps with nested parameter -- we unwrap the step if there's just a single parameter given
     *  Otherwise we leave it as-is.
     */
    @Test
    public void testSpecialMetastepCases() throws Exception {
        // First we test a metastep with a state argument
        WorkflowJob job = r.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition(
                // Need to do some customization to load me
                "state(moderate: true, state:[$class: 'Oregon']) \n", false));
        WorkflowRun run = r.buildAndAssertSuccess(job);
        LinearScanner scan = new LinearScanner();
        FlowNode node = scan.findFirstMatch(
                run.getExecution(), new DescriptorMatchPredicate(StateMetaStep.DescriptorImpl.class));
        assertNotNull(node);
        Map<String, Object> args = ArgumentsAction.getArguments(node);
        assertEquals(2, args.size());
        assertEquals(true, args.get("moderate"));
        Map<String, Object> stateArgs = (Map<String, Object>) args.get("state");
        assertTrue(
                "Nested state Describable should only include a class argument or none at all",
                stateArgs.size() <= 1
                        && Sets.difference(stateArgs.keySet(), Set.of("$class")).size() == 0);

        // Same metastep but only one arg supplied, shouldn't auto-unwrap the internal step because can take 2 args
        job = r.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition(
                // Need to do some customization to load me
                "state(state:[$class: 'Oregon']) \n"
                        + "state(new org.jenkinsci.plugins.workflow.testMetaStep.Oregon()) \n",
                false));
        run = r.buildAndAssertSuccess(job);
        List<FlowNode> nodes = scan.filteredNodes(
                run.getExecution(), new DescriptorMatchPredicate(StateMetaStep.DescriptorImpl.class));
        for (FlowNode n : nodes) {
            assertNotNull(n);
            args = ArgumentsAction.getArguments(n);
            assertEquals(1, args.size());
            Map<String, Object> argsMap = (Map) args;
            Object stateValue = argsMap.get("state");
            if (stateValue instanceof Map) {
                assertEquals("Oregon", ((Map<String, Object>) stateValue).get("$class"));
            }
        }
    }

    @Test
    public void simpleSemaphoreStep() throws Exception {
        WorkflowJob job = r.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("semaphore 'wait'", false));
        WorkflowRun run = job.scheduleBuild2(0).getStartCondition().get();
        SemaphoreStep.waitForStart("wait/1", run);
        FlowNode semaphoreNode = run.getExecution().getCurrentHeads().get(0);
        CpsThread thread = CpsThread.current();
        SemaphoreStep.success("wait/1", null);
        r.waitForCompletion(run);
        testDeserialize(run.getExecution());
    }

    @Test
    public void nul() throws Exception {
        var job = r.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("echo 'one\\0two'; echo 'this part is fine'", true));
        var store = r.buildAndAssertSuccess(job).getRootDir().toPath().resolve("workflow-completed/flowNodeStore.xml");
        assertThat(store + " was written", Files.readString(store), containsString("this part is fine"));
    }

    @Test
    public void testArgumentDescriptions() throws Exception {
        WorkflowJob job = r.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition(
                "echo 'test' \n " + " node('"
                        + r.jenkins.getSelfLabel().getName() + "') { \n" + "   retry(3) {\n"
                        + "     if (isUnix()) { \n"
                        + "       sh 'whoami' \n"
                        + "     } else { \n"
                        + "       bat 'echo %USERNAME%' \n"
                        + "     }\n"
                        + "   } \n"
                        + "}",
                false));
        WorkflowRun run = r.buildAndAssertSuccess(job);
        LinearScanner scan = new LinearScanner();

        // Argument test
        FlowNode echoNode =
                scan.findFirstMatch(run.getExecution().getCurrentHeads().get(0), new NodeStepTypePredicate("echo"));
        assertEquals(
                "test",
                echoNode.getPersistentAction(ArgumentsAction.class)
                        .getArguments()
                        .values()
                        .iterator()
                        .next());
        assertEquals("test", ArgumentsAction.getStepArgumentsAsString(echoNode));

        if (Functions.isWindows()) {
            FlowNode batchNode =
                    scan.findFirstMatch(run.getExecution().getCurrentHeads().get(0), new NodeStepTypePredicate("bat"));
            assertEquals(
                    "echo %USERNAME%",
                    batchNode
                            .getPersistentAction(ArgumentsAction.class)
                            .getArguments()
                            .values()
                            .iterator()
                            .next());
            assertEquals("echo %USERNAME%", ArgumentsAction.getStepArgumentsAsString(batchNode));
        } else { // Unix
            FlowNode shellNode =
                    scan.findFirstMatch(run.getExecution().getCurrentHeads().get(0), new NodeStepTypePredicate("sh"));
            assertEquals(
                    "whoami",
                    shellNode
                            .getPersistentAction(ArgumentsAction.class)
                            .getArguments()
                            .values()
                            .iterator()
                            .next());
            assertEquals("whoami", ArgumentsAction.getStepArgumentsAsString(shellNode));
        }

        FlowNode nodeNode = scan.findFirstMatch(
                run.getExecution().getCurrentHeads().get(0),
                Predicates.and(
                        Predicates.instanceOf(StepStartNode.class),
                        new NodeStepTypePredicate("node"),
                        FlowScanningUtils.hasActionPredicate(ArgumentsActionImpl.class)));
        assertEquals(
                r.jenkins.getSelfLabel().getName(),
                nodeNode.getPersistentAction(ArgumentsAction.class)
                        .getArguments()
                        .values()
                        .iterator()
                        .next());
        assertEquals(r.jenkins.getSelfLabel().getName(), ArgumentsAction.getStepArgumentsAsString(nodeNode));

        testDeserialize(run.getExecution());
    }

    @Test
    public void testUnusualStepInstantiations() throws Exception {
        WorkflowJob job = r.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition(
                " node('" + r.jenkins.getSelfLabel().getName() + "') { \n"
                        + "   writeFile text: 'hello world', file: 'msg.out'\n"
                        + "   step([$class: 'ArtifactArchiver', artifacts: 'msg.out', fingerprint: false])\n "
                        + "   withEnv(['CUSTOM=val']) {\n"
                        + // Symbol-based, because withEnv is a metastep; TODO huh? no it is not
                        "     echo env.CUSTOM\n"
                        + "   }\n"
                        + "}",
                false));
        WorkflowRun run = r.buildAndAssertSuccess(job);
        LinearScanner scan = new LinearScanner();

        FlowNode testNode = scan.findFirstMatch(
                run.getExecution().getCurrentHeads().get(0), new NodeStepTypePredicate("writeFile"));
        ArgumentsAction act = testNode.getPersistentAction(ArgumentsAction.class);
        assertNotNull(act);
        assertEquals("hello world", act.getArgumentValue("text"));
        assertEquals("msg.out", act.getArgumentValue("file"));

        testNode = scan.findFirstMatch(run.getExecution().getCurrentHeads().get(0), new NodeStepTypePredicate("step"));
        act = testNode.getPersistentAction(ArgumentsAction.class);
        assertNotNull(act);
        Map<String, Object> delegateMap = ((Map<String, Object>) act.getArgumentValue("delegate"));
        assertEquals("msg.out", delegateMap.get("artifacts"));
        assertEquals(Boolean.FALSE, delegateMap.get("fingerprint"));

        testNode = run.getExecution().getNode("7"); // Start node for EnvAction
        act = testNode.getPersistentAction(ArgumentsAction.class);
        assertNotNull(act);
        assertEquals(1, act.getArguments().size());
        Object ob = act.getArguments().get("overrides");
        assertEquals("CUSTOM=val", (String) ((ArrayList) ob).get(0));
        testDeserialize(run.getExecution());
    }

    @Test
    public void testReallyUnusualStepInstantiations() throws Exception {
        WorkflowJob job = r.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("""
                node() {
                   writeFile text: 'hello world', file: 'msg.out'
                   step(new hudson.tasks.ArtifactArchiver('msg.out')) // note, not whitelisted
                }""", false));
        WorkflowRun run = r.buildAndAssertSuccess(job);
        LinearScanner scan = new LinearScanner();

        FlowNode testNode =
                scan.findFirstMatch(run.getExecution().getCurrentHeads().get(0), new NodeStepTypePredicate("step"));
        ArgumentsAction act = testNode.getPersistentAction(ArgumentsAction.class);
        assertNotNull(act);
        Object delegate = act.getArgumentValue("delegate");

        // Test that for a raw Describable we explode it into its arguments Map
        assertThat(delegate, instanceOf(UninstantiatedDescribable.class));
        UninstantiatedDescribable ud = (UninstantiatedDescribable) delegate;
        Map<String, ?> args = (Map<String, ?>) (((UninstantiatedDescribable) delegate).getArguments());
        assertThat(args, IsMapContaining.hasEntry("artifacts", "msg.out"));
        assertEquals(ArtifactArchiver.class.getName(), ud.getModel().getType().getName());
    }

    @Test
    public void enumArguments() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition(
                "import java.util.concurrent.TimeUnit\n" + "import java.time.temporal.ChronoUnit\n"
                        + "enum UserDefinedEnum {\n"
                        + "    VALUE;\n"
                        + "    UserDefinedEnum() { /* JENKINS-33023 */ }\n"
                        + "}\n"
                        + "nop(UserDefinedEnum.VALUE)\n"
                        + "nop(TimeUnit.MINUTES)\n"
                        + "nop(ChronoUnit.MINUTES)\n",
                true));
        // TODO add to generic-whitelist
        ScriptApproval.get().approveSignature("staticField java.time.temporal.ChronoUnit MINUTES");
        WorkflowRun run = r.buildAndAssertSuccess(p);
        List<FlowNode> nodes =
                new DepthFirstScanner().filteredNodes(run.getExecution(), new NodeStepTypePredicate("nop"));
        assertThat(
                nodes.get(0).getPersistentAction(ArgumentsAction.class).getArgumentValueOrReason("value"),
                equalTo(ChronoUnit.MINUTES));
        assertThat(
                nodes.get(1).getPersistentAction(ArgumentsAction.class).getArgumentValueOrReason("value"),
                equalTo(TimeUnit.MINUTES));
        assertThat(
                nodes.get(2).getPersistentAction(ArgumentsAction.class).getArgumentValueOrReason("value"),
                equalTo(NotStoredReason.UNSERIALIZABLE));
    }

    public static class NopStep extends Step {
        @DataBoundConstructor
        public NopStep(Object value) {}

        @Override
        public StepExecution start(StepContext context) throws Exception {
            return StepExecutions.synchronousVoid(context, c -> {});
        }

        @TestExtension
        public static class DescriptorImpl extends StepDescriptor {
            @Override
            public String getFunctionName() {
                return "nop";
            }

            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                return Collections.emptySet();
            }
        }
    }
}
