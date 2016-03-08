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

import java.io.Serializable;
import com.googlecode.javaewah.datastructure.BitSet;
import java.util.Collection;
import java.util.List;
import mvm.provenance.Hasher;

/**
 *
 *
 * Implementation of a Bloom-filter, as described here:
 * http://en.wikipedia.org/wiki/Bloom_filter
 *
 * Inspired by the SimpleBloomFilter-class written by Ian Clarke. This
 * implementation provides a more evenly distributed Hash-function by using a
 * proper digest instead of the Java RNG. Many of the changes were proposed in
 * comments in his blog:
 * http://blog.locut.us/2008/01/12/a-decent-stand-alone-java
 * -bloom-filter-implementation/
 *
 * @param <E>
 *                Object type that is to be inserted into the Bloom filter, e.g.
 *                String or Integer.
 * @author Magnus Skjegstad magnus@skjegstad.com
 */
public final class BloomFilter<E> implements Serializable {

    /**
     * Constructs an empty Bloom filter with a given false positive
     * probability. The number of bits per element and the number of hash
     * functions is estimated to match the false positive probability.
     *
     * @param falsePositiveProbability
     *                is the desired false positive probability.
     * @param expectedNumberOfElements
     *                is the expected number of elements in the Bloom
     *                filter.
     */
    public BloomFilter(final Hasher hash, double falsePositiveProbability,
                       int expectedNumberOfElements, int metric) {
        this(hash, Math
             .ceil(-(Math.log(falsePositiveProbability) / Math
                     .log(2)))
             / Math.log(2), // c = k / ln(2)
             expectedNumberOfElements, (int) Math.ceil(-(Math
                     .log(falsePositiveProbability) / Math.log(2))),
             metric); // k = ceil(-log_2(false prob.))
    }
    /**
    * Constructs an empty Bloom filter. The total length of the Bloom
    * filter will be c*n.
    *
    * @param c
    *                is the number of bits used per element.
    * @param n
    *                is the expected number of elements the filter will
    *                contain.
    * @param k
    *                is the number of hash functions used.
    */
    public BloomFilter(final Hasher hash, final double c, final int n,
                       final int k, final int metric) {
        this.h = hash;
        this.expectedNumberOfFilterElements = n;
        hash.setNumberOfRandomKeys(k);
        this.k = k;
        this.bitsPerElement = c;
        this.bitSetSize = (int) Math.ceil(c * n);
        hash.setMaxValue(bitSetSize);
        numberOfAddedElements = 0;
        this.bitset = new BitSet(bitSetSize);
        // AC: add an ID for every BloomFilter created
        lastID++;
        this.id = lastID;
        // AC: set the metric used
        this.metric = metric;
    }
    // D. Lemire : hack for the unit tests to compile, default on metric 1
    public BloomFilter(Hasher hash, int n, int k) {
        this(hash, n, k, 1);
    }

    /**
     * Constructs an empty Bloom filter. The optimal number of hash
     * functions (k) is estimated from the total size of the Bloom and the
     * number of expected elements.
     *
     * @param bitSetSize
     *                defines how many bits should be used in total for the
     *                filter.
     * @param expectedNumberOElements
     *                defines the maximum number of elements the filter is
     *                expected to contain.
     */
    public BloomFilter(final Hasher hash, int bitSetSize,
                       int expectedNumberOElements, int metric) {
        this(
            hash,
            bitSetSize / (double) expectedNumberOElements,
            expectedNumberOElements,
            (int) Math
            .round((bitSetSize / (double) expectedNumberOElements)
                   * Math.log(2.0)), metric);
        assert bitSetSize == this.bitSetSize;
    }

    /**
     * Construct a new Bloom filter based on existing Bloom filter data.
     *
     * @param bitSetSize
     *                defines how many bits should be used for the filter.
     * @param expectedNumberOfFilterElements
     *                defines the maximum number of elements the filter is
     *                expected to contain.
     * @param actualNumberOfFilterElements
     *                specifies how many elements have been inserted into
     *                the <code>filterData</code> BitSet.
     * @param filterData
     *                a BitSet representing an existing Bloom filter.
     */
    public BloomFilter(final Hasher hash, int bitSetSize,
                       int expectedNumberOfFilterElements,
                       int actualNumberOfFilterElements, BitSet filterData, int metric) {
        this(hash, bitSetSize, expectedNumberOfFilterElements, metric);
        this.bitset = filterData;
        this.numberOfAddedElements = actualNumberOfFilterElements;
    }
    /**
     * Adds an object to the Bloom filter. The output from the object's
     * toString() method is used as input to the hash functions.
     *
     * @param element
     *                is an element to register in the Bloom filter.
     */
    public void add(E element) {
        for (int x = 0; x < k; x++) {
            final int hashvalue = h.hash(element, x);
            bitset.set(hashvalue);
        }
        numberOfAddedElements++;
    }
    /**
     * Adds all elements from a Collection to the Bloom filter.
     *
     * @param c
     *                Collection of elements.
     */
    public void addAll(Collection<? extends E> c) {
        for (E element : c)
            add(element);
    }
    /**
     * Sets all bits to false in the Bloom filter.
     */
    public void clear() {
        bitset.clear();
        numberOfAddedElements = 0;
    }
    /**
    * Compute the distance between this filter and the one received as
    * param.
    * If similarity metric to be used is 2 use Jaccard, if 3 use cosine, everything
    * else Hamming
    *
    * @param filter
    *
    * @return
    * @author Adina Crainiceanu
    */
    public double computeDistance(BloomFilter<E> filter) {

        if (this.metric == 2)
            return computeJaccardDistance(filter);
        else if (this.metric == 3)
            return computeCosineDistance(filter);
        return computeHammingDistance(filter);

    }
    /**
    * Returns true if the element could have been inserted into the Bloom
    * filter. Use getFalsePositiveProbability() to calculate the
    * probability of this being correct.
    *
    * @param element
    *                element to check.
    * @return true if the element could have been inserted into the Bloom
    *         filter.
    */
    public boolean contains(E element) {
        // String valString = element.toString();
        for (int x = 0; x < k; x++) {
            final int hash = h.hash(element, x);
            if (!bitset.get(hash))
                return false;
        }
        return true;
    }
    /**
     * Returns true if all the elements of a Collection could have been
     * inserted into the Bloom filter. Use getFalsePositiveProbability() to
     * calculate the probability of this being correct.
     *
     * @param c
     *                elements to check.
     * @return true if all the elements in c could have been inserted into
     *         the Bloom filter.
     */
    public boolean containsAll(Collection<? extends E> c) {
        for (E element : c)
            if (!contains(element))
                return false;
        return true;
    }

    /**
     * Returns the number of elements added to the Bloom filter after it was
     * constructed or after clear() was called.
     *
     * @return number of elements added to the Bloom filter.
     */
    public int count() {
        return this.numberOfAddedElements;
    }

    /**
     * Compares the contents of two instances to see if they are equal.
     *
     * @param obj
     *                is the object to compare to.
     * @return True if the contents of the objects are equal.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        @SuppressWarnings("unchecked")
        final BloomFilter<E> other = (BloomFilter<E>) obj;
        if (this.expectedNumberOfFilterElements != other.expectedNumberOfFilterElements) {
            return false;
        }
        if (this.k != other.k) {
            return false;
        }
        if (this.bitSetSize != other.bitSetSize) {
            return false;
        }
        if (this.bitset != other.bitset
                && (this.bitset == null || !this.bitset
                    .equals(other.bitset))) {
            return false;
        }
        return true;
    }

    /**
     * Calculates the expected probability of false positives based on the
     * number of expected filter elements and the size of the Bloom filter.
     *
     * The value returned by this method is the <i>expected</i> rate of
     * false positives, assuming the number of inserted elements equals the
     * number of expected elements. If the number of elements in the Bloom
     * filter is less than the expected value, the true probability of false
     * positives will be lower.
     *
     * @return expected probability of false positives.
     */
    public double expectedFalsePositiveProbability() {
        return getFalsePositiveProbability(expectedNumberOfFilterElements);
    }

    /**
     * Find the index of closest element in a list
     *
     * @param bfList
     * @return
     */
    public int findClosest(List<BloomFilter<E>> bfList) {

        // return null if no element to compare with
        if (bfList.isEmpty())
            return -1;

        // initialize min distance to be distance to first element
        double minDistance = this.computeDistance(bfList.get(0));
        int minIndex = 0;
        double currentDistance;

        // loop through all elements to find the closest
        for (int i = 1; i < bfList.size(); i++) {
            currentDistance = this.computeDistance(bfList.get(i));
            if (currentDistance < minDistance) {
                minDistance = currentDistance;
                minIndex = i;
            }
        }

        return minIndex;
    }

    /**
     * Return the bit set used to store the Bloom filter.
     *
     * @return bit set representing the Bloom filter.
     */
    public BitSet getBitSet() {
        return bitset;
    }

    /**
     * Get actual number of bits per element based on the number of elements
     * that have currently been inserted and the length of the Bloom filter.
     * See also getExpectedBitsPerElement().
     *
     * @return number of bits per element.
     */
    public double getBitsPerElement() {
        return this.bitSetSize / (double) numberOfAddedElements;
    }

    /**
     * Get expected number of bits per element when the Bloom filter is
     * full. This value is set by the constructor when the Bloom filter is
     * created. See also getBitsPerElement().
     *
     * @return expected number of bits per element.
     */
    public double getExpectedBitsPerElement() {
        return this.bitsPerElement;
    }

    /**
     * Returns the expected number of elements to be inserted into the
     * filter. This value is the same value as the one passed to the
     * constructor.
     *
     * @return expected number of elements.
     */
    public int getExpectedNumberOfElements() {
        return expectedNumberOfFilterElements;
    }

    /**
     * Get the current probability of a false positive. The probability is
     * calculated from the size of the Bloom filter and the current number
     * of elements added to it.
     *
     * @return probability of false positives.
     */
    public double getFalsePositiveProbability() {
        return getFalsePositiveProbability(numberOfAddedElements);
    }

    /**
     * Calculate the probability of a false positive given the specified
     * number of inserted elements.
     *
     * @param numberOfElements
     *                number of inserted elements.
     * @return probability of a false positive.
     */
    public double getFalsePositiveProbability(double numberOfElements) {
        // (1 - e^(-k * n / m)) ^ k
        return Math.pow(
                   (1 - Math.exp(-k * numberOfElements
                                 / bitSetSize)), k);

    }

    public Hasher getHasher() {
        return h;
    }

    /**
     * Generates a digest based on the contents of a String.
     *
     * @param val
     *                specifies the input data.
     * @param charset
     *                specifies the encoding of the input data.
     * @return digest as long.
     */
    // public static long createHash(String val, Charset charset) {
    // return createHash(val.getBytes(charset));
    // }

    /**
     * Generates a digest based on the contents of a String.
     *
     * @param val
     *                specifies the input data. The encoding is expected to
     *                be UTF-8.
     * @return digest as long.
     */
    // public static long createHash(String val) {
    // return createHash(val, charset);
    // }

    /**
     * Generates a digest based on the contents of an array of bytes.
     *
     * @param data
     *                specifies input data.
     * @return digest as long.
     */
    /*
     * public static long createHash(byte[] data) { long h = 0; byte[] res;
     *
     * synchronized (digestFunction) { res = digestFunction.digest(data); }
     *
     * for (int i = 0; i < 4; i++) { h <<= 8; h |= ((int) res[i]) & 0xFF; }
     * return h; }
     */

    /**
     * Get the id
     */
    public int getID() {
        return this.id;
    }

    /**
     * Returns the value chosen for K.
     *
     * K is the optimal number of hash functions based on the size of the
     * Bloom filter and the expected number of inserted elements.
     *
     * @return optimal k.
     */
    public int getK() {
        return k;
    }

    /**
     * Get the metric used when comparing 2 bloom filters
     *
     * @return
     */
    public int getMetric() {
        return this.metric;
    }

    /**
     * Calculates a hash code for this class.
     *
     * @return hash code representing the contents of an instance of this
     *         class.
     */
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 61 * hash
               + (this.bitset != null ? this.bitset.hashCode() : 0);
        hash = 61 * hash + this.expectedNumberOfFilterElements;
        hash = 61 * hash + this.bitSetSize;
        hash = 61 * hash + this.k;
        return hash;
    }

    public boolean isFull() {
        return bitset.cardinality() == bitSetSize;
    }

    /**
     * Compute the OR between this Bloom filter and the one received as
     * param The bitSet will be the or between the two bitSets, and the
     * number of elements added will be the sum of the counts for the two
     * bloom filters
     *
     * @param filter
     */
    public void orBloomFilter(BloomFilter<E> filter) {
        // sanity check: Bloom filters should be of same length
        assert bitSetSize == filter.size() : "Different size bitsets in orBloomFIlter: "
        + bitSetSize + " and " + filter.size();

        // compute the or
        this.bitset.or(filter.getBitSet());
        this.numberOfAddedElements += filter.count();

    }

    public static void resetLastID() {
        lastID = 0;
    }

    /**
     * Set a single bit in the Bloom filter.
     *
     * @param bit
     *                is the bit to set.
     * @param value
     *                If true, the bit is set. If false, the bit is cleared.
     */
    public void setBit(int bit, boolean value) {
        bitset.set(bit, value);
    }

    /**
     * Set the id of the Bloom Filter
     *
     * @param id
     */
    public void setID(int id) {
        this.id = id;
    }

    /**
     * Returns the number of bits in the Bloom filter. Use count() to
     * retrieve the number of inserted elements.
     *
     * @return the size of the bitset used by the Bloom filter.
     */
    public int size() {
        return this.bitSetSize;
    }

    /**
     * Return a string representation of the Bloom filter. For now, it just
     * returns the bitset. TODO
     *
     * @return string representation of the Bloom filter
     */
    @Override
    public String toString() {
        return "ID:" + id + ":" + bitset.cardinality() + ":"
               + bitSetSize; // + ":" + bitset.toString();
    }

    /**
     * Read a single bit from the Bloom filter.
     *
     * @param bit
     *                the bit to read.
     * @return true if the bit is set, false if it is not.
     */
    // public boolean getBit(int bit) {
    // return bitset.get(bit);
    // }

    /**
     * Compute 1- the Cosine similarity to the param filter Cosine
     * similarity = ab/norm(a)*norm(b)
     *
     * @param filter
     * @return 1- cosine similarity to the received Bloom filter
     * @author Adina Crainiceanu
     */
    private double computeCosineDistance(BloomFilter<E> filter) {

        assert bitSetSize == filter.size() : "Different size bitsets in computeCosineDistance: "
        + bitSetSize + " and " + filter.size();

        double distance = 0;
        // find the max index that is not 0
        int maxLength = this.bitset.length();
        BitSet otherBitSet = filter.getBitSet();
        if (otherBitSet.length() > maxLength) {
            maxLength = otherBitSet.length();
        }
        // compute the cardinalities
        int countAND = this.bitset.andcardinality(filter.bitset);
        int count1 = this.bitset.cardinality();
        int count2 = filter.bitset.cardinality();

        // compute distance
        if (count1 > 0 || count2 > 0) {
            distance = 1.0 - countAND
                       / (Math.sqrt(count1) * Math.sqrt(count2));
        }
        return distance;
    }

    /**
     * Compute the Hamming distance between this filter and the one given as
     * param. Hamming distance = number of positions with different value.
     * To compute Hamming distance, we XOR the two bitsets and take the sum
     * or all 1s
     *
     * @param filter
     * @return Hamming distance between this bloom filter and the received
     *         Bloom filter
     * @author Adina Crainiceanu
     */
    private int computeHammingDistance(BloomFilter<E> filter) {

        assert bitSetSize == filter.size() : "Different size bitsets in computeHammingDistance: "
        + bitSetSize + " and " + filter.size();

        return this.bitset.xorcardinality(filter.getBitSet());
    }

    /**
     * Compute the Jaccard distance to the param filter Jaccard distance = 1
     * - Jaccard similarity = 1 - size of intersection/size of union =
     * cardinality(A xor B)/ cardinality (A or B)
     *
     * @param filter
     * @return Jaccard distance to the received Bloom filter
     * @author Adina Crainiceanu
     */
    private double computeJaccardDistance(BloomFilter<E> filter) {

        assert bitSetSize == filter.size() : "Different size bitsets in computeJaccardDistance: "
        + bitSetSize + " and " + filter.size();

        double distance = 0;
        // find the max index that is not 0
        int maxLength = this.bitset.length();
        BitSet otherBitSet = filter.getBitSet();
        if (otherBitSet.length() > maxLength) {
            maxLength = otherBitSet.length();
        }
        int countAND = this.bitset.andcardinality(filter.bitset);
        int countOR = this.bitset.orcardinality(filter.bitset);

        if (countOR > 0) {
            distance = 1.0 - (double) countAND / countOR;
        }
        return distance;
    }

    public static int getLastID() {
        return lastID;
    }


    public static void incrementLastID() {
        lastID++;
    }

    private BitSet bitset;

    private int bitSetSize;

    private double bitsPerElement;

    private int expectedNumberOfFilterElements; // expected (maximum) number
    // of elements to be added
    private int numberOfAddedElements; // number of elements actually added

    public  Hasher h;

    private static int lastID = 0;

    private static final long serialVersionUID = 1L;// D. Lemire: this was
    // missing

    // Add ID so we can keep track of ID - bloom filter correspondence when
    // used for update
    private int id = 0;

    // to the Bloom filter
    private int k;

    // add metric to be used when comparing 2 Bloom filters
    // 1 if Hamming, 2 if Jaccard, 3 if cosine,
    // everything else Hamming
    private int metric = 1;


}
