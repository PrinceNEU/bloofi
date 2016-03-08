package mvm.provenance;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.googlecode.javaewah.datastructure.BitSet;
import com.skjegstad.utils.BloomFilter;

/**
 * This is what Daniel called Bloofi2. Basically, instead of using a tree
 * structure like Bloofi (see BloomFilterIndex), we "transpose" the BitSets.
 *
 *
 * @author Daniel Lemire
 *
 * @param <E>
 */
public final class FlatBloomFilterIndex<E> implements BloomIndex<E> {
    public FlatBloomFilterIndex() {
    }

    @Override
    public int deleteFromIndex(int id, InsDelUpdateStatistics stat) {
        int index = idMap.remove(id);
        idMap.remove(id);
        busy.unset(index);
        if (busy.getWord(index / 64) == 0) {
            for (int k = index / 64 * 64; k < index / 64 * 64 + 64; ++k)
                fromindextoId.remove(k);
            buffer.remove(index / 64);
            busy.removeWord(index / 64);
            for (Map.Entry<Integer, Integer> me : idMap.entrySet()) {
                if (me.getValue().intValue() / 64 >= index / 64) {
                    idMap.put(me.getKey(), me.getValue()
                              .intValue() - 64);
                }
            }
        } else {
            clearBloomAt(index);
        }
        return 0;
    }

    @Override
    public int getBloomFilterSize() {
        if (buffer.isEmpty())
            return 0;
        else
            return buffer.get(0).length;
    }

    @Override
    public int getHeight() {
        return 0;
    }

    @Override
    public Set<Integer> getIDs() {
        return idMap.keySet();
    }

    @Override
    public boolean getIsRootAllOne() {
        return false;
    }

    @Override
    public int getNbChildrenRoot() {
        return 0;// no root
    }

    @Override
    public int getSize() {
        return idMap.size();
    }

    @Override
    public void insertBloomFilter(BloomFilter<E> bf,
                                  InsDelUpdateStatistics stat) {
        if (h != null) {
            if (bf.getHasher() != h)
                throw new RuntimeException(
                    "You are using more than one hasher");
        } else
            h = bf.getHasher();
        int i = busy.nextUnsetBit(0);
        if (i < 0) {
            i = busy.length();
            busy.resize(busy.length() + 64);
            buffer.add(new long[bf.getBitSet().length()]);
        }
        if (i < fromindextoId.size()) {
            fromindextoId.set(i, bf.getID());
        } else { // if(i == fromindextoId.size()) {
            fromindextoId.add(bf.getID());
        }
        setBloomAt(i, bf.getBitSet());
        idMap.put(bf.getID(), i);
        busy.set(i);
    }

    @Override
    public List<Integer> search(E o, SearchStatistics stat) {
        ArrayList<Integer> answer = new ArrayList<Integer>();
        for (int i = 0; i < buffer.size(); ++i) {
            long w = ~0l;
            for (int l = 0; l < h.getNumberOfHashFunctions(); ++l) {
                final int hashvalue = h.hash(o, l);
                w &= buffer.get(i)[hashvalue];
            }

            while (w != 0) {
                long t = w & -w;
                answer.add(fromindextoId.get(i * 64
                                             + Long.bitCount(t - 1)));
                w ^= t;
            }
        }
        return answer;
    }

    @Override
    // this assumes that the bloom filter only received new values
    public int updateIndex(BloomFilter<E> newBloomFilter,
                           InsDelUpdateStatistics stat) {
        if (h != null) {
            if (newBloomFilter.getHasher() != h)
                throw new RuntimeException(
                    "You are using more than one hasher");
        } else
            h = newBloomFilter.getHasher();
        setBloomAt(idMap.get(newBloomFilter.getID()),
                   newBloomFilter.getBitSet());
        return 0;
    }

    // this is like updateIndex except that it does not
    // assume that the BloomFilter was only updated through the addition
    // of values.
    public int replaceIndex(BloomFilter<E> newBloomFilter) {
        if (h != null) {
            if (newBloomFilter.getHasher() != h)
                throw new RuntimeException(
                    "You are using more than one hasher");
        } else
            h = newBloomFilter.getHasher();
        replaceBloomAt(idMap.get(newBloomFilter.getID()),
                       newBloomFilter.getBitSet());
        return 0;
    }

    private void clearBloomAt(int i) {
        final long[] mybuffer = buffer.get(i / 64);
        final long mask = ~(1l << i);
        for (int k = 0; k < mybuffer.length; ++k) {
            mybuffer[k] &= mask;
        }
    }

    private void setBloomAt(int i, BitSet bs) {
        final long[] mybuffer = buffer.get(i / 64);
        if (bs.length() != mybuffer.length)
            throw new RuntimeException("BitSet has unexpected size");
        final long mask = (1l << i);
        for (int k = bs.nextSetBit(0); k >= 0; k = bs.nextSetBit(k + 1)) {
            mybuffer[k] |= mask;
        }
    }

    private void replaceBloomAt(int i, BitSet bs) {
        long[] mybuffer = buffer.get(i / 64);
        if (bs.length() != mybuffer.length)
            throw new RuntimeException("BitSet has unexpected size");
        final long mask = (1l << i);

        for (int k = 0; k < mybuffer.length; ++k) {
            if (bs.get(k))
                mybuffer[k] |= mask;
            else
                mybuffer[k] &= ~mask;
        }
    }


    private ArrayList<Integer> fromindextoId = new ArrayList<Integer>();

    private Hashtable<Integer, Integer> idMap = new Hashtable<Integer, Integer>();

    ArrayList<long[]> buffer = new ArrayList<long[]>(0);

    BitSet busy = new BitSet(0);

    Hasher h;

}
