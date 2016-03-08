package mvm.provenance;

import java.util.ArrayList;
import java.util.Random;

import com.skjegstad.utils.BloomFilter;

public class QuickBenchmark {

    public static void main(String[] args) {
        final int M = 1000;
        final int N = 10000;
        double proba = 0.01;
        basicBenchmark(M,N,proba,1);
        basicBenchmark(M,N,proba,0.1);
        for(int k = 0; k < 10 ; ++k ) {
            basicBenchmark(M,N,proba,0.01);
        }
    }
    public static void basicBenchmark(final int M, final int N, final double proba, final double fillpercentage) {
        FlatBloomFilterIndex<Integer> f = new FlatBloomFilterIndex<Integer>();
        Hasher h = new Hasher();
        ArrayList<BloomFilter<Integer>> allbf = new ArrayList<BloomFilter<Integer>>();
        Random r = new Random(0);
        System.out.println("M = "+M+" N = "+N+" proba = "+proba+" fillpercentage = "+fillpercentage);
        for (int k = 0; k < N; k++) {
            BloomFilter<Integer> bf = new BloomFilter<Integer>(h,
                    proba, M, 1);
            bf.setID(k);
            if (bf.getID() != k)
                throw new RuntimeException("unexpected id " + k
                                           + " " + bf.getID());
            int i;
            for (i = 0; i < M; i += M/Math.max(1, Math.round(fillpercentage * M))) {
                final int v = r.nextInt();
                bf.add(v);
            }
            allbf.add(bf);
            f.insertBloomFilter(bf, new InsDelUpdateStatistics());

        }
        ArrayList<BloomFilter<Integer>> toremove = new ArrayList<BloomFilter<Integer>>();
        for(int k = 0; k < N; k += 3) {
            toremove.add(allbf.get(k));
        }
        long bef,aft;
        bef = System.nanoTime();
        for(BloomFilter<Integer> R : toremove) {
            f.deleteFromIndex(R.getID(), new InsDelUpdateStatistics());
        }
        aft = System.nanoTime();
        System.out.println("delete = "+(aft-bef));
        bef = System.nanoTime();
        for(BloomFilter<Integer> R : toremove) {
            f.insertBloomFilter(R, new InsDelUpdateStatistics());
        }
        aft = System.nanoTime();
        System.out.println("insert = "+(aft-bef));
        aft = System.nanoTime();
        bef = System.nanoTime();
        for(BloomFilter<Integer> R : toremove) {
            R.add(1);
            f.updateIndex(R, new InsDelUpdateStatistics());
        }
        aft = System.nanoTime();
        System.out.println("update = "+(aft-bef));
        bef = System.nanoTime();
        for(BloomFilter<Integer> R : toremove) {
            f.deleteFromIndex(R.getID(), new InsDelUpdateStatistics());
        }
        aft = System.nanoTime();
        System.out.println("delete = "+(aft-bef));

    }

}
