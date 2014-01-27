package com.cloudbees.groovy.cps;

import org.junit.Test;

/**
 * @author Kohsuke Kawaguchi
 */
public class TestProgram {
    /**
     *
     */
    @Test
    public void test1() {
        Builder b = new Builder();

        /*
            sum = 0;
            for (x=0; x<10; x++) {
                sum += x;
            }
            println sum;
         */
        b.sequence(
            b.setLocalVariable("sum", b.constant(0)),
            b._for( b.setLocalVariable("x",b.constant(1)), null, )

    }
}
