node {
    stage('Preparation') { // for display purposes
        // Get some code from a GitHub repository
        git 'https://github.com/jglick/simple-maven-project-with-tests.git'
    }
    stage('Build') {
        // Run the build. You must have Maven installed.
        if (isUnix()) {
            sh 'mvn -Dmaven.test.failure.ignore clean package'
        } else {
            bat 'mvn -Dmaven.test.failure.ignore clean package'
        }
    }
    stage('Results') {
        junit '**/target/surefire-reports/TEST-*.xml'
        archiveArtifacts 'target/*.jar'
    }
}
