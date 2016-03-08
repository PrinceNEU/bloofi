/*
 * Implementation of the Bloom Filter Index - BlooFI
 * The BloomFilterIndex creates a hierarchical index for Bloom filters.
 * All the Bloom filters indexed have the same size and use the same
 * hashing functions for insertion/ to check for membership
 */
package mvm.provenance;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

import com.skjegstad.utils.*;

/**
 *
 * @author adina
 */
@SuppressWarnings({"unchecked","rawtypes"})
public final class BloomFilterIndex<E> implements BloomIndex<E> {

    private BFINode<E> root;
    private int order;

    // things to help with testing/experiments
    private Hashtable<Integer, BFINode<E>> idMap;
    private List<BloomFilter<E>> bfList;
    private boolean splitFull;

    @Override
    public Set<Integer> getIDs() {
        return idMap.keySet();
    }

    /**
     * Constructs an empty Bloom Filter Index with just the root
     */
    public BloomFilterIndex(int order, BloomFilter<E> sampleFilter,
                            boolean splitFull) {
        root = null;
        this.order = order;
        this.idMap = new Hashtable<Integer, BFINode<E>>();
        this.bfList = new ArrayList<BloomFilter<E>>();
        this.splitFull = splitFull;

        // initialize the BFI with a root with an all-zero bloom filter
        BloomFilter<E> zeroFilter = createZeroBloomFilter(sampleFilter);
        this.root = new BFINode<E>(zeroFilter, this.order,
                                   this.splitFull);
    }

    /**
     * Constructs a Bloom Filter Index for the Bloom Filters received as
     * param
     *
     */
    public BloomFilterIndex(List<BloomFilter<E>> bfList, int order,
                            boolean splitFull, InsDelUpdateStatistics stat) {
        this.order = order;
        this.splitFull = splitFull;
        this.idMap = new Hashtable<Integer, BFINode<E>>();
        this.bfList = bulkLoad(bfList, stat);

    }

    /**
     * Get the ID- BFINode map
     *
     * @return
     */
    // public Hashtable<Integer, BFINode<E>> getIDBFINodeMap(){
    // return this.idMap;
    // }

    /**
     * Return the height of the bloom Filter Index - A tree with only root
     * and leaves has height 1
     *
     * @return
     */
    @Override
    public int getHeight() {
        return root.getLevel();
    }

    /**
     * Return the number of nodes in this Bloom Filter Index
     *
     * @return
     */
    @Override
    public int getSize() {
        return this.root.getTreeSize();
    }

    /**
     * Return the size - number of bits in a Bloom Filter indexed by this
     * index
     *
     * @return
     */
    @Override
    public int getBloomFilterSize() {
        return this.root.getBloomFilterSize();
    }

    /**
     * Return the number of children of the root
     */
    @Override
    public int getNbChildrenRoot() {
        return this.root.children.size();
    }

    /**
     * Return whether all the bits in the root are 1 or not
     *
     * @return
     */
    @Override
    public boolean getIsRootAllOne() {
        return this.root.value.isFull();
    }

    /**
     * Get the list of all BloomFIlters indexed by this index
     *
     * @return
     */
    public List<BloomFilter<E>> getBFList() {
        return this.bfList;
    }

    /**
     * Update the Bloom Filter Index due to the new value for BloomFilter
     * with given ID
     *
     * @param newBloomFilter
     * @return
     */
    @Override
    public int updateIndex(BloomFilter<E> newBloomFilter,
                           InsDelUpdateStatistics stat) {
        int id = newBloomFilter.getID();
        // find the node corresponding to the id
        BFINode<E> node = this.idMap.get(id);
        if (node == null) {
            System.err
            .println("ERROR: Cound not find node with ID "
                     + id);
            return -1;
        }
        updateValueToTheRoot(node, newBloomFilter, stat);
        return 0;

    }

    /**
     * Delete the Bloom filter with the given ID from the index
     */
    @Override
    public int deleteFromIndex(int id, InsDelUpdateStatistics stat) {
        // find the node corresponding to the id
        BFINode<E> node = this.idMap.get(id);
        if (node == null) {
            System.err
            .println("ERROR delete: Could not find node with ID "
                     + id);
            return -1;
        }
        else {
            //System.err
            //       .println("OK delete: node with ID "
            //              + id);
        }
        deleteNode(node, stat);

        // delete from the bflist and idMap
        this.idMap.remove(id);
        this.bfList.remove(node.value);

        return 0;

    }

    /**
     * Delete the given node from the index. The deletion moves bottom up
     *
     * @param node
     */
    private void deleteNode(BFINode<E> childNode,
                            InsDelUpdateStatistics stat) {

        if (this.root.children.size() < 2) {
            System.err.println("ERROR: nb children of root is "
                               + this.root.children.size());
            System.err.println(this.toString());
            assert false;
        }

        // remove node from the list of children in its parent
        BFINode<E> node = childNode.parent;
        boolean ok = node.children.remove(childNode);
        assert ok;
        stat.nbBFNodesAccessed += 2; // get parent, plus parent node
        // accessed

        // check whether the tree height needs to be reduced
        // this is the case if the parent is the root and
        // only one non-leaf child remained
        if (node == this.root && node.children.size() == 1) {
            if (!node.children.get(0).isLeaf()) {
                this.root = node.children.get(0);
                this.root.parent = null;
                stat.nbBFNodesAccessed++; // changed root
                return;
            }
        }

        stat.nbBFNodesAccessed++; // check for merge
        // check if underflow at the parent
        if (!node.needMerge()) {
            // no underflow, update values
            recomputeValueToTheRoot(node, stat);
        } else {
            // try to re-distribute
            // get a sibling of the node
            int index = node.parent.children.indexOf(node);
            stat.nbBFNodesAccessed += 2; // check position to find
            // sibling
            BFINode<E> sibling;
            boolean isRightSibling = false;
            // try the right sibling. if does not exist, try the
            // left one
            if (index + 1 < node.parent.children.size()) {
                isRightSibling = true;
                sibling = (BFINode<E>) node.parent.children
                          .get(index + 1);
            } else {
                if (index - 1 < 0) {
                    System.err.println("Error "
                                       + this.toString() + " node: "
                                       + node.toString()
                                       + "childNode: "
                                       + childNode.toString());
                    assert false;
                }

                isRightSibling = false;
                sibling = (BFINode<E>) node.parent.children
                          .get(index - 1);
            }
            stat.nbBFNodesAccessed++; // get the sibling
            // see if the sibling can redistribute
            stat.nbBFNodesAccessed++; // check if can redistribute
            if (sibling.canRedistribute()) {
                redistribute(node, sibling, isRightSibling,
                             stat);
            } else {
                merge(node, sibling, isRightSibling, stat);
                // delete the node
                deleteNode(node, stat);

            }
        }

        return;
    }

    /**
     * Redistribute the entries between 2 siblings and update values to the
     * root
     *
     * @param node
     * @param sibling
     */
    private void redistribute(BFINode<E> node, BFINode<E> sibling,
                              boolean isRightSibling, InsDelUpdateStatistics stat) {

        stat.nbRedistributes++;

        // get nb entries in both;
        int nbChildren = node.children.size() + sibling.children.size();
        int nbChildren1 = nbChildren / 2;
        int nbChildren2 = nbChildren - nbChildren1;
        int nbChildrenToGive = sibling.children.size() - nbChildren2;

        stat.nbBFNodesAccessed += 2; // accessed siblings to get size

        BFINode<E> childToMove;
        if (isRightSibling) {
            // move first nbChildrenToGive from sibling to node
            for (int i = 0; i < nbChildrenToGive; i++) {
                childToMove = sibling.children.remove(0);
                node.children.add(childToMove);
                childToMove.parent = node;
            }
        } else {
            // move last nbChildrenToGive from sibling to node
            for (int i = 0; i < nbChildrenToGive; i++) {
                childToMove = sibling.children
                              .remove(sibling.children.size() - 1);
                node.children.add(0, childToMove);
                childToMove.parent = node;
            }

        }
        // update stat
        stat.nbBFNodesAccessed += nbChildrenToGive + 2; // accessed
        // node,
        // sibling, and
        // all new
        // children

        // recompute values for all nodes involved, up to the root
        sibling.recomputeValue(stat);
        recomputeValueToTheRoot(node, stat);
    }

    /**
     * Merge the entries between 2 siblings; all the entries from the node
     * are given to the sibling, since the node has fewer children value of
     * sibling will be updated to be the OR of all children
     *
     * @param node
     * @param sibling
     */
    private static  void merge(final BFINode node, final BFINode sibling,
                               final boolean isRightSibling, final InsDelUpdateStatistics stat) {

        stat.nbMerges++;

        // get nb entries to move;
        int nbChildrenToGive = node.children.size();

        stat.nbBFNodesAccessed++; // accessed node to get size

        BFINode childToMove;
        if (isRightSibling) {
            // move last nbChildrenToGive from node to sibling
            for (int i = 0; i < nbChildrenToGive; i++) {
                childToMove = (BFINode) node.children
                              .remove(node.children.size() - 1);
                sibling.children.add(0, childToMove);
                sibling.value.orBloomFilter(childToMove.value);
                childToMove.parent = sibling;
            }

        } else {
            // move first nbChildrenToGive from node to sibling
            for (int i = 0; i < nbChildrenToGive; i++) {
                childToMove = (BFINode) node.children.remove(0);
                sibling.children.add(childToMove);
                sibling.value.orBloomFilter(childToMove.value);
                childToMove.parent = sibling;
            }

        }

        // update stat
        stat.nbBFNodesAccessed += nbChildrenToGive + 2; // accessed
        // node,
        // sibling, and
        // all new
        // children
        stat.nbBFAccessed += nbChildrenToGive + 1; // add new children
        // to the value

    }

    /**
     * Bulk load a Bloom Filter Index. It changes the root field.
     */
    private List<BloomFilter<E>> bulkLoad(List<BloomFilter<E>> mbfList,
                                          InsDelUpdateStatistics stat) {
        if(mbfList.size()>0) {
            BloomFilter<E> base = mbfList.get(0);
            for(BloomFilter<E> c : mbfList) {
                if(c.h != base.h) throw new RuntimeException("You need to use the same hasher throughout!");
            }
        }
        // "sort" the received list of Bloom filters according to some
        // metric
        ArrayList<BloomFilter<E>> copy = new ArrayList<BloomFilter<E>>(mbfList);
        mbfList = sort(copy);

        // keep pointer to right-most leaf
        BFINode<E> rightmost;

        // initialize the BFI with a root with an all-zero bloom filter
        BloomFilter<E> sampleFilter = mbfList.get(0);
        BloomFilter<E> zeroFilter = createZeroBloomFilter(sampleFilter);
        this.root = new BFINode<E>(zeroFilter, this.order,
                                   this.splitFull);
        rightmost = this.root;

        // insert each Bloomfilter in the rightmost leaf
        // if needed, split
        BFINode<E> current;
        for (BloomFilter<E> bf : mbfList) {
            // create a BFINode for this BloomFilter
            current = new BFINode<E>(bf, this.order, this.splitFull);

            // insert the ID - node mapping into the hashtable
            this.idMap.put(bf.getID(), current);

            // insert it into the rightmost leaf
            rightmost = insertRight(true, rightmost, current,
                                    rightmost, stat);

        }

        return mbfList;
    }

    /**
     * Search for an object in the BFI and return the matching Bloom filters
     *
     * @param o
     * @return
     */
    public ArrayList<BloomFilter<E>> searchBloomFilters(E o,
            SearchStatistics stat) {
        return findMatches(this.root, o, stat);
    }

    @Override
    public List<Integer> search(E o, SearchStatistics stat) {
        ArrayList<BloomFilter<E>> x = searchBloomFilters(o, stat);
        ArrayList<Integer> ans = new ArrayList<Integer>(x.size());
        for (BloomFilter<E> bf : x) {
            ans.add( bf.getID());
        }
        return ans;
    }

    /**
     * Search for an object in the subtree rooted at given node and return
     * the Bloom filters matching the value
     *
     * @param node
     * @param o
     * @return
     */
    public ArrayList<BloomFilter<E>> findMatches(BFINode<E> node, E o,
            SearchStatistics stat) {
        ArrayList<BloomFilter<E>> result = new ArrayList<BloomFilter<E>>();
        stat.nbBFChecks++;
        // if node does not matches the object,
        // return empty set, else check the descendants
        if (!node.value.contains(o)) {
            return result;
        }

        // if this node is a leaf, just return the value
        if (node.isLeaf()) {

            result.add(node.value);
            return result;
        }

        // if not leaf, check the descendants
        for (int i = 0; i < node.children.size(); i++) {
            result.addAll(findMatches(node.children.get(i), o, stat));
        }

        return result;
    }
    /**
     * Search for an object in the subtree rooted at given node and return
     * the Bloom filters matching the value, it does so using a naive
     * approach where only leaf nodes are checked
     *
     * @param node
     * @param o
     * @return
     */
    public ArrayList<BloomFilter<E>> naivefindMatches(BFINode<E> node, E o,
            SearchStatistics stat) {

        ArrayList<BloomFilter<E>> result = new ArrayList<BloomFilter<E>>();

        // increase the number of bloom filters checks, since this node
        // will be checked
        stat.nbBFChecks++;
        if(node.isLeaf()) {
            if(node.value.contains(o)) {
                result.add(node.value);
            }
            return result;
        }
        for (int i = 0; i < node.children.size(); i++) {
            result.addAll(naivefindMatches(node.children.get(i), o, stat));
        }
        return result;
    }



    /**
     * Create an all-zero Bloom filter with the same size as given filter
     *
     * @param filter
     * @return Empty Bloom filter with the size and expected number of
     *         elements same as the input filter
     */
    private static BloomFilter createZeroBloomFilter(BloomFilter filter) {

        /*
         * Old implementation: does not work in extreme cases due to
         * double math where (a/double(b) * b is not always a) For
         * example, if expected number of elements is 100,000,000 and
         * falsePosProb is 0.01
         */
        /*
         * int bitSetSize = filter.size(); int expectedNumberOfElements
         * = filter.getExpectedNumberOfElements();
         * System.out.println("BitSetSize is: " + bitSetSize);
         * System.out.println("ExpectedNbOfElements: " +
         * expectedNumberOfElements);
         * System.out.println("bitsPerElement: " +
         * bitSetSize/(double)expectedNumberOfElements);
         * System.out.println("Calculated raw bitSetSize: " +
         * bitSetSize/(double)expectedNumberOfElements *
         * expectedNumberOfElements);
         * System.out.println("Math.ceil bitSetSize: " +
         * Math.ceil(bitSetSize/(double)expectedNumberOfElements *
         * expectedNumberOfElements));
         * System.out.println("(int)Math.ceil bitSetSize: " +
         * (int)Math.ceil(bitSetSize/(double)expectedNumberOfElements *
         * expectedNumberOfElements));
         *
         * BitSet zeroBitSet = new BitSet(bitSetSize);
         *
         * //create all zeros BloomFilter BloomFilter<E> zeroFilter =
         * new BloomFilter<E>(bitSetSize, expectedNumberOfElements, 0,
         * zeroBitSet);
         *
         * assert bitSetSize == zeroFilter.size(); assert filter.size()
         * == zeroFilter.size();
         */

        // public BloomFilter(double c, int n, int k) {
        double bitsPerElement = filter.getExpectedBitsPerElement();
        int expectedNumberOfFilterElements = filter
                                             .getExpectedNumberOfElements();
        int k = filter.getK();
        int metric = filter.getMetric();

        // create all zeros BloomFilter
        BloomFilter zeroFilter = new BloomFilter(filter.h,
                bitsPerElement, expectedNumberOfFilterElements, k,
                metric);

        // assert zeroFilter.getExpectedNumberOfElements() ==
        // expectedNumberOfFilterElements;
        // assert zeroFilter.getK() == k;
        // assert zeroFilter.getExpectedBitsPerElement() ==
        // bitsPerElement;

        assert filter.size() == zeroFilter.size();
        if(filter.size() != zeroFilter.size()) throw new RuntimeException("size mismatch?");
        if(zeroFilter.h != filter.h) throw new RuntimeException("different hasher?");

        return zeroFilter;
    }

    /**
     * Sort the given list according to some distance We use Hamming
     * distance for now and sort such that first element is closest to zero,
     * the next one is closest to the first, and so on
     *
     * @param bfList
     * @return
     */
    private ArrayList<BloomFilter<E>> sort(List<BloomFilter<E>> bf) {
        return sortIterative(bf);
    }

    /**
     * Sort the input list based on Hamming distance between objects First
     * object will be the closest to "000...", then the closest to first...
     *
     * @param c
     * @return
     */
    private  ArrayList<BloomFilter<E>> sortIterative(
        final List<BloomFilter<E>> bf) {

        System.out.print("| sortIterative start");

        long startTime = System.currentTimeMillis();

        ArrayList<BloomFilter<E>> sorted = new ArrayList<BloomFilter<E>>();

        BloomFilter<E> first = bf.get(0);

        // create all zeros BloomFilter
        BloomFilter<E> current = createZeroBloomFilter(first);

        BloomFilter<E> closest;
        int closestIndex;

        // Iterativelly, find the BloomFilter closest to current filter,
        // move it to the sorted list
        // the closest filter becomes the current
        while (!bf.isEmpty()) {
            // find the bloom filter closest to the current on
            closestIndex = current.findClosest(bf);
            closest = bf.get(closestIndex);
            // add it to the sorted list
            sorted.add(closest);
            // remove it from the initial list
            bf.remove(closestIndex);
            // make it the new current
            current = closest;
        }

        long endTime = System.currentTimeMillis();
        long diffTime = endTime - startTime;
        System.out.print("| sortIterative end");
        System.out.print("| Sorting time millis| " + diffTime);

        return sorted;
    }

    /**
     * Insert a new child into "current" node as last (rightmost) child
     * Return the possibly new rightmost index node above the leaf level
     *
     * @param newChild
     * @return new rightmost index node above leaf level
     */
    private BFINode<E> insertRight(boolean isInBFI, BFINode<E> current,
                                   BFINode<E> newChild, BFINode<E> rightmost,
                                   InsDelUpdateStatistics stat) {

        // create array of children if none exists
        if (current.children == null) {
            current.children = new ArrayList<BFINode<E>>();
        }

        // add the new child to the right
        current.children.add(newChild);
        newChild.parent = current;
        stat.nbBFNodesAccessed += 2; // current and new child link

        // update the value to be the current value or child value
        if (!isInBFI) {
            current.value.orBloomFilter(newChild.value);
            stat.nbBFAccessed += 2;
        }
        // if child inserted is a new leaf, update all parent values to
        // the root
        // if child inserted is not a leaf, no need to update the
        // parents,
        // since they already have the correct value
        if (newChild.isLeaf()) {
            updateValueToTheRoot(current, newChild.value, stat);
        }

        stat.nbBFNodesAccessed++; // check for split
        // check if need to split, if no, just return the old rightmost
        // node
        if (!current.needSplit()) {
            return rightmost;
        }

        // else, split the node
        rightmost = splitRight(current, rightmost, stat);

        return rightmost;
    }

    // check for bugs
    public void validate() {
        aggregateChildren(root);
    }

    // aggregate children and checks consistency, used by validate
    private BloomFilter<E> aggregateChildren(BFINode<E> node) {
        if (node.children == null) {
            return node.value;// nothing to check
        }

        BloomFilter<E> first = this.getBFList().get(0);

        BloomFilter<E> current = createZeroBloomFilter(first);
        if(current.h != node.value.h) throw new RuntimeException("different hasher?");

        for (BFINode<E> c : node.children) {
            if(current.h != c.value.h) throw new RuntimeException("different hasher?");
            BloomFilter<E> r = aggregateChildren(c);
            current.getBitSet().or(r.getBitSet());
        }
        if (!node.value.getBitSet().equals(current.getBitSet()))
            throw new RuntimeException("bug "
                                       + node.children.size() + " "
                                       + current.getBitSet().cardinality() + " "
                                       + node.value.getBitSet().cardinality());
        return node.value;
    }

    @Override
    public void insertBloomFilter(BloomFilter<E> bf,
                                  InsDelUpdateStatistics stat) {
        if(root.value.h != bf.h)
            throw new RuntimeException("different hasher?");

        // create new BFINode for this BloomFilter
        BFINode<E> newBFINode = new BFINode<E>(bf, this.order,
                                               this.splitFull);

        // insert the ID - node mapping into the hashtable
        this.idMap.put(bf.getID(), newBFINode);
        this.bfList.add(bf);

        // special case when this is the first child of the root
        if (root.children == null) {
            root.children = new ArrayList<BFINode<E>>();

            // add the new child to the right
            root.children.add(newBFINode);

            newBFINode.parent = root;

            // update the value to be the current value or child
            // value
            root.value.orBloomFilter(bf);

            // update stats
            stat.nbBFNodesAccessed++; // accessed the root
            stat.nbBFNodesAccessed++; // accessed the new node
            stat.nbBFAccessed += 2; // accessed root and new bloom
            // filter values

        } else {
            insert(root, newBFINode, stat);

        }
    }

    /**
     * Insert a new child into sub-tree rooted at "current" node Return null
     * of pointer to new child if split occurred
     *
     * @param newChild
     * @return
     */
    // private BFINode<E> insertRight(boolean isInBFI, BFINode<E> current,
    // BFINode<E> newChild, BFINode<E> rightmost){
    private BFINode<E> insert(BFINode<E> current, BFINode<E> newChild,
                              InsDelUpdateStatistics stat) {

        stat.nbBFNodesAccessed++;// current node accessed

        // if node is not leaf, need to direct the search for
        if (!current.isLeaf()) {
            // update the value of the current node, since it will
            // insert into that subtree
            current.value.orBloomFilter(newChild.value);
            stat.nbBFAccessed += 2; // current and new child values

            // find child closest to newChild and insert there
            BFINode<E> closestChild = newChild.findClosest(
                                          current.children, stat);
            // insert into that subtree
            BFINode<E> newSibling = insert(closestChild, newChild,
                                           stat);
            // if newSibling is null (no split), return null
            if (newSibling == null) {
                return null;
            }
            // there was a split;
            else {
                // check whether new root is needed
                if (current.parent == null) {
                    assert (current == root);
                    // root was split, create a new root
                    BFINode<E> newRoot = new BFINode<E>(
                        createZeroBloomFilter(current.value),
                        this.order, this.splitFull);
                    newRoot.value
                    .orBloomFilter(current.value);
                    newRoot.value
                    .orBloomFilter(newSibling.value);
                    newRoot.children = new ArrayList<BFINode<E>>();
                    newRoot.children.add(current);
                    current.parent = newRoot;
                    newRoot.children.add(newSibling);
                    newSibling.parent = newRoot;
                    this.root = newRoot;

                    // update stats
                    stat.nbBFAccessed += 3;
                    stat.nbBFNodesAccessed += 3;
                    return null;
                }
                // if this is not the root
                else {
                    newSibling = insertEntryIntoParent(
                                     newSibling, current, stat);
                    return newSibling;
                }
            } // end split or not
        }
        // if current is leaf, need to insert into its parent
        else {
            BFINode<E> newSibling = insertEntryIntoParent(newChild,
                                    current, stat);
            return newSibling;
        }

    }

    /**
     * Insert a new child in the parent of the provided node
     *
     * @param newChild
     * @param node
     * @return null, if the parent did not split, newNode otherwise
     */
    private BFINode<E> insertEntryIntoParent(BFINode<E> newChild,
            BFINode<E> node, InsDelUpdateStatistics stat) {
        // insert into the node's parent, after this node

        // System.out.println("CHECK: insertEntryIntoParent: " +
        // node.toString());
        // find the position of current node among its siblings
        int index = node.parent.children.indexOf(node);
        stat.nbBFNodesAccessed++; // access parent

        // System.out.println("CHECK: Found current node at position " +
        // index + " in the parent's children list");
        // insert the new child after this one
        node.parent.children.add(index + 1, newChild);
        newChild.parent = node.parent;
        stat.nbBFNodesAccessed += 2; // access parent and new sibling

        // check if split is needed
        stat.nbBFNodesAccessed++;
        if (!node.parent.needSplit()) {
            return null;
        }
        // else, need to split the node

        return split(node.parent, stat);
    }

    private BFINode<E> split(BFINode<E> current, InsDelUpdateStatistics stat) {
        // sanity check: current node should have 2*d +1 children
        assert current.children != null
        && current.children.size() >= 2 * this.order + 1 : "Split not needed in split"
        + current;

        BFINode<E> newNode;
        BFINode<E> newChild;
        // initialize the new BFINode with an all-zero bloom filter
        BloomFilter<E> sampleFilter = current.value;
        BloomFilter<E> zeroFilter = createZeroBloomFilter(sampleFilter);
        newNode = new BFINode<E>(zeroFilter, this.order, this.splitFull);
        newNode.children = new ArrayList<BFINode<E>>();

        stat.nbSplits++; // increase nb splits

        // insert the last half of the current children list into the
        // new node
        for (int i = this.order + 1; i < current.children.size(); i++) {
            // get the new child
            newChild = current.children.get(i);
            // add the new child to the right
            newNode.children.add(newChild);
            newChild.parent = newNode;

            newNode.value.orBloomFilter(newChild.value);

        }

        stat.nbBFNodesAccessed += newNode.children.size() + 1; // update
        // parent
        // info
        stat.nbBFAccessed += newNode.children.size() + 1; // or current
        // child value

        // remove the last half of the children for the current node
        current.children.subList(this.order + 1,
                                 current.children.size()).clear();
        stat.nbBFNodesAccessed++; // accessed current
        // update the value of current node to be the or of its reduced
        // set of children
        current.recomputeValue(stat);

        return newNode;
    }

    /**
     * Update the value of the current node and its ancestors to contain the
     * new value
     *
     * @param current
     * @param newValue
     */
    private void updateValueToTheRoot(BFINode<E> current,
                                      BloomFilter<E> newValue, InsDelUpdateStatistics stat) {

        assert current != null;
        // update value of current node
        current.value.orBloomFilter(newValue);
        stat.nbBFAccessed += 2;
        // if needed, recursively update the parent
        if (current.parent != null) {
            updateValueToTheRoot(current.parent, newValue, stat);
        }
    }

    /**
     * Recompute all values from the current node to the root
     *
     * @param current
     * @param newValue
     */
    private void recomputeValueToTheRoot(BFINode<E> current,
                                         InsDelUpdateStatistics stat) {

        assert current != null;
        // update value of current node
        current.recomputeValue(stat);

        // if needed, recursively update the parent
        if (current.parent != null) {
            recomputeValueToTheRoot(current.parent, stat);
        }
    }

    /**
     * Split the current node and return the possibly new rightmost index
     * node
     *
     * @param root
     * @return
     */
    private BFINode<E> splitRight(BFINode<E> current, BFINode<E> rightmost,
                                  InsDelUpdateStatistics stat) {

        // sanity check: current node should have 2*d +1 children
        assert current.children != null
        && current.children.size() >= 2 * this.order + 1 : "Split not needed in splitRight"
        + current;

        stat.nbSplits++; // increase splits

        BFINode<E> newNode;
        // initialize the new BFINode with an all-zero bloom filter
        BloomFilter<E> sampleFilter = current.value;
        BloomFilter<E> zeroFilter = createZeroBloomFilter(sampleFilter);
        newNode = new BFINode<E>(zeroFilter, this.order, this.splitFull);

        // insert the last half of the current children list into the
        // new node
        BFINode<E> receivedRight;
        for (int i = this.order + 1; i < current.children.size(); i++) {
            receivedRight = insertRight(false, newNode,
                                        current.children.get(i), rightmost, stat);
            assert receivedRight == rightmost;
        }

        // remove the last half of the children for the current node
        current.children.subList(this.order + 1,
                                 current.children.size()).clear();

        stat.nbBFNodesAccessed++; // changed current children

        // update the value of current node to be the or of its reduced
        // set of children
        current.recomputeValue(stat);

        // if current != root, insert the new sibling into the parent
        if (current.parent != null) {
            receivedRight = insertRight(true, current.parent,
                                        newNode, rightmost, stat);
            assert receivedRight == rightmost; // because the
            // rightmost node
            // above the leaf
            // level should not
            // change when
        }
        // otherwise need to create a new root
        else {
            // assert that current is root
            assert current == this.root : "splitRight: Split of current with no parent but not root";

            BFINode<E> newRoot;
            // initialize the new BFINode with an all-zero bloom
            // filter
            BloomFilter<E> sampleFilter1 = current.value;
            BloomFilter<E> zeroFilter1 = createZeroBloomFilter(sampleFilter1);
            newRoot = new BFINode<E>(zeroFilter1, this.order,
                                     this.splitFull);
            rightmost = insertRight(false, newRoot, current,
                                    rightmost, stat);
            rightmost = insertRight(false, newRoot, newNode,
                                    rightmost, stat);
            this.root = newRoot;

        }

        // update rightmost if current node that split was the rightmost
        // node
        if (current == rightmost) {
            rightmost = newNode;
        }

        return rightmost;
    }

    /**
     * TODO Show a representation of the BFI
     *
     * @return
     */
    @Override
    public String toString() {
        if (this.root == null) {
            return "null";
        }
        // return this.root.toString();
        return this.root.printTree();
    }

    /**
     * The node in a Bloom Filter Index
     */
    private static class BFINode<EL> {

        BloomFilter<EL> value;
        int order1;
        BFINode parent; // need parent info since updates propagate up
        ArrayList<BFINode<EL>> children;

        // if splitFull is true, the condition for split is just the
        // number of children
        // otherwise, we use the "optimization" where we do not split
        // the nodes if
        // they are full, since the children might also be full
        boolean splitFull1 = true;

        BFINode(BloomFilter<EL> value, int order, boolean splitFull) {
            this.value = value;
            this.order1 = order;
            this.splitFull1 = splitFull;
            parent = null;
            children = null;
        }

        /**
         * Return the level of this node in the index - leafs have level
         * 0, root has highest level
         *
         * @return
         */
        int getLevel() {
            if (isLeaf()) {
                // if leaf, return 0
                return 0;
            } else {
                // compute the level of children and add 1
                BFINode<EL> child = this.children.get(0);
                int childLevel = child.getLevel();
                return 1 + childLevel;
            }
        }

        /**
         * Return the number of nodes in the subtree rooted at this node
         *
         * @return
         */
        int getTreeSize() {
            // if leaf, return 1
            if (isLeaf()) {
                return 1;
            }
            // else, computet the number of nodes in each subtree
            // and add them up
            else {
                int size = 1; // this node
                for (BFINode<EL> currentNode : this.children) {
                    size += currentNode.getTreeSize();
                }
                return size;
            }
        }

        /**
         * Return the number of bits in the Bloom filter
         *
         * @return
         */
        int getBloomFilterSize() {
            return this.value.size();
        }

        /**
         * Recompute the value of the BloomFilter to be the or of its
         * children
         */
        void recomputeValue(InsDelUpdateStatistics stat) {

            assert this.value != null : "value in recomputeValue is null ";

            this.value.clear();
            for (BFINode<EL> currentNode : this.children) {
                this.value.orBloomFilter(currentNode.value);
                stat.nbBFAccessed++; // used current value
            }
            stat.nbBFAccessed++; // computed this value

        }

        /**
         * Return true if this node is a leaf-level node (no children)
         * and false otherwise
         *
         * @return true is no children, false otherwise
         */
        public boolean isLeaf() {
            return this.children == null || this.children.isEmpty();
        }

        /**
         * Return true if a split is needed: the number of children is
         * at least 2*order +2 and value is not all 1s
         *
         * @return
         */
        public boolean needSplit() {

            if (splitFull1) {
                return !(this.children == null || this.children
                         .size() <= 2 * this.order1);
            } else {
                return !(this.children == null
                         || this.children.size() <= 2 * this.order1 || this.value
                         .isFull());
            }
        }

        /**
         * Return true if this is not the root (a root never needs
         * merge) and the number of children is less than order + 1
         *
         * @return
         */
        public boolean needMerge() {
            return this.parent != null
                   && this.children.size() < this.order1;
        }

        public boolean canRedistribute() {
            return this.children.size() > this.order1;
        }

        /**
         * Find the BFINode in the list with the BloomFilter value
         * "closest" to this value. This is used to direct the search
         * during insert. If the distance between this bloom filter and
         * several filters in the list is the same, it should return one
         * of the closest filters, at random
         *
         * @param nodeList
         * @return
         */
        public BFINode<EL> findClosest(ArrayList<BFINode<EL>> nodeList,
                                       InsDelUpdateStatistics stat) {

            int index = findClosestIndex(nodeList, stat);
            if (index >= 0) {
                return nodeList.get(index);
            } else {
                return null;
            }

        }

        /**
         * Find the index of BFINode in the list with the BloomFilter
         * value "closest" to this value. This is used to direct the
         * search during insert. If the distance between this bloom
         * filter and several filters in the list is the same, it should
         * return one of the closest filters, at random
         *
         * @param list
         *                of BFINodes to compare with
         * @return
         */
        private int findClosestIndex(ArrayList<BFINode<EL>> nodeList,
                                     InsDelUpdateStatistics stat) {

            assert nodeList != null : "Empty list in BFINode findCLosest";

            // return null if no element to compare with
            if (nodeList.isEmpty())
                return -1;

            BFINode<EL> currentNode;

            // initialize min distance to be distance to first
            // element
            currentNode = nodeList.get(0);
            double minDistance = this.value
                                 .computeDistance(currentNode.value);
            stat.nbBFAccessed += 2; // this value and currentNode
            // value
            int minIndex = 0;
            double currentDistance;

            // loop through all elements to find the closest
            for (int i = 1; i < nodeList.size(); i++) {
                currentNode = nodeList.get(i);
                currentDistance = this.value
                                  .computeDistance(currentNode.value);
                stat.nbBFAccessed += 2; // this value and
                // currentNode value

                // replace current min if found a smaller one,
                // or if same, randomly replace the current one
                // TODO: might need to work on that probability:
                // if x nodes are at the same distance to
                // this.value, each node should be returned with
                // prob 1/x
                if (currentDistance < minDistance
                        || (minDistance - currentDistance < 0.00001 && Math
                            .random() < 1.0 / nodeList
                            .size())) {
                    minDistance = currentDistance;
                    minIndex = i;
                }

            }

            return minIndex;

        }

        @Override
        public String toString() {
            return this.value.toString();
        }

        /**
         * Print this node and the sub-tree rooted at this node
         *
         * @return
         */
        public String printTree() {

            // print value
            String output = "\n" + this.value.toString();
            // if no children, return
            if (this.children == null || this.children.isEmpty()) {
                return output;
            }

            output += "\n(";
            BFINode<EL> currentNode;
            // else recursively print the children sub-trees
            for (int i = 0; i < this.children.size(); i++) {
                currentNode = this.children.get(i);
                output += currentNode.printTree();
            }
            output += "\n)";

            return output;
        }
    }
}
