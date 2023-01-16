# The Pipeline Persistence Model

# Data Model
Running pipelines persist in 3 pieces:

1. The `FlowNode`s - stored by a `FlowNodeStorage` - this holds the FlowNodes created to map to `Step`s, and for block scoped Steps, start and end of blocks
2. The `CpsFlowExecution` - this is currently stored in the WorkflowRun, and the primary pieces of interest are:
    * heads - the current "tips" of the Flow Graph, i.e. the FlowNodes that represent running steps and are appended to
        - A head maps to a `CpsThread` in the Pipeline program, within the `CpsThreadGroup`
    * starts - the `BlockStartNode`s marking the start(s) of the currently executing blocks
    * scripts - the loaded Pipeline script files (text)
    * persistedClean
        - If true, Pipeline saved its execution cleanly to disk and we *might* be able to resume it
        - If false, something went wrong saving the execution, so we cannot resume even if we'd otherwise be able to
        - If null, probably the build dates back to before this field was added - we check to see if this is running in a highly persistent DurabilityMode (Max_survivability generally)
    * done - if true, this execution completed, if false or un-set, the pipeline is a candidate to resume unless its only head is a FlowEndNode
        - The handling of false is for legacy reasons, since it was only recently made persistent.
    * various other boolean flags & settings for the execution (durability setting, user that started the build, is it sandboxed, etc)
3. The Program -- this is the current execution state of the Pipeline
    * This holds the Groovy state - the `CpsThreadGroup` - with runtime calls transformed by CPS so they can persist
        * The `CpsThread`s map to the running branches of the Pipeline
    * The program depends on the FlowNodes from the FlowNodeStorage, since it reads them by ID rather than storing them in the program
    * This also depends on the heads in the CpsFlowExecution, because its FlowHeads are loaded from the heads of the CpsFlowExecution
    * Also holds the CpsStepContext, i.e. the variables such as EnvVars, Executor and Workspace uses (the latter stored as Pickles)
        - The pickles will be specially restored when the Pipeline resumes since they don't serialize/deserialize normally

## Persistence Issues And Logic

Some basic rules:

1. If the FlowNodeStorage is corrupt, incomplete, or un-persisted, various things will break
    - The pipeline can never be resumed (the key piece is missing)
    - Usually we fake up some placeholder FlowNodes to cover this situation and save them
2. Whenever persisting data, the Pipeline *must* have the FlowNodes persisted on disk (via `storage.flush()` generally)
in order to be able to load the heads and restore the program.
3. Once we've set persistedClean as false and saved the FlowExecution, then it doesn't matter what we do -- the Pipeline will assume
 it already has incomplete persistence data (as with 1) when trying to resume.  This is how we handle the low-durability modes, to
  avoid resuming a stale state of the Pipeline simply because we have old data persisted and are not updating it.
