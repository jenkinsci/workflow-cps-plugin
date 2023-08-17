package org.jenkinsci.plugins.workflow.cps.Snippetizer

import org.jenkinsci.plugins.structs.SymbolLookup
import org.jenkinsci.plugins.structs.describable.ArrayType
import org.jenkinsci.plugins.structs.describable.AtomicType
import org.jenkinsci.plugins.structs.describable.DescribableModel
import org.jenkinsci.plugins.structs.describable.DescribableParameter
import org.jenkinsci.plugins.structs.describable.EnumType
import org.jenkinsci.plugins.structs.describable.ErrorType
import org.jenkinsci.plugins.structs.describable.HeterogeneousObjectType
import org.jenkinsci.plugins.structs.describable.HomogeneousObjectType
import org.jenkinsci.plugins.structs.describable.ParameterType
import org.jenkinsci.plugins.workflow.cps.GlobalVariable
import org.jenkinsci.plugins.workflow.cps.Snippetizer
import org.jenkinsci.plugins.workflow.steps.StepDescriptor

// keeps track of recursion inside generateHelp
stack = new Stack();

Snippetizer snippetizer = my;

def l = namespace(lib.LayoutTagLib)
def st = namespace("jelly:stapler")

l.layout(title:_("Pipeline Syntax: Reference"), norefresh: true) {
    st.include(page: 'sidepanel')
    l.main_panel {

      h1(_("Overview"))
      st.include(page: 'help')

div(class:'dsl-reference'){
  h1(_("DSL Reference"))
  
  div(class:'steps-box basic'){
    h2(_("Steps"))
    dl(class:'steps basic root'){
      for (Snippetizer.QuasiDescriptor d : snippetizer.getQuasiDescriptors(false)) {
        generateStepHelp(d);
      }
    }
  }
  
  div(class:'steps-box advanced'){
    h2(_("Advanced/Deprecated Steps"))
    dl(class:'steps advanced root'){
      for (Snippetizer.QuasiDescriptor d : snippetizer.getQuasiDescriptors(true)) {
        generateStepHelp(d);
      }
    }
  }
}

    }
}

def generateStepHelp(Snippetizer.QuasiDescriptor d) throws Exception {
  return {
    dt(class:'step-title show-minimize'){
      code(d.getSymbol())
      raw(": ${d.real.getDisplayName()}")
    }
    dd(class:'step-body minimize'){
      try {
        generateHelp(new DescribableModel(d.real.clazz), 3);
      } catch (Exception x) {
        pre { code(x) }
      }
    }
  }.call()
}

def generateHelp(DescribableModel model, int headerLevel) throws Exception {
  return {
    String help = model.help;
    if (help != null && !help.equals("")) {
      div(class:"help", style:"display: block") { raw(help) }
    }
    if (stack.contains(model.type))
      return; // recursion. just show the title & cut the search

    stack.push(model.type);
    dl(class:'help-list mandatory'){
      // TODO else could use RequestDispatcher (as in Descriptor.doHelp) to serve template-based help
      model.parameters.findAll{ it.required }.each { p ->
        dt(class:'help-title'){ code(p.name)     }
        dd(class:'help-body'){
          generateAttrHelp(p, headerLevel);
        }
      }
    }
    dl(class:'help-list optional'){
      for (DescribableParameter p : model.parameters) {
        if (p.required) {
          continue;
        }
        dt(class:'help-title'){
          code(p.name)
          raw(" (optional)")

        }
        dd(class:'help-body'){
          generateAttrHelp(p, headerLevel);
        }
      }
    }
    stack.pop();
  }.call()
}

def generateAttrHelp(DescribableParameter param, int headerLevel) throws Exception {
  return {
    String help = param.help;
    if (help != null && !help.equals("")) {
      div(class:"help", style:"display: block") { raw(help) }
    }
    describeType(param.type, headerLevel);
  }.call()
}

def describeType(ParameterType type, int headerLevel) throws Exception {
  return {
    int nextHeaderLevel = Math.min(6, headerLevel + 1);
    if (type instanceof AtomicType) {
      div {
        strong(_("Type:"))
        text(type)
      }
    } else if (type instanceof EnumType) {
      div(class:'values-box nested'){
        div(class:'marker-title value-title'){
          span(_("Values:"))
        }
        for (String v : ((EnumType) type).getValues()) {
          div(class:'value list-item') { code(v) }
        }
      }
    } else if (type instanceof ArrayType) {
      div(class:'array-list-box marker'){
        div(class:'array-title marker-title'){
          span(_("Array/List:"))
        }
        div(class:'array-list'){
          describeType(((ArrayType) type).getElementType(), headerLevel)
        }
      }
    } else if (type instanceof HomogeneousObjectType) {
      dl(class:'nested-object-box nested') {
        DescribableModel model = ((HomogeneousObjectType) type).getSchemaType();
        Set<String> symbols = SymbolLookup.getSymbolValue(model.getType());
        dt(symbols.isEmpty() ? _("Nested object") : code(symbols.iterator().next()))
        dd{
          generateHelp(model, nextHeaderLevel);
        }
      }
    } else if (type instanceof HeterogeneousObjectType) {
      dl(class:'nested-choice-box nested') {
        dt(_("Nested choice of objects"))
        dd{
          if (type.actualType == Object) {
            span(_("(not enumerable)"))
          } else {
            dl(class:'schema root') {
              for (Map.Entry<String, DescribableModel> entry : ((HeterogeneousObjectType) type).getTypes().entrySet()) {
                Set<String> symbols = SymbolLookup.getSymbolValue(entry.getValue().getType());
                String symbol = symbols.isEmpty() ? DescribableModel.CLAZZ + ": '" + entry.getKey() + "'" : symbols.iterator().next();
                dt(class: 'show-minimize') {
                  code(symbol)
                }
                dd(class: 'minimize') {
                  generateHelp(entry.value, nextHeaderLevel);
                }
              }
            }
          }
        }
      }
    } else if (type instanceof ErrorType) {
      Exception x = ((ErrorType) type).getError();
      pre { code(x) }
    } else {
      assert false: type;
    }
  }.call()
}

st.adjunct(includes: 'org.jenkinsci.plugins.workflow.cps.Snippetizer.workflow')
