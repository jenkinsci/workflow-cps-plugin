package com.cloudbees.groovy.cps;

import java.math.BigDecimal;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;


@RunWith(Parameterized.class)
public class CpsDefaultGroovyMethodsTest extends AbstractGroovyCpsTest {
    private String testName;
    private String testCode;
    private Object testResult;

    public CpsDefaultGroovyMethodsTest(String testName, String testCode, Object testResult) {
        this.testName = testName;
        this.testCode = testCode;
        this.testResult = testResult;
    }

    @Parameterized.Parameters(name="{0}")
    public static Iterable<Object[]> generateParameters() {
        // First element is the name of the test
        // Second element is the code to eval
        // Third element is expected result
        List<List<Object>> rawTests = asList(
            // .any
            asList("any", "return [0, 1, 2].any { i -> i == 1 }", true),
            asList("anyMapKV", "return [a: 0, b: 1, c: 2].any { k, v -> v == 1 }", true),
            asList("anyMapEntry", "return [a: 0, b: 1, c: 2].any { e -> e.value == 1 }", true),
            asList("anyFalse", "return [0, 1, 2].any { i -> i > 2 }", false),

            // TODO: asType?

            // .collect
            asList("collectList", "return [1, 2, 3].collect { it * 2 }", asList(2, 4, 6)),
            asList("collectListIntoExistingList", "def existing = [2]\n" +
                "return [2, 3, 4].collect(existing) { it * 2 }", asList(2, 4, 6, 8)),
            asList("collectListIntoExistingSet", "return [2, 3, 4].collect([2] as HashSet) { it * 2 }",
              set(2, 4, 6, 8)),
            asList("collectSet", "return ([1, 2, 3] as HashSet).collect { it * 2 }", asList(2, 4, 6)),
            asList("collectSetIntoExistingList", "def existing = [2]\n" +
                "return ([2, 3, 4] as HashSet).collect(existing) { it * 2 }", asList(2, 4, 6, 8)),
            asList("collectSetIntoExistingSet", "return ([2, 3, 4] as HashSet).collect([2] as HashSet) { it * 2 }",
              set(2, 4, 6, 8)),
            asList("collectMapKV", "def m = [a: 1, b: 2, c: 3];\n" +
                "return m.collect { k, v -> v }", asList(1, 2, 3)),
            asList("collectMapEntry", "def m = [a: 1, b: 2, c: 3];\n" +
                "return m.collect { e -> e.value }", asList(1, 2, 3)),
            asList("collectMapKVIntoExistingList", "def m = [a: 2, b: 3, c: 4];\n" +
                "return m.collect([1]) { k, v -> v }", asList(1, 2, 3, 4)),
            asList("collectMapEntryIntoExistingList", "def m = [a: 2, b: 3, c: 4];\n" +
                "return m.collect([1]) { e -> e.value }", asList(1, 2, 3, 4)),
            asList("collectMapKVIntoExistingSet", "def m = [a: 2, b: 3, c: 4];\n" +
                "return m.collect([1] as HashSet) { k, v -> v }", set(1, 2, 3, 4)),
            asList("collectMapEntryIntoExistingSet", "def m = [a: 2, b: 3, c: 4];\n" +
                "return m.collect([1] as HashSet) { e -> e.value }", set(1, 2, 3, 4)),

            // .collectEntries
            asList("collectEntriesKV", "def m = [a: 1, b: 2, c: 3]\n" +
                "return m.collectEntries { k, v -> [(k): v * 2] }", map("a", 2, "b", 4, "c", 6)),
            asList("collectEntriesEntry", "def m = [a: 1, b: 2, c: 3]\n" +
                "return m.collectEntries { e -> [(e.key): e.value * 2] }", map("a", 2, "b", 4, "c", 6)),
            asList("collectEntriesIntoExistingMapKV", "def m = [b: 2, c: 3]\n" +
                "return m.collectEntries([a: 2]) { k, v -> [(k): v * 2] }", map("a", 2, "b", 4, "c", 6)),
            asList("collectEntriesIntoExistingMapEntry", "def m = [b: 2, c: 3]\n" +
                "return m.collectEntries([a: 2]) { e -> [(e.key): e.value * 2] }", map("a", 2, "b", 4, "c", 6)),
            asList("collectEntriesArray", "return ([1, 2, 3] as Integer[]).collectEntries { i -> [(i): i * 2] }", map(1, 2, 2, 4, 3, 6)),
            asList("collectEntriesArrayExistingMap", "return ([2, 3] as Integer[]).collectEntries([1: 2]) { i -> [(i): i * 2] }", map(1, 2, 2, 4, 3, 6)),
            asList("collectEntriesList", "return [1, 2, 3].collectEntries { i -> [(i): i * 2] }", map(1, 2, 2, 4, 3, 6)),
            asList("collectEntriesListExistingMap", "return [2, 3].collectEntries([1: 2]) { i -> [(i): i * 2] }", map(1, 2, 2, 4, 3, 6)),

            // .collectMany
            asList("collectMany", "(0..5).collectMany { [it, 2*it ]}", asList(0,0,1,2,2,4,3,6,4,8,5,10)),
            asList("collectManyExistingList", "(1..5).collectMany([0,0]) { [it, 2*it ]}", asList(0,0,1,2,2,4,3,6,4,8,5,10)),
            asList("collectManyArray", "([0, 1, 2, 3, 4, 5] as Integer[]).collectMany { [it, 2*it ]}", asList(0,0,1,2,2,4,3,6,4,8,5,10)),
            asList("collectManyMapKV", "[a:0,b:1,c:2].collectMany { k,v -> [v, 2*v ]}", asList(0,0,1,2,2,4)),
            asList("collectManyMapKVExistingList", "[b:1,c:2].collectMany([0,0]) { k,v -> [v, 2*v ]}", asList(0,0,1,2,2,4)),
            asList("collectManyMapEntry", "[a:0,b:1,c:2].collectMany { e -> [e.value, 2*e.value ]}", asList(0,0,1,2,2,4)),
            asList("collectManyMapEntryExistingList", "[b:1,c:2].collectMany([0,0]) { e -> [e.value, 2*e.value ]}", asList(0,0,1,2,2,4)),

            // .collectNested
            asList("collectNested", "[[0,1,2],[3,4]].collectNested { i -> i * 2 }", asList(asList(0,2,4),asList(6,8))),
            asList("collectNestedExistingList", "[[0,1,2],[3,4]].collectNested(['test']) { i -> i * 2 }", asList("test", asList(0,2,4),asList(6,8))),

            // .combinations
            // TODO { x, y -> x*y } does not work as CpsTransformer apparently does not grok how to deconstruct array arguments
            asList("combinations", "[[2, 3],[4, 5, 6]].combinations { xy -> xy[0] * xy[1] }", asList(8, 12, 10, 15, 12, 18)),

            // .count
            asList("countList", "[1, 2, 3].count { i -> i > 1 }", 2),
            asList("countArray", "([1, 2, 3] as Integer[]).count { i -> i > 1 }", 2),
            asList("countMapKV", "[a: 1, b: 2, c: 3].count { k, v -> v > 1 }", 2),
            asList("countMapEntry", "[a: 1, b: 2, c: 3].count { e -> e.value > 1 }", 2),

            // .countBy
            asList("countByList", "['aaa', 'bbb', 'cc'].countBy { i -> i.length() }", map(3, 2, 2, 1)),
            asList("countByArray", "(['aaa', 'bbb', 'cc'] as String[]).countBy { i -> i.length() }", map(3, 2, 2, 1)),
            asList("countByMapKV", "[a: 'aaa', b: 'bbb', c: 'cc'].countBy { k, v -> v.length() }", map(3, 2, 2, 1)),
            asList("countByMapEntry", "[a: 'aaa', b: 'bbb', c: 'cc'].countBy { e -> e.value.length() }", map(3, 2, 2, 1)),

            // TODO: downto

            /* TODO: Waiting for dropWhile support
            // .dropWhile
            asList("dropWhileList", "[1, 2, 3].dropWhile { i -> i < 2 }", asList(2, 3)),
            asList("dropWhileSet", "([1, 2, 3) as HashSet].dropWhile { i -> i < 2 }", asList(2, 3) as HashSet),
            asList("dropWhileSortedSet", "([1, 2, 3) as TreeSet].dropWhile { i -> i < 2 }", asList(2, 3) as TreeSet),
            asList("dropWhileMapKV", "[a: 1, b: 2, c: 3].dropWhile { k, v -> v < 2 }", [b: 2, c: 3]),
            asList("dropWhileMapEntry", "[a: 1, b: 2, c: 3].dropWhile { e -> e.value < 2 }", [b: 2, c: 3]),
            */

            // .each
            asList("each", "def x = 100; (0..10).each { y -> x+=y }; return x", 155),
            asList("eachArray", "def x = 10;\n" +
                "([1, 2, 3] as Integer[]).each { y -> x+=y; }\n" +
                "return x;", 16),
            asList("eachMapKV", "def x = 100\n" +
                "def m = [a: 1, b: 2, c: 3];\n" +
                "m.each { k, v -> x += v }\n" +
                "return x;", 106),
            asList("eachMapEntry", "def x = 100\n" +
                "def m = [a: 1, b: 2, c: 3];\n" +
                "m.each { e -> x += e.value }\n" +
                "return x;", 106),
            asList("eachSet", "def x = 100\n" +
                "([1,2,3] as HashSet).each { y -> x += y }\n" +
                "return x", 106),
            asList("eachSortedSet", "def x = 100\n" +
                "([1,2,3] as TreeSet).each { y -> x += y }\n" +
                "return x", 106),

            // TODO: eachByte

            // TODO: eachCombination

            // .eachPermutation
            asList("eachPermutation", "def l = [] as Set; ['a', 'b', 'c'].eachPermutation { i -> l << i }; return l",
             set(asList("a", "b", "c"), asList("a", "c", "b"), asList("b", "a", "c"), asList("b", "c", "a"), asList("c", "a", "b"), asList("c", "b", "a"))),

            // .eachWithIndex
            asList("eachWithIndex", "def x = 100; (0..10).eachWithIndex { y, i -> x+=y; x+=i }; return x", 210),
            asList("eachWithIndexArray", "def x = 10;\n" +
                "([1, 2, 3] as Integer[]).eachWithIndex { y, i -> x+=y; x+=i }\n" +
                "return x;", 19),
            asList("eachWithIndexMapKV", "def x = 100\n" +
                "def m = [a: 1, b: 2, c: 3];\n" +
                "m.eachWithIndex { k, v, i -> x += v; x+=i}\n" +
                "return x;", 109),
            asList("eachWithIndexMapEntry", "def x = 100\n" +
                "def m = [a: 1, b: 2, c: 3];\n" +
                "m.eachWithIndex { e, i -> x += e.value; x+=i }\n" +
                "return x;", 109),
            asList("eachWithIndexSet", "def x = 100\n" +
                "([1,2,3] as HashSet).eachWithIndex { y, i -> x += y; x+=i }\n" +
                "return x", 109),
            asList("eachWithIndexSortedSet", "def x = 100\n" +
                "([1,2,3] as TreeSet).eachWithIndex { y, i -> x += y; x+=i }\n" +
                "return x", 109),

            // .every
            asList("every", "return [0, 1, 2].every { i -> i < 3 }", true),
            asList("everyMapKV", "return [a: 0, b: 1, c: 2].every { k, v -> v < 3 }", true),
            asList("everyMapEntry", "return [a: 0, b: 1, c: 2].every { e -> e.value < 3 }", true),
            asList("everyFalse", "return [0, 1, 2].every { i -> i < 2 }", false),

            // .find
            asList("findList", "[1, 2, 3].find { i -> i == 2 }", 2),
            asList("findArray", "([1, 2, 3] as Integer[]).find { i -> i == 2 }", 2),
            asList("findMapKV", "[a: 1, b: 2, c: 3].find { k, v -> v == 2 }", map("b", 2).entrySet().iterator().next()),
            asList("findMapEntry", "[a: 1, b: 2, c: 3].find { e -> e.value == 2 }", map("b", 2).entrySet().iterator().next()),

            // .findAll
            asList("findAllList", "[1, 2, 3].findAll { i -> i > 1 }", asList(2, 3)),
            asList("findAllArray", "([1, 2, 3] as Integer[]).findAll { i -> i > 1 }", asList(2, 3)),
            asList("findAllSet", "([1, 2, 3] as HashSet).findAll { i -> i > 1 }", set(2, 3)),
            asList("findAllMapKV", "[a: 1, b: 2, c: 3].findAll { k, v -> v > 1 }", map("b", 2, "c", 3)),
            asList("findAllMapEntry", "[a: 1, b: 2, c: 3].findAll { e -> e.value > 1 }", map("b", 2, "c", 3)),

            // .findIndexOf
            asList("findIndexOf", "[1, 2, 3].findIndexOf { i -> i == 2 }", 1),

            // .findIndexValues
            asList("findIndexValues", "[0, 0, 1, 1, 2, 2].findIndexValues { i -> i == 1 }", asList(2L, 3L)),

            // .findLastIndexOf
            asList("findLastIndexOf", "[0, 0, 1, 1, 2, 2].findLastIndexOf { i -> i == 1 }", 3),

            // .findResult
            asList("findResultList", "[1, 2, 3].findResult { i -> if (i > 2) { return 'I found ' + i } }", "I found 3"),
            asList("findResultListDefault", "[1, 2, 3].findResult('default') { i -> if (i > 3) { return 'I found ' + i } }", "default"),
            asList("findResultMapKV", "[a: 1, b: 2, c: 3].findResult { k, v -> if (v > 2) { return 'I found ' + v } }", "I found 3"),
            asList("findResultMapKVDefault", "[a: 1, b: 2, c: 3].findResult('default') { k, v -> if (v > 3) { return 'I found ' + v } }", "default"),
            asList("findResultMapEntry", "[a: 1, b: 2, c: 3].findResult { e -> if (e.value > 2) { return 'I found ' + e.value } }", "I found 3"),
            asList("findResultMapEntryDefault", "[a: 1, b: 2, c: 3].findResult('default') { e -> if (e.value > 3) { return 'I found ' + e.value } }", "default"),

            // .findResults
            asList("findResultsList", "[1, 2, 3].findResults { i -> if (i > 1) { return 'I found ' + i } }", asList("I found 2", "I found 3")),
            asList("findResultsMapKV", "[a: 1, b: 2, c: 3].findResults { k, v -> if (v > 1) { return 'I found ' + v } }", asList("I found 2", "I found 3")),
            asList("findResultsMapEntry", "[a: 1, b: 2, c: 3].findResults { e -> if (e.value > 1) { return 'I found ' + e.value } }", asList("I found 2", "I found 3")),

            // .flatten
            asList("flatten", "[[0, 1], 'ab', 2].flatten { i -> def ans = i.iterator().toList(); ans != [i] ? ans : i }",
             asList(0, 1, "a", "b", 2)),

            // .groupBy
            asList("groupByList", "[1, 'a', 2, 'b', 3.5, 4.6].groupBy { i -> i.class.simpleName }",
             map("Integer", asList(1, 2), "String", asList("a", "b"), "BigDecimal", asList(new BigDecimal("3.5"), new BigDecimal("4.6")))),
            asList("groupByListMultipleCriteria", "[1, 'a', 2, 'b', 3.5, 4.6].groupBy({ i -> i.class.simpleName }, { it.class == Integer ? 'integer' : 'non-integer' })",
             map("Integer", map("integer", asList(1, 2)), "String", map("non-integer", asList("a", "b")), "BigDecimal", map("non-integer", asList(new BigDecimal("3.5"), new BigDecimal("4.6"))))),
            asList("groupByArray", "([1, 'a', 2, 'b', 3.5, 4.6] as Object[]).groupBy { i -> i.class.simpleName }",
             map("Integer", asList(1, 2), "String", asList("a", "b"), "BigDecimal", asList(new BigDecimal("3.5"), new BigDecimal("4.6")))),
            asList("groupByArrayMultipleCriteria", "([1, 'a', 2, 'b', 3.5, 4.6] as Object[]).groupBy({ i -> i.class.simpleName }, { it.class == Integer ? 'integer' : 'non-integer' })",
             map("Integer", map("integer", asList(1, 2)), "String", map("non-integer", asList("a", "b")), "BigDecimal", map("non-integer", asList(new BigDecimal("3.5"), new BigDecimal("4.6"))))),
            asList("groupByMapKV", "[1: 1, 2: 'a', 3: 2, 4: 'b', 5: 3.5, 6: 4.6].groupBy { k, v -> v.class.simpleName }",
             map("Integer", map(1, 1, 3, 2), "String", map(2, "a", 4, "b"), "BigDecimal", map(5, new BigDecimal("3.5"), 6, new BigDecimal("4.6")))),
            asList("groupByMapKVMultipleCriteria", "[1: 1, 2: 'a', 3: 2, 4: 'b', 5: 3.5, 6: 4.6].groupBy({ k, v -> v.class.simpleName }, { k, v -> v.class == Integer ? 'integer' : 'non-integer' })",
             map("Integer", map("integer", map(1, 1, 3, 2)), "String", map("non-integer", map(2, "a", 4, "b")), "BigDecimal", map("non-integer", map(5, new BigDecimal("3.5"), 6, new BigDecimal("4.6"))))),
            asList("groupByMapEntry", "[1: 1, 2: 'a', 3: 2, 4: 'b', 5: 3.5, 6: 4.6].groupBy { e -> e.value.class.simpleName }",
             map("Integer", map(1, 1, 3, 2), "String", map(2, "a", 4, "b"), "BigDecimal", map(5, new BigDecimal("3.5"), 6, new BigDecimal("4.6")))),
            asList("groupByMapEntryMultipleCriteria", "[1: 1, 2: 'a', 3: 2, 4: 'b', 5: 3.5, 6: 4.6].groupBy({ e -> e.value.class.simpleName }, { e -> e.value.class == Integer ? 'integer' : 'non-integer' })",
             map("Integer", map("integer", map(1, 1, 3, 2)), "String", map("non-integer", map(2, "a", 4, "b")), "BigDecimal", map("non-integer", map(5, new BigDecimal("3.5"), 6, new BigDecimal("4.6"))))),

            // .groupEntriesBy
            asList("groupEntriesByMapKV", "[1: 1, 2: 'a', 3: 2, 4: 'b', 5: 3.5, 6: 4.6].groupEntriesBy { k, v -> v.class.simpleName }",
             map("Integer", asList(mapEntry(1, 1), mapEntry(3, 2)), "String", asList(mapEntry(2, "a"), mapEntry(4, "b")), "BigDecimal", asList(mapEntry(5, new BigDecimal("3.5")), mapEntry(6, new BigDecimal("4.6"))))),
            asList("groupEntriesByMapEntry", "[1: 1, 2: 'a', 3: 2, 4: 'b', 5: 3.5, 6: 4.6].groupEntriesBy { e -> e.value.class.simpleName }",
             map("Integer", asList(mapEntry(1, 1), mapEntry(3, 2)), "String", asList(mapEntry(2, "a"), mapEntry(4, "b")), "BigDecimal", asList(mapEntry(5, new BigDecimal("3.5")), mapEntry(6, new BigDecimal("4.6"))))),

            // TODO: identity

            // .inject
            asList("injectList", "[1, 2, 3].inject(4) { c, i -> c + i }", 10),
            asList("injectListNoInitialValue", "[1, 2, 3].inject { c, i -> c + i }", 6),
            asList("injectArray", "([1, 2, 3] as Integer[]).inject(4) { c, i -> c + i }", 10),
            asList("injectArrayNoInitialValue", "([1, 2, 3] as Integer[]).inject { c, i -> c + i }", 6),
            asList("injectMapKV", "[a: 1, b: 2, c: 3].inject(4) { c, k, v -> c + v }", 10),
            asList("injectMapEntry", "[a: 1, b: 2, c: 3].inject(4) { c, e -> c + e.value }", 10),

            // .max
            asList("maxList", "[42, 35, 17, 100].max {i -> i.toString().toList().collect {it.toInteger()}.sum() }", 35),
            asList("maxArray", "([42, 35, 17, 100] as Integer[]).max { i -> i.toString().toList().collect {it.toInteger()}.sum() }", 35),
            /* TODO ClosureComparator
            asList("maxMap", "[a: 42, b: 35, c: 17, d: 100].max { first, second -> first.value.toString().toList().collect {it.toInteger()}.sum() <=> second.value.toString().toList().collect {it.toInteger()}.sum() }", [b: 35].entrySet().iterator().next()),
            */

            // TODO: metaClass

            // .min
            asList("minList", "[42, 35, 17, 100].min { i -> i.toString().toList().collect {it.toInteger()}.sum() }", 100),
            asList("minArray", "([42, 35, 17, 100] as Integer[]).min { i -> i.toString().toList().collect {it.toInteger()}.sum() }", 100),
            /* TODO ClosureComparator
            asList("minMap", "[a: 42, b: 35, c: 17, d: 100].min { first, second -> first.value.toString().toList().collect {it.toInteger()}.sum() <=> second.value.toString().toList().collect {it.toInteger()}.sum() }", [d: 100].entrySet().iterator().next()),
            */

            // .permutations
            asList("permutations", "[1, 2, 3].permutations { i -> i.collect { v -> v * 2 } } as Set", set(asList(2, 4, 6), asList(2, 6, 4), asList(4, 2, 6), asList(4, 6, 2), asList(6, 2, 4), asList(6, 4, 2))),

            // TODO: print and println?

            // .removeAll
            asList("removeAll", "def l = [1, 2, 3]; l.removeAll { i -> i == 2 }; return l", asList(1, 3)),

            // .retainAll
            asList("retainAll", "def l = [1, 2, 3, 4]; l.retainAll { i -> i % 2 == 0 }; return l", asList(2, 4)),

            // .reverseEach
            asList("reverseEachList", "def r = ''; ['a', 'b', 'c'].reverseEach { i -> r += i }; return r", "cba"),
            asList("reverseEachArray", "def r = ''; (['a', 'b', 'c'] as String[]).reverseEach { i -> r += i }; return r", "cba"),
            asList("reverseEachMapKV", "def r = ''; ['a': 1, 'b': 2, 'c': 3].reverseEach { k, v -> r += k }; return r", "cba"),
            asList("reverseEachMapEntry", "def r = ''; ['a': 1, 'b': 2, 'c': 3].reverseEach { e -> r += e.key }; return r", "cba"),

            /* TODO would need to translate OrderBy and ClosureComparator
            // .sort
            asList("sortList", "[3, 1, -2, -4].sort { i -> i * i }", [1, -2, 3, -4]),
            asList("sortArray", "([3, 1, -2, -4] as Integer[]).sort { i -> i * i }", [1, -2, 3, -4]),
            asList("sortMapEntryByKey", "[a: 3, c: 1, b: -2, d: -4].sort { e -> e.key }", [a: 3, b: -2, c: 1, d: -4]),
            asList("sortMapEntryByValue", "[a: 3, c: 1, b: -2, d: -4].sort { e -> e.value }", [d: -4, b: -2, c: 1, a: 3]),
            */

            // .split
            asList("splitList", "[1, 2, 3, 4].split { i -> i % 2 == 0 }", asList(asList(2, 4), asList(1, 3))),
            asList("splitSet", "([1, 2, 3, 4] as HashSet).split { i -> i % 2 == 0 }", asList(set(2, 4), set(1, 3))),

            // TODO: step?

            // .sum
            asList("sumList", "['a', 'bb', 'ccc'].sum { i -> i.length() }", 6),
            asList("sumListInitialValue", "['a', 'bb', 'ccc'].sum(4) { i -> i.length() }", 10),
            asList("sumArray", "(['a', 'bb', 'ccc'] as String[]).sum { i -> i.length() }", 6),
            asList("sumArrayInitialValue", "(['a', 'bb', 'ccc'] as String[]).sum(4) { i -> i.length() }", 10),

            /* TODO: waiting for takeWhile support
            // .takeWhile
            asList("takeWhileList", "[1, 2, 3].takeWhile { i -> i < 3 }", asList(1, 2)),
            asList("takeWhileSet", "([1, 2, 3) as HashSet].takeWhile { i -> i < 3 }", asList(1, 2) as HashSet),
            asList("takeWhileSortedSet", "([1, 2, 3) as TreeSet].takeWhile { i -> i < 3 }", asList(1, 2) as TreeSet),
            asList("takeWhileMapKV", "[a: 1, b: 2, c: 3].takeWhile { k, v -> v < 3 }", [a: 1, b: 2]),
            asList("takeWhileMapEntry", "[a: 1, b: 2, c: 3].takeWhile { e -> e.value < 3 }", [a: 1, b: 2]),
            */

            // TODO: times

            /* TODO as above
            // .toSorted
            asList("toSortedList", "[3, 1, -2, -4].toSorted { i -> i * i }", [1, -2, 3, -4]),
            asList("toSortedArray", "([3, 1, -2, -4] as Integer[]).toSorted { i -> i * i }", [1, -2, 3, -4]),
            asList("toSortedMapEntryByKey", "[a: 3, c: 1, b: -2, d: -4].toSorted { e -> e.key }", [a: 3, b: -2, c: 1, d: -4]),
            asList("toSortedMapEntryByValue", "[a: 3, c: 1, b: -2, d: -4].toSorted { e -> e.value }", [d: -4, b: -2, c: 1, a: 3]),
            */

            /* TODO: waiting for toUnique support
            // .toUnique
            asList("toUniqueList", "[1, 2, -2, 3].toUnique { i -> i * i }", asList(1, 2, 3)),
            asList("toUniqueArray", "([1, 2, -2, 3] as Integer[]).toUnique { i -> i * i }", asList(1, 2, 3)),
            asList("toUniqueSet", "([1, 2, -2, 3] as HashSet).toUnique { i -> i * i }", asList(1, 2, 3) as HashSet),
            */

            /* TODO also relies on OrderBy & ClosureComparator
            // .unique
            asList("uniqueList", "[1, 2, -2, 3].unique { i -> i * i }", asList(1, 2, 3)),
            asList("uniqueSet", "([1, 2, -2, 3] as HashSet).unique { i -> i * i }.collect { it.abs() } as HashSet", asList(1, 2, 3) as HashSet),
            */

            // TODO: with?

            // .withDefault
            asList("withDefaultList", "[].withDefault { i -> i * 2 }.get(1)", 2),
            asList("withDefaultMap", "[:].withDefault { k -> k * 2 }.get(1)", 2)

        );

        assertEquals("Duplicate test names", Collections.emptyList(), rawTests.stream()
                .collect(Collectors.groupingBy(l -> l.get(0)))
                .entrySet()
                .stream()
                .filter(e -> e.getValue().size() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList()));

        return rawTests.stream().map(Collection::toArray).collect(Collectors.toList());
    }

    @Test
    public void cps() throws Throwable {
        assertEvaluate(testResult, testCode);
    }

    @Test
    public void nonCps() throws Throwable {
        assertEvaluate(testResult,
            "@NonCPS\n" +
            "def someMethod() {\n" +
            "  " + testCode + "\n" +
            "}\n" +
            "someMethod()");
    }

    private static Map<Object, Object> map(Object... values) {
        return InvokerHelper.createMap(values);
    }
    
    private static <A, B> Map.Entry<A, B> mapEntry(A key, B value) {
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }

    private static Set<Object> set(Object... values) {
        return new HashSet<>(List.of(values));
    }
}
