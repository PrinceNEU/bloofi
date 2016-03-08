/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package mvm.provenance;

import com.skjegstad.utils.*;

/**
 *
 * @author adina
 */
public class IDBloomFilterPair<E> {
    public int id;
    public BloomFilter<E> bloomFilter;

    /**
     * Constructor
     * @param id
     * @param bf
     */
    public IDBloomFilterPair(int id, BloomFilter<E> bf) {
        this.id = id;
        this.bloomFilter = bf;
    }

    /**
     * Get id
     * @return id
     */
    public int getID() {
        return this.id;
    }


    public BloomFilter<E> getBloomFilter() {
        return this.bloomFilter;
    }
}

