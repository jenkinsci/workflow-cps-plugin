package com.cloudbees.groovy.cps;

import com.cloudbees.groovy.cps.impl.SourceLocation;
import java.io.Serializable;

/**
 * @author Kohsuke Kawaguchi
 */
public class CaseExpression implements Serializable {
    /**
     * Expression in the case that decides the match.
     */
    public final Block matcher;

    public final Block body;
    public final SourceLocation loc;

    public CaseExpression(SourceLocation loc, Block matcher, Block body) {
        this.loc = loc;
        this.matcher = matcher;
        this.body = body;
    }

    private static final long serialVersionUID = 1L;
}
