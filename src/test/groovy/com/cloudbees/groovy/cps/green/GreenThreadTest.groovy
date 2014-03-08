package com.cloudbees.groovy.cps.green

import com.cloudbees.groovy.cps.AbstractGroovyCpsTest
import org.junit.Ignore
import org.junit.Test

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class GreenThreadTest extends AbstractGroovyCpsTest {
    @Test @Ignore/*TODO: still a work in progress*/
    void thread() {
        assert evalCPS("""
            GreenWorld.startThread {
                int x=0;
                for (int i=0; i<100; i++)
                    x+=i;
                return x;
            }
        """)==[foo:"hello",bar:6,zot:null];
    }
}
