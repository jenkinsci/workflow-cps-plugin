package org.jenkinsci.plugins.workflow.cps;

import hudson.model.Result;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class CpsScriptTest {

    @ClassRule
    public static BuildWatcher watcher = new BuildWatcher();

    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    /**
     * Test the 'evaluate' method call.
     * The first test case.
     */
    @Test
    public void evaluate() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("assert evaluate('1+2+3')==6", true));
        r.buildAndAssertSuccess(p);
    }

    /**
     * The code getting evaluated must also get sandbox transformed.
     */
    @Test
    public void evaluateShallSandbox() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("evaluate('Jenkins.getInstance()')", true));
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        // execution should have failed with error, pointing that Jenkins.getInstance() is not allowed from sandbox
        r.assertLogContains(
                "org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use staticMethod jenkins.model.Jenkins getInstance",
                b);
    }

    /** Need to be careful that internal method names in {@link CpsScript} are not likely identifiers in user scripts. */
    @Test
    public void methodNameClash() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition(
                "def build() {20}; def initialize() {10}; def env() {10}; def getShell() {2}; assert build() + initialize() + env() + shell == 42",
                true));
        r.buildAndAssertSuccess(p);
    }

    @Issue("JENKINS-38167")
    @Test
    public void bindingDuringConstructor() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition(
                "@groovy.transform.Field def opt = (binding.hasVariable('opt')) ? opt : 'default'", true));
        r.buildAndAssertSuccess(p);
    }

    @Issue("SECURITY-2428")
    @Test
    public void blockImplicitCastingInEvaluate() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        BiFunction<String, String, String> embeddedScript = (decl, main) -> "" + "class Test"
                + counter.incrementAndGet() + " {\\n" + "  "
                + decl + "\\n" + "  Object map\\n"
                + "  @NonCPS public void main(String[] args) { "
                + main + " }\\n" + "}\\n";
        WorkflowJob p = r.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition(
                "list = ['secret.key']\n" + "map = [:]\n"
                        + "evaluate('"
                        + embeddedScript.apply("File list", "map.file = list") + "')\n" + "file = map.file\n"
                        + "evaluate('"
                        + embeddedScript.apply("String[] file", "map.lines = file") + "')\n"
                        + "for (String line in map.lines) { echo(line) }\n",
                true));
        WorkflowRun b = r.buildAndAssertStatus(Result.FAILURE, p);
        r.assertLogContains("Scripts not permitted to use new java.io.File java.lang.String", b);
    }

    @Issue("JENKINS-73031")
    @Test
    public void staticInterfaceMethod() throws Exception {
        var p = r.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("def x = List.of(1, 2, 3); echo(/x=$x/)", false));
        var b = r.buildAndAssertStatus(Result.FAILURE, p);
        r.assertLogContains("JENKINS-73031", b);
    }

    @Test
    public void blockRun() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("run(null, ['test'] as String[])\n", true));
        WorkflowRun b = r.buildAndAssertStatus(Result.FAILURE, p);
        // The existence of CpsScript.run leads me to believe that it was intended to be allowed by CpsWhitelist, but
        // that is not currently the case, and I see no reason to start allowing it at this point.
        r.assertLogContains(
                "Scripts not permitted to use method groovy.lang.Script run java.io.File java.lang.String[]", b);
    }

    @Test public void methodTooLargeExceptionFabricated() throws Exception {
        // Fabricate a MethodTooLargeException which "normally" happens when evaluated
        // groovy script becomes a Java class too large for Java to handle internally.
        // In Jenkins practice this can happen not only due to large singular pipelines
        // (one big nudge to offload code into shared libraries), but was also seen due
        // to heavy nesting of exception handling and other loops (simple refactoring
        // can help).
        WorkflowJob p = r.createProject(WorkflowJob.class);
        // sandbox == false to allow creation of the exception here:
        p.setDefinition(new CpsFlowDefinition(
                "import groovyjarjarasm.asm.MethodTooLargeException;\n\n" +
                        "throw new MethodTooLargeException('className', 'methodName', 'methodDescriptor', 65535);"
                , false));
        WorkflowRun b = r.buildAndAssertStatus(Result.FAILURE, p);
        r.assertLogContains("groovyjarjarasm.asm.MethodTooLargeException: Method too large: className.methodName methodDescriptor", b);
        r.assertLogContains("at WorkflowScript.run(WorkflowScript:3)", b);
        r.assertLogContains("at ___cps.transform___(Native Method)", b);
    }

    @Test public void methodTooLargeExceptionRealistic() throws Exception {
        // See comments above. Here we try to really induce a "method too large"
        // condition by abusing the nesting of exception-handling, too many stages
        // or methods, and whatever else we can throw at it.
        WorkflowJob p = r.createProject(WorkflowJob.class);
        StringBuffer sbMethods = new StringBuffer();
        StringBuffer sbStages = new StringBuffer();

        // Limits to the "max":
        // * java.lang.StackOverflowError varies per JDK platform
        //   (CI on Linux was unhappy with 255, on Windows with 1023)
        // * Up to 255 stages allowed
        // FIXME? Tune the value per platform and/or dynamically
        //  based on stack overflow mention in build log?
        int i, maxStagesMethods = 250, maxTryCatch = 127;

        for (i = 0; i < maxStagesMethods; i++) {
            sbStages.append("stage('Stage " + i + "') { steps { method" + i + "(); } }\n");
        }

        for (i = 0; i < maxStagesMethods; i++) {
            sbMethods.append("def method" + i + "() { echo 'i = " + i + "'; }\n");
        }

        sbMethods.append("def method() {\n");
        for (i = 0; i < maxTryCatch; i++) {
            sbMethods.append("try { // " + i + "\n");
        }
        sbMethods.append("  Integer x = 'zzz'; // incur conversion exception\n");
        for (i = 0; i < maxTryCatch; i++) {
            sbMethods.append("} catch (Throwable t) { // " + i + "\n  method" + i + "(); throw t; }\n");
        }
        sbMethods.append("}\n");

        p.setDefinition(new CpsFlowDefinition(sbMethods.toString() +
                "pipeline {\n" +
                "    agent none;\n" +
                "    stages {\n" +
                "        stage ('Test stage') {\n" +
                "            steps {\n" +
                "                script {\n" +
                "                    echo 'BEGINNING TEST IN PIPELINE';\n" +
                "                    method();\n" +
                "                    echo 'ENDED TEST IN PIPELINE';\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                sbStages.toString() +
                "    }\n" +
                "}\n" +
                "echo 'BEGINNING TEST OUT OF PIPELINE';\n" +
                "method();\n" +
                "echo 'ENDED TEST OUT OF PIPELINE';\n"
                , true));

        WorkflowRun b = p.scheduleBuild2(0).get();

        // DEV-TEST // System.out.println(b.getLog());

        // Do we have the expected error at all?
        // (Maybe also stack overflow on some platforms,
        // possibly success on others)
        r.assertLogContains("MethodTooLargeException", b);

        // "Prettier" explanation added by CpsFlowExecution.parseScript():
        r.assertLogContains("FAILED to parse WorkflowScript (the pipeline script) due to MethodTooLargeException", b);

/*
    // Report as of release 3880.vb_ef4b_5cfd270 (Feb 2024)
    // and same pattern seen since at least Jun 2022 (note
    // that numbers after ___cps___ differ from job to job):

org.codehaus.groovy.control.MultipleCompilationErrorsException: startup failed:
General error during class generation: Method too large: WorkflowScript.___cps___1 ()Lcom/cloudbees/groovy/cps/impl/CpsFunction;

groovyjarjarasm.asm.MethodTooLargeException: Method too large: WorkflowScript.___cps___1 ()Lcom/cloudbees/groovy/cps/impl/CpsFunction;
	at groovyjarjarasm.asm.MethodWriter.computeMethodInfoSize(MethodWriter.java:2087)
	at groovyjarjarasm.asm.ClassWriter.toByteArray(ClassWriter.java:447)
	at org.codehaus.groovy.control.CompilationUnit$17.call(CompilationUnit.java:850)
	at org.codehaus.groovy.control.CompilationUnit.applyToPrimaryClassNodes(CompilationUnit.java:1087)
	at org.codehaus.groovy.control.CompilationUnit.doPhaseOperation(CompilationUnit.java:624)
	at org.codehaus.groovy.control.CompilationUnit.processPhaseOperations(CompilationUnit.java:602)
	at org.codehaus.groovy.control.CompilationUnit.compile(CompilationUnit.java:579)
	at groovy.lang.GroovyClassLoader.doParseClass(GroovyClassLoader.java:323)
	at groovy.lang.GroovyClassLoader.parseClass(GroovyClassLoader.java:293)
	at org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.GroovySandbox$Scope.parse(GroovySandbox.java:163)
	at org.jenkinsci.plugins.workflow.cps.CpsGroovyShell.doParse(CpsGroovyShell.java:190)
	at org.jenkinsci.plugins.workflow.cps.CpsGroovyShell.reparse(CpsGroovyShell.java:175)
	at org.jenkinsci.plugins.workflow.cps.CpsFlowExecution.parseScript(CpsFlowExecution.java:637)
	at org.jenkinsci.plugins.workflow.cps.CpsFlowExecution.start(CpsFlowExecution.java:583)
	at org.jenkinsci.plugins.workflow.job.WorkflowRun.run(WorkflowRun.java:335)
	at hudson.model.ResourceController.execute(ResourceController.java:101)
	at hudson.model.Executor.run(Executor.java:442)
*/

        r.assertLogContains("Method too large: WorkflowScript.___cps___", b);
        r.assertLogContains("()Lcom/cloudbees/groovy/cps/impl/CpsFunction;", b);

        // Assert separately from (and after) log parsing, to facilitate test maintenance
        r.assertBuildStatus(Result.FAILURE, b);
    }
}
