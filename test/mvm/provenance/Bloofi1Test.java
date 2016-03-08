package mvm.provenance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import junit.framework.Assert;
import org.junit.Test;
import com.skjegstad.utils.BloomFilter;

@SuppressWarnings({ "static-method" })
public class Bloofi1Test {

    @SuppressWarnings("unchecked")
    public void btest(int order, boolean splitfull) {
        Hasher h = new Hasher(0);
        final int M = 1000;
        final int N = 1000;
        double proba = 0.01;
        int metric = 1;
        BloomFilter<Integer> proto = new BloomFilter<Integer>(h, proba,
                M, metric);
        BloomFilterIndex<Integer> f = new BloomFilterIndex<Integer>(
            order, proto, splitfull);
        ArrayList<BloomFilter<Integer>> allbf = new ArrayList<BloomFilter<Integer>>();
        Random r = new Random(0);
        for (int k = 0; k < N; k++) {
            BloomFilter<Integer> bf = new BloomFilter<Integer>(h,
                    proba, M, metric);
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
        f.validate();
        for (int i = 0; i < N + M; ++i) {
            Integer target = new Integer(i);
            List<Integer> ans = f.search(target,
                                         new SearchStatistics());
            Collections.sort(ans);
            List<Integer> ans2 = bruteForce(target, allbf);
            if (!ans.equals(ans2)) {
                System.out
                .println("By brute force, I expected "
                         + ans2 + " but Bloofi got me "
                         + ans);
            }
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
        System.out.println("[Time "+(aft-bef)+" for "+bogus+"]");
        System.out.println();
        List<BloomFilter<Integer>> x = ((List<BloomFilter<Integer>>) allbf.clone());
        f = new BloomFilterIndex<Integer>(x, order,
                                          splitfull, new InsDelUpdateStatistics());
        if(x.size() != allbf.size()) throw new RuntimeException("Bloofi ate? "+x.size()+ " "+allbf.size());
        f.validate();
        for (int i = 0; i < N + M; ++i) {
            Integer target = new Integer(i);
            List<Integer> ans = f.search(target,
                                         new SearchStatistics());
            Collections.sort(ans);
            List<Integer> ans2 = bruteForce(target, allbf);
            if (!ans.equals(ans2)) {
                System.out
                .println("By brute force, I expected "
                         + ans2 + " but Bloofi got me "
                         + ans);
            }
            Assert.assertEquals(ans, ans2);
        }

    }

    @Test
    public void basicTest() {
        for (int order = 1; order < 10; ++order) {
            btest(order, true);
            btest(order, false);
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
