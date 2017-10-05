package org.jenkinsci.plugins.workflow.cps.steps;

import com.google.inject.Inject;
import groovy.lang.Script;
import hudson.FilePath;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.CpsStepContext;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.cps.replay.ReplayAction;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

/**
 * Loads another Groovy script file and executes it.
 *
 * @author Kohsuke Kawaguchi
 */
public class LoadStepExecution extends AbstractStepExecutionImpl {
    @StepContextParameter
    private transient FilePath cwd;

    @Inject(optional=true)
    private transient LoadStep step;

    @StepContextParameter
    private transient TaskListener listener;

    @Override
    public boolean start() throws Exception {
        CpsStepContext cps = (CpsStepContext) getContext();
        CpsThread t = CpsThread.current();

        CpsFlowExecution execution = t.getExecution();

        String text = cwd.child(step.getPath()).readToString();
        String clazz = execution.getNextScriptName(step.getPath());
        String newText = ReplayAction.replace(execution, clazz);
        if (newText != null) {
            listener.getLogger().println("Replacing Groovy text with edited version");
            text = newText;
        }

        Script script = execution.getShell().parse(text);

        // execute body as another thread that shares the same head as this thread
        // as the body can pause.
        cps.newBodyInvoker(t.getGroup().export(script))
                .withDisplayName(step.getPath())
                .withCallback(BodyExecutionCallback.wrap(cps))
                .start(); // when the body is done, the load step is done

        return false;
    }

    private static final long serialVersionUID = 1L;

}
