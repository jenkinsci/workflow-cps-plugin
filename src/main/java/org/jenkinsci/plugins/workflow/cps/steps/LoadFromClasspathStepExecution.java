package org.jenkinsci.plugins.workflow.cps.steps;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

import org.apache.tools.ant.taskdefs.Classloader;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.jenkinsci.plugins.workflow.cps.CpsCompilationErrorsException;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.CpsStepContext;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.cps.replay.ReplayAction;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import groovy.lang.Script;
import hudson.model.TaskListener;

/**
 * Execution implementation for {@link LoadFromClasspathStep}.
 *
 * @author Austin Witt
 * 
 * @since 2.74
 *
 */
public class LoadFromClasspathStepExecution extends StepExecution {
	
	private static final long serialVersionUID = 1L;

	protected String path;

	protected Object classloaderSource;
	
	protected boolean unexport;
	
	protected transient TaskListener listener;

	public LoadFromClasspathStepExecution(
		StepContext _context, 
		String _path, 
		Object _classloaderSource,
		boolean _unexport)
			throws
				IOException,
				InterruptedException
	{
		super( _context );

		path = _path;
		classloaderSource = _classloaderSource;
		listener = _context.get( TaskListener.class );
	}

	@Override
	public boolean start() throws Exception {
		
		CpsStepContext cps = (CpsStepContext) getContext();
		CpsThread t = CpsThread.current();
		CpsFlowExecution execution = t.getExecution();

		String text = get_classpath_resource_contents( classloaderSource != null ? classloaderSource : this, path );
		String clazz = execution.getNextScriptName( path );
		String newText = ReplayAction.replace( execution, clazz );
		if( newText != null ) {
			listener.getLogger().println( "Replacing Groovy text with edited version" );
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
		cps.newBodyInvoker(t.getGroup().export( script ), unexport )
			.withDisplayName( path )
			.withCallback(BodyExecutionCallback.wrap( cps ) )
			.start(); // when the body is done, the load step is done

		return false;
	}

	@Override
	public void stop(Throwable cause) throws Exception {
		// noop
		//
		// the head of the CPS thread that's executing the body should stop and that's all we need to do.
	}

	/**
	 * Load the contents of a classpath resource as a String.
	 *
	 * @param _use_my_classloader An object to use to get a {@link Classloader} (via {@link Class#getClassLoader()}) to look for the _resource
	 * @param _resource The path to the classpath resource to read as a String.
	 *
	 * @return The contents of the classpath _resource
	 *
	 * @throws FileNotFoundException If the {@link Classloader} of _use_my_classloader cannot locate the _resource.
	 */
	protected String get_classpath_resource_contents(Object _use_my_classloader, String _resource) throws FileNotFoundException {

		if( null == _resource || _resource.trim().equalsIgnoreCase( "" ) ) {
			throw new IllegalStateException( "You must provide a _resource to load." );
		}

		ClassLoader classloader = _use_my_classloader.getClass().getClassLoader();

		if( null == classloader ) {
			classloader = getClass().getClassLoader();
		}

		if( null == classloader ) {
			throw new IllegalStateException( "Unable to find a java.lang.Classloader to use to look for [" + _resource + "]." );
		}

		InputStream resource_stream = classloader.getResourceAsStream( _resource );

		if( null == resource_stream ) {
			throw new FileNotFoundException( "Unable to locate classpath resource [" + _resource + "]." );
		}
		try( Scanner s = new Scanner( resource_stream, "UTF-8") ) {
			return s.useDelimiter("\\A").next();
		}
	}

}
