package com.cloudbees.groovy.cps.tool;

import com.sun.codemodel.writer.FileCodeWriter;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.file.RelativePath.RelativeDirectory;
import com.sun.tools.javac.file.ZipArchive;
import groovy.lang.GroovyShell;
import hudson.remoting.Which;

import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;

import static java.util.Arrays.*;

public class Driver {
    public static void main(String[] args) throws Exception {
        new Driver().run(new File(args[0]));
    }

    public void run(File dir) throws Exception {
        JavaCompiler javac = JavacTool.create();
        DiagnosticListener<JavaFileObject> errorListener = createErrorListener();

        try (StandardJavaFileManager fileManager = javac.getStandardFileManager(errorListener, Locale.getDefault(), Charset.defaultCharset())) {
            fileManager.setLocation(StandardLocation.CLASS_PATH,
                    Collections.singleton(Which.jarFile(GroovyShell.class)));

            File groovySrcJar = Which.jarFile(Driver.class.getClassLoader().getResource("groovy/lang/GroovyShell.java"));

            // classes to translate
            // TODO include other classes mentioned in DgmConverter if they have any applicable methods; and certainly StringGroovyMethods which has some Closure-based methods
            List<String> fileNames = asList("DefaultGroovyMethods", "DefaultGroovyStaticMethods");

            List<JavaFileObject> src = new ArrayList<>();
            ZipArchive a = new ZipArchive((JavacFileManager) fileManager, new ZipFile(groovySrcJar));

            for (String name : fileNames) {
                src.add(a.getFileObject(new RelativeDirectory("org/codehaus/groovy/runtime"),name+".java"));
            }

            // annotation processing appears to cause the source files to be reparsed
            // (even though I couldn't find exactly where it's done), which causes
            // Tree symbols created by the original JavacTask.parse() call to be thrown away,
            // which breaks later processing.
            // So for now, don't perform annotation processing
            List<String> options = asList("-proc:none");

            Translator t = new Translator(javac.getTask(null, fileManager, errorListener, options, null, src));

            for (String name : fileNames) {
                t.translate(
                        "org.codehaus.groovy.runtime."+name,
                        "com.cloudbees.groovy.cps.Cps"+name,
                        groovySrcJar.getName());
            }


            dir.mkdirs();
            t.generateTo(new FileCodeWriter(dir));
        }
    }

    private DiagnosticListener<JavaFileObject> createErrorListener() {
        return System.out::println;
    }

}
