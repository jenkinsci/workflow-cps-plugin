package com.cloudbees.groovy.cps

import com.cloudbees.groovy.cps.impl.CpsFunction
import org.junit.Test

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class ContinuableTest extends AbstractGroovyCpsTest {
    @Test
    void resumeAndSuspend() {
        Script s = csh.parse("""
            int x = 1;
            x = Continuable.suspend(x)
            return x;
        """)
        CpsFunction f = s.run();
        new Continuable(f.invoke(null,s,[],Continuation.HALT))

        def baos = new ByteArrayOutputStream()
        new ObjectOutputStream(baos).writeObject();

        Continuable cx = new ObjectInputStreamWithLoader(new ByteArrayInputStream(baos.toByteArray()),s.class.classLoader).readObject()
        assert 10==cx.run(null)
    }
}
