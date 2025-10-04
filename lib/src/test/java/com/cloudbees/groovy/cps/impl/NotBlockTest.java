package com.cloudbees.groovy.cps.impl;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.cloudbees.groovy.cps.AbstractGroovyCpsTest;
import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.ObjectInputStreamWithLoader;
import com.cloudbees.groovy.cps.Outcome;
import groovy.lang.Script;
import java.io.InputStream;
import java.io.ObjectInputStream;
import org.junit.Test;

public class NotBlockTest extends AbstractGroovyCpsTest {

    @Test
    public void serialFormBackwardsCompatibility() throws Throwable {
        // notBlock.dat was created before NotBlock$ContinuationImpl was created.
        /*
        Script s = getCsh().parse("!Continuable.suspend('suspended')");
        Continuable c = new Continuable(s);
        assertThat(c.run0(new Outcome(null, null), Collections.<Class>emptyList()).replay(), equalTo((Object)"suspended"));
        FileOutputStream baos = new FileOutputStream("notBlock.dat");
        new ObjectOutputStream(baos).writeObject(c);
        */
        // We need to define a script class in the current JVM
        // with a serialVersionUID that matches the serialized class.
        Script s = getCsh().parse(
                        """
                                  class Script1 extends SerializableScript {
                                    private static final long serialVersionUID = -2376309021360195963
                                    public Object run() { throw new RuntimeException('unused') }
                                  }
                                  """);
        Continuable c;
        try (InputStream is = NotBlockTest.class.getResourceAsStream("notBlock.dat");
                ObjectInputStream ois = new ObjectInputStreamWithLoader(is, getCsh().getClassLoader())) {
            c = (Continuable) ois.readObject();
        }
        assertTrue(c.isResumable());
        assertThat(c.run0(new Outcome(false, null)).replay(), equalTo(true));
    }
}
