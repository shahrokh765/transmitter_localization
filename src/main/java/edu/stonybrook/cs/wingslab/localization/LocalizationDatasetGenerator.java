package edu.stonybrook.cs.wingslab.localization;

import edu.stonybrook.cs.wingslab.commons.*;

import java.util.Arrays;

/**This class calculates sensor received power based on the set of sensors and transmitters*/
public class LocalizationDatasetGenerator {
    private final SpectrumSensor[] sss;   // list of sensors
    private final Shape shape;            // shape of the field
    private final TX[] txs;               // list of transmitters
    private final PropagationModel propagationModel;    // propagation model
    private final int cellSize;           // cell size of the field
    private final boolean sssLocation;    // if sensors location need to be written
    private final double noiseFloor;

    /**LocalizationDatasetGenerator constructor
     * @param txs array of TX
     * @param sss array of SpectrumSensor
     * @param propagationModel PropagationModel
     * @param shape Shape of field
     * @param cellSize cell size in square
     * @param sssLocation if sensors' location be written
     * @param noiseFloor noise floor*/
    public LocalizationDatasetGenerator(TX[] txs, SpectrumSensor[] sss, Shape shape,
                                        PropagationModel propagationModel, int cellSize, boolean sssLocation,
                                        double noiseFloor){
        super();
        this.sss = sss;
        this.txs = txs;
        this.shape = shape;
        this.propagationModel = propagationModel;
        this.cellSize = cellSize;
        this.sssLocation = sssLocation;
        this.noiseFloor = noiseFloor;
        computeSensorReceivedPower();
    }

    /**LocalizationDatasetGenerator constructor
     * @param txs array of TX
     * @param sss array of SpectrumSensor
     * @param propagationModel PropagationModel
     * @param shape Shape of field
     * @param cellSize cell size in square
     * @param noiseFloor noise floor*/
    public LocalizationDatasetGenerator(TX[] txs, SpectrumSensor[] sss, Shape shape,
                                        PropagationModel propagationModel, int cellSize, float noiseFloor){
        this(txs, sss, shape, propagationModel, cellSize, false, noiseFloor);
    }

    private void computeSensorReceivedPower(){
        if (this.sss == null) // if there is no sensors
            return;
        for (SpectrumSensor spectrumSensor : this.sss) {
            spectrumSensor.getRx().setReceived_power(Double.NEGATIVE_INFINITY); // resetting for new computations
            // add noise, creating tmp TX with noise_floor power at ss loc
            spectrumSensor.getRx().setReceived_power(powerWithPathLoss(
                    new TX(new Element(spectrumSensor.getRx().getElement().getLocation(),
                            spectrumSensor.getRx().getElement().getHeight()), this.noiseFloor),
                    spectrumSensor.getRx()));
        }
        for (SpectrumSensor spectrumSensor : this.sss) {
            for (TX tx : this.txs) {
                spectrumSensor.getRx().setReceived_power(powerWithPathLoss(tx,
                        spectrumSensor.getRx()));
            }
        }
    }

    // this method power value of receiver after transmitter effect. (return = rx_power + tx_power - path_loss)
    private double powerWithPathLoss(TX tx, RX rx){
        if (tx.getPower() == Double.NEGATIVE_INFINITY)
            return rx.getReceived_power();
        double loss = this.propagationModel.pathLoss(tx.getElement().mul(this.cellSize),
                rx.getElement().mul(this.cellSize));
        return WirelessTools.getDB(WirelessTools.getDecimal(tx.getPower() - loss) +
                WirelessTools.getDecimal(rx.getReceived_power()));
    }

    private String txsInfo(){
        if (this.txs == null || this.txs.length == 0)
            return "0";
        StringBuilder puInformation = new StringBuilder(""); // better to use StringBuilder for concatenation
        for (int i = 0; i < this.txs.length - 1; i++) {
            TX tx = this.txs[i];
            puInformation.append(tx.getElement().getLocation().getCartesian().getX())
                    .append(",").append(tx.getElement().getLocation().getCartesian().getY())
                    .append(",").append(String.format("%.3f", tx.getPower())).append(",");
        }
        //append the last tx
        TX tx  = this.txs[this.txs.length - 1];
        puInformation.append(tx.getElement().getLocation().getCartesian().getX())
                .append(",").append(tx.getElement().getLocation().getCartesian().getY())
                .append(",").append(String.format("%.3f", tx.getPower()));
        return String.format("%1$d,%2$s", this.txs.length, puInformation);
    }

    private String sssInfo(){
        StringBuilder sssInformation = new StringBuilder("");
        if (sssLocation)
            sssInformation.append(String.format("%d,", sss.length));
        for (int ssId = 0; ssId < this.sss.length - 1; ssId++){
            SpectrumSensor ss = this.sss[ssId];
            if (sssLocation)
                sssInformation.append(ss.getRx().getElement().getLocation().getCartesian().getX())
                        .append(",").append(ss.getRx().getElement().getLocation().getCartesian().getY())
                        .append(",");
            sssInformation.append(ss.getRx().getReceived_power() == Double.NEGATIVE_INFINITY ? "-inf" :
                    String.format("%.3f",ss.getRx().getReceived_power())).append(",");
        }
        SpectrumSensor ss = this.sss[this.sss.length - 1];
        if (sssLocation)
            sssInformation.append(ss.getRx().getElement().getLocation().getCartesian().getX())
                    .append(",").append(ss.getRx().getElement().getLocation().getCartesian().getY())
                    .append(",");
        sssInformation.append(ss.getRx().getReceived_power() == Double.NEGATIVE_INFINITY ? "-inf" :
                String.format("%.3f",ss.getRx().getReceived_power()));
        return sssInformation.toString();
    }

    private String strongest(){
        if (txs == null || txs.length == 0)
            return String.format("%.1f,%.1f,%.3f", 0.0, 0.0, this.noiseFloor);
        int strongestIdx = -1;
        double highestPower = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < txs.length; i++){
            if (txs[i].getPower() > highestPower){
                highestPower = txs[i].getPower();
                strongestIdx = i;
            }
        }

        return String.format("%.1f,%.1f,%.3f", txs[strongestIdx].getElement().getLocation().getCartesian().getX(),
                txs[strongestIdx].getElement().getLocation().getCartesian().getY(),
                txs[strongestIdx].getPower());
    }

    //finding the most isolated tX info as a String
    private String mostIsolatedTx(){
        if (txs == null || txs.length == 0)
            return String.format("%.1f,%.1f,%.3f", -1000.0, -1000.0, this.noiseFloor);
        double[] distances = new double[txs.length];
        //set MAX_VALUE for the minimum
        Arrays.fill(distances, Double.MAX_VALUE);
        // calculating distances of a tx to all other txs
        for (int i = 0; i < txs.length; i++){
            for (int j = 0; j < txs.length; j++){
                if (i == j) continue;
                // sum of distances to others
//                distances[i] += txs[i].getElement().getLocation().mul(cellSize).distance(
//                        txs[j].getElement().getLocation().mul(cellSize));
                // minimum distance to another TX
                distances[i] = Math.min(distances[i], txs[i].getElement().getLocation().mul(cellSize).distance(
                        txs[j].getElement().getLocation().mul(cellSize)));
            }
        }

        int mostIsolatedTxIdx = -1;     // the one that have the highest distances to others
        double highestDistance = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < txs.length; i++){
            if (distances[i] > highestDistance){
                highestDistance = distances[i];
                mostIsolatedTxIdx = i;
            }
        }
        return String.format("%.1f,%.1f,%.3f", txs[mostIsolatedTxIdx].getElement().getLocation().getCartesian().getX(),
                txs[mostIsolatedTxIdx].getElement().getLocation().getCartesian().getY(),
                txs[mostIsolatedTxIdx].getPower());
    }
    @Override
    public String toString(){
        return String.format("%s,%s,%s", sssInfo(), strongest(), txsInfo());
    }
}
