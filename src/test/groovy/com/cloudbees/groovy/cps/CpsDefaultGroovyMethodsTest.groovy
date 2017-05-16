package com.cloudbees.groovy.cps

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized.class)
class CpsDefaultGroovyMethodsTest extends AbstractGroovyCpsTest {
    private String testName
    private String testCode
    private Object testResult

    CpsDefaultGroovyMethodsTest(String testName, String testCode, Object testResult) {
        this.testName = testName
        this.testCode = testCode
        this.testResult = testResult
    }

    @Parameterized.Parameters(name="Name: {0}")
    public static Iterable<Object[]> generateParameters() {
        // First element is the name of the test
        // Second element is the code to eval
        // Third element is expected result
        return [
            ["each", "def x = 100; (0..10).each { y -> x+=y }; return x", 155] as Object[],
            ["eachArray", "def x = 10;\n" +
                "[1, 2, 3].each { y -> x+=y; }\n" +
                "return x;", 16] as Object[],
            ["eachMapKV", "def x = 100\n" +
                "def m = [a: 1, b: 2, c: 3];\n" +
                "m.each { k, v -> x += v }\n" +
                "return x;", 106] as Object[],
            ["eachMapEntry", "def x = 100\n" +
                "def m = [a: 1, b: 2, c: 3];\n" +
                "m.each { e -> x += e.getValue() }\n" +
                "return x;", 106] as Object[],
            ["setEach", "def x = 100\n" +
                "([1,2,3] as HashSet).each { y -> x += y }\n" +
                "return x", 106] as Object[],
            ["sortedSetEach", "def x = 100\n" +
                "([1,2,3] as TreeSet).each { y -> x += y }\n" +
                "return x", 106] as Object[],
            ["collectList", "return [1, 2, 3].collect { it * 2 }", [2, 4, 6]] as Object[],
            ["collectListIntoExistingList", "def existing = [2]\n" +
                "return [2, 3, 4].collect(existing) { it * 2 }", [2, 4, 6, 8]] as Object[],
            ["collectListIntoExistingSet", "return [2, 3, 4].collect([2] as HashSet) { it * 2 }",
             [2, 4, 6, 8] as HashSet] as Object[],
            ["collectSet", "return ([1, 2, 3] as HashSet).collect { it * 2 }", [2, 4, 6]] as Object[],
            ["collectSetIntoExistingList", "def existing = [2]\n" +
                "return ([2, 3, 4] as HashSet).collect(existing) { it * 2 }", [2, 4, 6, 8]] as Object[],
            ["collectSetIntoExistingSet", "return ([2, 3, 4] as HashSet).collect([2] as HashSet) { it * 2 }",
             [2, 4, 6, 8] as HashSet] as Object[],
            ["collectMapKV", "def m = [a: 1, b: 2, c: 3];\n" +
                "return m.collect { k, v -> v }", [1, 2, 3]] as Object[],
            ["collectMapEntry", "def m = [a: 1, b: 2, c: 3];\n" +
                "return m.collect { e -> e.getValue() }", [1, 2, 3]] as Object[],
            ["collectMapKVIntoExistingList", "def m = [a: 2, b: 3, c: 4];\n" +
                "return m.collect([1]) { k, v -> v }", [1, 2, 3, 4]] as Object[],
            ["collectMapEntryIntoExistingList", "def m = [a: 2, b: 3, c: 4];\n" +
                "return m.collect([1]) { e -> e.getValue() }", [1, 2, 3, 4]] as Object[],
            ["collectMapKVIntoExistingSet", "def m = [a: 2, b: 3, c: 4];\n" +
                "return m.collect([1] as HashSet) { k, v -> v }", [1, 2, 3, 4] as HashSet] as Object[],
            ["collectMapEntryIntoExistingSet", "def m = [a: 2, b: 3, c: 4];\n" +
                "return m.collect([1] as HashSet) { e -> e.getValue() }", [1, 2, 3, 4] as HashSet] as Object[],
            ["collectEntriesKV", "def m = [a: 1, b: 2, c: 3]\n" +
                "return m.collectEntries { k, v -> [(k): v * 2] }", [a: 2, b: 4, c: 6]] as Object[],
            ["collectEntriesEntry", "def m = [a: 1, b: 2, c: 3]\n" +
                "return m.collectEntries { e -> [(e.getKey()): e.getValue() * 2] }", [a: 2, b: 4, c: 6]] as Object[],
            ["collectEntriesIntoExistingMapKV", "def m = [b: 2, c: 3]\n" +
                "return m.collectEntries([a: 2]) { k, v -> [(k): v * 2] }", [a: 2, b: 4, c: 6]] as Object[],
            ["collectEntriesIntoExistingMapEntry", "def m = [b: 2, c: 3]\n" +
                "return m.collectEntries([a: 2]) { e -> [(e.getKey()): e.getValue() * 2] }", [a: 2, b: 4, c: 6]] as Object[],
            ["any", "return [0, 1, 2].any { i -> i == 1 }", true] as Object[]
/*
            // Waiting for StringGroovyMethods to be added to transformer
            ["eachLine", 'def s = """a\n' +
                'b\n' +
                'c\n' +
                '"""\n' +
                'def list = []\n' +
                's.eachLine { l -> list.add(l) }\n' +
                'return l', ["a", "b", "c"]] as Object[]
*/
        ]

    }

    @Test
    void cps() {
        assert evalCPS(testCode) == testResult
    }

    @Test
    void sync() {
        assert evalCPS("""
@NonCPS
def someMethod() {
  ${testCode}
}
someMethod()
""") == testResult
    }

}
