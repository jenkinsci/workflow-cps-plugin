package com.cloudbees.groovy.cps.impl

import com.cloudbees.groovy.cps.AbstractGroovyCpsTest
import org.junit.Test

/**
 * Tests
 *
 * - exception in switch exp / case exp
 * - testing case statement that has isCase method (like regexp)
 * - break from within the switch statement
 * - two case matching
 * - no case matching with default
 * - no case matching without default
 * - fall through
 *
 * @author Kohsuke Kawaguchi
 */
class SwitchBlockTest extends AbstractGroovyCpsTest {
    @Test
    void basic() {
        assert evalCPS("""
            def x = 2;
            def y;
            switch (x) {
            case 1:
                y = "one";
                break;
            case 2:
                y = "two";
                break;
            case 3:
                y = "three";
                break;
            }
            return y;
        """)=="two";
    }

    /**
     * Null in the switch expression.
     */
    @Test
    void nullSwitchExp() {
        assert evalCPS("""
            def x = null;
            def y = 'zero';
            switch (x) {
            case 1:
                y = "one";
                break;
            case 2:
                y = "two";
                break;
            case 3:
                y = "three";
                break;
            }
            return y;
        """)=="zero";
    }

    /**
     * Null in the case expression.
     */
    @Test
    void nullInCaseExp() {
        assert evalCPS("""
            def x = null;
            def y = 'zero';
            switch (x) {
            case 1:
                y = "one";
                break;
            case null:
                y = "null!";
                break;
            case 3:
                y = "three";
                break;
            }
            return y;
        """)=="null!";
    }
}
