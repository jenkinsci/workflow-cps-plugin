/**
 * @author Kohsuke Kawaguchi
 */
public class Foo {
    public static class Base {

    }

    public static class Derived extends Base {
        public void foo() {
            System.out.println(super.toString());
        }

        @Override
        public String toString() {
            return "derived";
        }
    }

    public static void main(String[] args) {
        new Derived().foo();
    }
}
