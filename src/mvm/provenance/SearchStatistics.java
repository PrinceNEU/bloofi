/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package mvm.provenance;

/**
 *
 * @author adina
 */
public class SearchStatistics {

    /** Number of BloomFilters checked for matches */
    public int nbBFChecks;

    public SearchStatistics() {
        nbBFChecks = 0;
    }

    /**
     * Reset the statistics to 0
     */
    public void clear() {
        nbBFChecks = 0;
    }
}
