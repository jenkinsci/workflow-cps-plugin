p(raw('''
    The <code>currentBuild</code> variable, which is of type
    <a href="https://javadoc.jenkins.io/plugin/workflow-support/org/jenkinsci/plugins/workflow/support/steps/build/RunWrapper.html" target="_blank">RunWrapper</a>,
    may be used to refer to the currently running build.
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
