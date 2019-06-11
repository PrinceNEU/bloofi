package mvm.provenance;

import com.googlecode.javaewah.datastructure.BitSet;
import com.skjegstad.utils.BloomFilter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FlatBFMergeBatch<E> implements BloomIndex<E> {
    /**
     * This is what Daniel called Bloofi2. Basically, instead of using a tree
     * structure like Bloofi (see BloomFilterIndex), we "transpose" the BitSets.
     *
     *
     * @author Daniel Lemire
     *
     * @param<E>
     */
        public FlatBFMergeBatch() {
        }

        @Override
        public int deleteFromIndex(int id, InsDelUpdateStatistics stat) {
            int index = idMap.remove(id);
            int counter=Long.bitCount(busy.getWord(index/64))-1;
            idMap.remove(id);
            busy.unset(index);
            //得到ID并从idMap中移除，arrayb中对应的占用解除
            if (busy.getWord(index / 64) == 0) {
                //如果删除之后当前的Flat为空
                for (int k = index / 64 * 64; k < index / 64 * 64 + 64; ++k)
                    fromindextoId.remove(k);
                buffer.remove(index / 64);
                busy.removeWord(index / 64);
                //移除此Flat中的ID，Flat组中的此Flat，arrayb中整体解除占用
                for (Map.Entry<Integer, Integer> me : idMap.entrySet()) {
                    if (me.getValue().intValue() / 64 >= index / 64) {
                        idMap.put(me.getKey(), me.getValue()
                                .intValue() - 64);
                    }
                    //其后的所有BF对应的index数字减少64
                }
            } else {
                clearBloomAt(index);
            }
            if(!flag&&counter<=32&&counter>0){
                flag=true;
                block=index/64;
            }
            if(flag&&counter<=32&&0>counter){
                merge(index/64);
            }
            return 0;
        }
        //删除指定ID的BF
        @Override
        public int getBloomFilterSize() {
            if (buffer.isEmpty())
                return 0;
            else
                return buffer.get(0).length;
        }
        //BF的长度

        @Override
        public int getHeight() {
            return 0;
        }
        //没有高度
        @Override
        public Set<Integer> getIDs() {
            return idMap.keySet();
        }
        // 所包含的BF的ID
        @Override
        public boolean getIsRootAllOne() {
            return false;
        }
        //不存在根结点，不会全1
        @Override
        public int getNbChildrenRoot() {
            return 0;// no root
        }
        //不存在根结点
        @Override
        public int getSize() {
            return idMap.size();
        }
        //BF包含ID的个数
        @Override
        public void insertBloomFilter(BloomFilter<E> bf,
                                      InsDelUpdateStatistics stat) {
            if (h != null) {
                if (bf.getHasher() != h)
                    throw new RuntimeException(
                            "You are using more than one hasher");
            } else
                h = bf.getHasher();
            //不能有不同的hash函数组
            int i = busy.nextUnsetBit(0);
            //得到第一个空位的index
            if (i < 0) {
                i = busy.length();
                busy.resize(busy.length() + 64);
                buffer.add(new long[bf.getBitSet().length()]);
            }
            //没有空位的时候新建一个Flat
            if (i < fromindextoId.size()) {
                fromindextoId.set(i, bf.getID());
            } else { // if(i == fromindextoId.size()) {
                fromindextoId.add(bf.getID());
            }
            //如果是找到的空位则设定该位，否则是新的Flat，追加到最后
            setBloomAt(i, bf.getBitSet());
            //把bf刷入
            idMap.put(bf.getID(), i);
            busy.set(i);
            //修改ID和index的对应关系，arrayb的占用记录
        }
        //将bf插入到BF组中

        @Override
        public List<Integer> search(E o, SearchStatistics stat) {
            ArrayList<Integer> answer = new ArrayList<Integer>();
            for (int i = 0; i < buffer.size(); ++i) {
                long w = ~0l;
                //w初始值为全1
                for (int l = 0; l < h.getNumberOfHashFunctions(); ++l) {
                    final int hashvalue = h.hash(o, l);
                    w &= buffer.get(i)[hashvalue];
                    //W分别与每一个hash值对应的位做与运算
                }

                while (w != 0) {
                    //如果有命中的结果
                    long t = w & -w;
                    //w是奇数时t值为1，w是偶数时t中只有一个1且末尾0与原数相同
                    //奇数t-1是0，计数后为0，代表最后一位是命中的。
                    //偶数t-1是把末尾的0都变成了1，计数得到第几个位是命中的。
                    answer.add(fromindextoId.get(i * 64
                            + Long.bitCount(t - 1)));
                    //bitCount是计数数字对应二进制中有几个1
                    w ^= t;
                    //和0异或是本身，和1异或是相反。相当于把计数过的位置零
                }
            }
            return answer;
        }
        //搜索object，返回结果对应的ID的集合

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
            //替换的BF的hash函数和Flat的hash函数必须相同
            setBloomAt(idMap.get(newBloomFilter.getID()),
                    newBloomFilter.getBitSet());
            //用BF的内容设置BF的index对应Flat位置的BF
            return 0;
        }
        //设置BF

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
            //替换的BF的hash函数和Flat的hash函数必须相同
            replaceBloomAt(idMap.get(newBloomFilter.getID()),
                    newBloomFilter.getBitSet());
            //用BF的内容替换BF的index对应Flat位置的BF
            return 0;
        }
        //替换BF

        private void clearBloomAt(int i) {
            final long[] mybuffer = buffer.get(i / 64);
            final long mask = ~(1l << i);
            for (int k = 0; k < mybuffer.length; ++k) {
                mybuffer[k] &= mask;
            }
        }
        //mask即111...101...111
        //mybuffer是取出buffer对应的那一块。循环抹掉。

        private void setBloomAt(int i, BitSet bs) {
            final long[] mybuffer = buffer.get(i / 64);
            if (bs.length() != mybuffer.length)
                throw new RuntimeException("BitSet has unexpected size");
            final long mask = (1l << i);
            for (int k = bs.nextSetBit(0); k >= 0; k = bs.nextSetBit(k + 1)) {
                mybuffer[k] |= mask;
            }
        }
        //mask即000...010...000
        //mybuffer是取出buffer对应的那一块。循环设置。

        private void replaceBloomAt(int i, BitSet bs) {
            long[] mybuffer = buffer.get(i / 64);
            if (bs.length() != mybuffer.length)
                throw new RuntimeException("BitSet has unexpected size");
            final long mask = (1l << i);

            for (int k = 0; k < mybuffer.length; ++k) {
                if (bs.get(k))
                    mybuffer[k] |= mask;
                    //与0001000或，其余位不变，第i位刷为1
                else
                    mybuffer[k] &= ~mask;
                //与11101111与，其余位不变。第i位为0
            }
        }
        // 用bs替换第i个BF

        private void merge(int blocknum){
            if(blocknum==block){
                return;
            }
            int block1=Math.min(blocknum,block);
            int block2=Math.max(blocknum,block);
            long w= busy.getWord(block1);
            ArrayList<Integer> index=new ArrayList<Integer>();
            while(w!=0){
                long t=w&-w;
                index.add(block1*64+Long.bitCount(t-1));
                w^=t;
            }
            int i=0;
            long[] mybuffer=buffer.get(block1);
            for(;i<index.size();i++){
                int cur_index=index.get(i);
                int cur_id=fromindextoId.get(cur_index);
                busy.unset(cur_index);
                busy.set(i);
                fromindextoId.set(i,cur_id);
                idMap.replace(cur_id,cur_index,i);
                for (int j=0;j<mybuffer.length;j++) {
                    String str = Long.toBinaryString(mybuffer[j]);
                    StringBuilder stb = new StringBuilder(str);
                    stb.setCharAt(i, str.charAt(cur_index));
                    mybuffer[i] = Long.parseLong(stb.toString());
                }
            }
            w= busy.getWord(block2);
            while(w!=0){
                long t=w&-w;
                index.add(block2*64+Long.bitCount(t-1));
                w^=t;
            }
            long[] mybuffer_new=buffer.get(block2);
            for(;i<index.size();i++){
                int cur_index=index.get(i);
                int cur_id=fromindextoId.get(cur_index);
                busy.unset(cur_index);
                busy.set(i);
                fromindextoId.set(i,cur_id);
                idMap.replace(cur_id,cur_index,i);
                for (int j=0;j<mybuffer.length;j++) {
                    String str = Long.toBinaryString(mybuffer[j]);
                    StringBuilder stb = new StringBuilder(str);
                    stb.setCharAt(i, Long.toBinaryString(mybuffer_new[j]).charAt(cur_index));
                    mybuffer[i] = Long.parseLong(stb.toString());
                }
            }
            for (int k = block2 * 64; k < block2* 64 + 64; ++k)
                fromindextoId.remove(k);
            buffer.remove(block2);
            busy.removeWord(block2);
            for (Map.Entry<Integer, Integer> me : idMap.entrySet()) {
                if (me.getValue().intValue() / 64 >= block2) {
                    idMap.put(me.getKey(), me.getValue()
                            .intValue() - 64);
                }
            }
            flag=false;
        }
        private ArrayList<Integer> fromindextoId = new ArrayList<Integer>();
        //由index查到对应falt的真实ID

        private Hashtable<Integer, Integer> idMap = new Hashtable<Integer, Integer>();
        //由真实ID查到对应flat的index

        ArrayList<long[]> buffer = new ArrayList<long[]>(0);
        //Flat的集合

        BitSet busy = new BitSet(0);
        //Array β，64位long型，记录那些位被占用

        Hasher h;
        //hash函数的集合

        boolean flag=false;
        //是否有占用率低于50%的Flat块

        int block;
        //占用率低于50%Flat的块号


}
