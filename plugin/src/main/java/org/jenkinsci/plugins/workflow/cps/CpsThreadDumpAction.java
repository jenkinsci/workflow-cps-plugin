package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import com.google.common.util.concurrent.FutureCallback;
import hudson.Extension;
import hudson.model.Action;
import hudson.security.Permission;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import java.nio.charset.StandardCharsets;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionList;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.WebMethod;

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
        return "symbol-analytics";
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

    public CpsThreadDump getThreadDump() {
        return execution.getThreadDump();
    }

    @WebMethod(name = "program.xml") public void doProgramDotXml(StaplerRequest req, StaplerResponse rsp) throws Exception {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        CompletableFuture<String> f = new CompletableFuture<>();
        execution.runInCpsVmThread(new FutureCallback<>() {
            @Override public void onSuccess(CpsThreadGroup g) {
                try {
                    f.complete(g.asXml());
                } catch (Throwable t) {
                    f.completeExceptionally(t);
                }
            }
            @Override public void onFailure(Throwable t) {
                f.completeExceptionally(t);
            }
        });
        String xml;
        try {
            xml = f.get(1, TimeUnit.MINUTES);
        } catch (Exception x) {
            HttpResponses.error(x).generateResponse(req, rsp, this);
            return;
        }
        rsp.setContentType("text/xml;charset=UTF-8");
        PrintWriter pw = rsp.getWriter();
        pw.print(xml);
        pw.flush();
    }

    @Extension(optional=true) public static class PipelineThreadDump extends Component {

        @Override public Set<Permission> getRequiredPermissions() {
            return Set.of(Jenkins.ADMINISTER);
        }

        @Override public String getDisplayName() {
            return "Thread dumps of running Pipeline builds";
        }

        @Override public void addContents(Container container) {
            container.add(new Content("nodes/master/pipeline-thread-dump.txt") {
                @Override public void writeTo(OutputStream outputStream) throws IOException {
                    PrintWriter pw = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
                    for (FlowExecution flow : FlowExecutionList.get()) {
                        if (flow instanceof CpsFlowExecution) {
                            pw.println("Build: " + flow.getOwner().getExecutable());
                            ((CpsFlowExecution) flow).getThreadDump().print(pw);
                            pw.println("Approximate graph size: " + ((CpsFlowExecution) flow).approximateNodeCount());
                            pw.println();
                        }
                    }
                    pw.flush();
                }
            });
        }

    }

}
