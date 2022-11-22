import $ from 'jquery';

export function addSamplesWidget(editor, editorId, samplesUrl) {
    var samples = [];

    if ($('#workflow-editor-wrapper .samples').length) {
        // Already there.
        return;
    }

    var $aceEditor = $('#' + editorId);
    var sampleSelect = $('<select></select>');

    sampleSelect.append('<option >try sample Pipeline...</option>');
    // eslint-disable-next-line no-undef
    new Ajax.Request(samplesUrl, {
        onSuccess : function(data) {
            samples = data.responseJSON.samples;
            for (var i = 0; i < samples.length; i++) {
                sampleSelect.append('<option value="' + samples[i].name + '">' + samples[i].title + '</option>');
            }
        }
    });

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
        var theSample = getSample(sampleSelect.val(), samples);
        editor.setValue(theSample, 1);
    });
}

function getSample(sampleName, samples) {
    for (var i = 0; i < samples.length; i++) {
        if (samples[i].name === sampleName) {
            return samples[i].script;
        }
    }
    return '';
}
