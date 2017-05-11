/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.cps.persistence;

import com.cloudbees.groovy.cps.impl.Caller;
import java.io.Serializable;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Makes Java iterators effectively serializable.
 */
public class IteratorHack {

    /**
     * Similar to the inner class of {@link AbstractList} except it is serializable.
     * Since {@link AbstractList#modCount} is {@code transient} we cannot rely on it and thus cannot throw {@link ConcurrentModificationException}.
     */
    private static final class Itr<E> implements Iterator<E>, Serializable {
        private static final long serialVersionUID = 1;
        private final List<E> list;
        private int cursor = 0;
        private int lastRet = -1;
        Itr(List<E> list) {
            this.list = list;
        }
        @Override public boolean hasNext() {
            return cursor != list.size();
        }
        @Override public E next() {
            try {
                int i = cursor;
                E next = list.get(i);
                lastRet = i;
                cursor = i + 1;
                return next;
            } catch (IndexOutOfBoundsException e) {
                throw new NoSuchElementException();
            }
        }
        @Override public void remove() {
            if (lastRet < 0) {
                throw new IllegalStateException();
            }
            try {
                list.remove(lastRet);
                if (lastRet < cursor) {
                    cursor--;
                }
                lastRet = -1;
            } catch (IndexOutOfBoundsException e) {
                throw new ConcurrentModificationException();
            }
        }
    }

    /** Serializable replacement for {@link List#iterator}. */
    public static <E> Iterator<E> iterator(List<E> list) {
        // TODO !Caller.isAsynchronous(list, "iterator") && !Caller.isAsynchronous(IteratorHack.class, "iterator", list) when used from a Java 5-style for-loop, so not sure how to sidestep this when running in @NonCPS
        return new Itr<>(list);
    }

    /** Serializable replacement for {@link Map#entrySet}. */
    public static <K, V> Collection<Map.Entry<K, V>> entrySet(Map<K, V> map) {
        if (!Caller.isAsynchronous(map, "entrySet") && !Caller.isAsynchronous(IteratorHack.class, "entrySet", map)) {
            // In @NonCPS so no need to bother processing this.
            return map.entrySet();
        }
        // TODO return an actual Set
        List<Map.Entry<K, V>> entries = new ArrayList<>();
        for (Map.Entry<K, V> entry : map.entrySet()) {
            // TODO return an object holding references to the map and the key and delegating all calls accordingly
            entries.add(new AbstractMap.SimpleImmutableEntry<>(entry));
        }
        return entries;
    }

    private IteratorHack() {}

}
