// playground to check semantics of Groovy code

class foo {
    public static void main(String[] args) {
        new foo().x();
    }

    public void x() {
        runClosureWith(new bar()) {
            println y();
            println prop;

            println this; // inside the closure 'this' gets resolved as getProperty('this') so it doesn't point to the closure object itself
            println this.y();
            println this.prop;
        }
    }

    public static void runClosureWith(Object delegate, Closure c) {
        c.setDelegate(delegate);
        c.setResolveStrategy(Closure.DELEGATE_FIRST)
        c.run();
    }

    public String y() {
        return "foo";
    }

    public String prop = "foo";

    public static class bar {
        public String prop = "foo";
        public String y() {
            return "bar";
        }
    }
}