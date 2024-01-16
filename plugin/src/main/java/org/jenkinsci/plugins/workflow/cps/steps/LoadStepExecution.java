package org.jenkinsci.plugins.workflow.cps.steps;

import groovy.lang.Script;
import hudson.FilePath;
import hudson.model.TaskListener;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.jenkinsci.plugins.workflow.cps.CpsCompilationErrorsException;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.CpsStepContext;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.cps.replay.ReplayAction;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;

/**
 * Loads another Groovy script file and executes it.
 *
 * @author Kohsuke Kawaguchi
 */
public class LoadStepExecution extends AbstractStepExecutionImpl {

    private transient LoadStep step;

    LoadStepExecution(LoadStep step, StepContext context) {
        super(context);
        this.step = step;
    }

    @Override
    public boolean start() throws Exception {
        CpsStepContext cps = (CpsStepContext) getContext();
        FilePath cwd = cps.get(FilePath.class);
        TaskListener listener = cps.get(TaskListener.class);
        CpsThread t = CpsThread.current();

        CpsFlowExecution execution = t.getExecution();

        String text = cwd.child(step.getPath()).readToString();
        String clazz = execution.getNextScriptName(step.getPath());
        String newText = ReplayAction.replace(execution, clazz);
        if (newText != null) {
            listener.getLogger().println("Replacing Groovy text with edited version");
            text = newText;
        }

        Script script;
        try {
            script = execution.getShell().parse(text);
        } catch (MultipleCompilationErrorsException e) {
            // Convert to a serializable exception, see JENKINS-40109.
            throw new CpsCompilationErrorsException(e);
        }

        // execute body as another thread that shares the same head as this thread
        // as the body can pause.
        cps.newBodyInvoker(t.getGroup().export(script), true)
                .withDisplayName(step.getPath())
                .withCallback(BodyExecutionCallback.wrap(cps))
                .start(); // when the body is done, the load step is done

        return false;
    }

    @Override
    public void onResume() {
        // do nothing
    }

    private static final long serialVersionUID = 1L;

}
