
function handlePrototype(url) {
    buildFormTree(document.forms.config);
    // TODO JSON.stringify fails in some circumstances: https://gist.github.com/jglick/70ec4b15c1f628fdf2e9 due to Array.prototype.toJSON
    var json = Object.toJSON(JSON.parse(document.forms.config.elements.json.value).prototype);
    if (!json) {
        return; // just a separator
    }
    new Ajax.Request(url, {
        method: 'POST',
        parameters: {json: json},
        onSuccess: function(r) {
            document.getElementById('prototypeText').value = r.responseText;
        }
    });
}


document.addEventListener('DOMContentLoaded', (event) => {

    const generatePipelineScript = document.getElementById("generatePipelineScript");
    const url = generatePipelineScript.getAttribute("data-url");
    generatePipelineScript.onclick = (_) => {
        handlePrototype(url);
        return false;
    };

});
