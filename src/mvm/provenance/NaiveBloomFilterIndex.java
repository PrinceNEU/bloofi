package mvm.provenance;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import com.skjegstad.utils.BloomFilter;

public class NaiveBloomFilterIndex<E> implements BloomIndex<E> {
    TreeMap<Integer,BloomFilter<E>> idMap = new TreeMap<Integer,BloomFilter<E>>();


    @Override
    public int deleteFromIndex(int id, InsDelUpdateStatistics stat) {
        idMap.remove(id);
        return 0;
    }

    @Override
    public int getBloomFilterSize() {
        return idMap.size();
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
        return 0;
    }

    @Override
    public int getSize() {
        return idMap.size();
    }

    @Override
    public void insertBloomFilter(BloomFilter<E> bf,
                                  InsDelUpdateStatistics stat) {
        idMap.put(bf.getID(), bf);
    }

    @Override
    public List<Integer> search(E o, SearchStatistics stat) {
        ArrayList<Integer> al = new ArrayList<Integer>();
        for(BloomFilter<E> bf : idMap.values())
            if(bf.contains(o)) al.add(bf.getID());
        return al;
    }

    @Override
    public int updateIndex(BloomFilter<E> newBloomFilter,
                           InsDelUpdateStatistics stat) {
        idMap.put(newBloomFilter.getID(), newBloomFilter);
        return 0;
    }

}
