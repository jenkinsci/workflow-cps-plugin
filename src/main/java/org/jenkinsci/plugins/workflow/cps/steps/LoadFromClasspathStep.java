package org.jenkinsci.plugins.workflow.cps.steps;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.jenkinsci.plugins.workflow.cps.CpsStepContext;
import org.jenkinsci.plugins.workflow.cps.CpsThreadGroup;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import hudson.model.TaskListener;

/**
 * Load the contents of some classpath resource and attempt to evaluate them as a Groovy Pipeline script in the current pipeline execution.
 * 
 * @author Austin Witt
 * 
 * @since 2.74
 *
 */
public class LoadFromClasspathStep extends Step {

	/**
	 * The classpath of a Groovy Pipeline script
	 */
	protected String path;

	/**
	 * An object to use to get the classloader to use.
	 * <br><br>
	 * Since this will be targeting a Groovy script file (not bytecode) resource on the classpath,
	 * a GroovyClassLoader must be used to look for the class to ensure that the Groovy script text is located and compiled.
	 * <br><br>
	 * Using a regular Java ClassLoader will only look for bytecode. 
	 * This may fail - but if it does succeed, the resulting script's source code will not be able to be CPS-transformed
	 * (since the source code was not loaded) and so it won't be able to be used by pipeline scripts without breaking the whole pipeline execution.
	 * <br><br>
	 * An easy way to find a GroovyClassLoader is to provide the current Groovy script (<code>this</code>) as the object.
	 */
	protected Object classloaderSource;
	
	/**
	 * Whether properties from the loaded script should be unexported when serializing the pipeline.
	 * Setting this to <code>true</code> may affect property availability after pipeline restarts.
	 * Setting this to <code>false</code> may make some pipelines non-resumable.
	 * 
	 * @see CpsStepContext#newBodyInvoker(org.jenkinsci.plugins.workflow.cps.BodyReference, boolean)
	 * @see CpsThreadGroup#unexport(org.jenkinsci.plugins.workflow.cps.BodyReference)
	 */
	protected boolean unexport = DescriptorImpl.DEFAULT_UNEXPORT;

	@DataBoundConstructor
	public LoadFromClasspathStep(String path) { this.path = path; }

	public String getPath() { return path; }

	public Object getClassloaderSource() { return classloaderSource; }

	@DataBoundSetter
	public void setClassloaderSource(Object classloaderSource) { this.classloaderSource = classloaderSource; }
	
	public boolean isUnexport() { return unexport; }
	
	@DataBoundSetter
	public void setUnexport( boolean unexport ) { this.unexport = unexport; }

	@Override
	public StepExecution start(StepContext _context) throws Exception {
		return new LoadFromClasspathStepExecution( _context, path, classloaderSource, unexport );
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {
		
		public static final boolean DEFAULT_UNEXPORT = false;

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return new HashSet<>(Arrays.asList(
				TaskListener.class
			));
		}

		@Override
		public String getFunctionName() {
			return "loadFromClasspath";
		}

		@Override
		public String getDisplayName() {
			return "Evaluate the contents of a classpath resource as a Groovy Pipeline script";
		}

		@Override
		public boolean isAdvanced() {
			return true;
		}
	}

}