package mvm.provenance;

import com.skjegstad.utils.BloomFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Iterator;

/**
 *
 * @author adina
 */
public class TestAC {

    public static void main(String[] args) {

        if (args.length < 1) {
            System.out.println("Wrong parameters");
            printHelp();
            return;
        }
        Hasher h = new Hasher();

        boolean Bloofi2 = false;

        boolean Naive = false;

        boolean Bloofi = false;

        //get input parameters
        double falsePosProb = 0.01;
        int expectedNbElemInFilter = 10000;
        int initialNbElemInFilter = 100;

        int nbBFs = 1000;
        int order = 2;

        String bulkOrIncremental = "i";

        int nbYesSearches = 1000; //Integer.parseInt(args[6]);
        int nbNoSearches = 1000; //Integer.parseInt(args[7]);

        boolean splitFull = false;

        int metric = 1; // metric used for BlooFI construction 1 is Hamming distance

        int nbInsDel = 0; //do numInsDel after the tree is full and done searches, to see cost

        int nbUpdates = 0;

        boolean buildIndex = true; // build the index or leave just the list of Bloom filters to be searched

        boolean nonRandomRanges = true; //if true, each Bloom filter i
        //gets the integers in [(i-1)* initialNbElemInFilter,i*actualNbElemInFilter)
        //if false, each bloom filter gets initialNbElemInFilter random integers from a random range

        int nbRuns = 10; //default times to rub the experiment
        boolean collectStats = true;

        //read the type of index to construct from command line
        String indexType = args[0];
        if (indexType.equalsIgnoreCase("-bloofi2")) {
            Bloofi2 = true;
        }
        else if (indexType.equalsIgnoreCase("-naive")) {
            Naive = true;
        }
        else if (indexType.equalsIgnoreCase("-bloofi")) {
            Bloofi = true;
        }
        else {
            System.out.println("Incorrect first parameter " + indexType + " See example usage below");
            printHelp();
            return;
        }

        //read command line param in the form -paramName paramValue
        int iArgs = 1;
        String paramName;
        while (iArgs + 1 < args.length) {
            paramName = args[iArgs];
            if (paramName.equalsIgnoreCase("-falsePositiveProb")) falsePosProb = Double.parseDouble(args[iArgs+1]);
            else if (paramName.equalsIgnoreCase("-expectedNbElemInBloomFilter")) expectedNbElemInFilter = Integer.parseInt(args[iArgs+1]);
            else if (paramName.equalsIgnoreCase("-initialNbElemInBloomFilter")) initialNbElemInFilter = Integer.parseInt(args[iArgs+1]);
            else if (paramName.equalsIgnoreCase("-nbBloomFilters")) nbBFs = Integer.parseInt(args[iArgs+1]);
            else if (paramName.equalsIgnoreCase("-bloofiOrder")) order = Integer.parseInt(args[iArgs+1]);
            else if (paramName.equalsIgnoreCase("-constructionMethod")) bulkOrIncremental = args[iArgs+1];
            else if (paramName.equalsIgnoreCase("-nbYesSearches")) nbYesSearches = Integer.parseInt(args[iArgs+1]);
            else if (paramName.equalsIgnoreCase("-nbNoSearches")) nbNoSearches = Integer.parseInt(args[iArgs+1]);
            else if (paramName.equalsIgnoreCase("-splitAllOneNodesIfOverflow")) splitFull = Boolean.parseBoolean(args[iArgs+1]);
            else if (paramName.equalsIgnoreCase("-metric")) {
                String metricString = args[iArgs+1];
                if (metricString.equalsIgnoreCase("Hamming")) metric = 1;
                else if (metricString.equalsIgnoreCase("Jaccard")) metric = 2;
                else if (metricString.equalsIgnoreCase("Cosine")) metric= 3;
                else {
                    System.out.println("Wrong parameter value: " + paramName + " " + metricString);
                    printHelp();
                    return;
                }
            }
            else if (paramName.equalsIgnoreCase("-nbBFInsertsDeletes")) nbInsDel = Integer.parseInt(args[iArgs+1]);
            else if (paramName.equalsIgnoreCase("-nbUpdates")) nbUpdates = Integer.parseInt(args[iArgs+1]);
            else if (paramName.equalsIgnoreCase("-nonOverlappingRanges")) nonRandomRanges = Boolean.parseBoolean(args[iArgs+1]);
            else if (paramName.equalsIgnoreCase("-nbRuns")) nbRuns = Integer.parseInt(args[iArgs+1]);
            else {
                System.out.println("Unknown parameter " + paramName);
                printHelp();
                return;
            }
            iArgs+=2;
        }

        //check param validity
        if (!(falsePosProb > 0 && falsePosProb < 1)) {
            System.err.println("False Positive Probability should be between 0 and 1");
            printHelp();
            return;
        }

        if (!(expectedNbElemInFilter >= initialNbElemInFilter)) {
            System.err.println("Expected number of elements in filter should be larger than the initial number of elements in filter");
            printHelp();
            return;
        }

        if (nbBFs <= 0) {
            System.err.println("Number of BloomFilters to index should be greater than 0");
            printHelp();
            return;
        }

        if (order < 2) {
            System.err.println("Order of BloomFilter Index should be at least 2 but it is "+order);
            printHelp();
            return;
        }

        if (!(metric == 1 || metric == 2 || metric == 3)) {
            System.err.println("Metric should be 1 2 or 3");
            printHelp();
            return;
        }

        if (nbUpdates >= initialNbElemInFilter) {
            System.err.println("Number updates should be lower than initial nb elem in filter");
            printHelp();
            return;
        }

        if (!buildIndex && nbUpdates > 0) {
            System.err.println("If no index build, nbUpdates should be 0");
            printHelp();
            return;
        }

        if (collectStats) {
            System.out.print("Input params |");
            for (int i = 0; i < args.length; i++) {
                System.out.print(" " + args[i]);
            }
            System.out.println();

        }



        for (int runNb = 0; runNb < nbRuns; runNb++) {

            int[] insertedValues;
            int nextIndexInArray = 0; //this is the next index available in the array
            int maxValueInserted = Integer.MAX_VALUE;

            //if random ranges, need to keep track of inserted values for experiments
            insertedValues = new int[nbBFs * initialNbElemInFilter];

            /*******Measure Effect of Updates ***********/
            /* if nbUpdates > 0, we first construct the index based on
            initialNbElemInfilter - nbUpdates elements, then insert
             * nbUpdates elem in each Bloom filter and update the BlooFI tree
             */
            int nbElemInFilterToConstructTree = initialNbElemInFilter - nbUpdates;


            //create a list of Bloom filters
            List<BloomFilter<Integer>> bfList = new ArrayList<BloomFilter<Integer>>();
            BloomFilter<Integer> current;

            for (int i = 0; i < nbBFs; i++) {
                // current = createBloomFilterAll(falsePosProb,expectedNbElemInFilter, i*initialNbElemInFilter,(i+1)*initialNbElemInFilter-1, metric);
                if (nonRandomRanges) {
                    current = createBloomFilterAll(h,falsePosProb,
                                                   expectedNbElemInFilter,
                                                   i * initialNbElemInFilter,
                                                   i * initialNbElemInFilter + nbElemInFilterToConstructTree - 1,
                                                   metric);
                } else {
                    current = createBloomFilterRandom(h,falsePosProb,
                                                      expectedNbElemInFilter,
                                                      nbElemInFilterToConstructTree,
                                                      metric,
                                                      insertedValues,
                                                      nextIndexInArray);
                    //update the next index in the array of values available
                    nextIndexInArray += nbElemInFilterToConstructTree;
                }
                bfList.add(current);
            }

            /*
                    System.out.println("InsertedValues are: ");
                    for (int j = 0; j < insertedValues.length; j++){
                        System.out.print (insertedValues[j] + " ");
                    }
                    System.out.println(" With length " + insertedValues.length);
            */

            //if no index needed, just search through the list
            if (!buildIndex) {

                System.out.print("| Nb Bloom filters |" + nbBFs
                                 + "| Nb yes searches| " + nbYesSearches);

                int nbFound;

                if (nonRandomRanges) {
                    maxValueInserted = initialNbElemInFilter * nbBFs;
                    nbFound = searchSomeElementsInBFList(bfList, 0, maxValueInserted, nbYesSearches);
                } else {
                    nbFound = searchSomeElementsInBFList(bfList, insertedValues, nbYesSearches);
                }
                System.out.print("| Number linear searches with non-empty results | " + nbFound);

                //search for elements not indexed

                System.out.print("| Nb no searches| " + nbNoSearches);

                nbFound = searchSomeElementsInBFList(bfList, maxValueInserted, Integer.MAX_VALUE, nbNoSearches);

                System.out.print("| Number linear searches with non-empty results (false positives)| " + nbFound);

                return;
            }


            long startTime, endTime, diffTime;
            InsDelUpdateStatistics insStat = new InsDelUpdateStatistics();

            //create an index based on the Bloom filters
            BloomIndex<Integer> bfi;


            if ((!Bloofi2) && (!Naive) && bulkOrIncremental.equalsIgnoreCase("b")) {
                // Create the index by using the bulkLoad functionality
                if (!collectStats) {
                    System.out.println("Start creating the index using the bulk load functionality. This might take a while.");
                }
                startTime = System.currentTimeMillis();
                bfi = new BloomFilterIndex<Integer>(bfList, order, splitFull, insStat);
                endTime = System.currentTimeMillis();
            } else {

                //Create the index by inserting the Bloom Filters one by one
                if (!collectStats) {
                    System.out.println("Start creating the index by inserting the filters one by one");
                }

                System.out.print("|na|na|na|na"); //na for sorting time
                startTime = System.currentTimeMillis();
                current = new BloomFilter<Integer>(h,falsePosProb, expectedNbElemInFilter, metric);
                if(Naive) {
                    System.out.print("| Using naive Bloom index");
                    bfi = new NaiveBloomFilterIndex<Integer>();
                } else if(Bloofi2) {
                    System.out.print("| Using Bloofi2");
                    bfi = new FlatBloomFilterIndex<Integer>();
                } else {
                    System.out.print("| Using Bloofi");
                    bfi = new BloomFilterIndex<Integer>(order, current, splitFull);
                }
                for (int i = 0; i < nbBFs; i++) {
                    current = bfList.get(i);
                    //System.out.println("Main: inserting filter i = " + i);
                    bfi.insertBloomFilter(current, insStat);
                }
                endTime = System.currentTimeMillis();
            }


            if (collectStats) {
                diffTime = endTime - startTime;
                System.out.print("| Index construction time| " + diffTime);
                System.out.print("| Index height| " + bfi.getHeight()
                                 + "| Nb nodes| " + bfi.getSize()
                                 + "| Filter size| " + bfi.getBloomFilterSize()
                                 + "| IsRootAllOne| " + bfi.getIsRootAllOne()
                                 + "| Nb children of root| " + bfi.getNbChildrenRoot()
                                 + "| Ins stats: nbBFAccessed| " + insStat.nbBFAccessed
                                 + "| Ins stats: nbBFNodesAccessed| " + insStat.nbBFNodesAccessed
                                 + "| Ins stats: nbSplits| " + insStat.nbSplits
                                 + "| Ins stats: nbMerges| " + insStat.nbMerges
                                 + "| Ins stats: nbRedistributes| " + insStat.nbRedistributes);

            }

//        bfList = null;
            //      Set<Integer>bfSet =bfi.getIDs();



            //testDelete(bfi, nbBFs, initialNbElemInFilter);

            //Print the bfi
            //System.out.println("Main: resulting index is: " + bfi.toString());

            //test the updateIndex method
            /*
            //insert one more element in "first" filter, update BFI and see later if we can find it
            //get Bloom Filter
            current = bfList.get(0);
            //add one element
            System.out.println("BloomFilter before update" + current.toString());
            current.add(nbBFs*initialNbElemInFilter);
            System.out.println("BloomFilter after update" + current.toString());
            //updateBFI
            bfi.updateIndex(current.getID(), current);


            //search for inserted element
            elem = nbBFs*initialNbElemInFilter;
            stat = new SearchStatistics();
            results = bfi.search(elem,stat);

            System.out.println("Search for " + elem + " Results in nb steps: " + stat.nbBFChecks);
            System.out.println(results);
            assert results.size() > 0;
             */


            InsDelUpdateStatistics updateStat = new InsDelUpdateStatistics();
            long totalUpdateTimeMilis = 0;
            int nbBFsUpdated = 0;

            if (initialNbElemInFilter != nbElemInFilterToConstructTree) {
                nbBFsUpdated = nbBFs;
                //for each Bloom filter in the list, add some elements and update BlooFI
                for (int i = 0; i < nbBFs; i++) {
                    //get current from the list
                    current = bfList.get(i);
                    //add more elements
                    for (int j = i * initialNbElemInFilter + nbElemInFilterToConstructTree; j < (i + 1) * initialNbElemInFilter; j++) {
                        current.add(j);
                    }
                    //update BlooFI
                    startTime = System.currentTimeMillis();
                    bfi.updateIndex(current, updateStat);
                    endTime = System.currentTimeMillis();

                    diffTime = endTime - startTime;
                    totalUpdateTimeMilis += diffTime;
                }
            }

            //output update statistics
            System.out.print("| Total BF updates | " + nbBFsUpdated
                             + "| Total update time milis| " + totalUpdateTimeMilis
                             + updateStat.toString());

            //bfList = bfi.getBFList();



            //Search for nbYesSEarches elements
            if (!collectStats) {
                System.out.println("Start searching for " + nbYesSearches
                                   + " elements inserted in any of the bloom filters");
            }


            System.out.print("| Nb Bloom filters |" + nbBFs
                             + "| Nb yes searches| " + nbYesSearches);



            int nbFound;
            if (nonRandomRanges) {
                maxValueInserted = initialNbElemInFilter * nbBFs;
                nbFound = searchSomeElements(bfi, 0, maxValueInserted, nbYesSearches);
            } else {
                maxValueInserted = Integer.MAX_VALUE / 2;
                nbFound = searchSomeElements(bfi, insertedValues, nbYesSearches);
            }
            System.out.print("| Number searches with non-empty results | " + nbFound);

            //search for elements not indexed
            if (!collectStats) {
                System.out.println("Start searching for " + nbNoSearches
                                   + " elements NOT inserted in any of the bloom filters");
            }

            System.out.print("| Nb no searches| " + nbNoSearches);

            nbFound = searchSomeElements(bfi, maxValueInserted, Integer.MAX_VALUE, nbNoSearches);

            System.out.print("| Number searches with non-empty results (false positives)| " + nbFound);

            //do nbInsDel inserts and deletes
            int nbCreatedBFs = nbBFs;
            InsDelUpdateStatistics insertStat = new InsDelUpdateStatistics();
            InsDelUpdateStatistics deleteStat = new InsDelUpdateStatistics();
            int insertedBFs = 0;
            int deletedBFs = 0;
            int bfListSize = 0;
            int randomIndex = 0;
            long totalInsertTimeMilis = 0;
            long totalDeleteTimeMilis = 0;

            Set<Integer> bfIDs;

            for (int i = 0; i < nbInsDel; i++) {
                //do one insert
                if (nonRandomRanges) {
                    current = createBloomFilterAll(h,falsePosProb,
                                                   expectedNbElemInFilter,
                                                   nbCreatedBFs * initialNbElemInFilter,
                                                   (nbCreatedBFs + 1) * initialNbElemInFilter - 1,
                                                   metric);
                } else {
                    current = createBloomFilterRandom(h,falsePosProb,
                                                      expectedNbElemInFilter,
                                                      initialNbElemInFilter,
                                                      metric,
                                                      null,
                                                      0);
                }


                startTime = System.currentTimeMillis();
                bfi.insertBloomFilter(current, insertStat);
                endTime = System.currentTimeMillis();

                totalInsertTimeMilis += (endTime - startTime);

                nbCreatedBFs++; // will use this for range for newly inserted Bfs
                insertedBFs++;

                //do one delete
                bfIDs = bfi.getIDs();
                // bfList = bfi.getIDs();// bfi.getBFList();
                //bfListSize = bfList.size();
                bfListSize = bfIDs.size();
                if (bfListSize > 1) {
                    //get a random position
                    randomIndex = (int) Math.floor(Math.random() * bfListSize);
                    //find the ID at that position
                    int counter = -1;
                    Integer bfID = null;
                    Iterator<Integer> it = bfIDs.iterator();
                    while (it.hasNext() && counter < randomIndex) {
                        counter++;
                        bfID = it.next();
                    }
                    if (bfID == null) {
                        System.err.println("ERROR in TestAC: null ID to delete");
                    }
                    //delete that Bf from index
                    startTime = System.currentTimeMillis();
                    //bfi.deleteFromIndex(bfList.get(randomIndex).getID(), deleteStat);
                    bfi.deleteFromIndex(bfID.intValue(), deleteStat);
                    endTime = System.currentTimeMillis();

                    totalDeleteTimeMilis += (endTime - startTime);
                    deletedBFs++;
                }
            }//end for nbInsDel

            //print collected stats
            System.out.println("| Total BF inserts | " + insertedBFs
                               + "| Total insert time milis| " + totalInsertTimeMilis
                               + insertStat.toString()
                               + "|Total BF deletes | " + deletedBFs
                               + "| Total delete time milis| " + totalDeleteTimeMilis
                               + deleteStat.toString());

        } //end run experiments

    }

    private static void printHelp() {
        System.out.println("Input parameters: "
                           + " -bloofi | -bloofi2 | -naive"
                           + " -falsePositiveProb falsePosProb"
                           + " -expectedNbElemInBloomFilter expectedNbElemInFilter"
                           + " -initialNbElemInBloomFilter initialNbElemInFilter"
                           + " -nbBloomFilters  nbBFs"
                           + " -bloofiOrder order"
                           + " -constructionMethod b | i (bulk or incrementl)"
                           + " -nbYesSearches nbyesSearches"
                           + " -nbNoSearches nbNoSearches"
                           + " -splitAllOneNodesIfOverflow true | false"
                           + " -metric Hamming | Jaccard | Cosine"
                           + " -nbBFInsertsDeletes nbBloomFiltersInsertsOrDeletes"
                           + " -nbUpdates nbOfElementsToBeInsertedDuringUpdateInEachFilter"
                           + " -nonOverlappingRanges true | false"
                           + " -nbRuns numberOfRunsForExperiments");
        System.out.println("Ex. -bloofi -falsePositiveProb 0.01 "
                           + " -expectedNbElemInBloomFilter 1000 "
                           + " -initialNbElemInBloomFilter 10"
                           + " -nbBloomFilters 1000 "
                           + " -bloofiOrder 4 "
                           + " -constructionMethod i"
                           + " -nbRuns 10");

    }

    /**
     * Test the delete method by deleting all nodes but one from Bloofi
     * After each delete, test that remaining elements can still be found.
     * This metho assumes that bloom filter i contained elements from
     * i*initialNbELementsInFIlter to (i+1)*initialNbElementsInFilter -1
     * @param bfi
     * @param nbBFs
     * @param initialNbElemInFilter
     */
    /*    private static void testDelete(BloomFilterIndex<Integer> bfi,
                int nbBFs,
                int initialNbElemInFilter) {

            InsDelUpdateStatistics stat = new InsDelUpdateStatistics();

            ArrayList<BloomFilter<Integer>> bfList;

            bfList = bfi.getBFList();
            int i = 0;
            while (bfList.size() > 1) {
                //test the delete method
                System.out.println("Try delete node with ID " + bfList.get(0).getID());
                bfi.deleteFromIndex(bfList.get(0).getID(), stat);
                System.out.println("Search deleted elements : ");
                searchAllElements(bfi, 0, (i + 1) * initialNbElemInFilter);
                System.out.println("Search remaining elements: ");
                searchAllElements(bfi, (i + 1) * initialNbElemInFilter, initialNbElemInFilter * nbBFs);
                bfList = bfi.getBFList();
                i++;

                //the deleteFromIndex should reduce the size of the BFList,
                //if it does not, we could have infinite loop
                if (i > nbBFs) {
                    assert false;
                }
            }
        }*/

    /**
     * Search for nbInteger elements in the [startRange, endRange)
     * @param bfi
     * @param startRange
     * @param endRange
     * @param nbSearches
     * @return number of searches that returned some result
     */
    private static int searchSomeElements(BloomIndex<Integer> bfi,
                                          int startRange,
                                          int endRange,
                                          int nbSearchesToDo) {


        int minSearchSteps = 10000000;
        int maxSearchSteps = 0;
        long sumSearchSteps = 0;
        int nbSearches = 0;
        int nbFound = 0;
        int elem;
        SearchStatistics stat = new SearchStatistics();

        long sumSearchTime = 0;

        //for (elem = 0; elem < initialNbElemInFilter *nbBFs; elem++){
        for (int i = 0; i < nbSearchesToDo; i++) {

            //pick a random element in [startRange, endRange)
            elem = (int) (Math.floor(Math.random() * (endRange - startRange) + startRange));

            stat.clear();
            long startTime = System.currentTimeMillis();
            //List<BloomFilter<Integer>> results = bfi.search(elem, stat);
            List<Integer> results = bfi.search(elem, stat);
            long endTime = System.currentTimeMillis();
            long diffTime = endTime - startTime;
            sumSearchTime += diffTime;

            //System.out.println("Search for " + elem + " Results in nb steps: " + stat.nbBFChecks);
            //System.out.println("Number results: " + results.size());
            //System.out.println(results);
            if (results.size() > 0) {
                nbFound++;
            }

            nbSearches++;
            if (stat.nbBFChecks < minSearchSteps) {
                minSearchSteps = stat.nbBFChecks;
            }
            if (stat.nbBFChecks > maxSearchSteps) {
                maxSearchSteps = stat.nbBFChecks;
            }
            sumSearchSteps += stat.nbBFChecks;
        }

        System.out.print(" |Min steps| " + minSearchSteps
                         + "| Max steps | " + maxSearchSteps
                         + "| Avg steps | " + (double) sumSearchSteps / nbSearches
                         + "| Avg time millis | " + (double) sumSearchTime / nbSearches
                         + "| Total time millis | " + sumSearchTime
                         + "| Nb searches | " + nbSearches);

        return nbFound;
    }

    /**
     * Search for nbInteger elements in the [startRange, endRange)
     * @param bfi
     * @param startRange
     * @param endRange
     * @param nbSearches
     * @return number of searches that returned some result
     */
    private static int searchSomeElements(BloomIndex<Integer> bfi,
                                          int[] values,
                                          int nbSearchesToDo) {


        int minSearchSteps = 10000000;
        int maxSearchSteps = 0;
        long sumSearchSteps = 0;
        int nbSearches = 0;
        int nbFound = 0;
        int nbResults = 0;
        int elem;
        SearchStatistics stat = new SearchStatistics();

        long sumSearchTime = 0;

        //for (elem = 0; elem < initialNbElemInFilter *nbBFs; elem++){
        for (int i = 0; i < nbSearchesToDo; i++) {

            //pick a random element in [startRange, endRange)
            //elem = (int)(Math.floor(Math.random()*(endRange-startRange)+ startRange));

            //pick a random element in the array of values
            int index = (int) Math.floor(Math.random() * values.length);
            elem = values[index];

            stat.clear();
            long startTime = System.currentTimeMillis();
            //List<BloomFilter<Integer>> results = bfi.search(elem, stat);
            List<Integer> results = bfi.search(elem, stat);

            long endTime = System.currentTimeMillis();
            long diffTime = endTime - startTime;
            sumSearchTime += diffTime;

            //System.out.println("Search for " + elem + " Results in nb steps: " + stat.nbBFChecks);
            //System.out.println("Number results: " + results.size());
            //System.out.println(results);
            if (results.size() > 0) {
                nbFound++;
            }
            nbResults += results.size();

            nbSearches++;
            if (stat.nbBFChecks < minSearchSteps) {
                minSearchSteps = stat.nbBFChecks;
            }
            if (stat.nbBFChecks > maxSearchSteps) {
                maxSearchSteps = stat.nbBFChecks;
            }
            sumSearchSteps += stat.nbBFChecks;
        }

        System.out.print(" |Min steps| " + minSearchSteps
                         + "| Max steps | " + maxSearchSteps
                         + "| Avg steps | " + (double) sumSearchSteps / nbSearches
                         + "| Avg time millis | " + (double) sumSearchTime / nbSearches
                         + "| Total time millis | " + sumSearchTime
                         + "| Nb searches | " + nbSearches
                         + "| Nb results | " + nbResults);

        return nbFound;
    }

    /**
     * Search in the given bfList for nbInteger elements in the [startRange, endRange)
     * @param bfList
     * @param startRange
     * @param endRange
     * @param nbSearches
     * @return number of searches that returned some result
     */
    private static int searchSomeElementsInBFList(List<BloomFilter<Integer>> bfList,
            int startRange,
            int endRange,
            int nbSearchesToDo) {


        int minSearchSteps = 10000000;
        int maxSearchSteps = 0;
        int sumSearchSteps = 0;

        int minSearchStepsToFirst = 10000000;
        int maxSearchStepsToFirst = 0;
        int sumSearchStepsToFirst = 0;

        int nbSearches = 0;
        int nbFound = 0;
        int elem;
        SearchStatistics stat = new SearchStatistics();

        long sumSearchTime = 0;
        long sumSearchTimeToFirst = 0;

        //for (elem = 0; elem < initialNbElemInFilter *nbBFs; elem++){
        for (int i = 0; i < nbSearchesToDo; i++) {

            //pick a random element in [startRange, endRange)
            elem = (int) (Math.floor(Math.random() * (endRange - startRange) + startRange));

            //search for ALL filters matching that element
            stat.clear();
            long startTime = System.currentTimeMillis();
            ArrayList<BloomFilter<Integer>> results = searchAllInBFList(bfList, elem, stat);
            long endTime = System.currentTimeMillis();
            long diffTime = endTime - startTime;
            sumSearchTime += diffTime;

            //System.out.println("Search for " + elem + " Results in nb steps: " + stat.nbBFChecks);
            //System.out.println("Number results: " + results.size());
            //System.out.println(results);
            if (results.size() > 0) {
                nbFound++;
            }

            nbSearches++;
            if (stat.nbBFChecks < minSearchSteps) {
                minSearchSteps = stat.nbBFChecks;
            }
            if (stat.nbBFChecks > maxSearchSteps) {
                maxSearchSteps = stat.nbBFChecks;
            }
            sumSearchSteps += stat.nbBFChecks;

            //search for FIRST filter matching that element
            stat.clear();
            startTime = System.currentTimeMillis();
            results = searchFirstInBFList(bfList, elem, stat);
            endTime = System.currentTimeMillis();
            diffTime = endTime - startTime;
            sumSearchTimeToFirst += diffTime;

            //nbResults already increased
            //if (results.size() > 0) nbFound++;

            // nbSearches++;
            if (stat.nbBFChecks < minSearchStepsToFirst) {
                minSearchStepsToFirst = stat.nbBFChecks;
            }
            if (stat.nbBFChecks > maxSearchStepsToFirst) {
                maxSearchStepsToFirst = stat.nbBFChecks;
            }
            sumSearchStepsToFirst += stat.nbBFChecks;

        }

        System.out.print(" |Min steps| " + minSearchSteps
                         + "| Max steps | " + maxSearchSteps
                         + "| Avg steps | " + (double) sumSearchSteps / nbSearches
                         + "| Avg time millis | " + (double) sumSearchTime / nbSearches
                         + "| Total time millis | " + sumSearchTime
                         + "| Nb searches | " + nbSearches
                         + " |Min steps to first| " + minSearchStepsToFirst
                         + "| Max steps to first| " + maxSearchStepsToFirst
                         + "| Avg steps to first| " + (double) sumSearchStepsToFirst / nbSearches
                         + "| Avg time millis to first| " + (double) sumSearchTimeToFirst / nbSearches
                         + "| Total time millis to first| " + sumSearchTimeToFirst);

        return nbFound;
    }

    /**
     * Search in the given bfList for nbInteger elements in the [startRange, endRange)
     * @param bfList
     * @param startRange
     * @param endRange
     * @param nbSearches
     * @return number of searches that returned some result
     */
    private static int searchSomeElementsInBFList(List<BloomFilter<Integer>> bfList,
            int[] values,
            int nbSearchesToDo) {


        int minSearchSteps = 10000000;
        int maxSearchSteps = 0;
        int sumSearchSteps = 0;

        int minSearchStepsToFirst = 10000000;
        int maxSearchStepsToFirst = 0;
        int sumSearchStepsToFirst = 0;

        int nbSearches = 0;
        int nbFound = 0;
        int nbResults = 0;
        int elem;
        SearchStatistics stat = new SearchStatistics();

        long sumSearchTime = 0;
        long sumSearchTimeToFirst = 0;

        //for (elem = 0; elem < initialNbElemInFilter *nbBFs; elem++){
        for (int i = 0; i < nbSearchesToDo; i++) {

            //pick a random element in the array of values
            int index = (int) Math.floor(Math.random() * values.length);
            elem = values[index];

            //search for ALL filters matching that element
            stat.clear();
            long startTime = System.currentTimeMillis();
            ArrayList<BloomFilter<Integer>> results = searchAllInBFList(bfList, elem, stat);
            long endTime = System.currentTimeMillis();
            long diffTime = endTime - startTime;
            sumSearchTime += diffTime;

            //System.out.println("Search for " + elem + " Results in nb steps: " + stat.nbBFChecks);
            //System.out.println("Number results: " + results.size());
            //System.out.println(results);
            if (results.size() > 0) {
                nbFound++;
            }
            nbResults += results.size();

            nbSearches++;
            if (stat.nbBFChecks < minSearchSteps) {
                minSearchSteps = stat.nbBFChecks;
            }
            if (stat.nbBFChecks > maxSearchSteps) {
                maxSearchSteps = stat.nbBFChecks;
            }
            sumSearchSteps += stat.nbBFChecks;

            //search for FIRST filter matching that element
            stat.clear();
            startTime = System.currentTimeMillis();
            results = searchFirstInBFList(bfList, elem, stat);
            endTime = System.currentTimeMillis();
            diffTime = endTime - startTime;
            sumSearchTimeToFirst += diffTime;

            //nbResults already increased
            //if (results.size() > 0) nbFound++;

            // nbSearches++;
            if (stat.nbBFChecks < minSearchStepsToFirst) {
                minSearchStepsToFirst = stat.nbBFChecks;
            }
            if (stat.nbBFChecks > maxSearchStepsToFirst) {
                maxSearchStepsToFirst = stat.nbBFChecks;
            }
            sumSearchStepsToFirst += stat.nbBFChecks;

        }

        System.out.print(" |Min steps| " + minSearchSteps
                         + "| Max steps | " + maxSearchSteps
                         + "| Avg steps | " + (double) sumSearchSteps / nbSearches
                         + "| Avg time millis | " + (double) sumSearchTime / nbSearches
                         + "| Total time millis | " + sumSearchTime
                         + "| Nb searches | " + nbSearches
                         + "| Nb results | " + nbResults
                         + " |Min steps to first| " + minSearchStepsToFirst
                         + "| Max steps to first| " + maxSearchStepsToFirst
                         + "| Avg steps to first| " + (double) sumSearchStepsToFirst / nbSearches
                         + "| Avg time millis to first| " + (double) sumSearchTimeToFirst / nbSearches
                         + "| Total time millis to first| " + sumSearchTimeToFirst);

        return nbFound;
    }

    /**
     * Find all the BF in the list that match the given element.
     * This is a linear search, and all filters are checked
     * @param bfList
     * @param o
     * @param stat
     * @return
     */
    public static ArrayList<BloomFilter<Integer>> searchAllInBFList(List<BloomFilter<Integer>> bfList,
            Integer o,
            SearchStatistics stat) {

        ArrayList<BloomFilter<Integer>> result = new ArrayList<BloomFilter<Integer>>();


        for (int i = 0; i < bfList.size(); i++) {
            //increase the number of bloom filters checks, since this node will be checked
            stat.nbBFChecks++;
            if (bfList.get(i).contains(o)) {
                result.add(bfList.get(i));
            }
        }

        return result;
    }

    /**
     * Find first BF in the list that matches the given element.
     * This is a linear search, and all filters are checked until the first match is found
     * @param bfList
     * @param o
     * @param stat
     * @return
     */
    public static ArrayList<BloomFilter<Integer>> searchFirstInBFList(
        List<BloomFilter<Integer>> bfList,
        Integer o,
        SearchStatistics stat) {

        ArrayList<BloomFilter<Integer>> result = new ArrayList<BloomFilter<Integer>>();

        //search the list. stop at first one found

        for (int i = 0; i < bfList.size(); i++) {
            //increase the number of bloom filters checks, since this node will be checked
            stat.nbBFChecks++;
            if (bfList.get(i).contains(o)) {
                result.add(bfList.get(i));
                break;
            }
        }

        //return results
        return result;
    }

    /**
     * Search for all integers in [startRange, endRange)
     * Assertion error if any of the elements not found
     * @param bfi
     * @param startRange
     * @param endRange
     */
    /*    private static int searchAllElements(BloomFilterIndex<Integer> bfi, int startRange, int endRange) {

            //search for all remaining elements, to make sure we can still find them
            int minSearchSteps = 10000000;
            int maxSearchSteps = 0;
            int sumSearchSteps = 0;
            int nbSearches = 0;
            int nbFound = 0;
            SearchStatistics stat = new SearchStatistics();

            long sumSearchTime = 0;

            for (int elem = startRange; elem < endRange; elem++) {

                stat.clear();
                long startTime = System.currentTimeMillis();
                ArrayList<BloomFilter<Integer>> results = bfi.search(elem, stat);
                long endTime = System.currentTimeMillis();
                long diffTime = endTime - startTime;
                sumSearchTime += diffTime;

                //System.out.println("Search for " + elem + " Results in nb steps: " + stat.nbBFChecks);
                //System.out.println("Number results: " + results.size());
                //System.out.println(results);
                if (results.size() > 0) {
                    nbFound++;
                }

                nbSearches++;
                if (stat.nbBFChecks < minSearchSteps) {
                    minSearchSteps = stat.nbBFChecks;
                }
                if (stat.nbBFChecks > maxSearchSteps) {
                    maxSearchSteps = stat.nbBFChecks;
                }
                sumSearchSteps += stat.nbBFChecks;
            }

            System.out.print("|NbSearches | " + nbSearches + "| nbFound | " + nbFound + " | Min steps | " + minSearchSteps
                    + "| Max steps| " + maxSearchSteps
                    + "| Avg steps| " + (double) sumSearchSteps / nbSearches
                    + "| Avg time millis | " + (double) sumSearchTime / nbSearches
                    + "| Total time millis | " + sumSearchTime
                    + "| Nb searches | " + nbSearches);

            return nbFound;
        }*/

    /**
     * Create a Bloom filter with all numbers between start and end inclusive
     * @param start
     * @param end
     * @return
     */
    private static BloomFilter<Integer> createBloomFilterAll(Hasher h,
            double falsePosProb, int expectedNbElemInFilter, int start, int end, int metric) {
        //create the empty bloom filter
        BloomFilter<Integer> bf = new BloomFilter<Integer>(h,falsePosProb, expectedNbElemInFilter, metric);
        //add elements to the bloom filter
        int i;
        for (i = start; i <= end; i++) {
            bf.add(i);
        }
        //System.out.println("BF"+start+": "+bf.toString());

        /*
        //check that all can be re-found
        for (i = start; i<= end; i++){
        if (bf.contains(i)){
        System.out.println("Bloom filter contains "  + i);
        }
        else{
        System.out.println("Bloom filter does not contain "  + i);
        }
        }
         */
        return bf;
    }

    /**
     *
     *
     * @param falsePosProb
     * @param expectedNbElemInFilter
     * @param actualNbElemInFilter
     * @param metric
     * @param values
     * @param nextIndexInArray
     * @return
     */
    private static BloomFilter<Integer> createBloomFilterRandom(Hasher h,
            double falsePosProb, int expectedNbElemInFilter,
            int actualNbElemInFilter,
            int metric,
            int[] values,
            int nextIndexInArray) {
        //create the empty bloom filter
        BloomFilter<Integer> bf = new BloomFilter<Integer>(h,falsePosProb, expectedNbElemInFilter, metric);

        //randomly select a range that has at least 2*initialNbElemInFilter length

        int startRange = (int) (Math.floor(Math.random() * 100000000));
        int endRange = (int) (Math.floor(Math.random() * 100000000));

        while (Math.abs(endRange - startRange) < 2 * actualNbElemInFilter) {
            endRange = (int) (Math.floor(Math.random() * Integer.MAX_VALUE / 2));
        }

        if (endRange < startRange) {
            int temp = endRange;
            endRange = startRange;
            startRange = temp;
        }
        //add elements to the bloom filter
        int elem;
        int i = 0;
        while (i < actualNbElemInFilter) {

            //pick a random element in [startRange, endRange)
            elem = (int) (Math.floor(Math.random() * (endRange - startRange) + startRange));

            if (!bf.contains(elem)) {
                bf.add(elem);
                i++;
                //if keeping track of values inserted, update the array
                if (values != null) {
                    values[nextIndexInArray] = elem;
                    nextIndexInArray++;
                }
            }
        }
        //System.out.println("BF"+start+": "+bf.toString());

        /*
        //check that all can be re-found
        for (i = start; i<= end; i++){
        if (bf.contains(i)){
        System.out.println("Bloom filter contains "  + i);
        }
        else{
        System.out.println("Bloom filter does not contain "  + i);
        }
        }
         */
        return bf;
    }
}
