/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.uva.generalinzedlm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import nl.uva.lm.CollectionSLM;
import nl.uva.lm.LanguageModel;
import nl.uva.lm.ParsimoniousLM;
import nl.uva.lm.StandardLM;
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
    private LanguageModel groupGeneralizedLM;
    private LanguageModel groupHGeneralizedLM;
    private LanguageModel groupParsimoniouseLM;
    private LanguageModel groupStandardLM;
    private LanguageModel GroupSpecificLM;
    private LanguageModel CollectionLM;

    public DocsGroup(IndexReader iReader, String field, ArrayList<Integer> docs) {
        this.iReader = iReader;
        this.docs = docs;
        this.field = field;
        this.iInfo = new IndexInfo(this.iReader);
    }

    public LanguageModel getGroupStandardLM() throws IOException {
        if (this.groupStandardLM == null) {
            this.groupStandardLM = new StandardLM(this.iReader, this.docs, this.field);
        }
        return this.groupStandardLM;
    }

    public LanguageModel getGroupGeneralizedLM() throws IOException {
        if (this.groupGeneralizedLM == null) {
            GroupGLM gGLM = new GroupGLM(this);
            this.groupGeneralizedLM = new LanguageModel(gGLM.getModel());
        }
        return this.groupGeneralizedLM;
    }

    public LanguageModel getGroupHGeneralizedLM() throws IOException {
        if (this.groupHGeneralizedLM == null) {
            GroupHGLM gHGLM = new GroupHGLM(this);
            this.groupHGeneralizedLM = new LanguageModel(gHGLM.getModel());
        }
        return this.groupHGeneralizedLM;
    }

    public LanguageModel getGroupParsimoniouseLM() throws IOException {
        if (this.groupParsimoniouseLM == null) {
            this.groupParsimoniouseLM = new ParsimoniousLM(this.getGroupStandardLM(), this.getCollectionLM());
//            this.groupParsimoniouseLM = new ParsimoniousLM(this.getGroupStandardLM(), this.getGroupSpecificLM());
        }
        return this.groupParsimoniouseLM;
    }

    public LanguageModel getCollectionLM() throws IOException {
        if (this.CollectionLM == null) {
            this.CollectionLM = new CollectionSLM(this.iReader, this.field);
        }
        return this.CollectionLM;
    }

    public LanguageModel getGroupSpecificLM() throws IOException {
        if (this.GroupSpecificLM == null) {
            this.GroupSpecificLM = new LanguageModel();

            HashMap<Integer, LanguageModel> docsLMs = new HashMap<>();
            for (int id : this.docs) {
                StandardLM docSLM = new StandardLM(this.iReader, id, this.field);
                docsLMs.put(id, docSLM);
//                StandardLM docSLM = new StandardLM(this.iReader, id, this.field);
//                ParsimoniousLM docPLM = new ParsimoniousLM(docSLM,this.getCollectionLM());
//                docsLMs.put(id, docPLM);
            }
            
            this.GroupSpecificLM.setModel(this.getSpecificLM(this.getGroupStandardLM().getTerms(), docsLMs).getModel());
        }
        return this.GroupSpecificLM;

    }

    public LanguageModel getSpecificLM(Set<String> allTerms, HashMap<Integer, LanguageModel> docsLMs) throws IOException {
        LanguageModel specLM = new LanguageModel();
        for (String term : allTerms) {
            Integer docFreq = 0;
            Double probability = 0D;
            for (int i : docsLMs.keySet()) {
                Double joineProb = docsLMs.get(i).getProb(term);
                if (joineProb > 0) {
                    docFreq++;
                }
                for (int j : docsLMs.keySet()) {
                    if (i == j) {
                        continue;
                    }
                    joineProb = joineProb * (1 - docsLMs.get(j).getProb(term));
                }
                probability += joineProb;
            }
            specLM.setProb(term, probability / docFreq);
        }
        return specLM.getNormalizedLM();
    }
}
