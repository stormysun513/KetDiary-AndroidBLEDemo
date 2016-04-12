package com.ubicomp.mybletest;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.StringTokenizer;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import libsvm.svm_parameter;
import libsvm.svm_print_interface;

/**
 * Created by larry on 3/3/16.
 */
public class modified_svm_predict {

    private static svm_print_interface svm_print_null = new svm_print_interface()
    {
        public void print(String s) {}
    };

    private static svm_print_interface svm_print_stdout = new svm_print_interface()
    {
        public void print(String s)
        {
            System.out.print(s);
        }
    };

    private static svm_print_interface svm_print_string = svm_print_stdout;
    private static double[] prob_estimates = null;
    private svm_model model = null;

    static void info(String s)
    {
        svm_print_string.print(s);
    }

    private static double atof(String s)
    {
        return Double.valueOf(s).doubleValue();
    }

    private static int atoi(String s)
    {
        return Integer.parseInt(s);
    }

    public boolean loadModel(String model_filename){
        boolean result = false;
        try {
            model = svm.svm_load_model(model_filename);
            if (model == null)
                System.err.print("can't open model file " + model_filename + "\n");
            else
                result = true;

            if(svm.svm_check_probability_model(model) == 0)
                System.err.print("Model does not support probabiliy estimates\n");

        }
        catch (Exception e){
            System.err.println(e);
            result = false;
        }
        return result;
    }

    public boolean isModelLoaded(){
        if(model == null)
            return false;
        else
            return true;
    }

    public double predict(String line, DataOutputStream output, int predict_probability) throws IOException {

        if(model == null)
            return (-Double.MAX_VALUE);

        int correct = 0;
        int total = 0;

        int svm_type = svm.svm_get_svm_type(model);
        int nr_class = svm.svm_get_nr_class(model);
        prob_estimates = null;

        if(predict_probability == 1)
        {
            if(svm_type == svm_parameter.EPSILON_SVR || svm_type == svm_parameter.NU_SVR){
                modified_svm_predict.info("Prob. model for test data: target value = predicted value + z,\nz: Laplace distribution e^(-|z|/sigma)/(2sigma),sigma=" + svm.svm_get_svr_probability(model) + "\n");
            }
            else{
                int[] labels=new int[nr_class];
                svm.svm_get_labels(model,labels);
                prob_estimates = new double[nr_class];
                output.writeBytes("labels");
                for(int j=0;j<nr_class;j++)
                    output.writeBytes(" "+labels[j]);
                output.writeBytes("\n");
            }
        }

        if(line != null){
            StringTokenizer st = new StringTokenizer(line," \t\n\r\f:");

            double target = atof(st.nextToken());
            int m = st.countTokens()/2;
            svm_node[] x = new svm_node[m];
            for(int j=0;j<m;j++)
            {
                x[j] = new svm_node();
                x[j].index = atoi(st.nextToken());
                x[j].value = atof(st.nextToken());
            }

            double v;
            if (predict_probability==1 && (svm_type==svm_parameter.C_SVC || svm_type==svm_parameter.NU_SVC))
            {
                v = svm.svm_predict_probability(model,x,prob_estimates);
                output.writeBytes(v+" ");
                for(int j=0;j<nr_class;j++)
                    output.writeBytes(prob_estimates[j]+" ");
                output.writeBytes("\n");
            }
            else
            {
                v = svm.svm_predict(model,x);
                output.writeBytes(v+"\n");
            }

            if(v == target)
                ++correct;
            ++total;

            modified_svm_predict.info("Accuracy = " + (double) correct / total * 100 + "% (" + correct + "/" + total + ") (classification)\n");
            return v;
        }
        return (-Double.MAX_VALUE);
    }
}
