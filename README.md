# Pipeline Groovy Plugin

[Wiki page](https://wiki.jenkins-ci.org/display/JENKINS/Pipeline+Groovy+Plugin)

## Introduction

A key component of the Pipeline plugin suite, this provides the standard execution engine for Pipeline steps, based on a custom [Groovy](http://www.groovy-lang.org/) interpreter that runs inside the Jenkins master process.

(In principle other execution engines could be supported, with `FlowDefinition` being the API entry point, but none has been prototyped and it would likely be a very substantial effort to write one.)

Pipeline Groovy script code such as

```groovy
retry(3) {
for (int i = 0; i < 10; i++) {
  branches["branch${i}"] = {
    node {
      retry(3) {
        checkout scm
      }
      sh 'make world'
    }
  }
}
parallel branches
```

gets run as a Groovy program, with certain special function calls called *steps* performing Jenkins-specific operations.
In this example the step `parallel` is defined in this plugin, while `node`, `retry`, `checkout`, and `sh` are defined in other plugins in the Pipeline suite.
The `scm` global variable is defined in the Pipeline Multibranch plugin.

Unlike a regular Groovy program run from a command line, the complete state of a Pipeline build’s program is saved to disk every time an *asynchronous* operation is performed, which includes most Pipeline steps.
Jenkins may be restarted while a build is running, and will resume running the program where it left off.
This is not intended to be efficient, and so should be limited to high-level “glue” code directly related to Jenkins features;
your project’s own build logic should be run from external programs on a build node, in a `sh` or `bat` step.

## Known limitations

The [Pipeline Groovy epic](https://issues.jenkins-ci.org/browse/JENKINS-35390) in JIRA covers some known limitations in the Groovy interpreter.
These issues stem from the fact that Pipeline cannot run Groovy directly, but must intercept each operation to save the program state.

The [Pipeline Sandbox epic](https://issues.jenkins-ci.org/browse/JENKINS-35391) covers issues with the *Groovy sandbox* used to prevent malicious Pipeline scripts from taking control of Jenkins.
Scripts run with the sandbox disabled can make direct calls to Jenkins internal APIs, which can be a useful workaround for missing step functionality, but for security reasons only administrators can approve such scripts.

The [Pipeline Snippet Generator epic](https://issues.jenkins-ci.org/browse/JENKINS-35393) covers issues with the tool used to provide samples of step syntax based on live configuration forms.

## Technical design

The plugin uses the [Groovy CPS library](https://github.com/cloudbees/groovy-cps/) to implement a [continuation-passing style transformation](https://en.wikipedia.org/wiki/Continuation-passing_style) on the program as it is compiled.
The standard Groovy compiler is used to create the AST, but generation of bytecode is intercepted by a `CompilationCustomizer` which replaces most operations with variants that throw a special “error”, `CpsCallableInvocation`.
This is then caught by the engine, which uses information from it (such as arguments about to be passed to a method call) to pass control on to the next continuation.

Pipeline scripts may mark designated methods with the annotation `@NonCPS`.
These are then compiled normally (except for sandbox security checks), and so behave much like “binary” methods from the Java Platform, Groovy runtime, or Jenkins core or plugin code.
`@NonCPS` methods may safely use non-`Serializable` objects as local variables, though they should not accept nonserializable parameters or return or store nonserializable values.
You may not call regular (CPS-transformed) methods, or Pipeline steps, from a `@NonCPS` method, so they are best used for performing some calculations before passing a summary back to the main script.
Note in particular that `@Override`s of methods defined in binary classes,
such as `Object.toString()`,
should in general be marked `@NonCPS` since it will commonly be binary code calling them.

Some kinds of objects are intrinsically not safe to serialize as such, yet we want to retain a reference to them in the program graph.
An example is the `Executor` (~ executor slot on a master or agent node) which is part of the context passed by a `node` step to any step in its block, especially `sh`/`bat`.
Pipeline uses the `Pickle` API to substitute serialization-safe versions of these objects.
When a `WorkflowRun` is loaded from disk after a restart, the program state is deserialized, and pickles are deserialized (“rehydrated”) in parallel.
If and when all pickles are successfully deserialized and the resulting objects placed back in the program state, the program begins running again, and `StepExecution.onResume` is called to restore timers and the like.

All program logic is run inside a “CPS VM thread”, which is just a Java thread pool that can run binary methods and figure out which continuation to do next.
The `parallel` step uses “green threads” (also known as coöperative multitasking): it records logical thread (~ branch) names for various actions, but does not literally run them simultaneously.
The program may seem to perform tasks concurrently, but only because most steps run asynchronously, while the VM thread is idle, and they may overlap in time.
No Java thread is consumed except during the typically brief intervals when Groovy code is actually being run on the VM thread.
The executor widget only displays an entry for the “flyweight” executor on the master node when the VM thread is busy; normally it is hidden.
