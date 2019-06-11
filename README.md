#基于Lemire的Bloofi论文源码的修改

便于理解将主类加了中文注释解释过程

FlatBFMerge是基于源码实现了动态合并的代码。
在原论文中并未对删除进行优化，只在该flat块完全删空后释放掉该空间，极端情况下每一个flat块只能保存一个bloom filter，导致空间利用率过低。
此代码中加入一个flag用于记录是否存在空间利用率低于50%的flat块
当删除时判断本flat块是否删至32个bloom filter以下，若是，则判断flag是否为真，若为真则有两个低于50%的块，进行合并，flag归位未假，否则将flag置为真。
实测空间利用率约为70%-80%

除此之外还可以定期进行全局的合并，代码后期上传。

#以下是原项目Readme

# Bloofi: A java implementation of multidimensional Bloom filters

Bloom filters are probabilistic data structures commonly used for approximate membership problems in many areas of Computer Science (networking, distributed systems, databases, etc.). With the increase in data size and distribution of data, problems arise where a large number of Bloom filters are available, and all them need to be searched for potential matches. As an example, in a federated cloud environment, each cloud provider could encode the information using Bloom filters and share the Bloom filters with a central coordinator. The problem of interest is not only whether a given element is in any of the sets represented by the Bloom filters, but which of the existing sets contain the given element. This problem cannot be solved by just constructing a Bloom filter on the union of all the sets. Instead, we effectively have a multidimensional Bloom filter problem: given an element, we wish to receive a list of candidate sets where the element might be.
To solve this problem, we consider 3 alternatives. Firstly, we can naively check many Bloom filters. Secondly, we propose to organize the Bloom filters in a hierarchical index structure akin to a B+ tree, that we call Bloofi. Finally, we propose another data structure that packs the Bloom filters in such a way as to exploit bit-level parallelism, which we call Flat-Bloofi.
Our theoretical and experimental results show that Bloofi and Flat-Bloofi provide scalable and efficient solutions alternatives to search through a large number of Bloom filters.

### Prerequisites

- Java
- ant http://ant.apache.org/ (on a Mac, you can install ant with ``brew install ant`` after installing ``brew``)


We build on an existing Bloom filter library (https://github.com/magnuss/java-bloomfilter) by Magnus Skjegstad which we embedded and modified. We also use junit and Hamcrest which we include as jar files for your convenience.

### Usage

We provide the necessary software to reproduce our experiments. The software includes unit testing. The documentation is minimal and the software is not meant for production use. It is provided mostly for research purposes and as a way to promote the ideas.

Building and running unit tests :

```
ant
```


Main class to run the experiments: ``mvm.provenance.TestAC``.

Sample run:
```
java -Xms7168m -Xmx7168m mvm.provenance.TestAC -bloofi -falsePositiveProb 0.01 -expectedNbElemInBloomFilter 10000 -initialNbElemInBloomFilter 100 -nbBloomFilters 1000 -bloofiOrder 2 -constructionMethod i -nbYesSearches 50000 -nbNoSearches 50000 -splitAllOneNodesIfOverflow false -metric Hamming -nbBFInsertsDeletes 0 -nbUpdates 0 -nonOverlappingRanges true -nbRuns 10
```

Input parameters, with default values:
```
-bloofi | -bloofi2 | -naive #this param is required and specifies which type of index tructure to construct - original bloofi (bloofi), flat bloofi (bloofi 2), or just store all the Bloom filters without indexing, in a map (naive)
-falsePositiveProb falsePosProb #Default: 0.01
-expectedNbElemInBloomFilter expectedNbElemInFilter #Default 10000
-initialNbElemInBloomFilter initialNbElemInFilter #Default 100
-nbBloomFilters  nbBFs #Default 1000
-bloofiOrder order #Default 2
-constructionMethod b | i (bulk or incremental) #Default i
-nbYesSearches nbyesSearches #searches for elements known to be in the Bloom filters. Default 1000
-nbNoSearches nbNoSearches #searches for elements not in the Bloom filters. Default 1000
-splitAllOneNodesIfOverflow true | false #if there is an overflow in the Bloofi index, and the value of the node is already all bits to one, should that node still split, or not? Default false
-metric Hamming | Jaccard | Cosine #metric used to compare similarity between two Bloom filters. Default Hamming
-nbBFInsertsDeletes nbBloomFiltersInsertsOrDeletes #Default 0
-nbUpdates nbOfElementsToBeInsertedDuringUpdateInEachFilter #Default 0
-nonOverlappingRanges true | false #if true, each Bloom filter i gets the integers in [(i-1)* initialNbElemInFilter,i*actualNbElemInFilter); if false, each bloom filter gets initialNbElemInFilter random integers from a random rangeDefault true
-nbRuns numberOfRunsForExperiments #Default 10
```

### References

> Adina Crainiceanu and Daniel Lemire. Bloofi: Multidimensional Bloom Filters.  Information Systems,Volume 54, December 2015, pp.311-324 http://arxiv.org/abs/1501.01941 http://www.sciencedirect.com/science/article/pii/S0306437915000125

> Adina Crainiceanu. Bloofi: A Hierarchical Bloom Filter Index with Applications to Distributed Data Provenance. 2nd International Workshop on Cloud Intelligence (Cloud-I 2013) collocated with the 39th International Conference in Very Large Data Bases VLDB. Riva del Garda, Italy, 2013


### License

Because we built on Magnus Skjegstad's Bloom filter library, we use the lesser GPL software license.
