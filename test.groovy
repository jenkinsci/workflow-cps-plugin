
def x() {
    println "eval x"
    return new int[1]
}

def y() {
    println "eval y"
    return 0;
}

def z() {
    println "eval z"
    return 1
}

x()[y()] = z()

