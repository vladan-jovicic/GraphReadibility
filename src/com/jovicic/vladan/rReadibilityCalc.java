package com.jovicic.vladan;
import ilog.concert.*;
import ilog.cplex.*;

import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;

/**
 * Created by vlada on 3/8/2016.
 */
public class rReadibilityCalc {

    private Graph g;
    public IloNumVar [][][][] xvar;
    private int r;
    private IloNumVar [][] zvar;
    public IloCplex model;
    private Vector<Tuple> tuples;
    private ParallelrReadibilityCalc [] threads;
    private CountDownLatch latch;

    public rReadibilityCalc(Graph gg, int rr)
    {
        this.r = rr;
        g = gg;
        xvar = new IloNumVar[g.n/2][g.n/2][r+1][];
        zvar = new IloNumVar[g.eSize][];
        try {
            model = new IloCplex();
            model.addMaximize(model.constant(1));
            for (int u = 0; u < g.n/2; u++) {
                for (int v = 0; v < g.n/2; v++) {
                    for (int i = 0; i <= r; i++) {
                        xvar[u][v][i] = model.numVarArray(r+1, 0, 1);
                    }
                }
            }
            for(int i=0; i<g.eSize; i++)
            {
                zvar[i] = model.numVarArray(r+1, 0, 1);
            }
        }
        catch (Exception e)
        {
            System.out.println("Failed to initialize model!");
            e.printStackTrace();
        }
    }
    public int isrReadibility()
    {
        int eCnt = 0;
        for(int u=0; u<g.n/2; u++)
        {
            for(int v = g.n/2; v<g.n; v++)
            {
                if(g.adjMatrix[u][v])
                {
                    //System.out.println("Dodajem za povezavu " + u + "," + v);
                    //model.addGe(asdas, 1);
                    try {
                        IloLinearNumExpr expr = model.linearNumExpr();
                        //sad za svaku poziciju i=r ... 1
                        for(int i=r; i>=1; i--)
                        {
                            //dodaj sve varijable
                            expr.addTerm(1, zvar[eCnt][i]);
                            IloLinearNumExpr expr1 = model.linearNumExpr();
                            int othSide = 1;
                            for(int j=i; j>=1; j--)
                            {
                                //xvars[u][v][r-j+1][j]
                                expr1.addTerm(1, xvar[u][v-g.n/2][r-j+1][othSide++]);
                            }
                            model.addGe(model.sum(expr1,model.prod(-i, zvar[eCnt][i])),0);
                        }
                        eCnt++;
                        model.addGe(expr, 1);
                    } catch (Exception e)
                    {
                        System.out.println("Failed to initialize linear expression");
                    }

                }
                else
                {
                    //ako nema povezave
                    //moram uzeti negaciju
                    //a to je
                    try {
                        for (int i = r; i >= 1; i--) //za svaku poziciju
                        {
                            IloLinearNumExpr expr = model.linearNumExpr();
                            int othSide = 1;
                            for(int j = i; j>= 1; j--)
                            {
                                expr.addTerm(-1, xvar[u][v-g.n/2][r-j+1][othSide++]);
                            }
                            model.addGe(model.sum(model.constant(i), expr), 1);
                        }
                    } catch (Exception e)
                    {
                        e.printStackTrace();
                        System.out.println("Failed to initialize expression for non-edge");
                    }
                }
            }
        }
        System.out.println("Adding transitivity constraints");


        int num_of_threads = Runtime.getRuntime().availableProcessors();
        int fourthRoot = (int)Math.sqrt(Math.sqrt(num_of_threads));
        int thread_cnt = 0;
        int numOfVer = g.n/(2*fourthRoot);
        int sizeOfVer = r/fourthRoot;
        int exceptedNumOfThreads = (int)Math.pow(fourthRoot,4);
        threads = new ParallelrReadibilityCalc[exceptedNumOfThreads];
        latch = new CountDownLatch(exceptedNumOfThreads);

        //System.out.println("num of threads: " + n);
        //jos pogledaj za neperfektno dijeljenje
        for(int left = 0; left<fourthRoot; left++)
        {
            for(int right = 0; right<fourthRoot; right++)
            {
                for(int i=0; i<fourthRoot; i++)
                {
                    for(int j=0; j<fourthRoot; j++)
                    {
                        threads[thread_cnt++] = new ParallelrReadibilityCalc(g, r, xvar, model,
                                new int[] {left*numOfVer,(left == fourthRoot-1)?g.n/2:(left+1)*numOfVer},
                                new int[] {g.n/2 + right*numOfVer, (right == fourthRoot-1)?g.n:g.n/2 + (right+1)*numOfVer},
                                new int[] {i*sizeOfVer+1,(i==fourthRoot-1)?r:(i+1)*sizeOfVer},
                                new int[] {j*sizeOfVer+1,(j==fourthRoot-1)?r:(j+1)*sizeOfVer}, latch);
                        //threads[thread_cnt-1].run();
                    }
                }
            }
        } // pretpostavimo da ovo gore radi xD

        System.out.println("Running up to " + exceptedNumOfThreads + " threads");
        if(exceptedNumOfThreads == thread_cnt)
        {
            for(int i=0; i<exceptedNumOfThreads; i++)
                threads[i].run();
        }
        else
            System.out.println("... parallel programming");
        try {
            latch.await();
        } catch (InterruptedException e)
        {
            System.out.println("Failed .....");
        }
        try {
            System.out.println("Calculation finished! Trying to solve a model ....");
            //IloCplex.Algorithm model1 = new IloCplex.Algorithm(IloCplex.Algorithm.Dual);
            //IloCplex.Algorithm alg = new IloCplex.Algorithm(IloCplex.Algorithm.Primal);
            //model.setParam(IloCplex.IntParam.RootAlg, IloCplex.Algorithm.Barrier);
            model.setParam(IloCplex.IntParam.NodeLim, 15);
            //System.out.println(model.getAlgorithm());
            model.solve();
            if(model.getStatus() == IloCplex.Status.Optimal)
            {
                /*tuples = new Vector<Tuple>();
                for(int u=0; u<g.n/2; u++)
                {
                    for(int v = g.n/2; v < g.n; v++)
                    {
                        for(int i=1; i<=r; i++)
                        {
                            for(int j=1; j<=r; j++)
                            {
                                //System.out.println(model.getValue(xvar[u][v][i][j]));
                                if(model.getValue(xvar[u][v-g.n/2][i][j]) == 1)
                                {
                                    Tuple t = new Tuple(4);
                                    t.setTouple(new int[] {u,v,i,j});
                                    tuples.add(t);
                                }
                            }
                        }
                    }
                }
                System.out.println("Graph has readibility " + r); //tu jos treba da ispitas koji su jednaki
                return 1;*/
                System.out.println("Izracunao sam");
                return 1;
            }
            else if(model.getStatus() == IloCplex.Status.Infeasible)
            {
                System.out.println("Graph has no readibility " + r);
                return 0;
            }
            else
            {
                return -1;
            }
        } catch (Exception e)
        {
            e.printStackTrace();
            System.out.println("Failed in solving model!");
        }
        model.end();
        return -1;
    }

    public Vector<Tuple> getTuples ()
    {
        return tuples;
    }
    public void setLabeling(Graph g)
    {
        g.setReadibility(r);
        char current = 'a';
        for(int i=0; i<tuples.size(); i++)
        {
            int u = tuples.elementAt(i).getiThCoordinate(0);
            int v = tuples.elementAt(i).getiThCoordinate(1);
            int j = tuples.elementAt(i).getiThCoordinate(2);
            int l = tuples.elementAt(i).getiThCoordinate(3);
            if(g.getLabel(u,j) == 0 && g.getLabel(v,l) == 0)
            {
                g.setLabel(u,j,current);
                g.setLabel(v,l, current);
                current++;
            }
            else if(g.getLabel(u,j) != 0 && g.getLabel(v,l) == 0)
            {
                g.setLabel(v,l, (char) g.getLabel(u,j));
            }
            else
            {
                g.setLabel(u,j,(char) g.getLabel(v,l));
            }
        }

    }

}
