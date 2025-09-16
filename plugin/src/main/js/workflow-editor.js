import $ from 'jquery';
// Import the resizable module to allow the textarea to be expanded
import 'jquery-ui/ui/widgets/resizable';
import { addSamplesWidget } from './samples';

// Import ACE
import ace from "ace-builds/src-noconflict/ace";
import "ace-builds/src-noconflict/ext-language_tools";
import "ace-builds/src-noconflict/mode-groovy";
import "ace-builds/src-noconflict/snippets/javascript";
import "ace-builds/src-noconflict/ext-searchbox";

// Import custom snippets
import "./snippets/workflow";

var editorIdCounter = 0;

$(function() {
        $('.workflow-editor-wrapper').each(function() {
            initEditor($(this));
        });

        function initEditor(wrapper) {
            var textarea = $('textarea', wrapper);
            var aceContainer = $('.editor', wrapper);

            $('.textarea-handle', wrapper).remove();

            // The ACE Editor js expects the container element to have an id.
            // We generate one and add it.
            editorIdCounter++;
            var editorId = 'workflow-editor-' + editorIdCounter;
            aceContainer.attr('id', editorId);

            var editor = ace.edit(editorId);

            // Attach the ACE editor instance to the element. Useful for testing.
            var $wfEditor = $('#' + editorId);
            $wfEditor.get(0).aceEditor = editor;

            // https://stackoverflow.com/a/66923593
            var snippetManager = ace.require('ace/snippets').snippetManager;
            var snippetContent = ace.require('ace/snippets/groovy').snippetText;
            var snippets = snippetManager.parseSnippetFile(snippetContent);
            snippetManager.register(snippets, 'groovy');
            editor.session.setMode("ace/mode/groovy");
            editor.setAutoScrollEditorIntoView(true);
            editor.setOption("minLines", 20);
            // enable autocompletion and snippets
            editor.setOptions({
                enableBasicAutocompletion: true,
                enableSnippets: true,
                enableLiveAutocompletion: false
            });

            editor.setValue(textarea.val(), 1);
            // eslint-disable-next-line no-unused-vars
            editor.getSession().on('change', function(delta) {
                textarea.val(editor.getValue);
                showSamplesWidget();
            });

            editor.on('blur', function() {
                editor.session.clearAnnotations();
                var url = textarea.attr("checkUrl") + 'Compile';

                fetch(url, {
                    method: textarea.attr('checkMethod') || 'POST',
                    headers: crumb.wrap({  // eslint-disable-line no-undef
                            "Content-Type": "application/x-www-form-urlencoded",
                    }),
                    body: new URLSearchParams({
                        value: editor.getValue(),
                    }),
                }).then((rsp) => {
                    if (rsp.ok) {
                        rsp.json().then((json) => {
                            var annotations = [];
                            if (json.status && json.status === 'success') {
                                // Fire script approval check - only if the script is syntactically correct
                                textarea.trigger('change');
                                return;
                            } else {
                                // Syntax errors
                                $.each(json, function(i, value) {
                                    annotations.push({
                                        row: value.line - 1,
                                        column: value.column,
                                        text: value.message,
                                        type: 'error'
                                    });
                                });
                            }
                            editor.getSession().setAnnotations(annotations);
                        });
                    }
                });
            });

            function showSamplesWidget() {
                // If there's no workflow defined (e.g. on a new workflow), then
                // we add a samples widget to let the user select some samples that
                // can be used to get them going.
                if (editor.getValue() === '') {
                    addSamplesWidget(editor, editorId, aceContainer.attr('samplesUrl'));
                    editor.searchBox.hide();
                }
            }

            showSamplesWidget();

            // Make the editor resizable using jQuery UI resizable (http://api.jqueryui.com/resizable).
            // ACE Editor doesn't have this as a config option.
            $wfEditor.wrap('<div class="jquery-ui-1"></div>');
            $wfEditor.resizable({
                handles: "s", // Only allow vertical resize off the bottom/south border
                minHeight: 100,
                resize: function () {
                    // Use requestAnimationFrame to throttle resizes to happen before frame render
                    requestAnimationFrame(() => {
                        editor.resize();
                        // window.layoutUpdateCallback is defined in Jenkins core.
                        // call it to allow buttonbars that are fixed on the screen to be pushed down
                        // when the editor size is increased.
                        if (window.layoutUpdateCallback) {
                            window.layoutUpdateCallback.call();
                        }
                    })
                },
            });

            wrapper.show();
            textarea.hide();
        }
    }
);
