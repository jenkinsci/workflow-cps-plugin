# Basics of Continuation Passing Style

[Continuation Passing Style](https://en.wikipedia.org/wiki/Continuation-passing_style) (CPS) is a style of programming
in which the remainder of the program is passed explicitly as a parameter, as opposed to that being handled implicitly
represented as call stack.

Consider the following function written in a normal manner:

    int factorial(int n) {
        return n*factorial(n-1);
    }

The CPS version of it, in pseudo code by using Java8 lambda syntax, would be something like this:

    // CPS function never returns value.
    // Instead it yields a value to the coninuation, which is a function that takes a value  
    void factorial(int n, Lambda/*(int)->{}*/ continuation) {
        // to compute factorial of n, compute factorial of n-1 first, then when that's done,
        // come back to this lambda, where we multiply the result by n, then pass that result
        // to 'continuation'
        factorial(n-1,(int r) -> {
            continuation(n*r)
        })
    }

CPS functions will never return. Instead of returning a value, it jumps to (basically `goto`) the
continuation with the value.

Another way to look at this is that a continuation is a call stack --- it represents where to jump
when a 'return' statement is reached, where the exception handler for `IOException` is, as well as
local variables, etc.

## Chaining CPS calls
Because CPS functions will never return, expressions will never nest in CPS.  Instead, they'll get
flattened out.

In the above example, I cheated a little by using `n-1` and `n*r` as a primitive.
If you really think about it, it's just a short-hand for a function call, say
`int minus(int,int)` and `int multiply(int,int)`. If we translate those to the CPS form as well,
the resulting code will be like this, which shows this chaining well:

    void factorial(int n, Lambda/*(int)->{}*/ continuation) {
        // first we compute n-1 => m
        minus(n,1,(int m) -> {
            // then we compute factorial(m) => r
            factorial(m,(int r) -> {
                // then we compute r*n => x
                multiply(r,n,(int x) -> {
                    // and that's the result of factorial(n), so pass the control to 'continuation'
                    continuation(x);
                })
            })
        })
    }

As you see, each step performs one atomic operation (one function call), and they are chained into a sequence.

## Control flow
In CPS, control flow statements like loops can be represented as ordinary functions:

    /*
        while (condition) {
            body
        }
        
        ... // rest of the program represented as 'continuation'
    */
    void whileLoop(Lambda condition, Lambda body, Lambda continuation) {
        // first evaluate the condition => cond
        condition((boolean cond) -> {
            if (!cond) {
                // if the condition evaluates to false, while loop terminates and the execution moves
                // on to the rest of the program
                continuation();
            } else {
                // if the condition evalutes to true, evaluate the body
                body(() -> {
                    // then run the while loop again
                    whileLoop(condition,body,continuation);
                })
            }
        })
    }

