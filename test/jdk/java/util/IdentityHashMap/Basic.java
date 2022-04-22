/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Predicate;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/*
 * @test
 * @bug 8285295
 * @summary Basic tests for IdentityHashMap
 * @run testng Basic
 */

// TODO need this? @modules java.base/java.util:open
// TODO add tests using null
// TODO copy constructor instead of k3 and v3 (key and value not in map)?

public class Basic {
    record Box(int i) {
        Box(Box other) {
            this(other.i());
        }
    }

    Predicate<Box> isIdenticalBox(Box b1) {
        return b2 -> b1 == b2;
    }

    Predicate<Map.Entry<Box, Box>> isIdenticalEntry(Map.Entry<Box, Box> e1) {
        return e2 -> e1.getKey() == e2.getKey() && e1.getValue() == e2.getValue();
    }

    <T> int indexOf(T[] array, Predicate<T> pred) {
        for (int i = 0; i < array.length; i++) {
            if (pred.test(array[i]))
                return i;
        }
        return -1;
    }

    final Box k1a = new Box(17);
    final Box k1b = new Box(17); // equals but != k1a
    final Box k2  = new Box(42);

    final Box v1a = new Box(30);
    final Box v1b = new Box(30); // equals but != v1a
    final Box v2  = new Box(99);

    IdentityHashMap<Box, Box> map;
    IdentityHashMap<Box, Box> map2;

    @BeforeMethod
    public void setup() {
        map = new IdentityHashMap<>();
        map.put(k1a, v1a);
        map.put(k1b, v1b);
        map.put(k2,  v2);

        map2 = new IdentityHashMap<>();
        map2.put(k1a, v1a);
        map2.put(k1b, v1b);
        map2.put(k2,  v2);
    }

    // containsKey
    // containsValue
    // size
    @Test
    public void testContainsSize() {
        assertEquals(map.size(), 3);

        assertTrue(map.containsKey(k1a));
        assertTrue(map.containsKey(k1b));
        assertTrue(map.containsKey(k2));
        assertFalse(map.containsKey(new Box(k1a)));

        assertTrue(map.containsValue(v1a));
        assertTrue(map.containsValue(v1b));
        assertFalse(map.containsValue(new Box(v1a)));
        assertTrue(map.containsValue(v2));
    }

    // get
    @Test
    public void testGet() {
        assertSame(map.get(k1a), v1a);
        assertSame(map.get(k1b), v1b);
        assertSame(map.get(k2), v2);
        assertNull(map.get(new Box(k1a)));
    }

    // getOrDefault
    @Test
    public void testGetOrDefault() {
        Box other = new Box(22);

        assertSame(map.getOrDefault(k1a, other), v1a);
        assertSame(map.getOrDefault(k1b, other), v1b);
        assertSame(map.getOrDefault(new Box(k1a), other), other);
        assertSame(map.getOrDefault(k2, other), v2);
    }

    // clear
    // isEmpty
    @Test
    public void testClear() {
        assertFalse(map.isEmpty());
        map.clear();
        assertTrue(map.isEmpty());
    }

    // hashCode
    @Test
    public void testHashCode() {
        int expected = (System.identityHashCode(k1a) ^ System.identityHashCode(v1a)) +
                       (System.identityHashCode(k1b) ^ System.identityHashCode(v1b)) +
                       (System.identityHashCode(k2)  ^ System.identityHashCode(v2));
        assertEquals(map.hashCode(), expected);
        assertEquals(map.entrySet().hashCode(), expected);
    }

    // equals
    @Test
    public void testEquals() {
        assertTrue(map.equals(map));
        assertTrue(map.equals(map2));
        assertTrue(map2.equals(map));
    }

    // equals
    @Test
    public void testNotEqualsKey() {
        map2.remove(k1a);
        map2.put(new Box(k1a), v1a);
        assertFalse(map.equals(map2));
        assertFalse(map2.equals(map));
    }

    // equals
    @Test
    public void testNotEqualsValue() {
        map2.put(k1a, new Box(v1a));
        assertFalse(map.equals(map2));
        assertFalse(map2.equals(map));
    }

    @Test
    public void testKeySet() {
        Set<Box> keySet = map.keySet();

        assertTrue(keySet.contains(k1a));
        assertTrue(keySet.contains(k1b));
        assertTrue(keySet.contains(k2));
        assertFalse(keySet.contains(new Box(k1a)));

        Box[] array = keySet.toArray(Box[]::new);
        int[] indexes = {
            indexOf(array, isIdenticalBox(k1a)),
            indexOf(array, isIdenticalBox(k1b)),
            indexOf(array, isIdenticalBox(k2))
        };
        Arrays.sort(indexes);
        assertTrue(Arrays.equals(new int[] { 0, 1, 2 }, indexes));
    }

    @Test
    public void testValues() {
        Collection<Box> values = map.values();

        assertTrue(values.contains(v1a));
        assertTrue(values.contains(v1b));
        assertTrue(values.contains(v2));
        assertFalse(values.contains(new Box(v1a)));

        Box[] array = values.toArray(Box[]::new);
        int[] indexes = {
            indexOf(array, isIdenticalBox(v1a)),
            indexOf(array, isIdenticalBox(v1b)),
            indexOf(array, isIdenticalBox(v2))
        };
        Arrays.sort(indexes);
        assertTrue(Arrays.equals(new int[] { 0, 1, 2 }, indexes));
    }

    @Test
    public void testEntrySet() {
        Set<Map.Entry<Box,Box>> entrySet = map.entrySet();

        assertTrue(entrySet.contains(Map.entry(k1a, v1a)));
        assertTrue(entrySet.contains(Map.entry(k1b, v1b)));
        assertTrue(entrySet.contains(Map.entry(k2, v2)));
        assertFalse(entrySet.contains(Map.entry(new Box(k1a), v1a)));
        assertFalse(entrySet.contains(Map.entry(k1b, new Box(v1b))));
        assertFalse(entrySet.contains(Map.entry(new Box(k2), new Box(v2))));

        @SuppressWarnings("unchecked")
        Map.Entry<Box, Box>[] array = entrySet.toArray(Map.Entry[]::new);
        int[] indexes = {
            indexOf(array, isIdenticalEntry(Map.entry(k1a, v1a))),
            indexOf(array, isIdenticalEntry(Map.entry(k1b, v1b))),
            indexOf(array, isIdenticalEntry(Map.entry(k2,  v2))),
        };
        Arrays.sort(indexes);
        assertTrue(Arrays.equals(new int[] { 0, 1, 2 }, indexes));
    }

    // put
    @Test
    public void testPutNew() {
        Box newKey = new Box(k1a);
        Box newVal = new Box(v1a);
        map.put(newKey, newVal);

        assertEquals(map.size(), 4);
        assertTrue(map.entrySet().contains(Map.entry(k1a, v1a)));
        assertTrue(map.entrySet().contains(Map.entry(k1b, v1b)));
        assertTrue(map.entrySet().contains(Map.entry(k2, v2)));
        assertTrue(map.entrySet().contains(Map.entry(newKey, newVal)));
    }

    // put
    @Test
    public void testPutOverwrite() {
        Box newVal = new Box(v1a);
        map.put(k1a, newVal);

        assertEquals(map.size(), 3);
        assertTrue(map.entrySet().contains(Map.entry(k1a, newVal)));
        assertTrue(map.entrySet().contains(Map.entry(k1b, v1b)));
        assertTrue(map.entrySet().contains(Map.entry(k2, v2)));
    }

    // putAll
    @Test
    public void testPutAll() {
        Box newKey  = new Box(k1a);
        Box newVal  = new Box(v1a);
        Box newValB = new Box(v1b);
        var argMap = new IdentityHashMap<Box, Box>();
        argMap.put(newKey, newVal); // new entry
        argMap.put(k1b, newValB);   // will overwrite value

        map.putAll(argMap);

        assertEquals(map.size(), 4);
        assertTrue(map.entrySet().contains(Map.entry(k1a, v1a)));
        assertTrue(map.entrySet().contains(Map.entry(k1b, newValB)));
        assertTrue(map.entrySet().contains(Map.entry(k2, v2)));
        assertTrue(map.entrySet().contains(Map.entry(newKey, newVal)));
    }

    // putIfAbsent
    @Test
    public void testPutIfAbsent() {
        map.putIfAbsent(k1a, new Box(v1a)); // no-op
        assertTrue(map.equals(map2));

        map.putIfAbsent(new Box(k1a), new Box(v1a)); // adds new entry
        assertFalse(map.equals(map2));
        assertEquals(map.size(), 4);
    }

    // remove(Object)
    @Test
    public void testRemoveKey() {
        map.remove(k1b);
        assertEquals(map.size(), 2);
        assertTrue(map.entrySet().contains(Map.entry(k1a, v1a)));
        assertTrue(map.entrySet().contains(Map.entry(k2, v2)));
    }

    // compute
    // computeIfAbsent
    // computeIfPresent
    // forEach
    // merge
    // replaceAll

    // TODO liach:
    // remove(k, v) // elsewhere
    // replace(k, v1, v2) // elsewhere
}
