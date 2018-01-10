package ngsep.sequences;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import ngsep.genome.GenomicRegion;
import ngsep.genome.GenomicRegionImpl;

public class FMIndexSingleSequence implements Serializable 
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 5981359942407474671L;
	
	private static final char SPECIAL_CHARACTER = '$';
	private static final int DEFAULT_TALLY_DISTANCE = 100;
	private static final int DEFAULT_SUFFIX_FRACTION = 50;
	

	//Name of the sequence
	private String sequenceName;

	//Start position in the original sequence of some rows of the BW matrix representing a partial suffix array
	private Map<Integer,Integer> partialSuffixArray = new HashMap<>();

	//Ranks in the bwt for each character in the alphabet for some of the rows in the BW matrix
	private int [][] tallyIndexes;

	//1 of each tallyDistance is saved
	private int tallyDistance;

	// 1/suffixFraction indexes are saved
	private int suffixFraction;

	//Burrows Wheeler transform
	private char [] bwt;

	//For each character tells the first time it appears in the left column of the BW matrix
	private Map<Character,Integer> firstRowsInMatrix;
	
	//For each character tells the last time it appears in the left column of the BW matrix
	private Map<Character,Integer> lastRowsInMatrix;

	//Inferred alphabet of the sequence ordered lexicographical 
	private String alphabet;
	
	public FMIndexSingleSequence(QualifiedSequence sequence) {
		this (sequence.getName(),sequence.getCharacters(),DEFAULT_TALLY_DISTANCE,DEFAULT_SUFFIX_FRACTION);
	}

	public FMIndexSingleSequence(String seqName, CharSequence sequence) 
	{
		this(seqName,sequence,DEFAULT_TALLY_DISTANCE,DEFAULT_SUFFIX_FRACTION);
	}
	
	public FMIndexSingleSequence(String seqName, CharSequence sequence, int tallyDistance, int suffixFraction) {
		this.tallyDistance = tallyDistance;
		this.suffixFraction = suffixFraction;
		sequenceName=seqName;
		calculate(sequence);
	}
	
	public String getSequenceName() 
	{
		return sequenceName;
	}
	public int getTallyDistance() {
		return tallyDistance;
	}
	public void setTallyDistance(int tallyDistance) {
		this.tallyDistance = tallyDistance;
	}

	private void calculate(CharSequence sequence) 
	{
		List<Integer> suffixes = buildSuffixArray(sequence);
		buildBWT(sequence, suffixes);
		buildAlphabetAndCounts(sequence,suffixes);
		buildTally();
		createPartialSuffixArray(suffixes);

	}
	private List<Integer> buildSuffixArray(CharSequence sequence) {
		ArrayList<Integer> sufixes = new ArrayList<Integer>();
		for (int i = 0; i < sequence.length(); i++) {
			sufixes.add(i);
		}
		Collections.sort(sufixes, new SuffixCharSequencePositionComparator(sequence));
		return sufixes;
	}
	
	private void buildBWT(CharSequence sequence, List<Integer> suffixes) 
	{
		bwt = new char [sequence.length()+1];
		bwt[0] = sequence.charAt(sequence.length()-1);
		int j=1;
		for (int i:suffixes) {
			if(i>0) {
				bwt[j] = sequence.charAt(i-1);
			}
			else {
				bwt[j]= SPECIAL_CHARACTER;	
			}
			j++;
		}
	}
	
	private void buildAlphabetAndCounts(CharSequence seq, List<Integer> suffixArray) {
		Map<Character,Integer> counts= new TreeMap<>();
		firstRowsInMatrix = new TreeMap<>();
		lastRowsInMatrix = new TreeMap<>();
		char lastC = SPECIAL_CHARACTER;
		StringBuilder alpB = new StringBuilder();
		firstRowsInMatrix.put(lastC, 0);
		lastRowsInMatrix.put(lastC, 0);
		//iterate last column to know alphabet and counts...
		for (int i = 0; i < suffixArray.size(); i++) 
		{
			int j = suffixArray.get(i);
			char c = seq.charAt(j);
			Integer countC = counts.get(c); 
			if(countC == null) {
				counts.put(c,1);	
			} else {
				counts.put(c, countC+1);
			}
			if(lastC != c) {
				alpB.append(c);
				firstRowsInMatrix.put(c, i+1);
				lastRowsInMatrix.put(lastC, i+1);
			}
			lastC = c;
		}
		lastRowsInMatrix.put(lastC, suffixArray.size());
		alphabet = alpB.toString();
		
	}
	private void buildTally() 
	{
		int [] arr= new int[alphabet.length()];
		Arrays.fill(arr, 0);
		int tallyRows = bwt.length/tallyDistance;
		if(bwt.length%tallyDistance>0)tallyRows++;
		tallyIndexes = new int[tallyRows][arr.length];
		int j=0;
		for (int i=0;i<bwt.length;i++) {
			char c = bwt[i];
			if (c != SPECIAL_CHARACTER) {
				int indexC = alphabet.indexOf(c);
				if(indexC<0) throw new RuntimeException("Character "+c+" not found in the alphabet "+alphabet);
				arr[indexC]++;
			}
			if(i%tallyDistance==0) {
				int [] copy= Arrays.copyOf(arr, arr.length);
				tallyIndexes[j] = copy;
				j++;
			}
		}
	}
	
	
	private void createPartialSuffixArray(List<Integer> suffixes) 
	{
		partialSuffixArray = new HashMap<Integer,Integer>();
		int n = suffixes.size();
		for(int i=0;i<n;i++) 
		{
			int startSeq = suffixes.get(i);
			if(startSeq%suffixFraction==0) 
			{
				partialSuffixArray.put(i+1, startSeq);
			}
		}
	}
	
	public List<GenomicRegion> search (String searchSequence) 
	{
		List<GenomicRegion> alignments = new ArrayList<>();
		int[] range = getRange(searchSequence);
		if (range !=null)
		{

			for (int i = range[0]; i <= range[1]; i++) 
			{
				int begin=0;
				int actual = i;
				if(partialSuffixArray.containsKey(actual))
				{
					begin = partialSuffixArray.get(actual);
				}
				else
				{
					boolean found = false;
					int possible = 0;
					int steps =0;
					while(!found)
					{
						//					System.out.println("ac " +actual);
						possible = lfMapping(actual);
						found=partialSuffixArray.containsKey(possible);
						actual =possible;
						steps++;
					}
					begin = partialSuffixArray.get(possible)+steps;
				}
				alignments.add(new GenomicRegionImpl(sequenceName,begin , begin+searchSequence.length()));

			}
		}

		return alignments;
	}

	public int[] getRange(String query)
	{
		char c = query.charAt(query.length()-1);
		
		Integer rowS=firstRowsInMatrix.get(c);
		Integer rowF=lastRowsInMatrix.get(c);
		if(rowS == -1 || rowF==-1) {
			return null;
		}
		for(int j=query.length()-2;j>=0;j--) {
			c = query.charAt(j);
			if(alphabet.indexOf(c)<0) return null;
			boolean add1 = (bwt[rowS]!=c); 
			rowS = lfMapping(c, rowS);
			if(add1) rowS++; 
			rowF = lfMapping(c, rowF);
			if(rowS>rowF) {
				return null;
			}
		}

		return new int[] {rowS, rowF };
	}
	
	
	public int getTallyOf(char c, int row)
	{
		int r = 0;

		int a = row/tallyDistance;
		int b = a+1;

		if( row-a*tallyDistance < b*tallyDistance-row || tallyIndexes.length<=b) {
			//Recalculate from top record
			r = tallyIndexes[a][alphabet.indexOf(c)];

			for (int j = a*tallyDistance+1; j <= row; j++) {
				char cA = bwt[j];
				if(cA==c) r++;
			}
		} else {
			//Recalculate from bottom record
			r = tallyIndexes[b][alphabet.indexOf(c)];
			for (int j = b*tallyDistance; j > row; j--) 
			{
				char cA = bwt[j];
				if(cA==c) r--;
			}
		}
		return r;
	}
	
	private int lfMapping(char c, int row) 
	{
		int rank = getTallyOf(c, row);
		return firstRowsInMatrix.get(c) + rank - 1;
	}
	
	private int lfMapping (int row)
	{
		char c = bwt[row];
		//		System.out.println(""+c);
		return lfMapping(c,row);
	}
	
	

}