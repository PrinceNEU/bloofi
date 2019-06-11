package mvm.provenance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import junit.framework.Assert;
import org.junit.Test;
import com.skjegstad.utils.BloomFilter;

@SuppressWarnings({ "static-method" })
public class FlatTest {

    @Test
    public void basicTest() {
        FlatBloomFilterIndex<Integer> f = new FlatBloomFilterIndex<Integer>();
        Hasher h = new Hasher();
        ArrayList<BloomFilter<Integer>> allbf = new ArrayList<BloomFilter<Integer>>();
        final int M = 1000;
        final int N = 1000;
        Random r = new Random(0);
        for (int k = 0; k < N; k++) {
            BloomFilter<Integer> bf = new BloomFilter<Integer>(h,
                    0.1, M, 1);
            bf.setID(k);
            if (bf.getID() != k)
                throw new RuntimeException("unexpected id " + k
                                           + " " + bf.getID());
            int i;
            for (i = 0; i < M; i += 3) {
                final int v = r.nextInt();
                bf.add(v);
            }
            allbf.add(bf);
            f.insertBloomFilter(bf, new InsDelUpdateStatistics());

        }
        for (int i = 0; i < N + M; ++i) {
            Integer target = new Integer(i);
            List<Integer> ans = f.search(target,
                                         new SearchStatistics());
            List<Integer> ans2 = bruteForce(target, allbf);
            Assert.assertEquals(ans, ans2);
        }
        long bef = System.currentTimeMillis();
        int bogus = 0;
        for (int i = 0; i < N + M; ++i) {
            Integer target = new Integer(i);
            List<Integer> ans = f.search(target,
                                         new SearchStatistics());
            bogus += ans.size();
        }

        long aft = System.currentTimeMillis();
        System.out.println();
        System.out.println("[Time " + (aft - bef) + " for " + bogus
                           + "]");
        System.out.println();
    }



    @Test
    public void basicTestWithDeletion() {
        FlatBloomFilterIndex<Integer> f = new FlatBloomFilterIndex<Integer>();
        Hasher h = new Hasher();
        ArrayList<BloomFilter<Integer>> allbf = new ArrayList<BloomFilter<Integer>>();
        final int M = 1000;
        final int N = 1000;
        Random r = new Random(0);
        for (int k = 0; k < N; k++) {
            BloomFilter<Integer> bf = new BloomFilter<Integer>(h,
                    0.1, M, 1);
            bf.setID(k);
            if (bf.getID() != k)
                throw new RuntimeException("unexpected id " + k
                                           + " " + bf.getID());
            int i;
            for (i = 0; i < M; i += 3) {
                final int v = r.nextInt();
                bf.add(v);
            }
            allbf.add(bf);
            f.insertBloomFilter(bf, new InsDelUpdateStatistics());

        }
        ArrayList<BloomFilter<Integer>> toremove = new ArrayList<BloomFilter<Integer>>();
        for(int k = 0; k < N; k += 3) {
            toremove.add(allbf.get(k));
            f.deleteFromIndex(allbf.get(k).getID(), new InsDelUpdateStatistics());
        }
        allbf.removeAll(toremove);
        for (int i = 0; i < N + M; ++i) {
            Integer target = new Integer(i);
            List<Integer> ans = f.search(target,
                                         new SearchStatistics());
            List<Integer> ans2 = bruteForce(target, allbf);
            Assert.assertEquals(ans, ans2);
        }
        long bef = System.currentTimeMillis();
        int bogus = 0;
        for (int i = 0; i < N + M; ++i) {
            Integer target = new Integer(i);
            List<Integer> ans = f.search(target,
                                         new SearchStatistics());
            bogus += ans.size();
        }

        long aft = System.currentTimeMillis();
        System.out.println();
        System.out.println("[Time " + (aft - bef) + " for " + bogus
                           + "]");
        System.out.println();
        for(BloomFilter<Integer> bf : toremove) {
            allbf.add(bf);
            f.insertBloomFilter(bf, new InsDelUpdateStatistics());
            for (int i = 0; i < N + M; ++i) {
                Integer target = new Integer(i);
                List<Integer> ans = f.search(target,
                                             new SearchStatistics());
                List<Integer> ans2 = bruteForce(target, allbf);
                Collections.sort(ans);
                Collections.sort(ans2);
                Assert.assertEquals(ans, ans2);
            }
        }


    }
    @Test
    public void BatchTestWithDeletion() {
        FlatBloomFilterIndex<Integer> f = new FlatBloomFilterIndex<Integer>();
        Hasher h = new Hasher();
        ArrayList<BloomFilter<Integer>> allbf = new ArrayList<BloomFilter<Integer>>();
        final int M = 1000;
        final int N = 1000;
        Random r = new Random(0);
        for (int k = 0; k < N; k++) {
            BloomFilter<Integer> bf = new BloomFilter<Integer>(h,
                    0.1, M, 1);
            bf.setID(k);
            if (bf.getID() != k)
                throw new RuntimeException("unexpected id " + k
                        + " " + bf.getID());
            int i;
            for (i = 0; i < M; i += 3) {
                final int v = r.nextInt();
                bf.add(v);
            }
            allbf.add(bf);
            f.insertBloomFilter(bf, new InsDelUpdateStatistics());

        }
        ArrayList<BloomFilter<Integer>> toremove = new ArrayList<BloomFilter<Integer>>();
        for(int k = 0; k < N; k += 3) {
            toremove.add(allbf.get(k));
            f.deleteFromIndex(allbf.get(k).getID(), new InsDelUpdateStatistics());
        }
        allbf.removeAll(toremove);
        for (int i = 0; i < N + M; ++i) {
            Integer target = new Integer(i);
            List<Integer> ans = f.search(target,
                    new SearchStatistics());
            List<Integer> ans2 = bruteForce(target, allbf);
            Assert.assertEquals(ans, ans2);
        }
        long bef = System.currentTimeMillis();
        int bogus = 0;
        for (int i = 0; i < N + M; ++i) {
            Integer target = new Integer(i);
            List<Integer> ans = f.search(target,
                    new SearchStatistics());
            bogus += ans.size();
        }

        long aft = System.currentTimeMillis();
        System.out.println();
        System.out.println("[Time " + (aft - bef) + " for " + bogus
                + "]");
        System.out.println();
        for(BloomFilter<Integer> bf : toremove) {
            allbf.add(bf);
            f.insertBloomFilter(bf, new InsDelUpdateStatistics());
            for (int i = 0; i < N + M; ++i) {
                Integer target = new Integer(i);
                List<Integer> ans = f.search(target,
                        new SearchStatistics());
                List<Integer> ans2 = bruteForce(target, allbf);
                Collections.sort(ans);
                Collections.sort(ans2);
                Assert.assertEquals(ans, ans2);
            }
        }


    }
    public static List<Integer> bruteForce(Integer target,
                                           ArrayList<BloomFilter<Integer>> allbf) {
        List<Integer> a = new ArrayList<Integer>();
        for (int k = 0; k < allbf.size(); ++k) {
            if (allbf.get(k).contains(target)) {
                a.add(allbf.get(k).getID());
            }
        }
        return a;
    }

}
