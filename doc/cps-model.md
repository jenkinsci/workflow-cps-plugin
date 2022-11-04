# How Groovy-CPS models CPS in Java
In [CPS basics](cps-basics.md), we used the Java8 lambda notation to illustrate CPS translation/execution
of the program as pseudo code.
 Unfortunately we cannot actually model groovy-cps like that, because in Java we cannot write a function that
doesn't return. That pseudo code will very quickly results in `StackOverflowError`.

So in groovy-cps, we use `Next`, `Continuation`, and `Block` to model this.

In pseudo-code, a continuation was a lambda of the form `(v)->{}`. In groovy-cps, `Continuation` interface
represents a continuation. Here, instead of encapsulating the whole remaining program as single lambda,
it encapsulates just the tiny logic of "receive the return value of a call and decide what to do next",
then return "what to do next" as a `Next` object

How does `Next` captures "what to do next"? `Next` is a pair of ...

* `Block`, which represents a program (say, a function call)
* `Continuation`, which represents how to process the value obtained by evaluating `Block` 

Thus the way the execution goes is like:

1. a `Block` starts execution, which (very quickly) returns a `Next`
2. We get `Block` and `Continuation` from the `Next` previous step produced
3. We start evaluating this new `Block` by giving it this new `Continuation`, which (very quickly) returns a `Next`
4. We get `Block` and `Continuation` from the `Next` previous step produced
3. We start evaluating this new `Block` by giving it this new `Continuation`, which (very quickly) returns a `Next`
5. ... repeat ...

Thus, the "interpreter" main loop is as follows. In groovy-cps, this code is actually in `Next.run()`:

    Next n = <<program to execute>>
    while(true) {
        n = <<evaluate n.block with n.continuation>>
    }
    
Ths main loop is a little bit like CPU instruction cycle. Fetch next instruction from [Program Counter](https://en.wikipedia.org/wiki/Program_counter),
execute that one instruction, and repeat that forever. Each instruction is atomic.

## Environment
We need one more thing to run programs, which is "environments". If you mentally picture how Java program
gets executed for a moment, then an "environment" would be everything that's in the call stack &mdash; 
local variables, where the 'return' statement jumps to, and exception handlers.

For example, imagine a program `println(x)` which loads a local variable `x` and print that value.
To actually run this program, something needs to tell us that the value of local variable 'x' is, say, 42.

If the groovy-cps is a [8086 CPU](https://en.wikipedia.org/wiki/Intel_8086#Registers_and_instructions),
environment is [Stack Pointer (SP)](https://en.wikipedia.org/wiki/Stack_register).

In groovy-cps, `Env` interface represents the environment.

`Env` extends our simple model of `Next`, `Block`, and `Continuation` above. Whenever we evaluate a `Block`,
we need accompanying `Env`, thus `Next` includes `Env`, and the `Block.eval` method takes both `Env` and
`Continuation`. Our "interpreter" main loop is now as follows:

    Next n = <<program to execute>>
    while(true) {
        n = <<evaluate n.block with n.continuation and n.env>>
    }
 

## Pausing and resuming execution
If we stop the interpreter main loop in a middle of an execution, we can pause the execution of this program,
regardless of what the program is doing at that moment. We can later come back to the loop again to resume
the program from where we left off.

    Next n = <<program to execute>>
    
    // let's run a program for a bit
    for (int i=0; i<100; i++) {
        n = <<evaluate n.block with n.continuation and n.env>>
    }
    
    // let's take a break
    makeSomeTea();
    
    // let's run a program for some more
    for (int i=0; i<100; i++) {
        n = <<evaluate n.block with n.continuation and n.env>>
    }
    
    // let's take a break
    bioBreak();
    
    ...

In practice it is usually more useful if a program being interpreted can request that the execution be suspended,
instead of the code above where a program is paused after executing 100 instructions. This way the interpreted program
can pause in a safe state, and it can also report a value to the code that's executing the interpreter loop.

    Next n = << parse the following program & prepare the starting point into 'n'
    
        int x = <<compute answer to life the universe and everything>>
        
        // suspend the execution of the program by reporting 42
        int r = suspend(x) 
        // when the interpreter loop resumes, the program gets going again from here
        // by picking up the value we received from there
        
        println(r);
    >>

    while(!interpreted_program_called_suspend()) {
        n = <<evaluate n.block with n.continuation and n.env>>
    }
    
    int answer = <<value given to the suspend() method>>

    // answer should be 42
    // 'n' represents the paused interpreted program inside the suspend() call 
    
    
    // modify n.env so that when the program resumes suspend() returns a value we want
    n.env.setReturnValueFromSuspendTo("Good job!")
    
    // resume the program and now it will print "Good job!"
    while(true) {
        n = <<evaluate n.block with n.continuation and n.env>>
    }

In actual groovy-cps, `Object Continuable.suspend(Object)` plays the role of the pseudo "suspend" function above,
and `Continuable.run()` is the interpreter loop. It resumes a previously paused program by supplying
the value to be returned from the `suspend()` call, then the `run()` method itself returns when
the next `suspend()` call takes place. Those two functions are in the ying-and-yang relationship.
Ying's parameter value is Yang's return value, and vice versa.

groovy-cps takes this resumability one step further by making `Next` serializable, which allows the program state
to live beyond a life of a JVM. This is the foundation of the resumability in Jenkins Pipeline.

## Calling a function when we don't know if it's CPS or not
Interpreted program often wants to call other functions, and how we want to run that function depends
on whether that function is also interpreted or not.

Consider the following program being interpreted:

    // this Comparable is interpreted 
    Comparable x = new Comparable() {
        int compareTo(o) {
            if (x > 42)     return 1;
            if (x == 42)    return 0;
            return -1;
        }
    };
    
    // this comparable is from JDK & non-interpreted
    Comparable y = new Integer(5);
    
    def z = randomBoolean() ? x : y
    z.compareTo(30);

If a random boolean picks `x`, then `z.compareTo(30)` call should be run in an interpreter loop in CPS,
for example so that the `suspend()` call inside, if any, will correctly suspend the program.

But if a random boolean picks `y`, then `z.compareTo(30)` should run like JVM runs it normally and produce -1,
From the interpreter loop's perspective this entire call will happen in one step, and thus we cannot pause
the execution in-between, but that's OK because such function call will never contains `suspend()` call to request
a pause.

At the call site of `z.compareTo(30)`, how do we determine whether this method is interpreted?

We do this by actually invoking a function. If a method is non-interpreted, the method will execute normally
and returns a value (or throws an exception if something bad happens.) If a method is interpreted, however,
we generate this method in such a way that it immediately throws `CpsCallableInvocation`. If the call site
receives this specific type of exception, then it knows that it just attempted to invoke an interpreted function,
and so it behaves accordingly. `ContinuationGroup.methodCall()` is the part of the interpreter that acts as
a call site.