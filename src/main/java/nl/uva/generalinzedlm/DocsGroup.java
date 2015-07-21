/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package nl.uva.generalinzedlm;

import java.util.ArrayList;
import nl.uva.lm.LanguageModel;
import nl.uva.lucenefacility.IndexInfo;
import org.apache.lucene.index.IndexReader;

/**
 *
 * @author Mostafa Dehghani
 */
public class DocsGroup {
    private static final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(DocsGroup.class.getName());
    public IndexReader iReader;
    public IndexInfo iInfo;
    public String field;
    public ArrayList<Integer> docs;
    public ArrayList<Double> docsPrior;
    public LanguageModel groupGLM;
    public LanguageModel groupSLM;

    public DocsGroup(IndexReader iReader, String field, ArrayList<Integer> docs) {
        this.iReader = iReader;
        this.docs = docs;
        this.field = field;
        this.iInfo = new IndexInfo(this.iReader);
    }
    
    
}
