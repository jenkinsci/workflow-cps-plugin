/*
 See the documentation for more options:

https://github.com/jenkins-infra/pipeline-library/

*/

@Library('pipeline-library@pull/883/head') _

buildPlugin(
  useContainerAgent: true, // Set to `false` if you need to use Docker for containerized tests
  configurations: [
    [platform: 'linux', jdk: 21],
    [platform: 'windows', jdk: 17],
])
