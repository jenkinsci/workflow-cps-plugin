package com.cloudbees.groovy.cps.impl

import com.cloudbees.groovy.cps.AbstractGroovyCpsTest
import org.junit.Test

import javax.naming.NamingException

/**
 * Tests
 *
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

    /**
     * Exception in the switch expression.
     */
    @Test
    void exceptionInSwitchExp() {
        assert evalCPS("""
            @WorkflowMethod
            def foo() {
                throw new javax.naming.NamingException();
            }

            try {
                switch (foo()) {
                case 1:
                    y = "one";
                    break;
                case 2:
                    y = "two!";
                    break;
                }
                return null;
            } catch (e) {
                return e.class;
            }
        """)==NamingException.class;
    }

    /**
     * Exception in the case expression.
     */
    @Test
    void exceptionInCaseExp() {
        assert evalCPS("""
            @WorkflowMethod
            def foo() {
                throw new javax.naming.NamingException();
            }

            try {
                switch (5) {
                case 1:
                    y = "one";
                    break;
                case foo():
                    y = "two";
                    break;
                case 3:
                    y = "three";
                    break;
                }
                return null;
            } catch (e) {
                return e.class;
            }
        """)==NamingException.class;
    }

    @Test
    void isCase() {
        assert evalCPS("""
            def x = 5;
            switch (x) {
            case 1:
                y = "one";
                break;
            case [2,4,6,8]:
                y = "even";
                break;
            case [3,5,7,9]:
                y = "odd";
                break;
            }
            return y;
        """)=="odd";
    }
}
