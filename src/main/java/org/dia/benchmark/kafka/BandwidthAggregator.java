package org.dia.benchmark.kafka;
/*
Licensed to the Apache Software Foundation (ASF) under one or more contributor
license agreements.  See the NOTICE.txt file distributed with this work for
additional information regarding copyright ownership.  The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License.  You may obtain a copy of
the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
License for the specific language governing permissions and limitations under
the License.
 */

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This consumer measures bandwidth as it consumes messages.
 *
 * @author starchmd
 */
public abstract class BandwidthAggregator implements Runnable,Aggregator {

    private static final Logger log = Logger.getLogger(BandwidthAggregator.class.getName());

    protected long count = -1;
    private boolean stop = false;

    @Override
    public abstract void setup(Configuration config);

    @Override
    public void start() {
        log.log(Level.INFO, String.format("Starting instance of type %s",this.getClass().getName()));
        new Thread(this).start();
    }

    @Override
    public synchronized long stop() {
        log.log(Level.INFO, String.format("Stoping instance of type %s",this.getClass().getName()));
        stop = true;
        return count;
    }
    @Override
    public synchronized long count() {
        log.log(Level.FINER, String.format("Counting instance of type %s",this.getClass().getName()));
        return count;
    }
    @Override
    public void run() {
        log.log(Level.INFO, String.format("Running thread %s of type %s",Thread.currentThread().getName(), this.getClass().getName()));
        count = 0;
        //Set termination flag safely
        boolean terminate = false;
        synchronized (this) {
            terminate = stop;
        }
        while (!terminate) {
            long ret = act();
            synchronized (this) {
                terminate = stop;
                count += ret;
            }
        }
    }
    /**
     * Perform the action specific to this class
     * @return number of messages processed
     */
    public abstract long act();
    /**
     * Test main program.
     * @param args - command line arguments
     */
    public static void main(String[] args) {
        Configuration config = null;
        try {
            config = new Configuration(Configuration.getProperties());
            Constructor<?> ctor = Class.forName(args[0]).getConstructor();
            Aggregator agg = (Aggregator)ctor.newInstance(new Object[] {});
            agg.setup(config);
            Monitor m = new Monitor(agg);
            agg.start();
            m.run();
        } catch (IOException e) {
            System.err.println("Error properties file does not exist."+e);
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            System.err.println("Illegal access exception in Configuration.java(this)"+e);
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Exception occured upon execution: "+e);
            e.printStackTrace();
        }
    }
    /**
     * A class to monitor an aggregator
     * 
     * @author starchmd
     */
    public static class Monitor implements Runnable {
        private Aggregator aggor;
        /**
         * ctor
         * @param agg - BandwidthConsumer to monitor
         */
        public Monitor(Aggregator agg) {
            this.aggor = agg;
            //Catch CTRL-C
            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    try {
                        log.log(Level.INFO,String.format("Final message count: %d",aggor.stop()));
                    } catch (Exception e) {log.log(Level.INFO, "Exception caught: "+e);}
                }
            });
        }
        @Override
        public void run() {
            while(true) {
                try {
                    log.log(Level.INFO,String.format("Current message count: %d",aggor.count()));
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.log(Level.WARNING,"Exception caught while monitoring: "+e);
                    e.printStackTrace();
                }
            }
        }
    }
}
