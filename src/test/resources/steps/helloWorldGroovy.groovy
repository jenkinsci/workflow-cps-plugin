def getDisplayName() {
    return 'This is a step written in Groovy'
}

def call(String message) {
    echo "Hello ${message}";
}