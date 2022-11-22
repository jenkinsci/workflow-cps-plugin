package com.cloudbees.groovy.cps;

import com.cloudbees.groovy.cps.impl.ConstantBlock;
import com.cloudbees.groovy.cps.impl.ThrowBlock;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;

/**
 * Result of an evaluation.
 *
 * Either represents a value in case of a normal return, or a throwable object in case of abnormal return.
 * Note that both fields can be null, in which case it means a normal return of the value 'null'.
 *
 * @author Kohsuke Kawaguchi
 */
public final class Outcome implements Serializable {
    private final Object normal;
    private final Throwable abnormal;

    public Outcome(Object normal, Throwable abnormal) {
        assert normal==null || abnormal==null;
        this.normal = normal;
        this.abnormal = abnormal;
    }

    /**
     * Like {@link #replay()} but wraps the throwable into {@link InvocationTargetException}.
     */
    public Object wrapReplay() throws InvocationTargetException {
        if (abnormal!=null)
            throw new InvocationTargetException(abnormal);
        else
            return normal;
    }

    public Object replay() throws Throwable {
        if (abnormal!=null)
            throw abnormal;
        else
            return normal;
    }

    public Object getNormal() {
        return normal;
    }

    public Throwable getAbnormal() {
        return abnormal;
    }

    public boolean isSuccess() {
        return abnormal==null;
    }

    public boolean isFailure() {
        return abnormal!=null;
    }

    public Next resumeFrom(Continuable c) {
        return resumeFrom(c.getE(), c.getK());
    }

    public Next resumeFrom(Env e, Continuation k) {
        if (abnormal!=null) {
            // resume program by throwing this exception
            return new Next(new ThrowBlock(new ConstantBlock(abnormal)),e,null/*unused*/);
        } else {
            // resume program by passing the value
            return k.receive(normal);
        }
    }

//    public Block asBlock() {
//        if (abnormal!=null) {
//            // resume program by throwing this exception
//            return new ThrowBlock(new ConstantBlock(abnormal));
//        } else {
//            // resume program by passing the value
//            return new ConstantBlock(normal);
//        }
//    }

    @Override
    public String toString() {
        if (abnormal!=null)     return "abnormal["+abnormal+']';
        else                    return "normal["+normal+']';
    }

    private static final long serialVersionUID = 1L;
}
