/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.uva.generalinzedlm;

import java.io.IOException;
import java.util.HashMap;
import nl.uva.lm.LanguageModel;

/**
 *
 * @author Mostafa Dehghani
 */
public class GroupGLM extends LanguageModel { //p(theta_r|t)

    private static final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(GroupGLM.class.getName());

//    private Character[] models = {'r', 's', 'g'};
    private Character[] models = {'r','g'};

    private final LanguageModel generalLM;  // Theta_g
    private final LanguageModel specificLM;  // Theta_s

    private final HashMap<Integer, LanguageModel> docsTermVectors;

    //For each Model, for each document, for each term
    private HashMap<Character, HashMap<Integer, LanguageModel>> modelSelectionProb; // p(X_{d,t} = x )| x \in {s,g,r}
    //Note: Language Model here does not mean a probabilistic Language Model, it is only uses as the data structure

    //For each model, for each document
    private HashMap<Character, HashMap<Integer, Double>> lambda_X_d; // Lambda_x = p(\theta_x|d) --> Coefficient of each model in each document

    private DocsGroup group;
    private Integer numberOfItereation = 100;

    public GroupGLM(DocsGroup group) throws IOException {
        this.group = group;

        //Fetcing termVectors from index
        this.docsTermVectors = new HashMap<>();
        for (int id : this.group.docs) {
            LanguageModel tv = new LanguageModel(group.iInfo.getDocTermFreqVector(id, this.group.field));
            this.docsTermVectors.put(id, tv);
        }

        //Relavance Model
        this.setModel(this.group.getGroupStandardLM().getModel());
//        log.info("Relevance model is initialized (with slm)....");

        //General Model
        this.generalLM = this.group.getCollectionLM();
//        log.info("General model is initialized....");

        //Specific Model
        this.specificLM = this.group.getGroupSpecificLM();
//        log.info("Specific model is initialized....");

        //Initializing  lambda_X_d
        lambda_X_d = new HashMap<>();
        for (Character m : models) {
            HashMap<Integer, Double> docsHM = new HashMap<>();
            for (int id : group.docs) {
                docsHM.put(id, 1.0 / 3);
            }
            lambda_X_d.put(m, docsHM);
        }
//        log.info("Lambdas' value are initialized....");

        //Caculate GLM
        this.CalculateGLM();
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
        this.modelSelectionProb = new HashMap<>();
        for (Character m : models) {
            HashMap<Integer, LanguageModel> docsHM = new HashMap<>();
            for (Integer id : this.group.docs) {
                LanguageModel lm = new LanguageModel();
                for (String term : this.docsTermVectors.get(id).getTerms()) {
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

        this.lambda_X_d = new HashMap<>(); //Clear Matrix
        Character modelToBeUpdate = 'r';
        this.getModelById(modelToBeUpdate).erase();
        Double denominator_relModel = null;
        for (Character m : models) {
            if (m.equals(modelToBeUpdate)) {
                denominator_relModel = this.Get_M_step_denominator_relModel(modelToBeUpdate);
            }
            HashMap<Integer, LanguageModel> docsHM = this.modelSelectionProb.get(m);
            HashMap<Integer, Double> lambdas = new HashMap<>();
            for (Integer id : docsHM.keySet()) {
                Double denominator_Lambda = this.Get_M_step_denominator_Lambda(id);
                for (String term : docsHM.get(id).getTerms()) {
                    // Updating relevance Model
                    if (m.equals(modelToBeUpdate)) {
                        Double newprob = this.Get_M_step_numerator_relModel(m, term) / denominator_relModel;
                        this.getModelById(modelToBeUpdate).setProb(term, newprob);
                    }
                    // Updating Lambdas
                    Double newLambda = this.Get_M_step_numerator_Lambda(m, id) / denominator_Lambda;
                    lambdas.put(id, newLambda);
                }
            }
            this.lambda_X_d.put(m, lambdas);
        }
    }

    private Double Get_M_step_numerator_relModel(Character m, String term) {
        Double numerator = 0D;
        HashMap<Integer, LanguageModel> docsHM = this.modelSelectionProb.get(m);
        for (Integer id : docsHM.keySet()) {
            Double tf = this.docsTermVectors.get(id).getProb(term);
            numerator += tf * docsHM.get(id).getProb(term);
        }
        return numerator;
    }

    private Double Get_M_step_numerator_Lambda(Character m, Integer did) {
        Double numerator = 0D;
        HashMap<Integer, LanguageModel> docsHM = this.modelSelectionProb.get(m);
        for (String term : docsHM.get(did).getTerms()) {
            Double tf = this.docsTermVectors.get(did).getProb(term);
            numerator += tf * docsHM.get(did).getProb(term);
        }
        return numerator;
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
        for (int i = 0; i < this.numberOfItereation; i++) {
//            log.info("iteration num:" + i);
//            System.out.println(this.getTopK(20));
            this.E_step();
            this.M_step();
        }
    }

}
