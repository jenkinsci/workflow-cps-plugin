package com.cloudbees.groovy.cps;

import com.cloudbees.groovy.cps.impl.Constant;

/**
 * @author Kohsuke Kawaguchi
 */
public class TestProgram {
    public final Expression onePlusTwo = new Constant(1).eval();
}
