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
package ngsep.sequences;

import java.util.Arrays;

/**
 * Implements the Sequence interface minimizing memory consumption no matter 
 * which is the underlying alphabet
 * @author Jorge Duitama
 *
 */
public abstract class AbstractLimitedSequence implements LimitedSequence {
	int [] sequence= new int [0];
	private byte lastHashSize=0;
	private int length=0;
	
	/**
	 * Makes this sequence equals to the given String
	 * @param sequence Sequence with data to copy
	 */
	public void setSequence(CharSequence sequence) {
		int l = sequence.length();
		int nHashNumbers = calculateHashNumbers(l);
		this.sequence = new int [nHashNumbers];
		if (l==0) lastHashSize = 0;
		else this.lastHashSize = this.encodeAndAppendSequence(sequence, 0, this.sequence, 0);
		this.length = l;
	}
	
	/**
	 * Append the given String at the end of this
	 * @param sequence String to append
	 */
	public void append(CharSequence sequence) {
		if(sequence.length()==0) return;
		int newLength = length()+sequence.length();
		//if(length()==0 && sequence.length()<5) System.out.println("Appending "+sequence+" to "+this.toString()+". Seq len: "+sequence.length()+" new length: "+newLength);
		int nHashNumbers = calculateHashNumbers(newLength);
		int maxHashSize = getMaxHashSize();
		//Generate junction number
		StringBuffer junction = new StringBuffer();
		if(lastHashSize>0 && lastHashSize < maxHashSize) {
			junction.append(getSequence(this.sequence[this.sequence.length-1], lastHashSize));
		}
		
		int firstPosAppend = 0;
		if(nHashNumbers == this.sequence.length) {
			junction.append(sequence);
			this.sequence[this.sequence.length-1] = getHash(junction.toString(), 0, junction.length());
			this.lastHashSize = (byte)junction.length();
			this.length = newLength;
			return;
		} else if (junction.length()>0) {
			firstPosAppend = maxHashSize-junction.length();
			//Avoid the use of subSequence or substring to avoid infinite recursion
			for(int i=0;i<firstPosAppend;i++) junction.append(sequence.charAt(i));
		}
		int [] newSequence = Arrays.copyOf(this.sequence, nHashNumbers);
		if(junction.length()>0) {
			newSequence[this.sequence.length-1] = getHash(junction.toString(), 0, junction.length());
		}
		this.lastHashSize = encodeAndAppendSequence(sequence, firstPosAppend, newSequence,this.sequence.length);
		
		this.sequence = newSequence;
		this.length = newLength;
	}
	private int calculateHashNumbers(int length) {
		byte maxHashSize = getMaxHashSize();
		int nHashNumbers = length/maxHashSize;
		if(length%maxHashSize>0) nHashNumbers++;
		return nHashNumbers;
	}
	private byte encodeAndAppendSequence(CharSequence sequence, int firstPosAppend, int[] newSequence, int firstIndex) {
		int nHashNumbers = newSequence.length;
		byte maxHashSize = getMaxHashSize();
		int j = firstPosAppend;
		for(int i=firstIndex;i<nHashNumbers-1;i++) {
			newSequence[i]= getHash(sequence,j,j+maxHashSize);
			j+=maxHashSize;
		}
		int lastStart = j;
		byte lastHashS = (byte)(sequence.length() - lastStart);
		newSequence[nHashNumbers-1]= getHash(sequence,lastStart,sequence.length());
		return lastHashS;
	}
	
	/**
	 * Returns the segment of this between the given indexes.
	 * @param beginIndex Zero based beginning index. Inclusive. 0<= beginIndex < this.length()
	 * @param endIndex Zero based ending index. Exclusive. 0<= endIndex <= this.length()
	 * @return Sequence Subsequence between the given indexes. The sequence type should
	 * be the same as this sequence
	 */
	public CharSequence subSequence (int start, int end) {
		if(start < 0 || start >= length) {
			throw new StringIndexOutOfBoundsException(start);
		}
		if(end<0 || end > length) {
			throw new StringIndexOutOfBoundsException(end);
		}
		if(end<start) {
			throw new StringIndexOutOfBoundsException("End index "+end+" cannot be less than start index: "+start);
		}
		LimitedSequence answer;
		try {
			answer = this.getClass().newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
		if(start== end) return answer; 
		byte maxHashSize = getMaxHashSize();
		int relStart = start/maxHashSize;
		int relEnd = end/maxHashSize;
		if (end % maxHashSize > 0) {
			relEnd++;
		}
		for(int i=relStart;i<relEnd;i++) {
			int hashSize = maxHashSize;
			if(i == sequence.length-1) {
				 hashSize = this.lastHashSize;
			} 
			String s = String.valueOf(getSequence(sequence[i],hashSize));
			int firstPos = maxHashSize*i;
			int endPos = maxHashSize*i + hashSize ;
			int firstSPos = 0;
			int endSPos = s.length();
			if(firstPos < start) {
				firstSPos = (start-firstPos);
			}
			if(endPos > end) {
				endSPos = end-firstPos; 
			}
			answer.append(s.substring(firstSPos, endSPos));
		}
		return answer;
	}
	public CharSequence subSequence (int start) {
		return subSequence(start,length);
	}
	/**
	 * Returns the character at the given position
	 * @param position Zero based position to look for
	 * @return char Character at the given position
	 */
	public char charAt(int position) {
		if(position < 0 || position>=this.length()) throw new StringIndexOutOfBoundsException(position);
		byte maxHashSize = getMaxHashSize();
		int relPos = position/maxHashSize;
		int subPos = position%maxHashSize;
		int size = getHashSize(relPos);
		char subSeq [] = getSequence(sequence[relPos], size);
		return subSeq[subPos];
	}
	/**
	 * Sets the given character at the given index
	 * @param index Zero based position to modify
	 * @param ch Character to set at index
	 */
	public void setCharAt(int position, char c) {
		if(getAlphabetIndex(c)<0) return;
		if(position < 0 || position>=this.length()) throw new StringIndexOutOfBoundsException(position);
		byte maxHashSize = getMaxHashSize();
		int relPos = position/maxHashSize;
		int subPos = position%maxHashSize;
		int size = getHashSize(relPos);
		char subSeq [] = getSequence(sequence[relPos], size);
		if(subSeq[subPos] != c) {
			subSeq[subPos] = c;
			int hash = getHash(String.valueOf(subSeq), 0, subSeq.length);
			sequence[relPos]=hash;
		}
	}
	@Override
	public String toString() {
		if(length()==0) return "";
		StringBuffer answer = new StringBuffer();
		for(int i=0;i<sequence.length;i++) {
			int size = getHashSize(i);
			answer.append(getSequence(sequence[i], size));
		}
		return answer.toString();
	}

	private int getHashSize(int pos) {
		byte maxHashSize = getMaxHashSize();
		if(pos < sequence.length-1) {
			return maxHashSize;
		} else if (pos == sequence.length-1) {
			return this.lastHashSize;
		}
		return 0;
	}
	
	@Override
	public int length() {
		return length;
	}
	private byte maxHashSize = 0;
	/**
	 * Return the maximum number of characters encoded in a single number
	 * @return byte
	 */
	private byte getMaxHashSize() {
		if(maxHashSize==0) maxHashSize = (byte)(32/getBitsPerCharacter());
		return maxHashSize;
	}
	/**
	 * Returns the number corresponding with a suitable size substring of the given sequence 
	 * @param seq Sequence to calculate hash
	 * @param start Zero based first position
	 * @param end Zero based last position
	 * @return int Number representing the substring of seq between first and last, both included
	 */
	private int getHash(CharSequence seq, int start, int end) {
		long number =0;
		int alpSize = getAlphabetSize();
		for(int i=start;i<end;i++) {
			number*=alpSize;
			int index = getAlphabetIndex(seq.charAt(i));
			if(index <0) {
				index = getDefaultIndex();
			}
			if(index <0) {
				throw new IllegalArgumentException("Character "+seq.charAt(i)+" not supported by sequence of type "+getClass().getName());
			}
			number+=index;
			if(number<0) throw new RuntimeException("Encoding reached a long negative number for sequence: "+seq+" between "+start+" and "+end);
		}
		return (int)(number+Integer.MIN_VALUE);
	}
	/**
	 * Gets the sequence corresponding withb the given hash and the given size
	 * @param number Hash number to decode
	 * @param size Size of the sequence to return
	 * @return char[] Decoded String as a char array
	 */
	private char [] getSequence(int number, int size) {
		char [] answer = new char[size];
		int alpSize = getAlphabetSize();
		long absoluteNumber = (long)number-(long)Integer.MIN_VALUE;
		for(int i=0;i<size;i++) {
			int nextDigit = (int)(absoluteNumber%alpSize);
			int index = size-i-1;
			answer[index] = getAlphabetCharacter(nextDigit);
			absoluteNumber = absoluteNumber/alpSize;
		}
		return answer;
	}
	/**
	 * Returns the character of the alphabet with the given index in the alphabet String.
	 * @param index Zero based index to look for in the alphabet
	 * @return char Character at the given index
	 */
	public char getAlphabetCharacter(int index) {
		String alp = getAlphabet();
		if(index>=0 && index<alp.length()) {
			return alp.charAt(index);
		}
		return 0;
	}
	/**
	 * Gets the index for the given character in the alphabet.
	 * Default implementation performs a linear search on the alphabet
	 * @param ch character to look for
	 * @return in Zero based index or -1 if the character is not in the alphabet
	 */
	public int getAlphabetIndex(char ch) {
		String alp = getAlphabet();
		return alp.indexOf(ch);
	}
	/**
	 * Tells if the character is in the alphabet
	 * @param ch character to look for
	 * @return boolean true if ch is in the alphabet, false otherwise
	 */
	public boolean isInAlphabet(char ch) {
		return getAlphabetIndex(ch)>=0;
	}
	/**
	 * Returns the size of the alphabet
	 * @return int Size of the alphabet
	 */
	public int getAlphabetSize() {
		return getAlphabet().length();
	}
	/**
	 * Gets the minimum number of bits needed to represent uniquely every character of
	 * the alphabet 
	 * @return int minimum number of bits needed to represent uniquely every character of
	 * the alphabet
	 */
	protected int getBitsPerCharacter() {
		return (int)(Math.log10(getAlphabetSize())/Math.log10(2))+1;
	}
	
	protected int getDefaultIndex () {
		return -1;
	}
	
}