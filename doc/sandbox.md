# Sandboxing CPS execution
In Jenkins, we want to combine this groovy-cps interpreter with [Groovy sandbox](http://groovy-sandbox.kohsuke.org/),
so that not only the program gets interpreted (the CPS part) but every method call and property access is also subject to
access control (the sandbox part.)

Furthermore, sometimes it is desirable to mix some untrusted code (say the script users write) with
some trusted code (for example ones that define API and libraries used by users), and execute the whole thing
in CPS.

To do this, groovy-cps has some optional components (`SandboxInvoker` and `SandboxCpsTransformer`) that combines
Groovy sandbox with groovy-cps.

# How to use it
Use `SandboxCpsTransformer` instead of `CpsTransformer` to translate the source code, then use `SandboxInvoker`
instead of `DefaultInvoker` during runtime to perform checks.

To mix trusted code and untrusted code, use two `GroovyShell`, one with `SandboxCpsTransformer` and the other with
`CpsTransformer`. This requires that you use two classloaders. Then execute the whole thing with `SandboxInvoker`.
In this way, the portion of the code compiled with `CpsTransformer` will run without security check, while the
other portion compiled with `SandboxCpsTransformer` will be sandboxed.

# How this works

## Tagging call site
As `CpsTransformer` translates the program, it adds some metadata to each AST node indicating
whether the code is trusted or not, wherever it calls methods or access properties. The idea
is that this will designate whether or not this invocation should be checked for security (untrusted),
or the invocation should be always allowed to happen (trusted.)

The implication of this is that, if you compile a function with the trusted tag, the function is
now responsible for making sure that it will never get tricked into allowing untrusted caller to
invoke something on its behalf that it's not supposed to. This is the same basic rule of thumb
for writing a code in `PrivilegedAction`. For example, the following code is not OK because
it allows untrusted code to access any environment variable:

```
def foo(name) {// imagine this function compiled with a trusted tag
  return System.getenv(name)
}
```

The following code is OK because arguments from the untrusted code cannot control where a file gets written:

```
def foo(value) {// imagine this function compiled with a trusted tag
  File.createTempFile("foo","tmp").text = value
}
```

The call site tagging mechanism itself is more general, so it can be used for other purposes,
for example to record where it came from, etc.

## Runtime check
During the interpretation, method calls, property access, and other such things will go through `Invoker`.
The invoker will receive call site tags that are added by `CpsTransformer`, including whether or not
this invocation is trusted (see `Invoker.contextualize()`).

If the client application has used `SandboxInvoker` instead of `DefaultInvoker`, it'll honor this trusted tag and
perform access control appropriately.
