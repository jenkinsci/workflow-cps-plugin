/*
 * The MIT License
 *
 * Copyright (c) 2016 IKEDA Yasuyuki
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.testMetaStep;

import org.jenkinsci.ConstSymbol;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.TaskListener;

/**
 * A step instantiated with constant values.
 */
public class WithConst extends State {
    public static enum EnumValue {
        GOOD,
        BAD,
    }
    public static class EnumLikeValue {
        private final String name;
        public EnumLikeValue(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @ConstSymbol("SUCCESS")
        public static final EnumLikeValue SUCCESS = new EnumLikeValue("SUCCESS");
        @ConstSymbol("FAILURE")
        public static final EnumLikeValue FAILURE = new EnumLikeValue("FAILURE");
    }

    private final EnumValue enumValue;
    private final EnumLikeValue enumLikeValue;

    @DataBoundConstructor
    public WithConst(EnumValue enumValue, EnumLikeValue enumLikeValue) {
        this.enumValue = enumValue;
        this.enumLikeValue = enumLikeValue;
    }

    @Override
    public void sayHello(TaskListener hello) {
        hello.getLogger().println(String.format(
            "I'm instantiated with (%s, %s)",
            enumValue.name(),
            enumLikeValue.getName()
        ));
    }

    @Symbol("with_const")
    @Extension
    public static class DescriptorImpl extends Descriptor<State> {
    }
}
