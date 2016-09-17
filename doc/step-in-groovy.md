# Implementing Step in Groovy
The standard way of implementing a step is by writing Java code that extends from `Step` and `StepDescriptor`,
but you can also define a step in Jenkins Pipeline Script.

This approach is quite convenient when your step composes other
existing steps. For example, the following files define
the equivalent of the `retry` step that repeats the body up to several times:

```java
// src/main/java/acme/RetryStep.java
package acme;

class RetryStep extends GroovyStep {
    private int times;

    @DataBoundConstructor
    public RetryStep(int times) {
        this.times = times;
    }

    public int getTimes() {
        return times;
    }

    @Extension
    public static class DescriptorImpl extends GroovyStepDescriptor {
        @Override public String getFunctionName() {
	        return "myRetryClone";
        }

        @Override public String getDisplayName() {
	        return "Retry step";
        }
    }
}
```

```groovy
// src/main/resources/acme/RetryStepExecution.groovy
package acme;

public class RetryStepExecution extends GroovyStepExecution {
    public void call(Closure body) {
        int attempt = 0;
        while (true) {
            try {
                echo "trying ${attempt}"
                body();
                return;
            } catch (Exception e) {
                if (attempt++ < step.times) {
                    continue; // retry
                } else {
                    throw e;
                }
            }
        }
    }
}
```

From Jenkins Pipeline, you can use this like you'd use any other steps:

```
myRetryClone(3) {
  sh './flakyTest.sh'
}
```

Note the directory in which the files are defined. `GroovyStep` and `GroovyStepDescriptor`
are defined in `src/main/java` and is written just like any other steps.
`GroovyStepExecution` is defined in `src/main/resources` to be packaged into the plugin
in the source form, so that it can run in the CPS groovy interpreter at the runtime.

The `call` method on `GroovyStepExecution` subtype defines the actual logic of step.
If the logic is complex, other methods can be defined on this class and all those methods
can invoke other steps just like the `call` method can.

`GroovyStepExecution` can access parameters given to `GroovyStep` via the `getStep()` method.
Note that the actual `GroovyStep` instance is not stored, but rather its invocation form.
IOW, any additional fields you define in `GroovyStep` will not be persisted. Only the parameters
users have given in Pipeline Script will. To make plugin developers aware of this behavior,
the `getStep()` method intentionally returns a fresh instance for each invocation.

## Groovy steps and sandbox
`GroovyStepExecution` subtypes are trusted,
so it is not subject to the sandbox execution environment nor the script approval process.
IOW, it can invoke any Java code.

User-written untrusted `Jenkinsfile` can in turn access `GroovyStepExecution`,
and this can even ignore Java access control rules like private fields.

If you have sensitive fields that you need to protect from malicious `Jenkinsfile`s,
define a subtype of `GroovyStepExecution` in Java and put those fields there,
then further subtype that in Groovy and access those fields.
`GroovyStepExecution` subtypes are trusted, so they can access the fields
defined in Java code, but untrusted `Jenkinsfile` will not be able to.
