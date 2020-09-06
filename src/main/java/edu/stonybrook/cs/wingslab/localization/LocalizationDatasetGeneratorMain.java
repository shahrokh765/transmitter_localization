package edu.stonybrook.cs.wingslab.localization;

import edu.stonybrook.cs.wingslab.commons.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class LocalizationDatasetGeneratorMain {
    public static void main(String... args) throws InterruptedException {
        // **********************************   PATHS   ****************************************
        String SPLAT_DIR = "../commons/resources/splat/";
        String SENSOR_PATH = "../commons/resources/sensors/";

        // ********************************** Field Parameters **********************************
        double txHeight = 30;                          // in meter
        double rxHeight =  15;                         // in meter
        Shape fieldShape = new Square(1000);       // Square and Rectangle are supported for now.
        // in meter and originated in (0, 0). 1000 for log, 100 for splat
        int cellSize = 1;                               // in meter

        // ********************************** Propagation Model **********************************
        String propagationModel = "log";                // 'splat' or 'log'
        double alpha = 2;                               // propagation model coeff.  2.0 for 4km, 3 for 1km, 4.9 for 200m.
        // Applicable for log
        boolean noise = false;                           // std in dB.
        double std =  1.0;                              // Applicable for log
        GeographicPoint splat_left_upper_ref = new GeographicPoint(40.800595,
                73.107507);                         // ISLIP lat and lon
        double noiseFloor = -90;                       // noise floor
        String splatFileName = "pl_map_array.json";            // splat saved file name

        // ********************************** PUs&PURs **********************************
        int minTxNUmber = 1;                        // min number of pus all over the field
        int maxTxNumber = 1;                        // max number of pus all over the field;
        // i.e. # of pus is different for each sample.
        // min=max means # of pus doesn't change
        double minTxPower = -30.0;
        double maxTxPower = 0.0;                      // in dB. PU's power do not change for static PUs case

        // ********************************** SSs **********************************
        int number_sensors = 400;
        boolean changingSss = true;

        // ********************************** General **********************************
        int number_of_process = 1;                      // number of process
        //INTERPOLATION, CONSERVATIVE = False, False
        int n_samples = 50000;                            // number of samples

        long beginTime = System.currentTimeMillis();
        String sensorPath = String.format("%s%s/%d/sensors.txt", SENSOR_PATH, fieldShape.toString(),
                number_sensors);

        // create proper propagation model
        PropagationModel pm = null;
        if (propagationModel.equals("log"))
            if (noise)
                pm = new LogDistancePM(alpha, std);
            else
                pm = new LogDistancePM(alpha);
        else if (propagationModel.equals("splat")) {
            pm = new Splat(splat_left_upper_ref);
            Splat.readPlDictFromJson(SPLAT_DIR + "pl_map/" + splatFileName);
            Splat.setSdfDir(SPLAT_DIR + "sdf/");
        }

        // reading sensors
        SpectrumSensor[] sss = null;
        try {
            sss = SpectrumSensor.SensorReader(sensorPath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // ****************************** creating threads ************************
        ConcurrentHashMap<Integer, HashMap<String, Double>> resultDict = new ConcurrentHashMap<>();
        int[] threadSampleNum = new int[number_of_process];
        for (int i = 0; i < number_of_process; i++)
            threadSampleNum[i] = n_samples / number_of_process;         // equally distributed among threads
        threadSampleNum[0] += n_samples - (n_samples / number_of_process) * number_of_process;
        // remaining will be assigned to the first one
        int fileAppendix =
                ThreadLocalRandom.current().nextInt(100000);      // a random value will be used to distinguish
        // created file by different run
        Thread[] threads = new Thread[number_of_process];               // threads

        for (int i = 0; i < number_of_process; i++){
            // creating new thread

            SpectrumSensor[] threadCopySss = new SpectrumSensor[sss.length];
            for (int ssId = 0; ssId < sss.length; ssId++)
                threadCopySss[ssId] = new SpectrumSensor(sss[ssId]);

            PropagationModel threadPM;
            if (pm instanceof LogDistancePM logDistancePM)
                threadPM = new LogDistancePM(logDistancePM);
            else if (pm instanceof Splat splat)
                threadPM = new Splat(splat);
            else
                throw new IllegalArgumentException("Constructor is not valid.");

            Shape threadShape;
            if (fieldShape instanceof Rectangle rectangle)
                threadShape = new Rectangle(rectangle);
            else if (fieldShape instanceof Square square)
                threadShape = new Square(square);
            else
                throw new IllegalArgumentException("Shape is not valid.");
            threads[i] = new Thread(new LocalizationDatasetGeneratorApp(threadSampleNum[i], Integer.toString(fileAppendix),
                    resultDict, threadPM, threadCopySss, threadShape, cellSize,
                    minTxNUmber, maxTxNumber, txHeight, minTxPower, maxTxPower, changingSss));
            threads[i].start();
        }

        // waiting for all the threads to finish their jobs
        for (Thread thread : threads)
            thread.join();

        // more for splat
        if (propagationModel.contains("splat")){
            long fetchNum = 0;           // number of using hash map
            long execNum = 0;            // number of executing splat command line
            long fetchTime = 0;          // duration(milliseconds) of fetching
            long execTime = 0;           // duration(milliseconds) of executing
            for (HashMap<String, Double> threadInfo : resultDict.values()) {
                if (threadInfo.containsKey("Fetch Number"))
                    fetchNum += threadInfo.get("Fetch Number").longValue();
                if (threadInfo.containsKey("Execution Number"))
                    execNum += threadInfo.get("Execution Number").longValue();
                if (threadInfo.containsKey("Fetch Time"))
                    fetchTime += threadInfo.get("Fetch Time").longValue();
                if (threadInfo.containsKey("Execution Time"))
                    execTime += threadInfo.get("Execution Time").longValue();
            }
            System.out.println(String.format("Fetching: %,d times (%fms per each)\n" +
                            "Execution Time: %,d times (%.2fms per each)",
                    fetchNum, (double) fetchTime / fetchNum, execNum, (double) execTime / execNum));

            // saving new pl map
            Splat.writePlDictToJson(SPLAT_DIR + "pl_map/" + splatFileName + ".new");
        }

        // merging result file generated by threads into one
        String date = new SimpleDateFormat("_yyyy_MM_dd_HH_mm").format(new Date());
        String output_format = n_samples + "_" +
                (minTxNUmber != maxTxNumber ?
                        "min" + minTxNUmber + "_max" + maxTxNumber :
                        maxTxNumber) + "TXs" + "_" +
                number_sensors + "sensor_" +
                fieldShape + "grid_" + propagationModel +
                (propagationModel.toLowerCase().contains("log") ?
                        "_alpha" + alpha : "") +
                (noise && propagationModel.contains("log") ?
                        "_noisy_std" + std:"")
                + date + ".txt";

        mergeFiles(LocalizationDatasetGeneratorApp.getDataDir(), "localization_" + fileAppendix, // merging pu related files
                LocalizationDatasetGeneratorApp.getDataDir(), output_format);
        System.out.println("File "  + output_format + " saved at: " +
                LocalizationDatasetGeneratorApp.getDataDir());

        long duration = System.currentTimeMillis() - beginTime;
        System.out.println(String.format("Duration = %d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(duration),
                TimeUnit.MILLISECONDS.toMinutes(duration) % TimeUnit.HOURS.toMinutes(1),
                TimeUnit.MILLISECONDS.toSeconds(duration) % TimeUnit.MINUTES.toSeconds(1)));
    }

    // merging results from multiple threads
    private static void mergeFiles(String srcPath, String pattern, String destPath, String fileName){
        Path path = Paths.get(destPath);// check if the director exists; if not, it try to create it.
        if (!Files.isDirectory(path)) {
            try {
                Files.createDirectories(path);
            }
            catch (IOException e){
                Logger logger = Logger.getLogger(SpectrumSensor.class.getName());
                logger.warning("Merging file was unsuccessful. Creating dest directory faile." +
                        Arrays.toString(e.getStackTrace()));
                return;
            }
        }
        // read all the files name
        File dir = new File(srcPath);
        File[] files = dir.listFiles((d, name) -> name.startsWith(pattern));
        if (files == null || files.length == 0){
            Logger logger = Logger.getLogger(SpectrumSensor.class.getName());
            logger.warning("Merging outputs: No such files was found");
            return;
        }
        Arrays.sort(files); // sorting files to avid misplacement
        // merging files
        File outputFile = new File(destPath + fileName);
        try(PrintWriter printWriter = new PrintWriter(outputFile)){
            for (File file : files){
                Scanner fileScanner = new Scanner(file);
                while (fileScanner.hasNextLine())
                    printWriter.println(fileScanner.nextLine());
                fileScanner.close();
                file.delete();
            }
        } catch (FileNotFoundException e) {
            Logger logger = Logger.getLogger(SpectrumSensor.class.getName());
            logger.warning("Merging output failed due to I/O error creating the file: " +
                    Arrays.toString(e.getStackTrace()));
        }
    }
}
