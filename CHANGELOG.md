## Changelog

### 2.80

Release date: 2020-02-14

* Fix: Always link the bindings of scripts loaded via the `load` step back to the binding for the main Pipeline script when a Pipeline is resumed. Previously, the bindings in the loaded script could become out of date in some cases. ([PR 348](https://github.com/jenkinsci/workflow-cps-plugin/pull/348))
* Developer: Introduced `GroovySample` extension point to allow plugins to dynamically add samples to (or filter samples from) the dropdown menu shown when configuring a Pipeline script in Jenkins. ([PR 350](https://github.com/jenkinsci/workflow-cps-plugin/pull/350))

### 2.79

Release date: 2020-02-12

* Security: Fix sandbox bypass vulnerability. ([SECURITY-1710](https://jenkins.io/security/advisory/2020-02-12/#SECURITY-1710))

### 2.78

Release date: 2019-12-10

* Fix: Resume Pipeline execution if Jenkins shutdown is canceled. Previously, when Pipelines were paused because Jenkins was preparing for shutdown, they remained paused even if shutdown was canceled. ([JENKINS-34256](https://issues.jenkins-ci.org/browse/JENKINS-34256))

### 2.77

Release date: 2019-11-26

* Fix: Make the `parallel` step propagate the worst result of all branches when not using `failFast: true`. Previously, the propagated result was the result of the first branch that completed. Note that before Pipeline: Build Step Plugin version 2.10, which changed the way that results are propagated for the `build` step, it was rare for there to be a distinction between the old and new behavior in practice. ([JENKINS-49073](https://issues.jenkins-ci.org/browse/JENKINS-49073))
* Improvement: Improve diagnostics and robustness for Pipeline-specific Whitelists. ([PR 338](https://github.com/jenkinsci/workflow-cps-plugin/pull/338))
* Internal: Update workflow-step-api to no longer rely on a beta API. ([PR 327](https://github.com/jenkinsci/workflow-cps-plugin/pull/327))

### 2.76

Release date: 2019-11-12

* Fix: Prevent block-scope steps implemented using `GeneralizedNonBlockingStepExecution` (such as `withCredentials` and `wrap`) from hanging indefinitely in some scenarios. ([JENKINS-58878](https://issues.jenkins-ci.org/browse/JENKINS-58878))

### 2.75

Release date: 2019-10-31

* Fix: Do not log CPS method mismatch warnings for invocations of closures stored in class fields or maps. ([JENKINS-58407](https://issues.jenkins-ci.org/browse/JENKINS-58407))
* Fix: Do not log CPS method mismatch warnings when the receiver is defined in a Jenkins Plugin. ([JENKINS-58643](https://issues.jenkins-ci.org/browse/JENKINS-58643))
* Fix: Do not log CPS method mismatch warnings for uses of `GroovyShell.evaluate`. ([JENKINS-58620](https://issues.jenkins-ci.org/browse/JENKINS-58620))
* Improvement: Make the error that is thrown when the script for a Pipeline definition cannot be found in the configured SCM clearer. ([JENKINS-59425](https://issues.jenkins-ci.org/browse/JENKINS-59425))
* Improvement: Add Declarative Pipeline samples to the editor for Pipeline jobs. ([JENKINS-42471](https://issues.jenkins-ci.org/browse/JENKINS-42471))
* Internal: Refactor code to use Java 7+ language features, improve performance, clarity, and coverage of tests, and migrate Wiki content to GitHub ([PR 310](https://github.com/jenkinsci/workflow-cps-plugin/pull/320), [PR 321](https://github.com/jenkinsci/workflow-cps-plugin/pull/321), [PR 322](https://github.com/jenkinsci/workflow-cps-plugin/pull/322), [PR 330](https://github.com/jenkinsci/workflow-cps-plugin/pull/330), [PR 331](https://github.com/jenkinsci/workflow-cps-plugin/pull/331), [PR 333](https://github.com/jenkinsci/workflow-cps-plugin/pull/333))

### 2.74

Release date: 2019-08-24

* Fix: Do not attempt to store enums defined in Pipeline scripts that are passed as arguments to Pipeline steps in `ArgumentsActionImpl` to avoid leaking Pipeline script class loaders. ([PR 318](https://github.com/jenkinsci/workflow-cps-plugin/pull/318))
* Fix: Do not try to open the listener for a completed build when looking up environment variables through `EnvActionImpl`. ([JENKINS-59083](https://issues.jenkins-ci.org/browse/JENKINS-59083))
* Internal: Add regression tests for SECURITY-1465. ([PR 310](https://github.com/jenkinsci/workflow-cps-plugin/pull/310))

### 2.73

Release date: 2019-08-01

* Fix: The generated GDSL file used to support syntax highlighting for Pipeline scripts in IntelliJ IDEA incorrectly reported some step parameter types as `Map` when they should have been `List`, and did not support the `parallel` step. ([JENKINS-30572](https://issues.jenkins-ci.org/browse/JENKINS-30572))
* Fix: Do not log CPS method mismatch warnings for some false positives related to metaprogramming. ([JENKINS-58501](https://issues.jenkins-ci.org/browse/JENKINS-58501))
* Improvement: Log a warning when named arguments passed to a Pipeline step cannot be bound to any parameter of that step (for example because the argument is spelled incorrectly). Previously, such arguments were silently ignored. ([JENKINS-33217](https://issues.jenkins-ci.org/browse/JENKINS-33217))
* Improvement: Integrate the `StepListener` API added to Pipeline API Plugin version 2.36 ([JENKINS-58084](https://issues.jenkins-ci.org/browse/JENKINS-58084))
* Internal: Add additional tests for false positive CPS mismatch warnings ([PR 309](https://github.com/jenkinsci/workflow-cps-plugin/pull/309))
* Internal: Speed up some tests using `ClassRule`. ([PR 308](https://github.com/jenkinsci/workflow-cps-plugin/pull/308))
* Internal: Migrate tests related to snippet generation for the `build` step to Pipeline Build Step Plugin ([PR 303](https://github.com/jenkinsci/workflow-cps-plugin/pull/303))

### 2.72

Release date: 2019-07-11

* Fix: Prevent a StackOverflowError from being thrown when calling overridden methods using `super` in some class hierarchies in a Pipeline. ([JENKINS-52395](https://issues.jenkins-ci.org/browse/JENKINS-52395))
* Internal: Update tests to fix PCT failures and unignore tests that no longer fail. ([PR 290](https://github.com/jenkinsci/workflow-cps-plugin/pull/290), [PR 302](https://github.com/jenkinsci/workflow-cps-plugin/pull/302))

### 2.71

Release date: 2019-07-05

* Fix: Allow script-level initializers (including `@Field`) in sandboxed Pipelines. Fixes a regression from version 2.64. ([JENKINS-56682](https://issues.jenkins-ci.org/browse/JENKINS-56682))
* Improvement: Print detailed warnings to the build log when CPS-transformed code is called in a non-CPS context where possible. The warnings link to [https://jenkins.io/redirect/pipeline-cps-method-mismatches/](https://jenkins.io/redirect/pipeline-cps-method-mismatches/) which gives additional context and some examples of how to fix common issues. ([JENKINS-31314](https://issues.jenkins-ci.org/browse/JENKINS-31314))
* Fix: Catch additional types of exceptions when calling GraphListener methods. ([PR 292](https://github.com/jenkinsci/workflow-cps-plugin/pull/292))
* Internal: Build and test on Java 11, light refactoring and simplification, fix flaky test. ([PR 294](https://github.com/jenkinsci/workflow-cps-plugin/pull/294), [PR 295](https://github.com/jenkinsci/workflow-cps-plugin/pull/295), [PR 296](https://github.com/jenkinsci/workflow-cps-plugin/pull/296), [PR 298](https://github.com/jenkinsci/workflow-cps-plugin/pull/298))

### 2.70

Release date: 2019-06-03

* Improvement: Interpret the `DynamicContext` extension point added in Pipeline Step API Plugin version 2.20. ([JENKINS-41854](https://issues.jenkins-ci.org/browse/JENKINS-41854))

### 2.69

Release date: 2019-05-28

* Fix: Prevent a memory leak that could occur when shared libraries were used inside of a `node` step. ([JENKINS-50223](https://issues.jenkins-ci.org/browse/JENKINS-50223))
* Fix: Make an internal collection thread-safe to prevent an `IOException` with the message "cannot find current thread" from being thrown intermittently when using some types of non-blocking steps. ([JENKINS-56890](https://issues.jenkins-ci.org/browse/JENKINS-56890))

### 2.68

Release date: 2019-05-10

* Fix: Improve handling of step arguments that cannot be data-bound. Fixes some cases where a non-fatal `NoStaplerConstructorException` would be visible in logs. ([JENKINS-54186](https://issues.jenkins-ci.org/browse/JENKINS-54186))
* Fix: Simplify stack traces for code inside of Pipeline libraries to avoid issues deserializing those stack traces. ([JENKINS-57085](https://issues.jenkins-ci.org/browse/JENKINS-57085))
* Fix: Make the support bundle component that provides Pipeline timing information more robust so that errors in one build do not keep timing information from being added to the support bundle for other builds. ([PR 283](https://github.com/jenkinsci/workflow-cps-plugin/pull/283))

### 2.67

Release date: 2019-04-19

* Improvement: Better handling of `RejectedAccessException` within `try/catch` and `catchError` blocks. ([JENKINS-34973](https://issues.jenkins-ci.org/browse/JENKINS-34973))

### 2.66

Release date: 2019-04-11

* Fix: `parallel` and `load` steps did not clean up internal state correctly after their executions completed, leading to failures upon resuming a Pipeline and various kinds of memory leaks in some cases. ([JENKINS-41791](https://issues.jenkins-ci.org/browse/JENKINS-41791))
* Fix: When checking out a Pipeline from SCM, the support for SCM retry count did not retry the checkout attempt for certain kinds of errors when it should have. ([PR 274](https://github.com/jenkinsci/workflow-cps-plugin/pull/274))
* Improvement: Avoid logging certain kinds of unhelpful warnings when determining whether a Pipeline should block Jenkins from restarting ([PR 277](https://github.com/jenkinsci/workflow-cps-plugin/pull/277)).
* Improvement: Update samples to use the non-deprecated `archiveArtifacts` step instead of `archive`. ([PR 273](https://github.com/jenkinsci/workflow-cps-plugin/pull/273))
* Improvement: Fix documentation for the `params` variable explaining how to use it with a default value. ([JENKINS-56688](https://issues.jenkins-ci.org/browse/JENKINS-56688))
* Improvement: Add internationalization support and Chinese localization for "Pipeline Syntax" links. ([PR 276](https://github.com/jenkinsci/workflow-cps-plugin/pull/276))

### 2.65

Release date: 2019-03-25

* [Fix security issue](https://jenkins.io/security/advisory/2019-03-25/#SECURITY-1353)

### 2.64

Release date: 2019-03-06

* [Fix security issue](https://jenkins.io/security/advisory/2019-03-06/#SECURITY-1336%20(2))

### 2.63

Release date: 2019-02-01

* Enhancement: Enable the `StepEnvironmentContributor` extension point added in version 2.19 of Pipeline Step API Plugin ([JENKINS-51170](https://issues.jenkins-ci.org/browse/JENKINS-51170))
* Fix: Notify global `GraphListener` implementations of `FlowStartNodes` when a Pipeline build begins ([JENKINS-52189](https://issues.jenkins-ci.org/browse/JENKINS-52189))

### 2.62

Release date: 2019-01-14

* Fix: Trim Pipeline script path (e.g. `Jenkinsfile`) when using a Pipeline script from SCM ([JENKINS-55424](https://issues.jenkins-ci.org/browse/JENKINS-55424))
* Add a link to [Pipeline Examples](https://jenkins.io/doc/pipeline/examples/) from the sidebar of the Pipeline Syntax page.
* Add support for `GeneralNonBlockingStepExecution` utility added to Pipeline Supporting APIs 2.18 ([JENKINS-49337](https://issues.jenkins-ci.org/browse/JENKINS-49337))
* Avoid use of deprecated APIs ([PR 256](https://github.com/jenkinsci/workflow-cps-plugin/pull/256))

### 2.61.1

Release date: 2019-01-08

* [Fix security vulnerability](https://jenkins.io/security/advisory/2019-01-08/)

### 2.61

Release date: 2018-11-30

* Fix: Catch errors thrown by `GraphListener`s during Pipeline execution so that they do not cause the build to fail ([JENKINS-54890](https://issues.jenkins-ci.org/browse/JENKINS-54890))
* Improvement: Only construct log messages when the specified logging level is enabled to improve performance in some cases

### 2.60

Release date: 2018-10-29

* [Fix security issue](https://jenkins.io/security/advisory/2018-10-29/)

### 2.59

Release date: 2018-10-17

* Improvement: Limit the types of Step arguments stored for visualization ([JENKINS-54032](https://issues.jenkins-ci.org/browse/JENKINS-54032))
  * Solves memory leaks for some plugins that abuse Step arguments by passing Pipeline-internal objects
  * Better protects against storing potentially problematic object types, and may reduce their memory use
  * Applies filtering to Describable objects passed by legacy syntaxes, so that filtering can be applied to their fields

### 2.58

Release date: 2018-10-12

* Internal bug fix important for display of steps in classic UI after update of [Pipeline Job Plugin](https://github.com/jenkinsci/workflow-job-plugin) to 2.26.
* Minimum Jenkins Core version updated to 1.121.1

### 2.58-beta-1

Release date: 2018-10-04

* Internal bug fix important for display of steps in classic UI after update of [Pipeline Job Plugin](https://github.com/jenkinsci/workflow-job-plugin) to 2.26-beta-1.

### 2.57

Release date: 2018-10-02

* Fix: Make compilation errors when using the `load` step serializable so that the actual compilation error is reported instead of a `NotSerializableException` ([JENKINS-40109](https://issues.jenkins-ci.org/browse/JENKINS-40109)).
* Improvement: Add localization support for a message on the global variable reference page.

### 2.56

Release date: 2018-09-27

* Fix: Do not persist Pipeline context variables that are no longer in scope. In particular, this fixes some cases where resuming builds outside of a node block would wait for an agent used previously in the Pipeline to become available ([JENKINS-53709](https://issues.jenkins-ci.org/browse/JENKINS-53709))
* Improvement: Chinese localizations have been migrated to the [Localization: Chinese (Simplified) Plugin](https://github.com/jenkinsci/localization-zh-cn-plugin).

### 2.55

Release date: 2018-09-19

* Improvement: Allow steps to be invoked using their full class name to avoid ambiguity, and log a warning when ambiguous steps are invoked ([JENKINS-53333](https://issues.jenkins-ci.org/browse/JENKINS-53333))
* Various documentation and localization improvements.

### 2.54

Release date: 2018-06-25

* **Fix:** Do not use `com.google.common.io.NullOutputStream`

### 2.53

Release date: 2018-05-08

* **Fix:** Fix deadlocks with `WorkflowRun#copyLogs()` + CPS things (tracked in comments for [JENKINS-51132](https://issues.jenkins-ci.org/browse/JENKINS-51132))

### 2.52

Release date: 2018-05-04

* **Fix:** Fix a critical deadlock with `CpsFlowExecution#getCurrentHeads` introduced in 2.50 due to [https://github.com/jenkinsci/workflow-cps-plugin/pull/223](https://github.com/jenkinsci/workflow-cps-plugin/pull/223) ([JENKINS-51132](https://issues.jenkins-ci.org/browse/JENKINS-51132))

### 2.51

Release date: 2018-05-03

* **Fix:** Allow the FlowExecution to still be saved if a Pipeline build (WorkflowJob) is modified and saved before the lazy load of the FlowExecution is done (onLoad not invoked on the execution) ([JENKINS-50888](https://issues.jenkins-ci.org/browse/JENKINS-50888))

### 2.50

Release date: 2018-05-02

* **We strongly encourage installing this update due to the issues resolved, and suggest combining with an upgrade to Pipeline Job (workflow-job) plugin v2.21 or later**
* **Major Fix:** NullPointerException in CPS VM thread for builds with certain data not properly persisted ([JENKINS-49686](https://issues.jenkins-ci.org/browse/JENKINS-49686))
* Fix/Improvement: Pipelines ensure that when part of the information is persisted, all necessary pieces to load that information are _also_ persisted
* Improvement: re-do the persist-at-shutdown behavior to be more robust
* Improvement: even if one Pipeline fails to persist at shutdown, allow other Pipelines to attempt to persist
* Fix: A variety of synchronization consistency problems
* Improvement: Rewrite handling of missing FlowNodeStorage and creation of placeholder nodes to ensure correctness and done state is persisted
* Fix: Pipelines blocking restart of Jenkins masters if they failed to resume or threw an exception when loading the Pipeline program
* Fix: Pipeline builds showing as incomplete when they failed to resume or load
* Greatly expanded test coverage for persistence and edge-cases where incorrect data is persisted

### 2.49

Release date: 2018-04-20

* **Bugfix**: Solve Replay not being visible or usable for builds (regression from lazy load of executions introduced in workflow-job) ([JENKINS-50874](https://issues.jenkins-ci.org/browse/JENKINS-50784))
* Bugfix: CpsScript invokeMethod does not execute closures defined in the script binding.
* Maintenance: Stop using the Junit Step in Metastep tests (prevents Plugin Compatibility Test failurs)

### 2.48

Release date: 2018-04-12

* Bugfix: FlowNode Serialization Could Fail Due to Unserializable Step Arguments ([JENKINS-50752](https://issues.jenkins-ci.org/browse/JENKINS-50752))
    * This could trigger deeper failures in Pipeline due to the serialization failures before workflow-api 2.27

### 2.47

Release date: 2018-04-08

* **Major bugfix / improvements**: numerous fixes & improvements to make Pipeline persistence & resume more robust (across all Durability Settings)
    * These do not have individual JIRAs because they were spinoffs from testing other work, discovered with fuzzing-like approaches
    * Many of these bugs would result in irreproducible errors that may have been reported - **link any related JIRAs here**: (TBD)
    * Improves error-handling logic
* **Part of Major Bugfix**: Error "NullPointerException in CPS VM thread at WorkflowRun$GraphL.onNewHead" as result of a race condition ([JENKINS-49686](https://issues.jenkins-ci.org/browse/JENKINS-49686))
    * The other part of the bugfix is in the Pipeline Job Plugin - version 2.18
* **Part of Major Bugfix**: Failed pipelines resume and won't die even when marked to not resume, and show resume failures ([JENKINS-50199](https://issues.jenkins-ci.org/browse/JENKINS-50199))
    * The other part of the bugfix is in the Pipeline Job Plugin - version 2.18
* **Part of Bugfix**: Error "NullPointerException in SandboxContinuable.run0" after restart in Performance-Optimized Durability Setting ([JENKINS-50407](https://issues.jenkins-ci.org/browse/JENKINS-50407))
    * The other part of the bugfix is in the Pipeline Job Plugin - version 2.18

### 2.46

Release date: 2018-04-05

* [JENKINS-45575](https://issues.jenkins-ci.org/browse/JENKINS-45575), [JENKINS-49679](https://issues.jenkins-ci.org/browse/JENKINS-49679) - Fix a couple issues with multiple assignment.
* [JENKINS-49961](https://issues.jenkins-ci.org/browse/JENKINS-49961) - Fix an NPE when toggling `ResumeEnabled` when `FlowExecutionOwner` is not yet set.
* [JENKINS-45982](https://issues.jenkins-ci.org/browse/JENKINS-45982) - Fix calling a CPS-transformed `super`.
* [JENKINS-33614](https://issues.jenkins-ci.org/browse/JENKINS-33614) - Include link to script approval for `RejectedAccessException` - but only when the user viewing the console has permissions for script approval.
* [JENKINS-50171](https://issues.jenkins-ci.org/browse/JENKINS-50171) - Avoid `LinkageError` with `load` step of Groovy files with a `package` declaration on resume of Pipeline.

### 2.45

Release date: 2018-02-14

* Enhancement: Improve performance of running pipelines - reduce CPU, wall time, and disk IO.
    * Works by eliminating reflection and classloading needed to determine how many arguments a Step needs
* Enhancement: In Snippetizer, support Symbol use with different lists of inputs to steps ([JENKINS-37215](https://issues.jenkins-ci.org/browse/JENKINS-37215))
* Misc: Pick up more modern structs, and reduce memory garbage for steps slightly by using DescribableModel.of API

### 2.44

Release date: 2018-01-31

* Pick up recent groovy-cps fixes including resolving a quirk with field initialization and CPS transforms
* Implement support for SCM retry count - [JENKINS-39194](https://issues.jenkins-ci.org/browse/JENKINS-39194)

### 2.43

Release date: 2018-01-22

* **Major Feature:** Support for faster Durability Settings which reduce I/O and improve performance significantly ([JENKINS-47300](https://issues.jenkins-ci.org/browse/JENKINS-47300))
* **Major Feature:** ability to disable Pipeline auto-resume when restarting ([JENKINS-33761](https://issues.jenkins-ci.org/browse/JENKINS-33761))
* **Major Feature:** consolidate writing FlowNodes using granular persistence APIs ([JENKINS-47172](https://issues.jenkins-ci.org/browse/JENKINS-47172))
* Robustness Enhancement: a giant wad of fixes to ensure we handle persistence failures and oddball circumstances
    * Extensive test coverage, plus protects against some "hung" pipeline states and other weird bugs
* Enhancement: reduce memory used by Pipeline for storing Step arguments ([PR #65](https://github.com/jenkinsci/workflow-api-plugin/pull/65))
* Bugfix: first Pipeline step lacks displayed arguments ([JENKINS-48644](https://issues.jenkins-ci.org/browse/JENKINS-48644))

### 2.42

Release date: 2017-11-29

* **Compatibility Note:** **this is the first version requiring Java 8 (Jenkins LTS 2.60.x+)**
* [JENKINS-44619](https://issues.jenkins-ci.org/browse/JENKINS-44619) - Don't allow replaying unbuildable jobs
* Rename `ReplayCommand` to `ReplayPipelineCommand`
* [JENKINS-47339](https://issues.jenkins-ci.org/browse/JENKINS-47339) - Allow users with `Build` permission but not `Configure` permission to replay a build with the same script.
* [JENKINS-46597](https://issues.jenkins-ci.org/browse/JENKINS-46597) - Fix `IteratorHack` to handle `SortedMap`, like `TreeMap`, without serialization issues.
* Protect against unlimited recursion with a sane error.

### 2.41

Release date: 2017-09-28

* [JENKINS-47071](https://issues.jenkins-ci.org/browse/JENKINS-47071) - Allow `.every`, `.any`, and other closure methods involving booleans to serialize properly.
* Reduce memory of CPS code significantly by lazily initializing locals maps
* [JENKINS-44027](https://issues.jenkins-ci.org/browse/JENKINS-44027) - Support multiple assignment in Pipeline scripts
* [JENKINS-32213](https://issues.jenkins-ci.org/browse/JENKINS-32213) - Automatically make all CPS-transformed classes `Serializable`, no longer requiring that to be done explicitly.
* Do not even offer `PauseUnpauseAction` unless you actually have `CANCEL` permissions

### 2.40

Release date: 2017-09-05

* [JENKINS-34645](https://issues.jenkins-ci.org/browse/JENKINS-34645) - Serialize array iterators properly
* [JENKINS-46391](https://issues.jenkins-ci.org/browse/JENKINS-46391) - Correctly translate `~/foo/` as a regexp
* [JENKINS-46358](https://issues.jenkins-ci.org/browse/JENKINS-46358) - Support for a number of `StringGroovyMethods`

### 2.39 (Aug 7, 2017)

Release date: 2017-08-07

* [Fixes for multiple security issues in script security plugin](https://jenkins.io/security/advisory/2017-08-07/)

### 2.38

Release date: 2017-08-01

* [JENKINS-44548](https://issues.jenkins-ci.org/browse/JENKINS-44548) Fix NullPointerException caused by corrupted FlowExecution records

### 2.37

Release date: 2017-07-25

* Robustness fix involving `StepContext.get(FlowNode)` calls.
* [JENKINS-45109](https://issues.jenkins-ci.org/browse/JENKINS-45109) Metastep display improvements useful especially for `step` and `wrap` calls in Blue Ocean.

* [JENKINS-31582](https://issues.jenkins-ci.org/browse/JENKINS-31582) API for obtaining step arguments in more realistic form.

* Improved logging of `@script` checkout.

### 2.36.1

Release date: 2017-07-10

* [Fix security issue](https://jenkins.io/security/advisory/2017-07-10/)

### 2.36

Release date: 2017-06-15

* Enabling whitelist entries to work for most Groovy built-in methods enabled in 2.33.
* Timeouts in `CpsFlowExecution.suspendAll` could block Jenkins shutdown.

### 2.35

Release date: 2017-06-13

* [38268@issue] Improper binding of local variables in closures.
* Functional tests sometimes failed due to `DSL` finding no step definitions.

### 2.34

Release date: 2017-06-03

* [JENKINS-44578](https://issues.jenkins-ci.org/browse/JENKINS-44578) Corrects a `StackOverflowError` seen on some JVMs with limited stack size running Declarative Pipelines after updating to 2.33.

### 2.33 (May 30, 2017)

Release date: 2017-05-30

Requires Jenkins 2.7.x or later.

> **Warning**: [GROOVY-6263](https://issues.apache.org/jira/browse/GROOVY-6263) will affect Jenkins installations until Groovy 2.5.0 is integrated. Briefly, `private` methods may not be visible if called from an instance of a subclass of the class defining the method. The workaround is to relax the access restriction, for example to `protected`.
>
> Users of Declarative Pipeline should update to 2.34.

* [JENKINS-26481](https://issues.jenkins-ci.org/browse/JENKINS-26481) Most Groovy built-in methods taking closures (such as `List.each`) may now be used from Pipeline script without `@NonCPS` annotations. Certain less commonly used methods (such as `sort` taking a closure) are not yet implemented.
* [JENKINS-27421](https://issues.jenkins-ci.org/browse/JENKINS-27421) Most Java methods returning iterators (such as looping over the result of `Map.entrySet`) may now be used from Pipeline script without `@NonCPS` annotations.
* [JENKINS-31967](https://issues.jenkins-ci.org/browse/JENKINS-31967) **Pipeline Syntax** support for `double` values, as in the `junit` step for example.
* [JENKINS-43055](https://issues.jenkins-ci.org/browse/JENKINS-43055) `GraphListener` may now be used as an extension point.

### 2.32

Release date: 2017-05-24

* Timing feature in 2.31 introduced a memory leak when using shared libraries.

### 2.31

Release date: 2017-05-22

* [JENKINS-37324](https://issues.jenkins-ci.org/browse/JENKINS-37324) Store and display arguments supplied to steps
* [JENKINS-44406](https://issues.jenkins-ci.org/browse/JENKINS-44406) Fix a NullPointerException from StepDescriptorCache when the plugin providing the Step is uninstalled
* Record timing information for various internal operations in Pipeline builds, available when `support-core` is installed.
* Deleting `program.dat` when a build finishes.

### 2.30

Release date: 2017-04-24

* Robustness fix related to [JENKINS-26137](https://issues.jenkins-ci.org/browse/JENKINS-26137).
* [JENKINS-43361](https://issues.jenkins-ci.org/browse/JENKINS-43361) Unreproducible `NullPointerException`.
* [JENKINS-43019](https://issues.jenkins-ci.org/browse/JENKINS-43019) `ClassCastException` under certain circumstances involving libraries.

### 2.29

Release date: 2017-03-03

> Pulls in [SCM API Plugin](https://github.com/jenkinsci/scm-api-plugin) 2.x; read [this blog post](https://jenkins.io/blog/2017/01/17/scm-api-2/).

* [JENKINS-33273](https://issues.jenkins-ci.org/browse/JENKINS-33273) New option for script-from-SCM jobs to load the script directly, rather than performing a full checkout. Requires a compatible SCM, currently Git. Enabled by default for new jobs (falls back to heavyweight checkout where necessary).
* [JENKINS-42367](https://issues.jenkins-ci.org/browse/JENKINS-42367) `NullPointerException` using `params` when certain kinds of parameter values were missing.

### 2.28

Release date: 2017-02-23

* [JENKINS-42189](https://issues.jenkins-ci.org/browse/JENKINS-42189) Problems in memory cleanup when using Groovy 2.4.8 (Jenkins 2.47+) could lead to deadlocks and/or large heap consumption.
* Flow node IDs incorrectly skipped even numbers after around 500.

### 2.27

Release date: 2017-02-13

* [JENKINS-41945](https://issues.jenkins-ci.org/browse/JENKINS-41945) `NullPointerException` during build cleanup, and consequent memory leak, under heavy load.
* [JENKINS-32986](https://issues.jenkins-ci.org/browse/JENKINS-32986) Apply timeouts to some operations in the CPS VM thread.
* Ensure build terminates after certain internal errors.

### 2.26

Release date: 2017-02-07

* Pull in workflow-api 1.10 and refactor StepNode so that libraries can obtain Step info without depending on workflow-cps

### 2.25

Release date: 2017-02-01

* [JENKINS-39719](https://issues.jenkins-ci.org/browse/JENKINS-39719) Cryptic error about overriding methods named like `___cps___2` under certain conditions involving global libraries.
* [JENKINS-31484](https://issues.jenkins-ci.org/browse/JENKINS-31484) Incorrect CPS translation of field references from getters and setters could lead to endless loops.

### 2.24

Release date: 2017-01-17

* Reducing frequency of `program.dat` saves, avoiding some otherwise harmless serialization errors, and possibly improving performance for builds with complex Groovy logic.
* Correcting two kinds of `NullPointerException` when loading old build records.
* [JENKINS-29656](https://issues.jenkins-ci.org/browse/JENKINS-29656) Avoid failing the build merely because renaming a temporary file to `program.dat` fails, typically on Windows due to file locks from antivirus scanners.
* [JENKINS-38551](https://issues.jenkins-ci.org/browse/JENKINS-38551) Invalid characters in GDSL.
* Performance improvement related to `StepDescriptor`.

### 2.23 (Nov 07, 2016)

Release date: 2016-11-07

* Fixed a number of memory leaks, extending fixes made in 2.12.
* [JENKINS-39456](https://issues.jenkins-ci.org/browse/JENKINS-39456) Reduce memory footprint from graph of execution.
* Language fixes, including support for:
  * `super.method(…)` calls
  * `abstract` methods
  * list to constructor coercion

### 2.22

Release date: 2016-11-01

* Bug fix required for proper display of [JENKINS-28385](https://issues.jenkins-ci.org/browse/JENKINS-28385) in step reference.
* [JENKINS-39275](https://issues.jenkins-ci.org/browse/JENKINS-39275) Cap the amount of time spent displaying one line of a virtual thread dump.
* Text changes in **Pipeline Syntax** page.

### 2.21

Release date: 2016-10-21

* [JENKINS-39154](https://issues.jenkins-ci.org/browse/JENKINS-39154) In-browser Pipeline script editor was broken in 2.20.

### 2.20

Release date: 2016-10-20

> **Warning**: Do not use: see 2.21

* [JENKINS-34637](https://issues.jenkins-ci.org/browse/JENKINS-34637) `timeout` did not work when using most nested block steps.
* Improved behavior on `NotSerializableException` such as in [JENKINS-27421](https://issues.jenkins-ci.org/browse/JENKINS-27421).

### 2.19

Release date: 2016-10-11

> Addresses some issues related to `params` encountered in 2.18. In particular, a simple `params.paramName` now suffices to obtain a parameter including a fallback to its default value even in the first build of a branch project using `properties`.

* [JENKINS-35698](https://issues.jenkins-ci.org/browse/JENKINS-35698) `params` now honors currently defined parameter definition defaults.
* Global variables now take precedence over environment variables in case of ambiguity.
* Clarifying and enforcing that `params` is read-only.

### 2.18

Release date: 2016-09-23

> You should also update the [Pipeline Job Plugin](https://github.com/jenkinsci/workflow-job-plugin) to 2.7 or later, so that build parameters are defined as environment variables and thus accessible as if they were global Groovy variables.
>
> Beware that `binding['parameter.with-funny+characters']` will no longer work; use `params['parameter.with-funny+characters']` instead. Also note that `buildParameterName = 'new-value'` will not work, since the fallback to `env` currently takes precedence over global variable bindings; better to treat build parameters as read-only and introduce a new local variable with `def`.
>
> Similarly, if using `ParametersDefinitionProperty` from `properties` in a multibranch `Jenkinsfile` (including via the symbol `parameters` in Jenkins 2.x), the trick
>
>     binding.hasVariable('paramName') ? paramName : 'fallback'
>
> for accessing the parameter value with a fallback in the initial build no longer works. You can now use the simpler
>
>     params.paramName ?: 'fallback'
>
> or to maintain compatibility with installations _with or without_ these changes:
>
>     env.paramName ?: binding.hasVariable('paramName') ? paramName : 'fallback'
>

* [JENKINS-29952](https://issues.jenkins-ci.org/browse/JENKINS-29952) `env.PROP` may now be shortened simply to `PROP` when unambiguous. (Setting a variable still requires the prefix, or the `withEnv` step.)
* [JENKINS-27295](https://issues.jenkins-ci.org/browse/JENKINS-27295) Build parameters may now be accessed via the `params` global variable, with typed values.
* [JENKINS-38114](https://issues.jenkins-ci.org/browse/JENKINS-38114) `currentBuild` global variable documentation now displayed in full, rather than referring to `build` step documentation; and updated to better explain usage in light of changes in 2.14.
* Unreproducible case of an error during build abort handling of a step making the build not abort cleanly.
* Fixed a certain class of build hangs due to code mistakes, and improved error reporting for these cases.
* Improved display in the build log of predictable problems resuming a build, such as cancellation of queue items for offline agents inside a `node` block.
* Adding HTML anchors to the _Global Variable Reference_ for easier linking.

### 2.17

Release date: 2016-09-13

* [JENKINS-38169](https://issues.jenkins-ci.org/browse/JENKINS-38169) Regression in 2.14 affecting certain steps with a single parameter.

### 2.16

Release date: 2016-09-13

* [JENKINS-38167](https://issues.jenkins-ci.org/browse/JENKINS-38167) Regression in 2.14 affecting certain usages of `@Field`.

### 2.15

Release date: 2016-09-07

* [JENKINS-38037](https://issues.jenkins-ci.org/browse/JENKINS-38037) Regression in 2.14 affecting certain usages of symbols, such as with `artifactArchiver` in Jenkins 2.

### 2.14

Release date: 2016-09-07

> **_Note_**: [JENKINS-25623](https://issues.jenkins-ci.org/browse/JENKINS-25623) makes some scripts fail which usually worked before, in case they used non-`Serializable` values in CPS-transformed code (i.e., regular Pipeline script). Such scripts were erroneous and may previously have failed ([JENKINS-27421](https://issues.jenkins-ci.org/browse/JENKINS-27421)), albeit less commonly and less reproducibly. The Pipeline tutorial [describes this scenario](https://github.com/jenkinsci/pipeline-plugin/blob/master/TUTORIAL.md#serializing-local-variables). Briefly, you can choose between
> * use a Pipeline-safe idiom like `for (int i = 0; i < list.size(); i++) {handle(list[i])`}
> * use `for (def elt in list) {handle(elt)`} if `list` is an `ArrayList` (i.e., usual Groovy `[1, 2, 3]` but not slices etc.)
> * wrap any Groovy code not calling steps in a method marked `@NonCPS`
> * delegate to external programs via `sh`/`bat` for any nontrivial computation
>
> Note that if you are iterating the `entrySet()` of `java.util.Map` you will want to use this helper method:
>
>     @NonCPS def entrySet(m) {m.collect {k, v -> [key: k, value: v]}}
>
> Note that 2.24 essentially reverts this change, but you are still advised to avoid even temporary use of nonserializable values.

* [JENKINS-29711](https://issues.jenkins-ci.org/browse/JENKINS-29711) Fixed _Snippet Generator_ output for steps taking a single array of arguments, and fixed the runtime for steps taking a single fixed argument, both with an implicit parameter name.
* [JENKINS-25623](https://issues.jenkins-ci.org/browse/JENKINS-25623) Ability to abort/`timeout` an endless loop of Groovy code not inside any step.
* Updated the Maven sample offered for fresh jobs.
* Updated samples and code completion to use block-scoped `stage` from [JENKINS-26107](https://issues.jenkins-ci.org/browse/JENKINS-26107).
* Fixed link text.
* Infrastructure for [JENKINS-31155](https://issues.jenkins-ci.org/browse/JENKINS-31155).

### 2.13

Release date: 2016-08-25

* [JENKINS-37538](https://issues.jenkins-ci.org/browse/JENKINS-37538) Trying to correct a regression in 2.12. Unreproducible but reported to affect usages of the Artifactory and Subversion plugins.

### 2.12

Release date: 2016-08-15

* Correcting a memory leak introduced with the fix of [JENKINS-36372](https://issues.jenkins-ci.org/browse/JENKINS-36372), as well as a longstanding leak affecting only Jenkins 2.x.
* Print a message whenever we are ready to resume running the program. Normally immediately after _Resuming build_ but could be delayed for various reasons, such as offline agents.
* Setting the thread context class loader, improving performance on systems running numerous Pipeline builds with complex Groovy scripts if you also update [Script Security Plugin](https://github.com/jenkinsci/script-security-plugin) to 1.22.

### 2.11

Release date: 2016-08-09

* [JENKINS-29922](https://issues.jenkins-ci.org/browse/JENKINS-29922) follow-up from 2.10: single-argument metasteps such as `step` and `wrap` should now display their delegates like first-class steps in _Snippet Generator_.
* [JENKINS-29922](https://issues.jenkins-ci.org/browse/JENKINS-29922) follow-up from 2.10: error reporting for undefined functions now mentions available symbols as well as steps.
* Include the running node count in Pipeline thread dumps in support bundles, to help estimate size of the Pipeline script and its libraries.
* Infrastructure for [JENKINS-34650](https://issues.jenkins-ci.org/browse/JENKINS-34650).

### 2.10

Release date: 2016-07-28

* [JENKINS-29922](https://issues.jenkins-ci.org/browse/JENKINS-29922) Simplified step call syntax available for certain cases where previously the `$class` notation was required.
* [JENKINS-25736](https://issues.jenkins-ci.org/browse/JENKINS-25736) Ability to pause a running build.

### 2.9

Release date: 2016-07-05

* [JENKINS-36372](https://issues.jenkins-ci.org/browse/JENKINS-36372) Root bindings not accessible to scripts loaded after restart.

### 2.8

Release date: 2016-06-29

* [JENKINS-31842](https://issues.jenkins-ci.org/browse/JENKINS-31842) The virtual thread dump for a running build can now display information about the status of running steps.
* [JENKINS-36289](https://issues.jenkins-ci.org/browse/JENKINS-36289) API for accessing Replay from Blue Ocean.

### 2.7

Release date: 2016-06-27

* Some _Snippet Generator_ forms were not using the correct job context since 2.3; affected _Credentials_ dropdowns, for example.
* Show the _Pipeline Syntax_ link also from _Replay_ screens.
* Improved the Maven script sample in the inline editor.

### 2.6

Release date: 2016-06-16

* [JENKINS-26481](https://issues.jenkins-ci.org/browse/JENKINS-26481) Pending a true fix for passing closures to “binary” methods such as `Collection.each`, the Pipeline build should now fail with an error message mentioning the issue, rather than silently behaving in an erratic fashion.
* [JENKINS-35395](https://issues.jenkins-ci.org/browse/JENKINS-35395) Moving documentation for global variables into their own page for clarity.
* Missing colon in editor snippet.
* Infrastructure for [JENKINS-26130](https://issues.jenkins-ci.org/browse/JENKINS-26130): display information in the build log after _Resuming build_ about what objects are still being loaded.

### 2.5

Release date: 2016-06-09

* [JENKINS-34281](https://issues.jenkins-ci.org/browse/JENKINS-34281) workaround: some builds could fail to resume properly in a Jenkins installation with no anonymous read access, depending on how Jenkins was shut down.

### 2.4

Release date: 2016-05-25

* Adding another link to _Pipeline Syntax_ from a job configuration screen itself, to make it more prominent for Jenkins 2.x users without a sidebar.

### 2.3

Release date: 2016-05-23

* [JENKINS-31831](https://issues.jenkins-ci.org/browse/JENKINS-31831) Moving _Snippet Generator_ and related content to a new set of top-level pages under the label _Pipeline Syntax_.
* API implementation useful for [JENKINS-26107](https://issues.jenkins-ci.org/browse/JENKINS-26107).

### 2.2

Release date: 2016-05-02

* Enable the Groovy sandbox by default, even for administrators.
* Improved log appearance of block-scoped steps.
* [JENKINS-25894](https://issues.jenkins-ci.org/browse/JENKINS-25894) Better error reporting inside `parallel`.
* Flow node graph improvement for `load` step.
* [JENKINS-26156](https://issues.jenkins-ci.org/browse/JENKINS-26156) API problem in `BodyInvoker.withDisplayName`.

### 2.1

Release date: 2016-04-06

* [JENKINS-34064](https://issues.jenkins-ci.org/browse/JENKINS-34064) Fix of [JENKINS-26481](https://issues.jenkins-ci.org/browse/JENKINS-26481) reverted for now since it broke _all_ Pipeline scripts in Jenkins 2.0 betas.

### 2.0

Release date: 2016-04-05

* First release under per-plugin versioning scheme. See [1.x changelog](https://github.com/jenkinsci/workflow-plugin/blob/82e7defa37c05c5f004f1ba01c93df61ea7868a5/CHANGES.md) for earlier releases.
* [JENKINS-26481](https://issues.jenkins-ci.org/browse/JENKINS-26481) `list.each {…`} now works from Pipeline scripts (without `@NonCPS`).
* [JENKINS-27421](https://issues.jenkins-ci.org/browse/JENKINS-27421) `for (def x in list) {…`} now works from Pipeline scripts (without `@NonCPS`).
