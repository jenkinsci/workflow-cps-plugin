package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import com.google.common.util.concurrent.FutureCallback;
import hudson.Extension;
import hudson.Functions;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.security.Permission;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionList;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.WebMethod;

/**
 * Shows thread dump for {@link CpsFlowExecution}.
 */
public final class CpsThreadDumpAction extends RunningFlowAction {

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

    @WebMethod(name = "program.xml")
    public void doProgramDotXml(StaplerRequest2 req, StaplerResponse2 rsp) throws Exception {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        CompletableFuture<String> f = new CompletableFuture<>();
        execution.runInCpsVmThread(new FutureCallback<>() {
            @Override
            public void onSuccess(CpsThreadGroup g) {
                try {
                    f.complete(g.asXml());
                } catch (Throwable t) {
                    f.completeExceptionally(t);
                }
            }

            @Override
            public void onFailure(Throwable t) {
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

    @Extension(optional = true)
    public static class PipelineThreadDump extends Component {

        @Override
        public Set<Permission> getRequiredPermissions() {
            return Set.of(Jenkins.ADMINISTER);
        }

        @Override
        public String getDisplayName() {
            return "Running Pipeline builds";
        }

        @Override
        public ComponentCategory getCategory() {
            return ComponentCategory.BUILDS;
        }

        @Override
        public void addContents(Container container) {
            container.add(new Content("nodes/master/pipeline-running-builds.txt") {
                @Override
                public void writeTo(OutputStream outputStream) {
                    PrintWriter pw = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
                    for (FlowExecution flow : FlowExecutionList.get()) {
                        if (flow instanceof CpsFlowExecution) {
                            Queue.Executable ownerExec;
                            try {
                                ownerExec = flow.getOwner().getExecutable();
                            } catch (IOException e) {
                                pw.println("No data available for " + flow);
                                Functions.printStackTrace(e, pw);
                                pw.println();
                                continue;
                            }
                            pw.println("Build: " + ownerExec);
                            if (ownerExec instanceof Run<?, ?>) {
                                var run = (Run<?, ?>) ownerExec;
                                var started = Instant.ofEpochMilli(run.getStartTimeInMillis());
                                pw.println("Started: " + started);
                                var duration = Duration.between(started, Instant.now());
                                pw.print("Duration: " + duration);
                                if (duration.toDays() > 3) {
                                    pw.println(" (Running for more than 3 days!)");
                                } else {
                                    pw.println();
                                }
                            }
                            var cpsFlow = (CpsFlowExecution) flow;
                            Map<String, LongAdder> sortedTimings = new TreeMap<>(cpsFlow.liveTimings);
                            pw.println("Timings:");
                            sortedTimings.forEach(
                                    (k, v) -> pw.println("  " + k + "\t" + v.longValue() / 1000 / 1000 + "ms"));
                            pw.println("Active operations:");
                            long nanos = System.nanoTime();
                            Map<String, Optional<CountAndDuration>> sortedIncompleteTimings = new HashSet<>(
                                            cpsFlow.liveIncompleteTimings)
                                    .stream()
                                            .collect(Collectors.groupingBy(
                                                    t -> t.getKind().name(),
                                                    TreeMap::new,
                                                    Collectors.mapping(
                                                            t -> new CountAndDuration(nanos - t.getStartNanos()),
                                                            Collectors.reducing(CountAndDuration::new))));
                            sortedIncompleteTimings.forEach((k, optional) -> optional.ifPresent(cd ->
                                    pw.println("  " + k + "\t" + cd.count + "\t" + cd.duration / 1000 / 1000 + "ms")));
                            pw.println("Approximate graph size: " + cpsFlow.approximateNodeCount());
                            cpsFlow.getThreadDump().print(pw);
                            pw.println();
                        }
                    }
                    pw.flush();
                }
            });
        }

        private static class CountAndDuration {
            private final int count;
            private final long duration;

            CountAndDuration(long duration) {
                this.count = 1;
                this.duration = duration;
            }

            CountAndDuration(CountAndDuration a, CountAndDuration b) {
                this.count = a.count + b.count;
                this.duration = a.duration + b.duration;
            }
        }
    }
}
