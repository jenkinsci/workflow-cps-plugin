package com.cloudbees.groovy.cps.impl

import com.cloudbees.groovy.cps.AbstractGroovyCpsTest
import org.junit.Ignore
import org.junit.Test

import javax.naming.NamingException

/**
 * Tests for switch/case
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

    /**
     * Two matching case statements.
     */
    @Test
    void twoMatchingCases() {
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
            case 2:
                y = "TWO";
                break;
            case 3:
                y = "three";
                break;
            }
            return y;
        """)=="two";
    }

    /**
     * Matches to the default clause
     */
    @Test
    void defaultClause() {
        assert evalCPS("""
            def x = 5;
            def y;
            switch (x) {
            case 1:
                y = "one";
                break;
            default:
                y = "other";
                break;
            case 2:
                y = "two";
                break;
            case 3:
                y = "three";
                break;
            }
            return y;
        """)=="other";
    }

    /**
     * Matches to nothing
     */
    @Test
    void noMatch() {
        assert evalCPS("""
            def x = 5;
            def y = "initial";
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
        """)=="initial";
    }

    /**
     * Case match and fall through the rest.
     */
    @Test
    void fallthrough() {
        assert evalCPS("""
            def x = 1;
            def y = "";
            switch (x) {
            case 1:
                y += "one";
                // fall through
            case 2:
                y += "two";
                // fall through
            case 3:
                y += "three";
                // fall through
            }
            return y;
        """)=="onetwothree";
    }

    /**
     * Default match and fall through
     */
    @Test
    @Ignore("Groovy doesn't handle this correctly")
    void fallthroughWithDefault() {
        assert evalCPS("""
            def x = 9;
            def y = "";
            switch (x) {
            default:
                y += "other";
                // fall through
            case 1:
                y += "one";
                // fall through
            case 2:
                y += "two";
                // fall through
            case 3:
                y += "three";
                // fall through
            }
            return y;
        """)=="otheronetwothree";
    }
}
