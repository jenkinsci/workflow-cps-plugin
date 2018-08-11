p(raw('''
    The <code>currentBuild</code> variable may be used to refer to the currently running build.
    It has the following readable properties:
'''))
raw(org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper.class.getResource("RunWrapper/help.html").text)
p(raw('''
    Additionally, for this build only (but not for other builds), the following properties are writable:
'''))
ul {
    li {code {b('result')}}
    li {code {b('displayName')}}
    li {code {b('description')}}
    li {code {b('keepLog')}}
}
