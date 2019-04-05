package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import com.google.common.util.concurrent.FutureCallback;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Functions;
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
import org.apache.commons.io.Charsets;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionList;
import org.jenkinsci.plugins.workflow.pickles.Pickle;
import org.jenkinsci.plugins.workflow.support.pickles.SingleTypedPickleFactory;
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

    @WebMethod(name = "program.xml") public void doProgramDotXml(StaplerRequest req, StaplerResponse rsp) throws Exception {
        Jenkins.get().checkPermission(Jenkins.RUN_SCRIPTS);
        XStream xs = new XStream();
        // Could not handle a general PickleFactory without doing something weird with XStream
        // and there is no apparent way to make a high-priority generic Convertor delegate to others.
        // Anyway the only known exceptions are ThrowablePickle, which we are unlikely to need,
        // and RealtimeJUnitStep.Pickler which could probably be replaced by a DescribablePickleFactory
        // (and anyway these Describable objects would be serialized fine by XStream, just not JBoss Marshalling).
        for (SingleTypedPickleFactory<?> stpf : ExtensionList.lookup(SingleTypedPickleFactory.class)) {
            Class<?> factoryType = Functions.getTypeParameter(stpf.getClass(), SingleTypedPickleFactory.class, 0);
            xs.registerConverter(new Converter() {
                @Override public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
                    Pickle p = stpf.writeReplace(source);
                    assert p != null : "failed to pickle " + source + " using " + stpf;
                    context.convertAnother(p);
                }
                @Override public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
                    throw new UnsupportedOperationException(); // unused
                }
                @SuppressWarnings("rawtypes")
                @Override public boolean canConvert(Class type) {
                    return factoryType.isAssignableFrom(type);
                }
            });
        }
        // Could also register a convertor for FlowExecutionOwner, though it seems harmless.
        CompletableFuture<String> f = new CompletableFuture<>();
        execution.runInCpsVmThread(new FutureCallback<CpsThreadGroup>() {
            @Override public void onSuccess(CpsThreadGroup g) {
                try {
                    f.complete(xs.toXML(g));
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
