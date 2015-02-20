package com.cloudbees.groovy.cps;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.InvocationTargetException;

import org.junit.Test;

import com.cloudbees.groovy.cps.impl.FunctionCallEnv;

/**
 * Tests variable declaration using the groovy-cps Builder. 
 */
public class TestVariableDeclaration {

	Builder b = new Builder(MethodLocation.UNKNOWN);
	
	Block $x = b.localVariable("x");
	
	@SuppressWarnings("unchecked")
	private <T> T createVariable(Class<T> clazz) {
		try {
			FunctionCallEnv env = new FunctionCallEnv(null, Continuation.HALT, null, null);
			Next p = new Next(b.block(
						b.declareVariable(clazz, "x"),
						b.return_($x)
					), env, Continuation.HALT);
			return (T) p.run().yield.wrapReplay();
		} catch (InvocationTargetException x) {
            throw new AssertionError(x);
        }
	}
	
	/**
	 * Tests the default value of variables (e.g. 0 for int, 0L for long, etc).
	 */
	@Test
	public void testDefaultValues() {
		int iv = createVariable(int.class); 
		assertEquals(0, iv);
		
		long lv = createVariable(long.class);
		assertEquals(0L, lv);
		
		Object ov = createVariable(Object.class);
		assertEquals(null, ov);
		
		float fv = createVariable(float.class); 
		assertEquals(0.0f, fv, 0.0);
		
		double dv = createVariable(double.class);
		assertEquals(0.0d, dv, 0.0);
	}
	
}
