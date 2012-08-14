package edu.ucsd.msjava.msutil;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Iterator;

import uk.ac.ebi.jmzidml.model.mzidml.SpectrumIDFormat;

import edu.ucsd.msjava.jmzparser.MzMLSpectraIterator;
import edu.ucsd.msjava.jmzparser.MzMLSpectraMap;
import edu.ucsd.msjava.parser.MS2SpectrumParser;
import edu.ucsd.msjava.parser.MgfSpectrumParser;
import edu.ucsd.msjava.parser.MzXMLSpectraIterator;
import edu.ucsd.msjava.parser.MzXMLSpectraMap;
import edu.ucsd.msjava.parser.PNNLSpectraIterator;
import edu.ucsd.msjava.parser.PNNLSpectraMap;
import edu.ucsd.msjava.parser.PklSpectrumParser;
import edu.ucsd.msjava.parser.SpectrumParser;
import edu.ucsd.msjava.psi.Constants;



public class SpecFileFormat extends FileFormat {
	private final String psiAccession;
	private final String psiName;
	
	private SpecFileFormat(String suffix, String psiAccession, String psiName)
	{
		super(suffix);
		this.psiAccession = psiAccession;
		this.psiName = psiName;
	}
	
	public String getPSIAccession()	
	{
		return psiAccession;
	}
	
	public String getPSIName()
	{
		return psiName;
	}
	
	public static final SpecFileFormat MGF;
	public static final SpecFileFormat MZXML;
	public static final SpecFileFormat MZML;
	public static final SpecFileFormat MS2;
	public static final SpecFileFormat PKL;
	public static final SpecFileFormat MZDATA;
	public static final SpecFileFormat DTA_TXT;
	
	public static SpectrumAccessorBySpecIndex getSpecMap(File specFile)
	{
		SpecFileFormat specFormat = getSpecFileFormat(specFile.getName());
		if(specFormat == null)
			return null;
		else
			return getSpecMap(specFile, specFormat);
	}

	public static SpectrumAccessorBySpecIndex getSpecMap(File specFile, SpecFileFormat specFormat)
	{
		SpectrumAccessorBySpecIndex specMap = null;
		if(specFormat == MZXML)
			specMap = new MzXMLSpectraMap(specFile.getPath());
		else if(specFormat == MZML)
			specMap = new MzMLSpectraMap(specFile);
		else if(specFormat == SpecFileFormat.DTA_TXT)
			specMap = new PNNLSpectraMap(specFile.getPath());
		else
		{
			SpectrumParser parser = null;
			if(specFormat == SpecFileFormat.MGF)
				parser = new MgfSpectrumParser();
			else if(specFormat == SpecFileFormat.MS2)
				parser = new MS2SpectrumParser();
			else if(specFormat == SpecFileFormat.PKL)
				parser = new PklSpectrumParser();
			specMap = new SpectraMap(specFile.getPath(), parser);
		}
		
		return specMap;
	}
			
	public static Iterator<Spectrum> getSpecItr(File specFile) throws FileNotFoundException
	{
		SpecFileFormat specFormat = getSpecFileFormat(specFile.getName());
		if(specFormat == null)
			return null;
		else
			return getSpecItr(specFile, specFormat);
	}
	
	public static Iterator<Spectrum> getSpecItr(File specFile, SpecFileFormat specFormat) throws FileNotFoundException
	{
		Iterator<Spectrum> specItr = null;
		
		if(specFormat == MZXML)
			specItr = new MzXMLSpectraIterator(specFile.getPath());
		else if(specFormat == MZML)
			specItr = new MzMLSpectraIterator(specFile);
		else if(specFormat == SpecFileFormat.DTA_TXT)
			specItr = new PNNLSpectraIterator(specFile.getPath());
		else
		{
			SpectrumParser parser = null;
			if(specFormat == SpecFileFormat.MGF)
				parser = new MgfSpectrumParser();
			else if(specFormat == SpecFileFormat.MS2)
				parser = new MS2SpectrumParser();
			else if(specFormat == SpecFileFormat.PKL)
				parser = new PklSpectrumParser();
			specItr = new SpectraIterator(specFile.getPath(), parser);
		}
		
		return specItr;
	}
	
	public static SpecFileFormat getSpecFileFormat(String specFileName)
	{
		String lowerCaseFileName = specFileName.toLowerCase();
		for(SpecFileFormat f : specFileFormatList)
		{
			for(String suffix : f.getSuffixes())
			{
				if(lowerCaseFileName.endsWith(suffix.toLowerCase()))
					return f;
			}
		}
		return null;
	}
	
	private static ArrayList<SpecFileFormat> specFileFormatList;
	static {
		MGF = new SpecFileFormat(".mgf", "MS:1001062", "Mascot MGF file");
		MZXML = new SpecFileFormat(".mzXML", "MS:1000566", "ISB mzXML file");
		MZML = new SpecFileFormat(".mzML", "MS:1000584", "mzML file");
		MS2 = new SpecFileFormat(".ms2", "MS:1001466", "MS2 file");
		PKL = new SpecFileFormat(".pkl", "MS:1000565", "Micromass PKL file");
		MZDATA = new SpecFileFormat(".mzData", "MS:1000564", "PSI mzData file");
		DTA_TXT = new SpecFileFormat("_dta.txt", "MS:XXXXXXX", "PNNL dta.txt file");
		
		specFileFormatList = new ArrayList<SpecFileFormat>();
		specFileFormatList.add(MGF);
		specFileFormatList.add(MZXML);
		specFileFormatList.add(MZML);
		specFileFormatList.add(MS2);
		specFileFormatList.add(PKL);
		specFileFormatList.add(MZDATA);
		specFileFormatList.add(DTA_TXT);
	}
}