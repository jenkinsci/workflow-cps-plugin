package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.AbstractGroovyCpsTest;
import java.util.Collections;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.Test;

/**
 * @author Kohsuke Kawaguchi
 */
public class MapBlockTest extends AbstractGroovyCpsTest {
    @Test
    public void mapLiteral() throws Throwable {

        assertEvaluate(
                InvokerHelper.createMap(new Object[] {"foo", "hello", "bar", 6, "zot", null}),
                "def x=[foo:'hello', bar:2+2+2, zot:null]\n" + "return x\n");
    }

    @Test
    public void empty() throws Throwable {
        assertEvaluate(Collections.emptyMap(), "return [:]");
    }
}
