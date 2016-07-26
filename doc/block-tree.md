# How interpreted program is represented
In [earlier doc](cps-model.md), we saw that `Block` represents a
program/code to be run.

In groovy-cps, there's `Block` implementations that correspond
1:1 to most Groovy AST nodes that are expressions (such as binary operators,
function calls, variable access) and statements (such as if statement, for loop,
and return statement.) There's no `Block` impls for class/method/field declarations
because those are not executable.

Just like AST nodes, `Block` forms a tree structure so that
any complex program can be represented. For example, representing `!f(3)`
requires 4 block nodes. In pseudo code, it would be the following:

    new NotBlock(new FunctionCallBlock(new ConstantBlock("f"),new ConstantBlock(3)))

To make it easier to construct a tree of `Block`s, there's the `Builder`
class. This defines a number of convenience methods as well as handling
details like source location.

## How a Block tree gets executed
Let's see how the above `!f(3)` gets executed by the interpreter 
main loop:

    Next n = <<program to execute>>
    while(true) {
        n = <<evaluate n.block with n.continuation>>
    }

First, `n` is the whole program and continuation `c` is `Continuation.HALT`
(i.e., "when the program yields a value, end the interpretation")

    n     = NotBlock(FunctionCallBlock(constantBlock("f"),constantBlock(3)))
    c1(x) = halt(x)

Next, `NotBlock` is evaluated, which says "evaluate the nested expression
and when it yields a value, negate the value and pass it to `c`". Thus,

    n     = FunctionCallBlock(constantBlock("f"),constantBlock(3))
    c2(x) = c1(!x) = halt(!x)

Next, `FunctionCallBlock` is evaluated, which first says
"evaluate the expression of the function name and when it yields a value,
hold on to the resulting value and evaluate arguments":

    n     = constantBlock("f")
    c3(x) = <<evaluate rest of functionCall>>, followed by c2(x)

Next, `constantBlock("f")` is evaluated, which just passes `"f"` to `c3`
which gets the rest of `FunctionCallBlock`, which is to evaluate arguments.

    constantBlock("f").eval(c3)
    -> c3("f")
    -> n     = constantBlock(3)
       c4(x) = <<evaluate rest of functionCall>>, followed by c2(x)

Next, `constantBlock(3)` is evaluated, which just passes `3` to `c4`.
At this point, we are ready to invoke a function. 

    constantBlock(3).eval(c4)
    -> c4(3)
    -> <<evaluate f(3)>>, followed by c2(x)

Let's say `f(3)` is a function call defined in JDK like this, which is not interpreted:

    public static boolean f(int x) {
        return x>0;
    }

This function call will happen atomically from the perspective of groovy-cps,
and it yields `true`. This value is passed to `c2`, which eventually evaluate
to `halt(true)` and the program halts with the final value `true`:

    ...
    -> <<evaluate f(3)>>, followed by c2(x)
    -> c2(true)
    -> c1(!true)
    -> halt(false)


## How does generated code look like?
TBD
