/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.skjegstad.utils;

import java.util.List;
import java.io.UnsupportedEncodingException;
import java.util.Random;
import java.util.UUID;
import java.util.ArrayList;

import mvm.provenance.Hasher;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for BloomFilter.java
 *
 * @author Magnus Skjegstad <magnus@skjegstad.com>
 */

@SuppressWarnings({"static-method","rawtypes"})
public class BloomFilterTest {
    static Random r = new Random();
    @Test
    public void testConstructorCNK() throws Exception {
        System.out.println("BloomFilter(c,n,k)");

        for (int i = 0; i < 10000; i++) {
            double c = r.nextInt(20)+1 ;
            int n = r.nextInt(10000) + 1;
            int k = r.nextInt(20) + 1;
            BloomFilter bf = new BloomFilter(new Hasher(), c, n, k,1);
            assertEquals(bf.getK(), k);
            assertEquals(bf.getExpectedBitsPerElement(), c, 0);
            assertEquals(bf.getExpectedNumberOfElements(), n);
            assertEquals(bf.size(), c*n, 0);
        }
    }


    /**
     * Test of createHash method, of class BloomFilter.
     * @throws Exception
     */
    /*@Test
    public void testCreateHash_String() throws Exception {
        System.out.println("createHash");
        String val = UUID.randomUUID().toString();
        long result1 = BloomFilter.createHash(val);
        long result2 = BloomFilter.createHash(val);
        assertEquals(result2, result1);
        long result3 = BloomFilter.createHash(UUID.randomUUID().toString());
        assertNotSame(result3, result2);

        long result4 = BloomFilter.createHash(val.getBytes("UTF-8"));
        assertEquals(result4, result1);
    }*/

    /**
     * Test of createHash method, of class BloomFilter.
     * @throws UnsupportedEncodingException
     */
    /*Test
    public void testCreateHash_byteArr() throws UnsupportedEncodingException {
        System.out.println("createHash");
        String val = UUID.randomUUID().toString();
        byte[] data = val.getBytes("UTF-8");
        long result1 = BloomFilter.createHash(data);
        long result2 = BloomFilter.createHash(val);
        assertEquals(result1, result2);
    }*/

    /**
     * Test of equals method, of class BloomFilter.
     * @throws UnsupportedEncodingException
     */
    @Test
    public void testEquals() throws UnsupportedEncodingException {
        System.out.println("equals");
        Hasher h = new Hasher();
        BloomFilter<String> instance1 = new BloomFilter<String>(h,1000, 100);
        BloomFilter<String> instance2 = new BloomFilter<String>(h,1000, 100);

        for (int i = 0; i < 100; i++) {
            String val = UUID.randomUUID().toString();
            instance1.add(val);
            instance2.add(val);
        }

        assert(instance1.equals(instance2));
        assert(instance2.equals(instance1));

        instance1.add("Another entry"); // make instance1 and instance2 different before clearing

        instance1.clear();
        instance2.clear();

        assert(instance1.equals(instance2));
        assert(instance2.equals(instance1));

        for (int i = 0; i < 100; i++) {
            String val = UUID.randomUUID().toString();
            instance1.add(val);
            instance2.add(val);
        }

        assertTrue(instance1.equals(instance2));
        assertTrue(instance2.equals(instance1));
    }

    /**
     * Test of hashCode method, of class BloomFilter.
     * @throws UnsupportedEncodingException
     */
    @Test
    public void testHashCode() throws UnsupportedEncodingException {
        System.out.println("hashCode");
        Hasher h = new Hasher();

        BloomFilter<String> instance1 = new BloomFilter<String>(h,1000, 100);
        BloomFilter<String> instance2 = new BloomFilter<String>(h,1000, 100);

        assertTrue(instance1.hashCode() == instance2.hashCode());

        for (int i = 0; i < 100; i++) {
            String val = UUID.randomUUID().toString();
            instance1.add(val);
            instance2.add(val);
        }

        assertTrue(instance1.hashCode() == instance2.hashCode());

        instance1.clear();
        instance2.clear();

        assertTrue(instance1.hashCode() == instance2.hashCode());

        instance1 = new BloomFilter<String>(new Hasher(),100, 10);
        instance2 = new BloomFilter<String>(new Hasher(),100, 9);
        assertFalse(instance1.hashCode() == instance2.hashCode());

        instance1 = new BloomFilter<String>(new Hasher(),100, 10);
        instance2 = new BloomFilter<String>(new Hasher(),99, 9);
        assertFalse(instance1.hashCode() == instance2.hashCode());

        instance1 = new BloomFilter<String>(new Hasher(),100, 10);
        instance2 = new BloomFilter<String>(new Hasher(),50, 10);
        assertFalse(instance1.hashCode() == instance2.hashCode());
    }

    /**
     * Test of expectedFalsePositiveProbability method, of class BloomFilter.
     */
    @Test
    public void testExpectedFalsePositiveProbability() {
        // These probabilities are taken from the bloom filter probability table at
        // http://pages.cs.wisc.edu/~cao/papers/summary-cache/node8.html
        System.out.println("expectedFalsePositiveProbability");
        BloomFilter instance = new BloomFilter(new Hasher(),1000, 100);
        double expResult = 0.00819; // m/n=10, k=7
        double result = instance.expectedFalsePositiveProbability();
        assertEquals(instance.getK(), 7);
        assertEquals(expResult, result, 0.000009);

        instance = new BloomFilter(new Hasher(),100, 10);
        expResult = 0.00819; // m/n=10, k=7
        result = instance.expectedFalsePositiveProbability();
        assertEquals(instance.getK(), 7);
        assertEquals(expResult, result, 0.000009);

        instance = new BloomFilter(new Hasher(),20, 10);
        expResult = 0.393; // m/n=2, k=1
        result = instance.expectedFalsePositiveProbability();
        assertEquals(1, instance.getK());
        assertEquals(expResult, result, 0.0005);

        instance = new BloomFilter(new Hasher(),110, 10);
        expResult = 0.00509; // m/n=11, k=8
        result = instance.expectedFalsePositiveProbability();
        assertEquals(8, instance.getK());
        assertEquals(expResult, result, 0.00001);
    }

    /**
     * Test of clear method, of class BloomFilter.
     */
    @Test
    public void testClear() {
        System.out.println("clear");
        Hasher h = new Hasher();
        BloomFilter instance = new BloomFilter(h,1000, 100);
        for (int i = 0; i < instance.size(); i++)
            instance.setBit(i, true);
        instance.clear();
        for (int i = 0; i < instance.size(); i++)
            assertSame(instance.getBitSet().get(i), false);
    }

    /**
     * Test of add method, of class BloomFilter.
     * @throws Exception
     */
    @Test
    public void testAdd() throws Exception {
        System.out.println("add");

        Hasher h = new Hasher();
        BloomFilter<String> instance = new BloomFilter<String>(h,1000, 100);

        for (int i = 0; i < 100; i++) {
            String val = UUID.randomUUID().toString();
            instance.add(val);
            assert(instance.contains(val));
        }
    }

    /**
     * Test of addAll method, of class BloomFilter.
     * @throws Exception
     */
    @Test
    public void testAddAll() throws Exception {
        System.out.println("addAll");
        List<String> v = new ArrayList<String>();

        Hasher h = new Hasher();
        BloomFilter<String> instance = new BloomFilter<String>(h,1000, 100);

        for (int i = 0; i < 100; i++)
            v.add(UUID.randomUUID().toString());

        instance.addAll(v);

        for (int i = 0; i < 100; i++)
            assert(instance.contains(v.get(i)));
    }

    /**
     * Test of contains method, of class BloomFilter.
     * @throws Exception
     */
    @Test
    public void testContains() throws Exception {
        System.out.println("contains");

        Hasher h = new Hasher();
        BloomFilter<String> instance = new BloomFilter<String>(h,10000, 10);

        for (int i = 0; i < 10; i++) {
            instance.add(Integer.toBinaryString(i));
            assert(instance.contains(Integer.toBinaryString(i)));
        }

        assertFalse(instance.contains(UUID.randomUUID().toString()));
    }

    /**
     * Test of containsAll method, of class BloomFilter.
     * @throws Exception
     */
    @Test
    public void testContainsAll() throws Exception {
        System.out.println("containsAll");
        List<String> v = new ArrayList<String>();

        Hasher h = new Hasher();
        BloomFilter<String> instance = new BloomFilter<String>(h,1000, 100);

        for (int i = 0; i < 100; i++) {
            v.add(UUID.randomUUID().toString());
            instance.add(v.get(i));
        }

        assert(instance.containsAll(v));
    }

    /**
     * Test of getBit method, of class BloomFilter.
     */
    @Test
    public void testGetBit() {
        System.out.println("getBit");

        Hasher h = new Hasher();
        BloomFilter instance = new BloomFilter(h,1000, 100);

        for (int i = 0; i < 100; i++) {
            boolean b = r.nextBoolean();
            instance.setBit(i, b);
            assertSame(instance.getBitSet().get(i), b);
        }
    }

    /**
     * Test of setBit method, of class BloomFilter.
     */
    @Test
    public void testSetBit() {
        System.out.println("setBit");

        Hasher h = new Hasher();
        BloomFilter instance = new BloomFilter(h,1000, 100);
        //Random r = new Random();

        for (int i = 0; i < 100; i++) {
            instance.setBit(i, true);
            assertSame(instance.getBitSet().get(i), true);
        }

        for (int i = 0; i < 100; i++) {
            instance.setBit(i, false);
            assertSame(instance.getBitSet().get(i), false);
        }
    }

    /**
     * Test of size method, of class BloomFilter.
     */
    @Test
    public void testSize() {
        System.out.println("size");

        for (int i = 100; i < 1000; i++) {
            BloomFilter instance = new BloomFilter(new Hasher(),i, 10);
            assertEquals(instance.size(), i);
        }
    }

    /** Test error rate *
     * @throws UnsupportedEncodingException
     */
    @Test
    public void testFalsePositiveRate1() throws UnsupportedEncodingException {
        // Numbers are from // http://pages.cs.wisc.edu/~cao/papers/summary-cache/node8.html
        System.out.println("falsePositiveRate1");

        for (int j = 10; j < 21; j++) {
            System.out.print(j-9 + "/11");
            List<String> v = new ArrayList<String>();
            BloomFilter<String> instance = new BloomFilter<String>(new Hasher(),100*j,100);

            for (int i = 0; i < 100; i++) {
                v.add(UUID.randomUUID().toString());
            }
            instance.addAll(v);

            long rr = 0;
            double tests = 100000;
            for (int i = 0; i < tests; i++) {
                String s = UUID.randomUUID().toString();
                if (instance.contains(s)) {
                    if (!v.contains(s)) {
                        rr++;
                    }
                }
            }

            double ratio = rr / tests;

            System.out.println(" - got " + ratio + ", math says " + instance.expectedFalsePositiveProbability());
            assertEquals(instance.expectedFalsePositiveProbability(), ratio, 0.01);


        }
    }

    /** Test for correct k **/
    @Test
    public void testGetK() {
        // Numbers are from http://pages.cs.wisc.edu/~cao/papers/summary-cache/node8.html
        System.out.println("testGetK");

        BloomFilter instance = null;

        instance = new BloomFilter(new Hasher(),2, 1);
        assertEquals(1, instance.getK());

        instance = new BloomFilter(new Hasher(),3, 1);
        assertEquals(2, instance.getK());

        instance = new BloomFilter(new Hasher(),4, 1);
        assertEquals(3, instance.getK());

        instance = new BloomFilter(new Hasher(),5, 1);
        assertEquals(3, instance.getK());

        instance = new BloomFilter(new Hasher(),6, 1);
        assertEquals(4, instance.getK());

        instance = new BloomFilter(new Hasher(),7, 1);
        assertEquals(5, instance.getK());

        instance = new BloomFilter(new Hasher(),8, 1);
        assertEquals(6, instance.getK());

        instance = new BloomFilter(new Hasher(),9, 1);
        assertEquals(6, instance.getK());

        instance = new BloomFilter(new Hasher(),10, 1);
        assertEquals(7, instance.getK());

        instance = new BloomFilter(new Hasher(),11, 1);
        assertEquals(8, instance.getK());

        instance = new BloomFilter(new Hasher(),12, 1);
        assertEquals(8, instance.getK());
    }


}