package com.cloudbees.groovy.cps

import org.junit.Ignore
import org.junit.Test


class CpsStringGroovyMethodsTest extends AbstractGroovyCpsTest {
    @Test
    void eachMatch() {
        evalCPSAndSync('''
int matchCount = 0
"foobarfoooobar".eachMatch(~/foo/) { matchCount++ }
return matchCount
''', 2)

        evalCPSAndSync('''
int matchCount = 0
"foobarfoooobar".eachMatch('foo') { matchCount++ }
return matchCount
''', 2)
    }

    @Test
    void find() {
        evalCPSAndSync('''
return "foobar".find("oob") { it.reverse() }
''', "raboof")

        evalCPSAndSync('''
return "foobar".find(~/oob/) { it.reverse() }
''', "raboof")
    }

    @Test
    void findAll() {
        evalCPSAndSync('''
return "foobarfoobarfoo".findAll("foo") { it.reverse() }
''', ['oof', 'oof', 'oof'])

        evalCPSAndSync('''
return "foobarfoobarfoo".findAll(~/foo/) { it.reverse() }
''', ['oof', 'oof', 'oof'])
    }

    @Test
    void replaceAll() {
        evalCPSAndSync('''
return "foobarfoobarfoo".replaceAll("foo") { it.reverse() }
''', "oofbaroofbaroof")

        evalCPSAndSync('''
return "foobarfoobarfoo".replaceAll(~/foo/) { it.reverse() }
''', "oofbaroofbaroof")
    }

    @Test
    void replaceFirst() {
        evalCPSAndSync('''
return "foobarfoobarfoo".replaceFirst("foo") { it.reverse() }
''', "oofbarfoobarfoo")

        evalCPSAndSync('''
return "foobarfoobarfoo".replaceFirst(~/foo/) { it.reverse() }
''', "oofbarfoobarfoo")
    }

    @Ignore("Waiting for StringGroovyMethods.LineIterable translation")
    @Test
    void splitEachLine() {
        evalCPSAndSync('''
return """
abc|def
ghi|jkl
mno|pqr
""".splitEachLine("|") { it.reverse() }
''', "bob")
    }

    @Test
    void takeWhile() {
        evalCPSAndSync('''
return "Groovy".takeWhile{ it != 'v' }
''', "Groo")

        evalCPSAndSync('''
def vStr = 'v'
return "Groovy".takeWhile{ it != "${vStr}" }
''', "Groo")
    }

    private void evalCPSAndSync(String script, Object value) {
        evalCPS(script) == value
        evalCPS("""
@NonCPS
def someMethod() {
  ${script}
}
someMethod()
""") == value
    }
}
