/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.skjegstad.utils;

import mvm.provenance.Hasher;

import com.skjegstad.utils.BloomFilter;

/**
 *
 * @author adina
 */
public class TestAC {

    public static void main(String[] args) {
        Hasher h = new Hasher();

        BloomFilter<Integer> bf = new BloomFilter<Integer>(h,0.1,10,1);
        int i;
        for (i = 0; i< 10; i++) {
            bf.add(i);
        }

        for (i = 0; i< 15; i++) {
            if (bf.contains(i)) {
                System.out.println("Bloom filter contains "  + i);
            }
            else {
                System.out.println("Bloom filter does not contain "  + i);
            }
        }
    }

}
