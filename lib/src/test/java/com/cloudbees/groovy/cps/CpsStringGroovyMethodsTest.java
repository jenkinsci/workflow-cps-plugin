package com.cloudbees.groovy.cps;

import java.util.List;
import org.junit.Ignore;
import org.junit.Test;

public class CpsStringGroovyMethodsTest extends AbstractGroovyCpsTest {
    @Test
    public void eachMatch() throws Throwable {
        assertEvaluate(
                2,
                "int matchCount = 0\n" + "'foobarfoooobar'.eachMatch(~/foo/) { matchCount++ }\n"
                        + "return matchCount\n");

        assertEvaluate(
                2,
                "int matchCount = 0\n" + "'foobarfoooobar'.eachMatch('foo') { matchCount++ }\n"
                        + "return matchCount\n");
    }

    @Test
    public void find() throws Throwable {
        assertEvaluate("boo", "return 'foobar'.find('oob') { it.reverse() }");
        assertEvaluate("boo", "return 'foobar'.find(~/oob/) { it.reverse() }");
    }

    @Test
    public void findAll() throws Throwable {
        assertEvaluate(List.of("oof", "oof", "oof"), "'foobarfoobarfoo'.findAll('foo') { it.reverse() }");
        assertEvaluate(List.of("oof", "oof", "oof"), "'foobarfoobarfoo'.findAll(~/foo/) { it.reverse() }");
    }

    @Test
    public void replaceAll() throws Throwable {
        assertEvaluate("oofbaroofbaroof", "'foobarfoobarfoo'.replaceAll('foo') { it.reverse() }");
        assertEvaluate("oofbaroofbaroof", "'foobarfoobarfoo'.replaceAll(~/foo/) { it.reverse() }");
    }

    @Test
    public void replaceFirst() throws Throwable {
        assertEvaluate("oofbarfoobarfoo", "'foobarfoobarfoo'.replaceFirst('foo') { it.reverse() }");
        assertEvaluate("oofbarfoobarfoo", "'foobarfoobarfoo'.replaceFirst(~/foo/) { it.reverse() }");
    }

    @Ignore("Waiting for StringGroovyMethods.LineIterable translation")
    @Test
    public void splitEachLine() throws Throwable {
        assertEvaluate(
                "bob",
                "return '''\n" + "   abc|def\n"
                        + "   ghi|jkl\n"
                        + "   mno|pqr\n"
                        + "'''.splitEachLine('|') { it.reverse() }\n");
    }

    @Test
    public void takeWhile() throws Throwable {
        assertEvaluate("Groo", "'Groovy'.takeWhile{ it != 'v' }");
        assertEvaluate("Groo", "def ovyStr = 'ovy'; /Gro${ovyStr}/.takeWhile{ it != 'v' }");
    }
}
