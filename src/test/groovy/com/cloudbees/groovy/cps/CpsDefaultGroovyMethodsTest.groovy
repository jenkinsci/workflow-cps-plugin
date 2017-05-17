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

    @Parameterized.Parameters(name="{0}")
    public static Iterable<Object[]> generateParameters() {
        // First element is the name of the test
        // Second element is the code to eval
        // Third element is expected result
        def rawTests = [
            // .any
            ["any", "return [0, 1, 2].any { i -> i == 1 }", true],
            ["anyMapKV", "return [a: 0, b: 1, c: 2].any { k, v -> v == 1 }", true],
            ["anyMapEntry", "return [a: 0, b: 1, c: 2].any { e -> e.value == 1 }", true],
            ["anyFalse", "return [0, 1, 2].any { i -> i > 2 }", false],

            // TODO: asType?

            // .collect
            ["collectList", "return [1, 2, 3].collect { it * 2 }", [2, 4, 6]],
            ["collectListIntoExistingList", "def existing = [2]\n" +
                "return [2, 3, 4].collect(existing) { it * 2 }", [2, 4, 6, 8]],
            ["collectListIntoExistingSet", "return [2, 3, 4].collect([2] as HashSet) { it * 2 }",
             [2, 4, 6, 8] as HashSet],
            ["collectSet", "return ([1, 2, 3] as HashSet).collect { it * 2 }", [2, 4, 6]],
            ["collectSetIntoExistingList", "def existing = [2]\n" +
                "return ([2, 3, 4] as HashSet).collect(existing) { it * 2 }", [2, 4, 6, 8]],
            ["collectSetIntoExistingSet", "return ([2, 3, 4] as HashSet).collect([2] as HashSet) { it * 2 }",
             [2, 4, 6, 8] as HashSet],
            ["collectMapKV", "def m = [a: 1, b: 2, c: 3];\n" +
                "return m.collect { k, v -> v }", [1, 2, 3]],
            ["collectMapEntry", "def m = [a: 1, b: 2, c: 3];\n" +
                "return m.collect { e -> e.value }", [1, 2, 3]],
            ["collectMapKVIntoExistingList", "def m = [a: 2, b: 3, c: 4];\n" +
                "return m.collect([1]) { k, v -> v }", [1, 2, 3, 4]],
            ["collectMapEntryIntoExistingList", "def m = [a: 2, b: 3, c: 4];\n" +
                "return m.collect([1]) { e -> e.value }", [1, 2, 3, 4]],
            ["collectMapKVIntoExistingSet", "def m = [a: 2, b: 3, c: 4];\n" +
                "return m.collect([1] as HashSet) { k, v -> v }", [1, 2, 3, 4] as HashSet],
            ["collectMapEntryIntoExistingSet", "def m = [a: 2, b: 3, c: 4];\n" +
                "return m.collect([1] as HashSet) { e -> e.value }", [1, 2, 3, 4] as HashSet],

            // .collectEntries
            ["collectEntriesKV", "def m = [a: 1, b: 2, c: 3]\n" +
                "return m.collectEntries { k, v -> [(k): v * 2] }", [a: 2, b: 4, c: 6]],
            ["collectEntriesEntry", "def m = [a: 1, b: 2, c: 3]\n" +
                "return m.collectEntries { e -> [(e.key): e.value * 2] }", [a: 2, b: 4, c: 6]],
            ["collectEntriesIntoExistingMapKV", "def m = [b: 2, c: 3]\n" +
                "return m.collectEntries([a: 2]) { k, v -> [(k): v * 2] }", [a: 2, b: 4, c: 6]],
            ["collectEntriesIntoExistingMapEntry", "def m = [b: 2, c: 3]\n" +
                "return m.collectEntries([a: 2]) { e -> [(e.key): e.value * 2] }", [a: 2, b: 4, c: 6]],
            ["collectEntriesArray", "return ([1, 2, 3] as Integer[]).collectEntries { i -> [(i): i * 2] }", [1: 2, 2: 4, 3: 6]],
            ["collectEntriesArrayExistingMap", "return ([2, 3] as Integer[]).collectEntries([1: 2]) { i -> [(i): i * 2] }", [1: 2, 2: 4, 3: 6]],
            ["collectEntriesList", "return [1, 2, 3].collectEntries { i -> [(i): i * 2] }", [1: 2, 2: 4, 3: 6]],
            ["collectEntriesListExistingMap", "return [2, 3].collectEntries([1: 2]) { i -> [(i): i * 2] }", [1: 2, 2: 4, 3: 6]],

            // .collectMany
            ["collectMany", "(0..5).collectMany { [it, 2*it ]}", [0,0,1,2,2,4,3,6,4,8,5,10]],
            ["collectManyExistingList", "(1..5).collectMany([0,0]) { [it, 2*it ]}", [0,0,1,2,2,4,3,6,4,8,5,10]],
            ["collectManyArray", "([0, 1, 2, 3, 4, 5] as Integer[]).collectMany { [it, 2*it ]}", [0,0,1,2,2,4,3,6,4,8,5,10]],
            ["collectManyMapKV", "[a:0,b:1,c:2].collectMany { k,v -> [v, 2*v ]}", [0,0,1,2,2,4]],
            ["collectManyMapKVExistingList", "[b:1,c:2].collectMany([0,0]) { k,v -> [v, 2*v ]}", [0,0,1,2,2,4]],
            ["collectManyMapEntry", "[a:0,b:1,c:2].collectMany { e -> [e.value, 2*e.value ]}", [0,0,1,2,2,4]],
            ["collectManyMapEntryExistingList", "[b:1,c:2].collectMany([0,0]) { e -> [e.value, 2*e.value ]}", [0,0,1,2,2,4]],

            // .collectNested
            ["collectNested", "[[0,1,2],[3,4]].collectNested { i -> i * 2 }", [[0,2,4],[6,8]]],
            ["collectNestedExistingList", "[[0,1,2],[3,4]].collectNested(['test']) { i -> i * 2 }", ['test', [0,2,4],[6,8]]],

            // .combinations
            ["combinations", "[[2, 3],[4, 5, 6]].combinations { x, y -> x*y }", [8, 12, 10, 15, 12, 18]],

            // .count
            ["countList", "[1, 2, 3].count { i -> i > 1 }", 2],
            ["countArray", "([1, 2, 3] as Integer[]).count { i -> i > 1 }", 2],
            ["countMapKV", "[a: 1, b: 2, c: 3].count { k, v -> v > 1 }", 2],
            ["countMapEntry", "[a: 1, b: 2, c: 3].count { e -> e.value > 1 }", 2],

            // .countBy
            ["countByList", "['aaa', 'bbb', 'cc'].countBy { i -> i.length() }", [3:2, 2:1]],
            ["countByArray", "(['aaa', 'bbb', 'cc'] as String[]).countBy { i -> i.length() }", [3:2, 2:1]],
            ["countByMapKV", "[a: 'aaa', b: 'bbb', c: 'cc'].countBy { k, v -> v.length() }", [3:2, 2:1]],
            ["countByMapEntry", "[a: 'aaa', b: 'bbb', c: 'cc'].countBy { e -> e.value.length() }", [3:2, 2:1]],

            // TODO: downto

            /* TODO: Waiting for dropWhile support
            // .dropWhile
            ["dropWhileList", "[1, 2, 3].dropWhile { i -> i < 2 }", [2, 3]],
            ["dropWhileSet", "([1, 2, 3] as HashSet).dropWhile { i -> i < 2 }", [2, 3] as HashSet],
            ["dropWhileSortedSet", "([1, 2, 3] as TreeSet).dropWhile { i -> i < 2 }", [2, 3] as TreeSet],
            ["dropWhileMapKV", "[a: 1, b: 2, c: 3].dropWhile { k, v -> v < 2 }", [b: 2, c: 3]],
            ["dropWhileMapEntry", "[a: 1, b: 2, c: 3].dropWhile { e -> e.value < 2 }", [b: 2, c: 3]],
            */

            // .each
            ["each", "def x = 100; (0..10).each { y -> x+=y }; return x", 155],
            ["eachArray", "def x = 10;\n" +
                "([1, 2, 3] as Integer[]).each { y -> x+=y; }\n" +
                "return x;", 16],
            ["eachMapKV", "def x = 100\n" +
                "def m = [a: 1, b: 2, c: 3];\n" +
                "m.each { k, v -> x += v }\n" +
                "return x;", 106],
            ["eachMapEntry", "def x = 100\n" +
                "def m = [a: 1, b: 2, c: 3];\n" +
                "m.each { e -> x += e.value }\n" +
                "return x;", 106],
            ["eachSet", "def x = 100\n" +
                "([1,2,3] as HashSet).each { y -> x += y }\n" +
                "return x", 106],
            ["eachSortedSet", "def x = 100\n" +
                "([1,2,3] as TreeSet).each { y -> x += y }\n" +
                "return x", 106],

            // TODO: eachByte

            // TODO: eachCombination

            // .eachPermutation
            ["eachPermutation", "def l = [] as Set; ['a', 'b', 'c'].eachPermutation { i -> l << i }; return l",
             [['a', 'b', 'c'], ['a', 'c', 'b'], ['b', 'a', 'c'], ['b', 'c', 'a'], ['c', 'a', 'b'], ['c', 'b', 'a']] as Set],

            // .eachWithIndex
            ["eachWithIndex", "def x = 100; (0..10).eachWithIndex { y, i -> x+=y; x+=i }; return x", 210],
            ["eachWithIndexArray", "def x = 10;\n" +
                "([1, 2, 3] as Integer[]).eachWithIndex { y, i -> x+=y; x+=i }\n" +
                "return x;", 19],
            ["eachWithIndexMapKV", "def x = 100\n" +
                "def m = [a: 1, b: 2, c: 3];\n" +
                "m.eachWithIndex { k, v, i -> x += v; x+=i}\n" +
                "return x;", 109],
            ["eachWithIndexMapEntry", "def x = 100\n" +
                "def m = [a: 1, b: 2, c: 3];\n" +
                "m.eachWithIndex { e, i -> x += e.value; x+=i }\n" +
                "return x;", 109],
            ["eachWithIndexSet", "def x = 100\n" +
                "([1,2,3] as HashSet).eachWithIndex { y, i -> x += y; x+=i }\n" +
                "return x", 109],
            ["eachWithIndexSortedSet", "def x = 100\n" +
                "([1,2,3] as TreeSet).eachWithIndex { y, i -> x += y; x+=i }\n" +
                "return x", 109],

            // .every
            ["every", "return [0, 1, 2].every { i -> i < 3 }", true],
            ["everyMapKV", "return [a: 0, b: 1, c: 2].every { k, v -> v < 3 }", true],
            ["everyMapEntry", "return [a: 0, b: 1, c: 2].every { e -> e.value < 3 }", true],
            ["everyFalse", "return [0, 1, 2].every { i -> i < 2 }", false],

            // .find
            ["findList", "[1, 2, 3].find { i -> i == 2 }", 2],
            ["findArray", "([1, 2, 3] as Integer[]).find { i -> i == 2 }", 2],
            ["findMapKV", "[a: 1, b: 2, c: 3].find { k, v -> v == 2 }", [b: 2].entrySet().iterator().next()],
            ["findMapEntry", "[a: 1, b: 2, c: 3].find { e -> e.value == 2 }", [b: 2].entrySet().iterator().next()],

            // .findAll
            ["findAllList", "[1, 2, 3].findAll { i -> i > 1 }", [2, 3]],
            ["findAllArray", "([1, 2, 3] as Integer[]).findAll { i -> i > 1 }", [2, 3]],
            ["findAllSet", "([1, 2, 3] as HashSet).findAll { i -> i > 1 }", [2, 3] as HashSet],
            ["findAllMapKV", "[a: 1, b: 2, c: 3].findAll { k, v -> v > 1 }", [b: 2, c: 3]],
            ["findAllMapEntry", "[a: 1, b: 2, c: 3].findAll { e -> e.value > 1 }", [b: 2, c: 3]],

            // .findIndexOf
            ["findIndexOf", "[1, 2, 3].findIndexOf { i -> i == 2 }", 1],

            // .findIndexValues
            ["findIndexValues", "[0, 0, 1, 1, 2, 2].findIndexValues { i -> i == 1 }", [2, 3]],

            // .findLastIndexOf
            ["findLastIndexOf", "[0, 0, 1, 1, 2, 2].findLastIndexOf { i -> i == 1 }", 3],

            // .findResult
            ["findResultList", "[1, 2, 3].findResult { i -> if (i > 2) { return 'I found ' + i } }", "I found 3"],
            ["findResultListDefault", "[1, 2, 3].findResult('default') { i -> if (i > 3) { return 'I found ' + i } }", "default"],
            ["findResultMapKV", "[a: 1, b: 2, c: 3].findResult { k, v -> if (v > 2) { return 'I found ' + v } }", "I found 3"],
            ["findResultMapKVDefault", "[a: 1, b: 2, c: 3].findResult('default') { k, v -> if (v > 3) { return 'I found ' + v } }", "default"],
            ["findResultMapEntry", "[a: 1, b: 2, c: 3].findResult { e -> if (e.value > 2) { return 'I found ' + e.value } }", "I found 3"],
            ["findResultMapEntryDefault", "[a: 1, b: 2, c: 3].findResult('default') { e -> if (e.value > 3) { return 'I found ' + e.value } }", "default"],

            // .findResults
            ["findResultsList", "[1, 2, 3].findResults { i -> if (i > 1) { return 'I found ' + i } }", ["I found 2", "I found 3"]],
            ["findResultsMapKV", "[a: 1, b: 2, c: 3].findResults { k, v -> if (v > 1) { return 'I found ' + v } }", ["I found 2", "I found 3"]],
            ["findResultsMapEntry", "[a: 1, b: 2, c: 3].findResults { e -> if (e.value > 1) { return 'I found ' + e.value } }", ["I found 2", "I found 3"]],

            // .flatten
            ["flatten", "[[0, 1], 'ab', 2].flatten { i -> def ans = i.iterator().toList(); ans != [i] ? ans : i }",
             [0, 1, 'a', 'b', 2]],

            // .groupBy
            ["groupByList", "[1, 'a', 2, 'b', 3.5, 4.6].groupBy { i -> i.class.simpleName }",
             [Integer: [1, 2], String: ["a", "b"], BigDecimal: [3.5, 4.6]]],
            ["groupByListMultipleCriteria", "[1, 'a', 2, 'b', 3.5, 4.6].groupBy({ i -> i.class.simpleName }, { it.class == Integer ? 'integer' : 'non-integer' })",
             [Integer: ['integer': [1, 2]], String: ['non-integer': ["a", "b"]], BigDecimal: ['non-integer': [3.5, 4.6]]]],
            ["groupByArray", "([1, 'a', 2, 'b', 3.5, 4.6] as Object[]).groupBy { i -> i.class.simpleName }",
             [Integer: [1, 2], String: ["a", "b"], BigDecimal: [3.5, 4.6]]],
            ["groupByArrayMultipleCriteria", "([1, 'a', 2, 'b', 3.5, 4.6] as Object[]).groupBy({ i -> i.class.simpleName }, { it.class == Integer ? 'integer' : 'non-integer' })",
             [Integer: ['integer': [1, 2]], String: ['non-integer': ["a", "b"]], BigDecimal: ['non-integer': [3.5, 4.6]]]],
            ["groupByMapKV", "[1: 1, 2: 'a', 3: 2, 4: 'b', 5: 3.5, 6: 4.6].groupBy { k, v -> v.class.simpleName }",
             [Integer: [1: 1, 3: 2], String: [2: "a", 4: "b"], BigDecimal: [5: 3.5, 6: 4.6]]],
            ["groupByMapKVMultipleCriteria", "[1: 1, 2: 'a', 3: 2, 4: 'b', 5: 3.5, 6: 4.6].groupBy({ k, v -> v.class.simpleName }, { k, v -> v.class == Integer ? 'integer' : 'non-integer' })",
             [Integer: ['integer': [1: 1, 3: 2]], String: ['non-integer': [2: "a", 4: "b"]], BigDecimal: ['non-integer': [5: 3.5, 6: 4.6]]]],
            ["groupByMapEntry", "[1: 1, 2: 'a', 3: 2, 4: 'b', 5: 3.5, 6: 4.6].groupBy { e -> e.value.class.simpleName }",
             [Integer: [1: 1, 3: 2], String: [2: "a", 4: "b"], BigDecimal: [5: 3.5, 6: 4.6]]],
            ["groupByMapEntryMultipleCriteria", "[1: 1, 2: 'a', 3: 2, 4: 'b', 5: 3.5, 6: 4.6].groupBy({ e -> e.value.class.simpleName }, { e -> e.value.class == Integer ? 'integer' : 'non-integer' })",
             [Integer: ['integer': [1: 1, 3: 2]], String: ['non-integer': [2: "a", 4: "b"]], BigDecimal: ['non-integer': [5: 3.5, 6: 4.6]]]],

            // .groupEntriesBy
            ["groupEntriesByMapKV", "[1: 1, 2: 'a', 3: 2, 4: 'b', 5: 3.5, 6: 4.6].groupEntriesBy { k, v -> v.class.simpleName }",
             [Integer: [1: 1, 3: 2].entrySet().toList(), String: [2: "a", 4: "b"].entrySet().toList(), BigDecimal: [5: 3.5, 6: 4.6].entrySet().toList()]],
            ["groupEntriesByMapEntry", "[1: 1, 2: 'a', 3: 2, 4: 'b', 5: 3.5, 6: 4.6].groupEntriesBy { e -> e.value.class.simpleName }",
             [Integer: [1: 1, 3: 2].entrySet().toList(), String: [2: "a", 4: "b"].entrySet().toList(), BigDecimal: [5: 3.5, 6: 4.6].entrySet().toList()]],

            // TODO: identity

            // .inject
            ["injectList", "[1, 2, 3].inject(4) { c, i -> c + i }", 10],
            ["injectListNoInitialValue", "[1, 2, 3].inject { c, i -> c + i }", 6],
            ["injectArray", "([1, 2, 3] as Integer[]).inject(4) { c, i -> c + i }", 10],
            ["injectArrayNoInitialValue", "([1, 2, 3] as Integer[]).inject { c, i -> c + i }", 6],
            ["injectMapKV", "[a: 1, b: 2, c: 3].inject(4) { c, k, v -> c + v }", 10],
            ["injectMapEntry", "[a: 1, b: 2, c: 3].inject(4) { c, e -> c + e.value }", 10],

            // .max
            ["maxList", "[42, 35, 17, 100].max { i -> i.toString().toList()*.toInteger().sum() }", 35],
            ["maxArray", "([42, 35, 17, 100] as Integer[]).max { i -> i.toString().toList()*.toInteger().sum() }", 35],
            ["maxMap", "[a: 42, b: 35, c: 17, d: 100].max { first, second -> first.value.toString().toList()*.toInteger().sum() <=> second.value.toString().toList()*.toInteger().sum() }", [b: 35].entrySet().iterator().next()],

            // TODO: metaClass

            // .min
            ["minList", "[42, 35, 17, 100].min { i -> i.toString().toList()*.toInteger().sum() }", 100],
            ["minArray", "([42, 35, 17, 100] as Integer[]).min { i -> i.toString().toList()*.toInteger().sum() }", 100],
            ["minMap", "[a: 42, b: 35, c: 17, d: 100].min { first, second -> first.value.toString().toList()*.toInteger().sum() <=> second.value.toString().toList()*.toInteger().sum() }", [d: 100].entrySet().iterator().next()],

            // .permutations
            ["permutations", "[1, 2, 3].permutations { i -> i.collect { v -> v * 2 } } as Set", [[2, 4, 6], [2, 6, 4], [4, 2, 6], [4, 6, 2], [6, 2, 4], [6, 4, 2]] as Set],

            // TODO: print and println?

            // .removeAll
            ["removeAll", "def l = [1, 2, 3]; l.removeAll { i -> i == 2 }; return l", [1, 3]],

            // .retainAll
            ["retainAll", "def l = [1, 2, 3, 4]; l.retainAll { i -> i % 2 == 0 }; return l", [2, 4]],

            // .reverseEach
            ["reverseEachList", "def r = ''; ['a', 'b', 'c'].reverseEach { i -> r += i }; return r", "cba"],
            ["reverseEachArray", "def r = ''; (['a', 'b', 'c'] as String[]).reverseEach { i -> r += i }; return r", "cba"],
            ["reverseEachMapKV", "def r = ''; ['a': 1, 'b': 2, 'c': 3].reverseEach { k, v -> r += k }; return r", "cba"],
            ["reverseEachMapEntry", "def r = ''; ['a': 1, 'b': 2, 'c': 3].reverseEach { e -> r += e.key }; return r", "cba"],

            // .sort
            ["sortList", "[3, 1, -2, -4].sort { i -> i * i }", [1, -2, 3, -4]],
            ["sortArray", "([3, 1, -2, -4] as Integer[]).sort { i -> i * i }", [1, -2, 3, -4]],
            ["sortMapEntryByKey", "[a: 3, c: 1, b: -2, d: -4].sort { e -> e.key }", [a: 3, b: -2, c: 1, d: -4]],
            ["sortMapEntryByValue", "[a: 3, c: 1, b: -2, d: -4].sort { e -> e.value }", [d: -4, b: -2, c: 1, a: 3]],

            // .split
            ["splitList", "[1, 2, 3, 4].split { i -> i % 2 == 0 }", [[2, 4], [1, 3]]],
            ["splitSet", "([1, 2, 3, 4] as HashSet).split { i -> i % 2 == 0 }", [[2, 4] as HashSet, [1, 3] as HashSet]],

            // TODO: step?

            // .sum
            ["sumList", "['a', 'bb', 'ccc'].sum { i -> i.length() }", 6],
            ["sumListInitialValue", "['a', 'bb', 'ccc'].sum(4) { i -> i.length() }", 10],
            ["sumArray", "(['a', 'bb', 'ccc'] as String[]).sum { i -> i.length() }", 6],
            ["sumArrayInitialValue", "(['a', 'bb', 'ccc'] as String[]).sum(4) { i -> i.length() }", 10],

            /* TODO: waiting for takeWhile support
            // .takeWhile
            ["takeWhileList", "[1, 2, 3].takeWhile { i -> i < 3 }", [1, 2]],
            ["takeWhileSet", "([1, 2, 3] as HashSet).takeWhile { i -> i < 3 }", [1, 2] as HashSet],
            ["takeWhileSortedSet", "([1, 2, 3] as TreeSet).takeWhile { i -> i < 3 }", [1, 2] as TreeSet],
            ["takeWhileMapKV", "[a: 1, b: 2, c: 3].takeWhile { k, v -> v < 3 }", [a: 1, b: 2]],
            ["takeWhileMapEntry", "[a: 1, b: 2, c: 3].takeWhile { e -> e.value < 3 }", [a: 1, b: 2]],
            */

            // TODO: times

            // .toSorted
            ["toSortedList", "[3, 1, -2, -4].toSorted { i -> i * i }", [1, -2, 3, -4]],
            ["toSortedArray", "([3, 1, -2, -4] as Integer[]).toSorted { i -> i * i }", [1, -2, 3, -4]],
            ["toSortedMapEntryByKey", "[a: 3, c: 1, b: -2, d: -4].toSorted { e -> e.key }", [a: 3, b: -2, c: 1, d: -4]],
            ["toSortedMapEntryByValue", "[a: 3, c: 1, b: -2, d: -4].toSorted { e -> e.value }", [d: -4, b: -2, c: 1, a: 3]],

            /* TODO: waiting for toUnique support
            // .toUnique
            ["toUniqueList", "[1, 2, -2, 3].toUnique { i -> i * i }", [1, 2, 3]],
            ["toUniqueArray", "([1, 2, -2, 3] as Integer[]).toUnique { i -> i * i }", [1, 2, 3]],
            ["toUniqueSet", "([1, 2, -2, 3] as HashSet).toUnique { i -> i * i }", [1, 2, 3] as HashSet],
            */

            // .unique
            ["uniqueList", "[1, 2, -2, 3].unique { i -> i * i }", [1, 2, 3]],
            ["uniqueSet", "([1, 2, -2, 3] as HashSet).unique { i -> i * i }", [1, 2, 3] as HashSet],

            // TODO: use?

            // TODO: with?

            // .withDefault
            ["withDefaultList", "[].withDefault { i -> i * 2 }.get(1)", 2],
            ["withDefaultMap", "[:].withDefault { k -> k * 2 }.get(1)", 2],

        ]

        assertEquals("Duplicate test names", [], rawTests.countBy { it[0] }.grep { it.value > 1}.collect { it.key })

        return rawTests.collect { it as Object[] }
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
