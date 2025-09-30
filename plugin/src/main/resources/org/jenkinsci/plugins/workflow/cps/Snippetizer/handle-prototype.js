function handlePrototype(url) {
    buildFormTree(document.forms.config);
    const json = JSON.stringify(JSON.parse(document.forms.config.elements.json.value).prototype);
    if (!json) {
        return; // just a separator
    }

    fetch(url, {
        method: "POST",
        headers: crumb.wrap({
            "Content-Type": "application/x-www-form-urlencoded"
        }),
        body: "json=" + encodeURIComponent(json),

    })
        .then(response => {
            if (response.ok) {
                response.text().then((responseText) => {
                    document.getElementById('prototypeText').value = responseText;
                    copybutton = document.querySelector('.jenkins-copy-button');
                    copybutton.setAttribute("text", responseText);
                    copybutton.classList.remove('jenkins-hidden');
                });
            }
        })
        .catch(error => {
            console.error('Fetch error:', error);
        });
}


document.addEventListener('DOMContentLoaded', () => {

    const generatePipelineScript = document.getElementById("generatePipelineScript");
    const url = generatePipelineScript.getAttribute("data-url");
    generatePipelineScript.onclick = (_) => {
        handlePrototype(url);
        return false;
    };

});