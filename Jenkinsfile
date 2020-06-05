// Ideally we would test against Java 11 in at least one configuration, but
// groovy-cps-dgm-builder cannot run on Java 9+, so groovy-cps cannot be built
// on Java 9+. See the note in pom.xml.
buildPlugin(useAci: false, configurations: [
  [ platform: "linux", jdk: "8" ],
  [ platform: "windows", jdk: "8" ],
  [ platform: "linux", jdk: "8", jenkins: "2.222.3" ]
])
