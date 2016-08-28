# Implementing Step in Groovy
The standard way of implementing a step is by writing Java code that extends from `Step` and `StepDescriptor`,
but you can also define a step in Jenkins Pipeline Script.

This approach is quite convenient when your step composes other
existing steps. For example, the following `hello.groovy` defines
a step that prints a greeting message to log:

```
String getDisplayName() {
    return 'Greets someone'
}

void call(String person) {
    echo "Hello ${person}";
}
```

From Jenkins Pipeline you can use this like you'd use any other steps:

```
hello 'Duke'    // this will print 'Hello Duke'
```

`src/main/webapp/WEB-INF/steps` is the content root for Groovy files
to be run by the CPS interpreter (aka Pipeline classpath). You can create regular package
structures in this content root directory. For example, the above
helloWorld.groovy could be in `src/main/webapp/WEB-INF/steps/org/kohsuke/jenkins/helloWorld.groovy`

It is also possible to put other supporting classes in this content root directory.

These files will be packaged into the plugin as source files, then
interpreted at runtime together with user's `Jenkinsfile`.

## Details
To define a step in Groovy, the following requirements have to be met:

* There must be a public default constructor.
* There must be a `call()` method that takes parameters that the step needs.
  This can include a `Closure` parameter, but it has to be the last parameter.
* There must be a `getDisplayName` method that returns a short human readable name
  of your step. See `Descriptor#getDisplayName()` for more about it.
* There must be `src/main/webapp/WEB-INF/steps/META-INF/index` that lists fully-qualified class names
  of all the steps implemented in Groovy. This is how the runtime discovers all the available steps.

## Groovy steps and sandbox
Groovy code in Pipeline classpath are trusted,
so it is not subject to the sandbox execution environment nor the script approval process.
This means code in Pipeline classpath can invoke any Java code.

User-written `Jenkinsfile` can in turn access any code in Pipeline classpath,
subject to the normal Java access restriction.

This means a care must be taken to ensure that your code
does not expose any methods and properties that can be exploited by malicious `Jenkinsfile`.
