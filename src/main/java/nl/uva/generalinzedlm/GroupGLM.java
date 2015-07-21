/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.uva.generalinzedlm;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;
import nl.uva.expose.lm.CollectionSLM;
import nl.uva.expose.lm.LanguageModel;
import nl.uva.expose.lm.StandardLM;

/**
 *
 * @author Mostafa Dehghani
 */
public class GroupGLM extends LanguageModel { //p(theta_r|t)

    private static final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(GroupGLM.class.getName());

    Character[] models = {'r', 's', 'g'};

    private LanguageModel generalLM;  // Theta_g
    private LanguageModel specificLM;  // Theta_s

    private HashMap<Integer, LanguageModel> docsTermVectors;

    //For each Model, for each document, for each term
    private HashMap<Character, HashMap<Integer, LanguageModel>> modelSelectionProb; // p(X_{d,t} = x )| x \in {s,g,r}
    //Note: Language Model here does not mean a probabilistic Language Model, it is only uses as the data structure

    //For each model, for each document
    private HashMap<Character, HashMap<Integer, Double>> lambda_X_d; // Lambda_x = p(\theta_x|d) --> Coefficient of each model in each document

    private HashMap<String, Double> documentTV;
    private DocsGroup group;
    private Integer numberOfIttereation = 100;

    public GroupGLM(DocsGroup group) throws IOException {
        this.group = group;

        //Fetcing termVectors from index
        for (int id : this.group.docs) {
            LanguageModel tv = new LanguageModel(group.iInfo.getDocTermFreqVector(id, this.group.field));
            this.docsTermVectors.put(id, tv);
        }

        //General Model
        this.generalLM = new CollectionSLM(this.group.iReader, this.group.field);

        //Specific Model
        this.specificLM = this.generateSpecificLM();

        //Relavance Model
        this.initRelevanceModel();

        //Initializing  lambda_X_d
        lambda_X_d = new HashMap<>();
        for (Character m : models) {
            HashMap<Integer, Double> docsHM = new HashMap<>();
            for (int id : group.docs) {
                docsHM.put(id, 1.0 / 3);
            }
            lambda_X_d.put(m, docsHM);
        }

        //Caculate GLM
        this.CalculateGLM();
    }

    private LanguageModel generateSpecificLM() throws IOException {
        LanguageModel specLM = new LanguageModel();

        HashMap<Integer, LanguageModel> specificLMs = new HashMap<>();
        for (int id : this.group.docs) {
            StandardLM docSLM = new StandardLM(this.group.iReader, id, this.group.field);
            specificLMs.put(id, docSLM);
        }
        for (String term : this.getTerms()) {
            Double probability = 0D;
            for (int i = 0; i < specificLMs.size(); i++) {
                Double joineProb = specificLMs.get(i).getProb(term);
                for (int j = 0; j < specificLMs.size(); j++) {
                    if (i == j) {
                        continue;
                    }
                    joineProb = joineProb * (1 - specificLMs.get(j).getProb(term));
                }
                probability += joineProb;
            }
            specLM.setProb(term, probability);
        }
        return specLM;
    }

    private void initRelevanceModel() throws IOException {
        StandardLM slm = new StandardLM(this.group.iReader, this.group.docs, this.group.field);
        this.setModel(slm.getModel());
    }

    private LanguageModel getModelById(Character m) {
        if (m.equals('s')) {
            return this.specificLM;
        } else if (m.equals('g')) {
            return this.generalLM;
        } else {
            return this;
        }
    }

    private void E_step() {

        for (Character m : models) {
            HashMap<Integer, LanguageModel> docsHM = this.modelSelectionProb.get(m);
            for (Integer id : docsHM.keySet()) {
                LanguageModel lm = docsHM.get(id);
                for (String term : lm.getTerms()) {
                    Double prob = this.getModelById(m).getProb(term);
                    Double lambda = this.lambda_X_d.get(m).get(id);
                    Double selectionProb = prob * lambda / this.Get_E_step_denominator(id, term);
                    lm.setProb(term, selectionProb);
                }
                docsHM.put(id, lm);
            }
            modelSelectionProb.put(m, docsHM);
        }
    }

    private Double Get_E_step_denominator(Integer did, String term) {
        Double denominator = 0D;
        for (Character m : models) {
            Double prob = this.getModelById(m).getProb(term);
            Double lambda = this.lambda_X_d.get(m).get(did);
            denominator += prob * lambda;
        }
        return denominator;
    }

    private void M_step() {

        Character modelToBeUpdate = 'r';
        Double denominator_relModel = this.Get_M_step_denominator_relModel(modelToBeUpdate);

        for (Character m : models) {
            HashMap<Integer, LanguageModel> docsHM = this.modelSelectionProb.get(m);
            HashMap<Integer, Double>  lambdas = new HashMap<>();
            for (Integer id : docsHM.keySet()) {
                for (String term : docsHM.get(id).getTerms()) {
                    // Updating relevance Model
                    if (m.equals(modelToBeUpdate)) {
                        Double tf = this.docsTermVectors.get(id).getProb(term);
                        Double numerator = tf * docsHM.get(id).getProb(term);
                        this.getModelById(modelToBeUpdate).setProb(term, numerator / denominator_relModel);
                    }

                    // Updating Lambdas
                    Double tf = this.docsTermVectors.get(id).getProb(term);
                    Double numerator = tf * docsHM.get(id).getProb(term);
                    Double denominator_Lambda = this.Get_M_step_denominator_Lambda(id);
                    Double lambda =  numerator / denominator_Lambda;
                    lambdas.put(id,lambda);
                }
            }
            this.lambda_X_d.put(m,lambdas);
        }
    }

    private Double Get_M_step_denominator_relModel(Character m) {
        Double denominator = 0D;
        HashMap<Integer, LanguageModel> docsHM = this.modelSelectionProb.get(m);
        for (Integer id : docsHM.keySet()) {
            for (String term : docsHM.get(id).getTerms()) {
                Double tf = this.docsTermVectors.get(id).getProb(term);
                denominator += tf * docsHM.get(id).getProb(term);
            }
        }
        return denominator;
    }

    private Double Get_M_step_denominator_Lambda(Integer did) {
        Double denominator = 0D;
        for (Character m : models) {
            HashMap<Integer, LanguageModel> docsHM = this.modelSelectionProb.get(m);
            for (String term : docsHM.get(did).getTerms()) {
                Double tf = this.docsTermVectors.get(did).getProb(term);
                denominator += tf * docsHM.get(did).getProb(term);
            }
        }
        return denominator;
    }

    public void CalculateGLM() {
        for (int i = 0; i < this.numberOfIttereation; i++) {
            this.E_step();
            this.M_step();
        }
    }

}
