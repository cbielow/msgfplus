package edu.ucsd.msjava.msscorer;

import edu.ucsd.msjava.msgf.ScoredSpectrum;
import edu.ucsd.msjava.msgf.Tolerance;
import edu.ucsd.msjava.msutil.*;

public class NewScoredSpectrum<T extends Matter> implements ScoredSpectrum<T> {

    private Spectrum spec;
    private NewRankScorer scorer;
    private Tolerance mme;

    private IonType[][] ionTypes;    // segmentNum, ionType
    private final int charge;
    private final float parentMass;
    private final Peak precursor;
    private final int[] scanNumArr;
    private ActivationMethod[] activationMethodArr;
    private IonType mainIon;
    private Partition partition;    // partition of the last segment
    private float probPeak;

    public NewScoredSpectrum(Spectrum spec, NewRankScorer scorer) {
        this.scorer = scorer;

        this.charge = spec.getCharge();
        this.parentMass = spec.getPrecursorMass();
        this.mme = scorer.mme;
        this.precursor = spec.getPrecursorPeak().clone();
        this.activationMethodArr = new ActivationMethod[1];
//		activationMethodArr[0] = scorer.getActivationMethod();
        if (spec.getActivationMethod() != null)
            activationMethodArr[0] = spec.getActivationMethod();
        else
            activationMethodArr[0] = scorer.getSpecDataType().getActivationMethod();
        this.scanNumArr = new int[1];
        scanNumArr[0] = spec.getScanNum();

        int numSegments = scorer.getNumSegments();
        ionTypes = new IonType[numSegments][];
        for (int seg = 0; seg < numSegments; seg++)
            ionTypes[seg] = scorer.getIonTypes(charge, parentMass, seg);

        // filter precursor peaks
        for (PrecursorOffsetFrequency off : scorer.getPrecursorOFF(spec.getCharge()))
            spec.filterPrecursorPeaks(mme, off.getReducedCharge(), off.getOffset());
        spec.setRanksOfPeaks();

        // deconvolute spectra
        if (scorer.applyDeconvolution())
            spec = spec.getDeconvolutedSpectrum(scorer.deconvolutionErrorTolerance());

        // for edge scoring
        partition = scorer.getPartition(spec.getCharge(), spec.getPrecursorMass(), scorer.getNumSegments() - 1);
        mainIon = scorer.getMainIonType(partition);

        float approxNumBins = spec.getPeptideMass() / (scorer.getMME().getValue() * 2);

        if (spec.size() == 0)
            probPeak = 1 / Math.max(approxNumBins, 1);
        else
            probPeak = spec.size() / Math.max(approxNumBins, 1);

        this.spec = spec;
    }

    public Peak getPrecursorPeak() {
        return precursor;
    }

    public ActivationMethod[] getActivationMethodArr() {
        return this.activationMethodArr;
    }

    public int getNodeScore(T prm, T srm) {
        float prefScore = getNodeScore(prm, true);
        float sufScore = getNodeScore(srm, false);
        return Math.round(prefScore + sufScore);
    }

    public int getEdgeScore(T curNode, T prevNode, float theoMass) {
        if (!scorer.supportEdgeScores())
            return 0;

        int ionExistenceIndex = 0;
        float curNodeMass = getNodeMass(curNode);
        if (curNodeMass >= 0)
            ionExistenceIndex += 1;
        Float prevNodeMass = getNodeMass(prevNode);
        if (prevNodeMass >= 0)
            ionExistenceIndex += 2;

        float edgeScore = scorer.getIonExistenceScore(partition, ionExistenceIndex, probPeak);
        if (ionExistenceIndex == 3)
            edgeScore += scorer.getErrorScore(partition, curNodeMass - prevNodeMass - theoMass);

//		// debug
//		if(edgeScore < -1000 || edgeScore > 1000)
//		{
//			System.out.println("Error! EdgeScore = " + edgeScore);
//			System.out.println("Spectrum ScanNum: " + spec.getScanNum());
//			System.out.println("Partition: " + partition.getCharge() + " " + partition.getSegNum() + " " + partition.getParentMass());
//			System.out.println("IonExistence: " + scorer.getIonExistenceScore(partition, ionExistenceIndex, probPeak));
//			System.out.println("Error: " + scorer.getErrorScore(partition, curNodeMass-prevNodeMass-theoMass));
//		}
        return Math.round(edgeScore);
    }

    public NewRankScorer getScorer() {
        return scorer;
    }

    public Partition getPartition() {
        return partition;
    }

    public float getProbPeak() {
        return probPeak;
    }

    public IonType getMainIon() {
        return mainIon;
    }

    public boolean getMainIonDirection() {
        return mainIon.isPrefixIon();
    }

    /**
     * returns the corrected mass of the node based on the peak observed in the spectrum
     *
     * @param node
     * @return corrected mass of the node if peak exists, null -1
     */
    public float getNodeMass(T node) {
        if (node.getNominalMass() == 0)
            return 0;
        float theoMass = mainIon.getMz(node.getMass());
        Peak p = spec.getPeakByMass(theoMass, scorer.getMME());
        if (p != null)
            return mainIon.getMass(p.getMz());
        else
            return -1;
    }

    public float getNodeScore(T node, boolean isPrefix) {
        return getNodeScore(node.getMass(), isPrefix);
    }

    public float getNodeScore(float nodeMass, boolean isPrefix) {
        float score = 0;
        for (int segIndex = 0; segIndex < scorer.getNumSegments(); segIndex++) {
            for (IonType ion : ionTypes[segIndex]) {
                float theoMass;
                if (isPrefix)    // prefix
                {
                    if (ion instanceof IonType.PrefixIon)
                        theoMass = ion.getMz(nodeMass);
                    else
                        continue;
                } else {
                    if (ion instanceof IonType.SuffixIon)
                        theoMass = ion.getMz(nodeMass);
                    else
                        continue;
                }

                int segNum = scorer.getSegmentNum(theoMass, parentMass);
                if (segNum != segIndex)
                    continue;

                Peak p = spec.getPeakByMass(theoMass, mme);
                Partition part = scorer.getPartition(charge, parentMass, segNum);

                if (p != null)    // peak exists
                    score += scorer.getNodeScore(part, ion, p.getRank());
                else    // missing peak
                    score += scorer.getMissingIonScore(part, ion);
            }
        }
        return score;
    }

    public float getExplainedIonCurrent(float residueMass, boolean isPrefix, Tolerance fragmentTolerance) {
        float explainedIonCurrent = 0;
        for (int segIndex = 0; segIndex < scorer.getNumSegments(); segIndex++) {
            for (IonType ion : ionTypes[segIndex]) {
                float theoMass;
                if (isPrefix)    // prefix
                {
                    if (ion instanceof IonType.PrefixIon)
                        theoMass = ion.getMz(residueMass);
                    else
                        continue;
                } else {
                    if (ion instanceof IonType.SuffixIon)
                        theoMass = ion.getMz(residueMass);
                    else
                        continue;
                }

                int segNum = scorer.getSegmentNum(theoMass, parentMass);
                if (segNum != segIndex)
                    continue;

                Peak p = spec.getPeakByMass(theoMass, fragmentTolerance);

                if (p != null)    // peak exists
                    explainedIonCurrent += p.getIntensity();
            }
        }
        return explainedIonCurrent;
    }

    public Pair<Float, Float> getMassErrorWithIntensity(float residueMass, boolean isPrefix, Tolerance fragmentTolerance) {
        Float error = null;
        float maxIntensity = 0;
//		IonType bestIon = null;
//		Peak bestPeak = null;
//		float bestTheoMass = 0;

        for (int segIndex = 0; segIndex < scorer.getNumSegments(); segIndex++) {
            for (IonType ion : ionTypes[segIndex]) {
                if (ion.getCharge() != 1)
                    continue;
                float theoMass;
                if (isPrefix)    // prefix
                {
                    if (ion instanceof IonType.PrefixIon)
                        theoMass = ion.getMz(residueMass);
                    else
                        continue;
                } else {
                    if (ion instanceof IonType.SuffixIon)
                        theoMass = ion.getMz(residueMass);
                    else
                        continue;
                }

                int segNum = scorer.getSegmentNum(theoMass, parentMass);
                if (segNum != segIndex)
                    continue;

                Peak p = spec.getPeakByMass(theoMass, fragmentTolerance);

                if (p != null)    // peak exists
                {
                    float err = (p.getMz() - theoMass) / theoMass * 1e6f;
//					float err = p.getMz() - theoMass;
//					if(err < 0)
//						err = -err;
                    float intensity = p.getIntensity();
                    // Debug
//					System.out.println(residueMass + " " + ion.getName() + " " + err + " " + intensity);
                    if (intensity > maxIntensity) {
                        error = err;
                        maxIntensity = intensity;
//						bestIon = ion;
//						bestPeak = p;
//						bestTheoMass = theoMass;
                    }
                }
            }
        }
        if (error == null)
            return null;
        else {
//			// Debug
//			System.out.println("*\t" + residueMass + "\t" + bestIon.getName() + "\t" + error + "\t" + bestPeak.getRank() 
//					+ "\t" + bestPeak.getMz() + "\t" + bestPeak.getIntensity() + "\t" + bestTheoMass);
            return new Pair<Float, Float>(error, maxIntensity);
        }
    }

    public Pair<Float, Float> getNodeMassAndScore(float residueMass, boolean isPrefix) {
        Float nodeMass = null;
        float nodeScore = 0;
        float curBestScore = 0;

        for (int segIndex = 0; segIndex < scorer.getNumSegments(); segIndex++) {
            for (IonType ion : ionTypes[segIndex]) {
                float theoMass;
                if (isPrefix)    // prefix
                {
                    if (ion instanceof IonType.PrefixIon)
                        theoMass = ion.getMz(residueMass);
                    else
                        continue;
                } else {
                    if (ion instanceof IonType.SuffixIon)
                        theoMass = ion.getMz(residueMass);
                    else
                        continue;
                }

                int segNum = scorer.getSegmentNum(theoMass, parentMass);
                if (segNum != segIndex)
                    continue;

                Peak p = spec.getPeakByMass(theoMass, mme);
                Partition part = scorer.getPartition(charge, parentMass, segNum);

                if (p != null)    // peak exists
                {
                    float score = scorer.getNodeScore(part, ion, p.getRank());
                    if (ion.getCharge() == 1 && score > curBestScore) {
                        nodeMass = ion.getMass(p.getMz());
                        curBestScore = score;
                    }
                    nodeScore += score;
                } else    // missing peak
                {
                    nodeScore += scorer.getMissingIonScore(part, ion);
                }
            }
        }
        return new Pair<Float, Float>(nodeMass, nodeScore);
    }

    public int[] getScanNumArr() {
        return scanNumArr;
    }
}
