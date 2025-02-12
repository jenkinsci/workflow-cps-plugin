package org.jenkinsci.plugins.workflow.cps.Snippetizer


import org.jenkinsci.plugins.workflow.cps.GlobalVariable
import org.jenkinsci.plugins.workflow.cps.Snippetizer

Snippetizer snippetizer = my;

def l = namespace(lib.LayoutTagLib)
def st = namespace("jelly:stapler")

l.layout(title:_("Pipeline Syntax: Global Variable Reference"), norefresh: true) {
    st.include(page: 'sidepanel')
    l.main_panel {

      h1(_("Overview"))
      st.include(page: 'globalsHelp')

      div(class:'dsl-reference'){
        h1(_("Global Variable Reference"))

        div(class:'steps-box variables'){

          h2(_("Variables"))
          dl(class:'steps variables root'){
            for (GlobalVariable v : snippetizer.getGlobalVariables()) {
              dt(id: v.getName()) {
                a(href: '#' + v.getName()) {
                  code(v.getName())
                }
              }
              dd{
                def rd = request2.getView(v, "help");
                div(class:"help", style:"display: block") {
                  if (rd != null) {
                    st.include(page: "help", it: v)
                  } else {
                    raw(_("(no help)"))
                  }
                }
              }
            }
          }
        }
      }
    }
}
