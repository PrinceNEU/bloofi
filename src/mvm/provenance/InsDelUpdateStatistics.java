/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package mvm.provenance;

/**
 *
 * @author adina
 */
public class InsDelUpdateStatistics {

    /** Number of BloomFilters accessed  */
    public long nbBFAccessed;
    public long nbBFNodesAccessed;
    public int nbSplits;
    public int nbMerges;
    public int nbRedistributes;

    public InsDelUpdateStatistics() {
        this.clear();
    }

    /**
     * Reset the statistics to 0
     */
    public void clear() {
        nbBFAccessed = 0;
        nbBFNodesAccessed = 0;
        nbSplits = 0;
        nbMerges = 0;
        nbRedistributes = 0;

    }

    public String toString() {
        return "| nbBFAccessed | " + nbBFAccessed
               + "| nbBFNodesAccessed | "+ nbBFNodesAccessed
               + "| nbSplits |" + nbSplits
               + "| nbMerges |" + nbMerges
               + "| nbRedistributes |" + nbRedistributes;
    }
}
