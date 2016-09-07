/*******************************************************************************
 * NGSEP - Next Generation Sequencing Experience Platform
 * Copyright 2016 Jorge Duitama
 *
 * This file is part of NGSEP.
 *
 *     NGSEP is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     NGSEP is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with NGSEP.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package ngsep.transcriptome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ngsep.genome.GenomicRegion;
import ngsep.genome.GenomicRegionPositionComparator;
import ngsep.sequences.DNAMaskedSequence;


/**
 * Information for a set of exons in the same gene making a valid transcript
 * @author Jorge Duitama
 */
public class Transcript implements GenomicRegion {
	private Gene gene;
	private String id;
	private String status;
	private String sequenceName;
	private int first;
	private int last; //Always greater or equal than first
	private boolean negativeStrand;
	private DNAMaskedSequence cdnaSequence;
	private String proteinSequence;
	private boolean coding=false;
	private List<Exon> exons=new ArrayList<Exon>();
	//Precalculated exons information
	private List<Exon> exonsSortedTranscript=new ArrayList<Exon>();
	private int codingRelativeStart = -1;
	private int codingRelativeEnd = -1;
	private int length = 0;
	
	/**
	 * Creates a new transcript with the given information
	 * @param id Id of the transcript
	 * @param sequenceName Name of the sequence where the transcript is located
	 * @param start First position of the transcript in the sequence where the gene is located
	 * @param end Last position of the transcript in the sequence where the gene is located
	 * @param negativeStrand Tells if the transcription is done in the forward or in the reverse strand
	 * @param exons List of exons making up the transcript
	 */
	public Transcript(String id, String sequenceName, int first, int last, boolean negativeStrand) {
		super();
		this.sequenceName = sequenceName;
		this.setId(id);
		this.setStatus(status);
		this.setFirst(first);
		this.setLast(last);
		this.setNegativeStrand(negativeStrand);
	}
	/**
	 * Changes the list of exons making up the transcript
	 * @param exons New list of exons making up the transcript
	 */
	public void setExons(List<Exon> exons) {
		this.exons.clear();
		this.exons.addAll(exons);
		Collections.sort(this.exons,GenomicRegionPositionComparator.getInstance());
		this.exonsSortedTranscript.clear();
		this.exonsSortedTranscript.addAll(this.exons);
		if(negativeStrand) Collections.reverse(this.exonsSortedTranscript);
		length = 0;
		codingRelativeStart = -1;
		codingRelativeEnd = -1;
		for(Exon e: exonsSortedTranscript) {
			if(e.isCoding()) {
				if(codingRelativeStart==-1) codingRelativeStart = length;
				codingRelativeEnd = length+e.length()-1;
				coding = true;
			}
			length+=e.length();
		}
	}
	/**
	 * Calculates the position relative to the start of the transcript given the position relative to the sequence
	 * @param absolutePosition Position relative to the sequence where the transcript is located
	 * @return int Zero based position relative to the start of the transcript or -1 if the given 
	 * position does not belong to the transcript
	 */
	public int getRelativeTranscriptPosition (int absolutePosition) {
		int answer = 0;
		for(Exon e:exonsSortedTranscript) {
			if(negativeStrand && e.getLast()< absolutePosition) {
				return -1;
			} else if (!negativeStrand && e.getFirst()> absolutePosition) {
				return -1;
			}
			if(e.getFirst()<=absolutePosition && e.getLast()>=absolutePosition) {
				if(negativeStrand) {
					answer+= (e.getLast()-absolutePosition);
				} else {
					answer+= (absolutePosition-e.getFirst());
				}
				return answer;
			}
			answer+= e.length();
		}
		return -1;
	}
	/**
	 * Calculates the position relative to the sequence given the position relative to the start of the transcript
	 * @param relativeTranscriptPosition Zero based Position relative to the start of the transcript
	 * @return int Position relative to the sequence or -1 if the given position is negative or larger
	 * or equal than the transcript length 
	 */
	public int getAbsolutePosition(int relativeTranscriptPosition) {
		for(Exon e:exonsSortedTranscript) {
			if(relativeTranscriptPosition<e.length()) {
				if(this.negativeStrand) {
					return e.getLast() - relativeTranscriptPosition;
				} else {
					return e.getFirst() + relativeTranscriptPosition;
				}
			} else {
				relativeTranscriptPosition-=e.length();
			}
		}
		return -1;
	}
	/**
	 * Finds the reference base for the given position
	 * @param absolutePosition Position relative to the sequence where the transcript is located
	 * @return char Reference base in the given position or 0 if the given position
	 * does not belong to the transcript
	 */
	public char getReferenceBase (int absolutePosition) {
		int pos = getRelativeTranscriptPosition(absolutePosition);
		if( cdnaSequence!=null && pos>=0 && pos < length()) {
			char refBase = cdnaSequence.charAt(pos);
			if(negativeStrand) {
				refBase = DNAMaskedSequence.getComplement(refBase);
			}
			return refBase;
		}
		return 0;
	}
	/**
	 * Returns the transcript sequence between the given absolute coordinates
	 * @param absoluteFirst First position to retrieve
	 * @param absoluteLast Last position to retrieve
	 * @return String Segment of the protein between the given transcripts or null if one of the two coordinates can not be mapped
	 */
	public String getReference (int absoluteFirst, int absoluteLast) {
		int pos1 = getRelativeTranscriptPosition(absoluteFirst);
		int pos2 = getRelativeTranscriptPosition(absoluteLast);
		int posMin = Math.min(pos1, pos2);
		int posMax = Math.max(pos1, pos2);
		if( cdnaSequence!=null && pos1>=0 && pos1 < length() && pos2>=0 && pos2 < length()) {
			DNAMaskedSequence seq = (DNAMaskedSequence)cdnaSequence.subSequence(posMin, posMax+1);
			if(negativeStrand) {
				return seq.getReverseComplement().toString();
			}
			return seq.toString();
		}
		return null;
	}
	/**
	 * Changes the reference base for the given position
	 * @param absolutePosition Position relative to the sequence where the transcript is located
	 * @param base New base to set in the given position. No changes are done if the given
	 * position does not belong to the transcript
	 */
	public void setReferenceBase(int absolutePosition, char base) {
		int pos = getRelativeTranscriptPosition(absolutePosition);
		if( cdnaSequence!=null && pos>=0 && pos < length()) {
			if(negativeStrand) {
				base = DNAMaskedSequence.getComplement(base);
			}
			cdnaSequence.setCharAt(pos, base);
		}
	}
	/**
	 * Returns the Exon spanning the given relative position 
	 * @param relativeTranscriptPosition Zero based position relative to the start of the transcript
	 * @return Exon spanning the given position or null if the given position is invalid
	 */
	public Exon getExon (int relativeTranscriptPosition) {
		if(relativeTranscriptPosition<0) return null;
		for(Exon e:exonsSortedTranscript) {
			if(relativeTranscriptPosition<e.length()) {
				return e;
			} else {
				relativeTranscriptPosition-=e.length();
			}
		}
		return null;
	}
	/**
	 * Returns the Exon spanning the given position 
	 * @param absolutePosition Position relative to the sequence where the transcript is located
	 * @return Exon spanning the given position or null if the given position does not belong 
	 * to the transcript
	 */
	public Exon getExonByAbsolutePosition (int absolutePosition) {
		if(first > absolutePosition || last < absolutePosition) {
			return null;
		}
		for(Exon e:exons) {
			if(e.getFirst()<=absolutePosition && e.getLast()>=absolutePosition) {
				return e;
			} else if (e.getLast() > absolutePosition) {
				return null;
			}
		}
		return null;
	}
	/**
	 * Returns a list of exons overlapping the region delimited by the given start and end 
	 * @param absoluteStart First position relative to the sequence where the transcript is located
	 * @param absoluteEnd Last position relative to the sequence where the transcript is located
	 * @return List<Exon> Exons in the transcript spanning the interval defined by the given coordinates
	 */
	public List<Exon> getExonsByAbsolute(int absoluteStart, int absoluteEnd) {
		ArrayList<Exon> answer = new ArrayList<Exon>();
		for(Exon e:exons) {
			if(e.getFirst()<=absoluteEnd && e.getLast() >= absoluteStart ) {
				answer.add(e);
			}
		}
		return answer;
	}
	/**
	 * @return int Length of the transcript calculated as the sum of the exons making up the transcript
	 */
	public int length() {
		return length;
	}
	/**
	 * Returns a list of exons overlapping the region delimited by the given start and end
	 * @param transcriptRelativeStart Zero based first position relative to the start of the transcript
	 * @param transcriptRelativeEnd Zero based last position relative to the start of the transcript
	 * @return List<Exon> Exons in the transcript spanning the interval defined by the given coordinates
	 */
	public List<Exon> getExons(int transcriptRelativeStart, int transcriptRelativeEnd) {
		ArrayList<Exon> answer = new ArrayList<Exon>();
		int exonStart=0;
		for(Exon e:exonsSortedTranscript) {
			int nextExonStart = exonStart+e.length();
			if(transcriptRelativeStart<nextExonStart && transcriptRelativeEnd >= exonStart ) {
				answer.add(e);
			} 
			exonStart=nextExonStart;
		}
		return answer;
	}
	public int getCodingRelativeStart() {
		return codingRelativeStart;
	}
	public int getCodingRelativeEnd() {
		return codingRelativeEnd;
	}
	/**
	 * @return String Id of the gene related with this transcript
	 */
	public String getGeneId() {
		if(gene!=null) {
			return gene.getId();
		}
		return "";
	}
	/**
	 * @return String name of the gene related with this transcript
	 */
	public String getGeneName() {
		if(gene!=null) {
			return gene.getName();
		}
		return "";
	}
	/**
	 * @return String Name of the sequence where the transcript is mapped
	 */
	public String getSequenceName() {
		return sequenceName;
	}
	/**
	 * @return String Id of the transcript
	 */
	public String getId() {
		return id;
	}
	/**
	 * Changes the id of the transcript
	 * @param id New id
	 */
	public void setId(String id) {
		this.id = id;
	}
	/**
	 * @return String Status of the transcript
	 */
	public String getStatus() {
		return status;
	}
	/**
	 * Changes the status of the trasncript
	 * @param status New status of the transcript
	 */
	public void setStatus(String status) {
		this.status = status;
	}
	/**
	 * @return int First position of the transcript in the sequence where the gene is located
	 */
	public int getFirst() {
		return first;
	}
	/**
	 * Changes the first position of the transcript
	 * @param first New first
	 */
	public void setFirst(int first) {
		this.first = first;
	}
	/**
	 * @return int Last position of the transcript in the sequence where the gene is located
	 */
	public int getLast() {
		return last;
	}
	/**
	 * Changes the end of the transcript
	 * @param end New end
	 */
	public void setLast(int last) {
		this.last = last;
	}
	/**
	 * Changes the strand of the transcript
	 * @param negativeStrand true if the transcript is transcribed from the reverse strand. False otherwise
	 */
	public void setNegativeStrand(boolean negativeStrand) {
		this.negativeStrand = negativeStrand;
	}
	/**
	 * @return Sequence the sequence of the transcript as it is translated into a protein
	 */
	public DNAMaskedSequence getCDNASequence() {
		return cdnaSequence;
	}
	/**
	 * Changes the CDNA sequence
	 * @param sequence New sequence
	 */
	public void setCDNASequence(DNAMaskedSequence sequence) {
		cdnaSequence = sequence;
	}
	/**
	 * @return String the protein sequence after translation
	 */
	public String getProteinSequence() {
		return proteinSequence;
	}
	/**
	 * Changes the protein sequence
	 * @param proteinSequence New protein sequence
	 */
	public void setProteinSequence(String proteinSequence) {
		this.proteinSequence = proteinSequence;
	}
	/**
	 * Gets the translation to protein for the CDNA sequence of this transcript according with the translator
	 * @param translator Object that translates CDNA sequences into protein sequences
	 * @return String Protein sequence after translation
	 */
	public String getProteinSequence(ProteinTranslator translator) {
		int translationStart = getCodingRelativeStart();
		if(translationStart>=0) {
			return translator.getProteinSequence(cdnaSequence.subSequence(translationStart));
		}
		return "";
	}
	/**
	 * @return List<Exon> List of exons making up the transcript sorted by genomic position
	 */
	public List<Exon> getExons() {
		return exons;
	}
	/**
	 * @return Gene the gene to which the transcript belongs
	 */
	public Gene getGene() {
		return gene;
	}
	/**
	 * Changes the gene information for this transcript
	 * @param gene New gene
	 */
	public void setGene(Gene gene) {
		this.gene = gene;
	}
	/**
	 * @return boolean true if the transcript encodes for a protein, false otherwise
	 */
	public boolean isCoding() {
		return coding;
	}
	/**
	 * @return boolean true if the transcript comes from the positive strand
	 */
	public boolean isPositiveStrand() {
		return !negativeStrand;
	}
	/**
	 * @return boolean true if the transcript comes from the negative strand
	 */
	public boolean isNegativeStrand() {
		return negativeStrand;
	}
}