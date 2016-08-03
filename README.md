Groovy interpreter that runs Groovy code in the continuation passing style, so that the execution can be 
paused any moment and restarted without cooperation from the program being interpreted.

See the [Pipeline Groovy plugin for Jenkins](https://github.com/jenkinsci/workflow-cps-plugin) for the main use case.

* [Basics of CPS](doc/cps-basics.md)
* [Continuation, Next, and Env](doc/cps-model.md) and how we interpret Groovy program
* [How interpreted program is represented](doc/block-tree.md)
* [CPS + Sandbox](doc/sandbox.md)