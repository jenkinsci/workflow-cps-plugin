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
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import jenkins.model.Jenkins;
import org.apache.commons.io.Charsets;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionList;

/**
 * Shows thread dump for {@link CpsFlowExecution}.
 */
public final class CpsThreadDumpAction implements Action {

    private final CpsFlowExecution execution;

    CpsThreadDumpAction(CpsFlowExecution execution) {
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
