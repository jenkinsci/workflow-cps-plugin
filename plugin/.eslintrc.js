module.exports = {
    // When releasing with maven, all the repo is copied again in target/checkout and build and tested there
    // eslint by default loads any configuration in ancestor directories, so it founds the configuration in . and in ./target/checkout and fails
    // With this property we are telling it to not load other config files from ancestor directories 
    root: true,
    env: {
        browser: true,
        es6: true
    },
    // Uses eslint default ruleset
    extends: "eslint:recommended",
    parserOptions: {
        ecmaVersion: 2018,
        sourceType: "module"
    },
    rules: {
    },
    globals: {
        Atomics: "readonly",
        SharedArrayBuffer: "readonly",

        '__dirname': false,

        // Allow jest globals used in tests
        jest: false,
        expect: false,
        it: false,
        describe: false,
        beforeEach: false,
        afterEach: false,
        beforeAll: false,
        afterAll: false,
    }
  }; 

