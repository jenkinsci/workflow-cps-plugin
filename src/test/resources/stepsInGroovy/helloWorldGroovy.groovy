String getDisplayName() {
    return 'This is a step written in Groovy'
}

void call(String message) {
    echo "Hello ${message}";
}