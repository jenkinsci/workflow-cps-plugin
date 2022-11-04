package com.cloudbees.groovy.cps;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;

/**
 * {@link ObjectInputStream} with a custom {@link ClassLoader}.
 *
 * @author Kohsuke Kawaguchi
 */
public class ObjectInputStreamWithLoader extends ObjectInputStream {
    private final ClassLoader cl;

    public ObjectInputStreamWithLoader(InputStream in, ClassLoader cl) throws IOException {
        super(in);
        this.cl = cl;
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
        try {
            return cl.loadClass(desc.getName());
        } catch (ClassNotFoundException e) {
            return super.resolveClass(desc);
        }
    }
}
