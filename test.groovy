def config(c) {
    def map = [:];
    map.inc = { x -> x+1 }
    c.resolveStrategy = Closure.DELEGATE_FIRST;
    c.delegate = map;
    c();
    return map;
}

def x = config {
    foo = 3
    fog = containsKey('foo');
}

println(x.fog);
