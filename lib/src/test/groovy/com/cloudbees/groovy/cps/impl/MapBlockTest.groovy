package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.AbstractGroovyCpsTest;
import org.junit.Test;

/**
 * @author Kohsuke Kawaguchi
 */
public class MapBlockTest extends AbstractGroovyCpsTest {
    @Test
    void mapLiteral() {
        assert evalCPS("""
            def x=[foo:"hello", bar:2+2+2, zot:null];
            return x;
        """)==[foo:"hello",bar:6,zot:null];
    }

    @Test
    void empty() {
        assert evalCPS("""
            return [:]
        """)==[:]
    }
}
