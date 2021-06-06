package edu.stonybrook.cs.wingslab.localization;

import edu.stonybrook.cs.wingslab.commons.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class LocalizationDatasetGeneratorApp implements Runnable {
    private static String DATA_DIR = "resources/data/";
    //directory where the results should be written
    private final int sampleCount;
    // number of samples to be created. in case of SPLAT!, it might be less due to exceptions
    private static int threadNum = 0;
    private final int threadId;
    // will be used when there are multiple threads to write some useful information if a hash dictionary is given
    private final String fileAppendix;
    // will be used to save results; user might need it to merge multiple thread outputs
    private ConcurrentHashMap<Integer, HashMap<String, Double>> resultDict;
    // some statistics will be written in this dictionary
    private final PropagationModel propagationModel;
    // propagation model that will be used
    private final SpectrumSensor[] sss;
    // array of sensor that will be used
    private final Shape shape;
    // shape of the field
    private final int cellSize;
    // square cell size
    private final int minTxNum;
    // number of minimum active PUs for each sample
    private final int maxTxNum;
    // number of maximum active PUs for each sample
    private final double minTxPower;
    // minimum power value(dB) an PU can get
    private final double maxTXPower;
    // maximum power value(dB) an PU can get
    private final double txHeight;
    // height of SU the class creates
    private final boolean changingSss;
    // progress bar length
    private final static int progressBarLength = 50;
    private final double noiseFLoor;

    /**
     * LocalizationDatasetGeneratorApp constructor.
     * @param sampleCount number of sample to be generated
     * @param fileAppendix an appendix that will be added to results file if provided
     * @param resultDict a hashMap that will be used to write statistics
     * @param propagationModel propagation model
     * @param sss array of SpectrumSensors
     * @param shape shape of the target region
     * @param cellSize size of square cells
     * @param minTxNum minimum number of TXs for each sample
     * @param maxTxNum maximum number of TXs for each sample
     * @param minTxPower minimum power value of TXs for each sample
     * @param maxTxPower maximum power value of TXs for each sample
     * @param txHeight height of TXs
     * @param changingSss whether sensors' location is changing
     * @param noiseFloor noise floor*/
    public LocalizationDatasetGeneratorApp(int sampleCount, String fileAppendix,
                                           ConcurrentHashMap<Integer, HashMap<String, Double>> resultDict,
                                           PropagationModel propagationModel, SpectrumSensor[] sss, Shape shape,
                                           int cellSize, int minTxNum, int maxTxNum, double txHeight,
                                           double minTxPower, double maxTxPower, boolean changingSss,
                                           double noiseFloor){
        super();
        this.sampleCount = sampleCount;
        this.threadId = LocalizationDatasetGeneratorApp.threadNum++;
        this.fileAppendix = fileAppendix;
        this.resultDict = resultDict;
        this.propagationModel = propagationModel;
        this.sss = sss;
        this.shape = shape;
        this.cellSize = cellSize;
        this.minTxNum = minTxNum;
        this.maxTxNum = maxTxNum;
        this.minTxPower = minTxPower;
        this.maxTXPower = maxTxPower;
        this.txHeight = txHeight;
        this.changingSss = changingSss;
        this.noiseFLoor = noiseFloor;
        //creating files and directory(if needed)
        Path dataPath = Paths.get(LocalizationDatasetGeneratorApp.DATA_DIR);
        if (!Files.isDirectory(dataPath)){
            try {
                Files.createDirectories(dataPath);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(this.getClass().getSimpleName() + " can't create proper directory!");
            }
        }
    }

    @Override
    public void run(){
        String fileNameFormat = String.format("_%1$s_%2$d.txt", fileAppendix != null ? fileAppendix : "", this.threadId);
        // this format would be added to file name
        File localizeFile = new File(LocalizationDatasetGeneratorApp.DATA_DIR +
                "/localization" + fileNameFormat);  //
        try(PrintWriter localizeWriter = new PrintWriter(localizeFile)) {
            long beginTime = System.currentTimeMillis();
            for (int sample = 1; sample < this.sampleCount + 1; sample++) {
                try {
                    localizeWriter.println(new LocalizationDatasetGenerator(createTXs(),
                            changingSss ? createSSs() : this.sss,
                            this.shape, this.propagationModel, this.cellSize, changingSss, noiseFLoor));
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
                System.out.print(progressBar(sample, System.currentTimeMillis() - beginTime));
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException(this.getClass().getSimpleName() + "Failed opening proper files");
        }

    }


    // method to return Progress Bar based on sample #
    private String progressBar(int sampleNumber, long timeElapsedMilli){
        int progress = (int)((float)sampleNumber/this.sampleCount * progressBarLength);  // number of = to be print
        return "=".repeat(Math.max(0, progress)) +
                " ".repeat(Math.max(0, LocalizationDatasetGeneratorApp.progressBarLength - progress)) +
                "| " + (int)((double)sampleNumber / this.sampleCount * 100) + "% " +
                timeFormat(sampleNumber, timeElapsedMilli) + "\r";
    }

    // based on elapsed time and sample number, a time format info will be returned
    private String timeFormat(int sampleNumber, long timeElapseMilli){
        int sampleRemaining = this.sampleCount - sampleNumber;  // remaining samples
        long timeRemainingMilli = (long)(((double) sampleRemaining / sampleNumber) * timeElapseMilli); // remaining time
        String extraInfo = (double)timeElapseMilli / (1000 * sampleNumber) > 1.0 ?
                String.format("%.2fs/it", (double)timeElapseMilli / (1000 * sampleNumber)) :   // seconds per iteration
                String.format("%.2fit/s", (double)(1000 * sampleNumber) / timeElapseMilli);    // iteration per seconds
        return String.format("(%s / %s), %s", timeFormat(timeElapseMilli),
                timeFormat(timeRemainingMilli), extraInfo);
    }

    // return time format (HH:mm:ss) for a milliseconds input
    private String timeFormat(long millis){
        return String.format("%d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1),
                TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1));
    }

    // creating random sus
    private TX[] createTXs(){
        int txsNum = ThreadLocalRandom.current().nextInt(this.minTxNum, this.maxTxNum + 1);
        double[] fixedPowers = {-15.0, -10.0, -5.0, 0.0};
        Point[] susPoint = this.shape.points(txsNum);
        TX[] txs = new TX[txsNum];
        for (int i = 0; i < txsNum; i++)
            txs[i] = new TX(new Element(susPoint[i], this.txHeight), fixedPowers[i]);
//                    ThreadLocalRandom.current().nextDouble(this.minTxPower, this.maxTXPower + Double.MIN_VALUE));
        return txs;
    }

    //creating random sensors
    private SpectrumSensor[] createSSs(){
        int n = this.sss.length;
        Point[] sssPoint = this.shape.points(n);
        double height = this.sss[0].getRx().getElement().getHeight();
        double cost = this.sss[0].getCost();
        double std = this.sss[0].getStd();
        SpectrumSensor[] sss = new SpectrumSensor[n];
        for (int i = 0; i < n; i++){
            sss[i] = new SpectrumSensor(new RX(new Element(sssPoint[i], height)), cost, std);
        }
        return sss;
    }
    // **************************** Setter & Getter ******************************
    public static String getDataDir() { return DATA_DIR; }

    public static void setDataDir(String dataDir) { DATA_DIR = dataDir; }

//    public static ConcurrentHashMap<Integer, HashMap<String, Double>> getResultDict() { return this.resultDict; }

    public static void setResultDict(ConcurrentHashMap<Integer, HashMap<String, Double>> resultDict) {
        resultDict = resultDict;
    }
}
