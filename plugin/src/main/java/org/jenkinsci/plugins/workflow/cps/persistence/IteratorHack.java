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
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedMap;

/**
 * Makes Java iterators effectively serializable.
 */
public class IteratorHack {

    /**
     * Similar to the inner class of {@link AbstractList} except it is serializable.
     * Since {@link AbstractList#modCount} is {@code transient} we cannot rely on it and thus cannot throw {@link ConcurrentModificationException}.
     */
    private static class Itr<E> implements Iterator<E>, Serializable {
        private static final long serialVersionUID = 1;
        final List<E> list;
        int cursor = 0;
        int lastRet = -1;
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

    public static <E> Iterator<E> iterator(Set<E> set) {
        // TODO as above
        return new Itr<>(new ArrayList<>(set));
    }

    public static <E> Iterator<E> iterator(E[] array) {
        // TODO as above
        return new Itr<>(Arrays.asList(array));
    }

    private static final class ListItr<E> extends Itr<E> implements ListIterator<E> {
        private static final long serialVersionUID = 1;
        ListItr(List<E> list, int idx) {
            super(list);
            cursor = idx;
        }
        @Override public boolean hasPrevious() {
            return cursor != 0;
        }
        @Override public E previous() {
            try {
                if (cursor == 0) {
                    throw new NoSuchElementException();
                }
                cursor--;
                lastRet = cursor;
                return list.get(lastRet);
            } catch (IndexOutOfBoundsException e) {
                throw new NoSuchElementException();
            }
        }
        @Override public int nextIndex() {
            return cursor;
        }
        @Override public int previousIndex() {
            return cursor - 1;
        }
        @Override public void set(E e) {
            list.set(lastRet, e);
        }
        @Override public void add(E e) {
            list.add(cursor, e);
            cursor++;
            lastRet = -1;
        }
    }

    public static <E> ListIterator<E> listIterator(List<E> list) {
        if (!Caller.isAsynchronous(list, "listIterator") && !Caller.isAsynchronous(IteratorHack.class, "listIterator", list)) {
            return list.listIterator();
        }
        return new ListItr<>(list, 0);
    }

    public static <E> ListIterator<E> listIterator(List<E> list, int idx) {
        if (!Caller.isAsynchronous(list, "listIterator", idx) && !Caller.isAsynchronous(IteratorHack.class, "listIterator", list, idx)) {
            return list.listIterator(idx);
        }
        return new ListItr<>(list, idx);
    }

    /** Serializable replacement for {@link Map#entrySet}. */
    public static <K, V> Set<Map.Entry<K, V>> entrySet(Map<K, V> map) {
        if (!Caller.isAsynchronous(map, "entrySet") && !Caller.isAsynchronous(IteratorHack.class, "entrySet", map)) {
            // In @NonCPS so no need to bother processing this.
            return map.entrySet();
        }
        Set<Map.Entry<K, V>> entries = new LinkedHashSet<>();
        for (Map.Entry<K, V> entry : map.entrySet()) {
            // TODO return an object holding references to the map and the key and delegating all calls accordingly
            entries.add(new AbstractMap.SimpleImmutableEntry<>(entry));
        }
        return entries;
    }

    public static <K, V> Set<Map.Entry<K, V>> entrySet(SortedMap<K, V> map) {
        return entrySet((Map<K,V>)map);

    }

    public static <K> Set<K> keySet(Map<K, ?> map) {
        if (!Caller.isAsynchronous(map, "keySet") && !Caller.isAsynchronous(IteratorHack.class, "keySet", map)) {
            return map.keySet();
        }
        return new LinkedHashSet<>(map.keySet());
    }

    public static <K> Set<K> keySet(SortedMap<K, ?> map) {
        return keySet((Map<K,?>)map);
    }

    public static <V> Collection<V> values(Map<?, V> map) {
        if (!Caller.isAsynchronous(map, "values") && !Caller.isAsynchronous(IteratorHack.class, "values", map)) {
            return map.values();
        }
        return new ArrayList<>(map.values());
    }

    public static <V> Collection<V> values(SortedMap<?, V> map) {
        return values((Map<?,V>)map);
    }

    private IteratorHack() {}

}
