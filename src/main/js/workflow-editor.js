import $ from 'jquery';
import jenkinsJSModules from 'jenkins-js-modules';
// Polyfill for window.requestAnimationFrame
import 'raf/polyfill';
// Import the resizable module to allow the textarea to be expanded
import 'jquery-ui/ui/widgets/resizable';
import { addSamplesWidget } from './samples';

var editorIdCounter = 0;

// The Jenkins 'ace-editor:ace-editor-122' plugin doesn't support a synchronous
// require option. This is because of how the ACE editor is written. So, we need
// to use lower level jenkins-js-modules async 'import' to get a handle on a
// specific version of ACE, from which we create an editor instance for workflow.
jenkinsJSModules.import('ace-editor:ace-editor-122')
    .onFulfilled(function (acePack) {
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

            // The 'ace-editor:ace-editor-122' plugin supplies an "ACEPack" object.
            // ACEPack understands the hardwired async nature of the ACE impl and so
            // provides some async ACE script loading functions.

            acePack.edit(editorId, function() {
                var ace = acePack.ace;
                var editor = this.editor;

                // Attach the ACE editor instance to the element. Useful for testing.
                var $wfEditor = $('#' + editorId);
                $wfEditor.get(0).aceEditor = editor;

                acePack.addPackOverride('snippets/groovy.js', '../workflow-cps/snippets/workflow.js');

                acePack.addScript('ext-language_tools.js', function() {
                    ace.require("ace/ext/language_tools");

                    editor.$blockScrolling = Infinity;
                    editor.session.setMode("ace/mode/groovy");
                    editor.setTheme("ace/theme/tomorrow");
                    editor.setAutoScrollEditorIntoView(true);
                    editor.setOption("minLines", 20);
                    // enable autocompletion and snippets
                    editor.setOptions({
                        enableBasicAutocompletion: true,
                        enableSnippets: true,
                        enableLiveAutocompletion: false
                    });

                    editor.setValue(textarea.val(), 1);
                    editor.getSession().on('change', function() {
                        textarea.val(editor.getValue());
                        showSamplesWidget();
                    });

                    editor.on('blur', function() {
                        editor.session.clearAnnotations();
                        var url = textarea.attr("checkUrl") + 'Compile';


                        // eslint-disable-next-line no-undef
                        new Ajax.Request(url, { // jshint ignore:line
                            method: textarea.attr('checkMethod') || 'POST',
                            parameters: {
                                value: editor.getValue()
                            },
                            onSuccess : function(data) {
                                var json = data.responseJSON;
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
                            }
                        });
                    });

                    function showSamplesWidget() {
                        // If there's no workflow defined (e.g. on a new workflow), then
                        // we add a samples widget to let the user select some samples that
                        // can be used to get them going.
                        if (editor.getValue() === '') {
                            addSamplesWidget(editor, editorId, aceContainer.attr('samplesUrl'));
                        }
                    }
                    showSamplesWidget();
                });

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
            });

            wrapper.show();
            textarea.hide();
        }
    });
