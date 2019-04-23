var samples = [];

exports.addSamplesWidget = function(editor, editorId) {
    var $ = require('jqueryui-detached').getJQueryUI();

    if ($('#workflow-editor-wrapper .samples').length) {
        // Already there.
        return;
    }

    var $aceEditor = $('#' + editorId);
    var sampleSelect = $('<select></select>');
    
    sampleSelect.append('<option >try sample Pipeline...</option>');
    for (var i = 0; i < samples.length; i++) {
        sampleSelect.append('<option value="' + samples[i].name + '">' + samples[i].title + '</option>');
    }
    
    var samplesDiv = $('<div class="samples"></div>');
    samplesDiv.append(sampleSelect);

    samplesDiv.insertBefore($aceEditor);
    samplesDiv.css({
        'position': 'absolute',
        'right': '1px',
        'z-index': 100,
        'top': '1px'
    });

    sampleSelect.change(function() {
        var theSample = getSample(sampleSelect.val());
        editor.setValue(theSample, 1);
    });    
};

function getSample(sampleName) {
    for (var i = 0; i < samples.length; i++) {
        if (samples[i].name === sampleName) {
            return samples[i].script;
        }
    }
    return '';
}

samples.push({
    name: 'hello',
    title: 'Hello World',
    script:
        "node {\n" +
        "   echo 'Hello World'\n" +
        "}"
}); 

samples.push({
    name: 'github-maven',
    title: 'GitHub + Maven',
    script:
        "node {\n" +
        "   def mvnHome\n" +
        "   stage('Preparation') { // for display purposes\n" +
        "      // Get some code from a GitHub repository\n" +
        "      git 'https://github.com/jglick/simple-maven-project-with-tests.git'\n" +
        "      // Get the Maven tool.\n" +
        "      // ** NOTE: This 'M3' Maven tool must be configured\n" +
        "      // **       in the global configuration.           \n" +
        "      mvnHome = tool 'M3'\n" +
        "   }\n" +
        "   stage('Build') {\n" +
        "      // Run the maven build\n" +
        "      if (isUnix()) {\n" +
        "         sh \"'${mvnHome}/bin/mvn' -Dmaven.test.failure.ignore clean package\"\n" +
        "      } else {\n" +
        "         bat(/\"${mvnHome}\\bin\\mvn\" -Dmaven.test.failure.ignore clean package/)\n" +
        "      }\n" +
        "   }\n" +
        "   stage('Results') {\n" +
        "      junit '**/target/surefire-reports/TEST-*.xml'\n" + // assumes junit & workflow-basic-steps up to date
        "      archiveArtifacts 'target/*.jar'\n" +
        "   }\n" +
        "}"
});
