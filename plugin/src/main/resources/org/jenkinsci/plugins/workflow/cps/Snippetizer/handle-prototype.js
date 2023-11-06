function handlePrototype(url, crumb) {
    buildFormTree(document.forms.config);
    // TODO JSON.stringify fails in some circumstances: https://gist.github.com/jglick/70ec4b15c1f628fdf2e9 due to Array.prototype.toJSON
    // TODO simplify when Prototype.js is removed
    const json = Object.toJSON ? Object.toJSON(JSON.parse(document.forms.config.elements.json.value).prototype) : JSON.stringify(JSON.parse(document.forms.config.elements.json.value).prototype);
    if (!json) {
        return; // just a separator
    }

    const xhr = new XMLHttpRequest();
    xhr.open("POST", url, true);
    xhr.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
    xhr.setRequestHeader("Jenkins-Crumb", crumb);

    xhr.onreadystatechange = function() {
        if (xhr.readyState === XMLHttpRequest.DONE) {
            if (xhr.status === 200) {
                document.getElementById('prototypeText').value = xhr.responseText;
            }
        }
    };

    xhr.send("json=" + encodeURIComponent(json));
}


document.addEventListener('DOMContentLoaded', () => {

    const generatePipelineScript = document.getElementById("generatePipelineScript");
    const url = generatePipelineScript.getAttribute("data-url");
    const crumb = generatePipelineScript.getAttribute("data-crumb");
    generatePipelineScript.onclick = (_) => {
        handlePrototype(url, crumb);
        return false;
    };

});