package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;

/**
 * @author Kohsuke Kawaguchi
 */
public class BlockScopedBlock implements Block {
    private final Block exp;

    public BlockScopedBlock(Block exp) {
        this.exp = exp;
    }

    public Next eval(Env _e, final Continuation k) {
        final Env e = new BlockScopeEnv(_e); // block statement creates a new scope

        return new Next(exp, e, k);
    }

    private static final long serialVersionUID = 1L;
}
