package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import hudson.Extension;
import hudson.model.Action;
import hudson.security.Permission;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import jenkins.model.Jenkins;
import jenkins.model.TransientActionFactory;
import org.apache.commons.io.Charsets;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionList;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;

/**
 * Shows thread dump for {@link CpsFlowExecution}.
 */
public final class CpsThreadDumpAction implements Action {

    private final CpsFlowExecution execution;

    private CpsThreadDumpAction(CpsFlowExecution execution) {
        this.execution = execution;
    }

    @Override
    public String getIconFileName() {
        return "gear.png";
    }

    @Override
    public String getDisplayName() {
        return "Thread Dump";
    }

    @Override
    public String getUrlName() {
        return "threadDump";
    }
    
    public String getParentUrl() throws IOException {
        return execution.getOwner().getUrl();
    }

    /* for tests */ CpsThreadDump threadDumpSynchronous() throws InterruptedException, ExecutionException {
        execution.waitForSuspension();
        return execution.getThreadDump();
    }

    public String getThreadDump() {
        return execution.getThreadDump().toString();
    }

    @Extension public static class Factory extends TransientActionFactory<FlowExecutionOwner.Executable> {

        @Override public Class<FlowExecutionOwner.Executable> type() {
            return FlowExecutionOwner.Executable.class;
        }

        @Override public Collection<? extends Action> createFor(FlowExecutionOwner.Executable executable) {
            FlowExecutionOwner owner = executable.asFlowExecutionOwner();
            if (owner != null) {
                FlowExecution exec = owner.getOrNull();
                if (exec instanceof CpsFlowExecution && !exec.isComplete()) {
                    return Collections.singleton(new CpsThreadDumpAction((CpsFlowExecution) exec));
                }
            }
            return Collections.emptySet();
        }

    }

    @Extension(optional=true) public static class PipelineThreadDump extends Component {

        @Override public Set<Permission> getRequiredPermissions() {
            return Collections.singleton(Jenkins.ADMINISTER);
        }

        @Override public String getDisplayName() {
            return "Thread dumps of running Pipeline builds";
        }

        @Override public void addContents(Container container) {
            container.add(new Content("nodes/master/pipeline-thread-dump.txt") {
                @Override public void writeTo(OutputStream outputStream) throws IOException {
                    PrintWriter pw = new PrintWriter(new OutputStreamWriter(outputStream, Charsets.UTF_8));
                    for (FlowExecution flow : FlowExecutionList.get()) {
                        if (flow instanceof CpsFlowExecution) {
                            pw.println("Build: " + flow.getOwner().getExecutable());
                            ((CpsFlowExecution) flow).getThreadDump().print(pw);
                            pw.println();
                        }
                    }
                    pw.flush();
                }
            });
        }

    }

}
