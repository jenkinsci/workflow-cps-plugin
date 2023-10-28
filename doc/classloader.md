# Class loading for CPS Groovy execution
Pipeline Script needs to be loaded into JVM for it to run. This is done
by a `GroovyClassLoader` created inside `CpsGroovyShell`.

We create two classloaders in the following hierarchy

    <<Jenkins UberClassLoader>> <-- <<trusted classloader>> <-- <<regular classloader>>

Jenkins uber classloader (`PluginManager.uberClassLoader`) defines
visibility to every code in every plugins, so that every domain model
of Jenkins can be accessed. This is the starting point of the hierarchy.

"Trusted classloader" (TCL) and "regular classloader" (RCL) are both
`GroovyClassLoader`. Groovy compiler associated with them are configured for Jenkins Pipeline.
For example, scripts loaded by those are CPS transformed to run with groovy-cps.
See `CpsGroovyShellFactory` for more about the customizations that happen here.

Additionally, scripts loaded in RCL lives in the security sandbox
(unless the user opts out of it, in which case it requires approval.)
This classloader is meant to be used to load user-written Groovy scripts,
which is not trusted.

Scripts loaded in TCL, OTOH, does not live in the security sandbox. This
classloader is meant to be used to load Groovy code packaged inside
plugins and global libraries. Write access to these sources should be
restricted to `ADMINISTER` permission.

## Persisting code & surviving restarts
When a Groovy script is loaded via one of `GroovyShell.parse*()` and
`eval*()` methods, script text is captured and persisted as a part
of the program state. This ensures that the exact same code is available
when a pipeline execution resumes execution after a JVM restart.

This behaviour is desirable for the situation where script is supplied
from outside and not readily available at the point of restart.
For example, `Jenkinsfile` and scripts loaded from the `load` step
fits this description.

It's also possible to augument `GroovyClassLoader` classpath via
`addURL()` so that scripts are loaded on-demand whenever needed.
This is more suitable for scripts that are considered a part of the
system, such as global libraries or plugin code.

Those scripts are not snapshotted & persisted with the running program,
so if they change while the program is running, with or without Jenkins restarts
in the middle, then the program could fail. (For example, if a program
call stack includes a class from a global library and that class goes away,
then the program fails to survive the restart because the call stack cannot
be deserialized.
