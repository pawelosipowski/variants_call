package assemble;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.concurrent.atomic.AtomicInteger;

import jgi.BBMerge;
import kmer.AbstractKmerTable;
import kmer.HashArray1D;
import kmer.HashForest;
import kmer.KmerNode;
import kmer.KmerTableSet;
import stream.ByteBuilder;
import stream.ConcurrentReadInputStream;
import stream.Read;
import ukmer.Kmer;
import align2.IntList;
import align2.ListNum;
import align2.LongList;
import align2.Shared;
import align2.Tools;
import dna.AminoAcid;
import dna.Parser;
import dna.Timer;


/**
 * Short-kmer assembler based on KmerCountExact.
 * @author Brian Bushnell
 * @date May 15, 2015
 *
 */
public class Tadpole1 extends Tadpole {
	
	/**
	 * Code entrance from the command line.
	 * @param args Command line arguments
	 */
	public static void main(String[] args){
		
		args=Parser.parseConfig(args);
		if(Parser.parseHelp(args, true)){
			printOptions();
			System.exit(0);
		}
		
		Timer t=new Timer(), t2=new Timer();
		t.start();
		t2.start();
		
		//Create a new CountKmersExact instance
		Tadpole1 wog=new Tadpole1(args, true);
		t2.stop();
		outstream.println("Initialization Time:      \t"+t2);
		
		///And run it
		wog.process(t);
	}
	
	/**
	 * Constructor.
	 * @param args Command line arguments
	 */
	public Tadpole1(String[] args, boolean setDefaults){
		super(args, setDefaults);
		
		final int bytesPerKmer;
		{
			int mult=12;
			if(useOwnership){mult+=4;}
			if(processingMode==correctMode){}
			else if(processingMode==contigMode || processingMode==extendMode){mult+=1;}
			bytesPerKmer=mult;
		}
		
		tables=new KmerTableSet(args, bytesPerKmer);
		k=tables.k;
		k2=tables.k2;
	}

	
	/*--------------------------------------------------------------*/
	/*----------------         Outer Methods        ----------------*/
	/*--------------------------------------------------------------*/
	
	
	/*--------------------------------------------------------------*/
	/*----------------         Inner Methods        ----------------*/
	/*--------------------------------------------------------------*/
	
	@Override
	void initializeOwnership(){
		tables.initializeOwnership();
	}
	
	@Override
	long shave(boolean shave, boolean rinse){
		final Shaver shaver=new Shaver1(tables, THREADS);
		long sum=0;

		for(int i=0; i<maxShaveDepth; i++){
			int a=1, b=maxShaveDepth, c=i+1;
			//				if(i>3){Shaver.verbose2=true;}
			outstream.println("\nShave("+a+", "+b+", "+c+")");
			long removed=shaver.shave(a, b, c, Tools.max(minContigLen, shaveDiscardLen), shaveExploreDist, shave, rinse);
			sum+=removed;
			if(removed<100 || i>2){break;}
		}

		System.err.println();
		return sum;
	}
	
	@Override
	public long loadKmers(Timer t){
		tables.process(t);
		return tables.kmersLoaded;
	}
	
	
	/*--------------------------------------------------------------*/
	/*----------------        Recall Methods        ----------------*/
	/*--------------------------------------------------------------*/
	
	private final long rcomp(long kmer){return AminoAcid.reverseComplementBinaryFast(kmer, k);}
	private final long toValue(long kmer, long rkmer){return tables.toValue(kmer, rkmer);}
	public final int getCount(long kmer, long rkmer){return tables.getCount(kmer, rkmer);}
	private final boolean claim(long kmer, int id){return claim(kmer, rcomp(kmer), id);}
	private final boolean claim(long kmer, long rkmer, int id){return tables.claim(kmer, rkmer, id);}
	private final boolean doubleClaim(ByteBuilder bb, int id/*, long rid*/){return tables.doubleClaim(bb, id/*, rid*/);}
	private final boolean claim(ByteBuilder bb, int id, /*long rid, */boolean earlyExit){return tables.claim(bb, id/*, rid*/, earlyExit);}
	private final boolean claim(byte[] array, int len, int id, /*long rid, */boolean earlyExit){return tables.claim(array, len, id/*, rid*/, earlyExit);}
	private final int findOwner(long kmer){return tables.findOwner(kmer);}
	private final int findOwner(ByteBuilder bb, int id){return tables.findOwner(bb, id);}
	private final int findOwner(byte[] array, int len, int id){return tables.findOwner(array, len, id);}
	private final void release(long key, int id){tables.release(key, id);}
	private final void release(ByteBuilder bb, int id){tables.release(bb, id);}
	private final void release(byte[] array, int len, int id){tables.release(array, len, id);}
	private final int fillRightCounts(long kmer, long rkmer, int[] counts, long mask, int shift2){return tables.fillRightCounts(kmer, rkmer, counts, mask, shift2);}
	private final int fillLeftCounts(long kmer, long rkmer, int[] counts, long mask, int shift2){return tables.fillLeftCounts(kmer, rkmer, counts, mask, shift2);}
	private final StringBuilder toText(long kmer){return AbstractKmerTable.toText(kmer, k);}
	
	/*--------------------------------------------------------------*/
	/*----------------         Inner Classes        ----------------*/
	/*--------------------------------------------------------------*/
	
	/*--------------------------------------------------------------*/
	/*----------------          BuildThread         ----------------*/
	/*--------------------------------------------------------------*/
	
	@Override
	BuildThread makeBuildThread(int id, int mode, ConcurrentReadInputStream[] crisa){
		return new BuildThread(id, mode, crisa);
	}
	
	/**
	 * Builds contigs. 
	 */
	private class BuildThread extends AbstractBuildThread{
		
		public BuildThread(int id_, int mode_, ConcurrentReadInputStream[] crisa_){
			super(id_, mode_, crisa_);
		}
		
		@Override
		public void run(){
			if(crisa==null || crisa.length==0){
				//Build from kmers
				
				if(id==0){System.err.print("Seeding with min count = ");}
				String comma="";
				for(int i=contigPasses-1; i>0; i--){
					minCountSeedCurrent=(int)Tools.min(Integer.MAX_VALUE, Tools.max(minCountSeed+i, (long)Math.floor((minCountSeed)*Math.pow(contigPassMult, i)*0.92-0.25) ));
					if(id==0){
						System.err.print(comma+minCountSeedCurrent);
						comma=", ";
					}
					while(processNextTable(nextTable[i])){}
					while(processNextVictims(nextVictims[i])){}
				}
				//Final pass
				minCountSeedCurrent=minCountSeed;
				if(id==0){System.err.println(comma+minCountSeedCurrent);}
				while(processNextTable(nextTable[0])){}
				while(processNextVictims(nextVictims[0])){}
			}else{
				//Extend reads
				for(ConcurrentReadInputStream cris : crisa){
					synchronized(crisa){
						if(!cris.started()){
							cris.start();
						}
					}
					run(cris);
				}
			}
		}
		
		private boolean processNextTable(AtomicInteger aint){
			final int tnum=aint.getAndAdd(1);
			if(tnum>=tables.ways){return false;}
			final HashArray1D table=tables.getTable(tnum);
			if(verbose && id==0){System.err.println("Processing table "+tnum+", size "+table.size());}
			final int max=table.arrayLength();
			for(int cell=0; cell<max; cell++){
				int x=processCell(table, cell);
			}
			return true;
		}
		
		private boolean processNextVictims(AtomicInteger aint){
			final int tnum=aint.getAndAdd(1);
			if(tnum>=tables.ways){return false;}
			final HashArray1D table=tables.getTable(tnum);
			final HashForest forest=table.victims();
			if(verbose && id==0){System.err.println("Processing forest "+tnum+", size "+forest.size());}
			final int max=forest.arrayLength();
			for(int cell=0; cell<max; cell++){
				KmerNode kn=forest.getNode(cell);
				int x=traverseKmerNode(kn);
			}
			return true;
		}
		
		private int processCell(HashArray1D table, int cell){
			int count=table.readCellValue(cell);
			if(count<minCountSeedCurrent){return 0;}
			
			long key=table.getKmer(cell);

			if(verbose){outstream.println("id="+id+" processing cell "+cell+"; \tkmer="+key+"\t"+toText(key));}
			if(useOwnership){
				int owner=table.getCellOwner(cell);
				if(verbose){outstream.println("Owner is initially "+owner);}
				if(owner>-1){return 0;}
				owner=table.setOwner(key, id, cell);
				if(verbose){outstream.println("Owner is now "+owner);}
				if(owner!=id){return 0;}
			}
			return processKmer(key);
		}
		
		private int traverseKmerNode(KmerNode kn){
			int sum=0;
			if(kn!=null){
				sum+=processKmerNode(kn);
				if(kn.left()!=null){
					sum+=traverseKmerNode(kn.left());
				}
				if(kn.right()!=null){
					sum+=traverseKmerNode(kn.right());
				}
			}
			return sum;
		}
		
		private int processKmerNode(KmerNode kn){
			final long key=kn.pivot();
			final int count=kn.getValue(key);
			if(count<minCountSeedCurrent){return 0;}

			if(verbose){outstream.println("id="+id+" processing KmerNode; \tkmer="+key+"\t"+toText(key));}
			if(useOwnership){
				int owner=kn.getOwner(key);
				if(verbose){outstream.println("Owner is initially "+owner);}
				if(owner>-1){return 0;}
				owner=kn.setOwner(key, id);
				if(verbose){outstream.println("Owner is now "+owner);}
				if(owner!=id){return 0;}
			}
			return processKmer(key);
		}
		
		private int processKmer(long key){
			byte[] contig=makeContig(key, builderT, true);
			if(contig!=null){
				float coverage=tables.calcCoverage(contig, contig.length);
				if(coverage<minCoverage){return 0;}
				if(verbose){System.err.println("Added "+contig.length);}
				final long num=contigNum.incrementAndGet();
				Read r=new Read(contig, -1, -1, -1, "*", null, num, 0);
				float gc=r.gc();
				r.id="contig_"+num+",length="+contig.length+",cov="+String.format("%.1f", coverage)+",gc="+String.format("%.3f", gc);
				contigs.add(r);
				return contig.length;
			}else{
				if(verbose){System.err.println("Created null contig.");}
			}
			return 0;
		}
		
		private void run(ConcurrentReadInputStream cris){
			
			ListNum<Read> ln=cris.nextList();
			ArrayList<Read> reads=(ln!=null ? ln.list : null);
			
			//While there are more reads lists...
			while(reads!=null && reads.size()>0){
				
				//For each read (or pair) in the list...
				for(int i=0; i<reads.size(); i++){
					final Read r1=reads.get(i);
					final Read r2=r1.mate;
					
					processReadPair(r1, r2);
				}
				
				//Fetch a new read list
				cris.returnList(ln.id, ln.list.isEmpty());
				ln=cris.nextList();
				reads=(ln!=null ? ln.list : null);
			}
			cris.returnList(ln.id, ln.list.isEmpty());
		}
		
		private void processReadPair(Read r1, Read r2){
			if(verbose){System.err.println("Considering read "+r1.id+" "+new String(r1.bases));}
			
			readsInT++;
			basesInT+=r1.length();
			if(r2!=null){
				readsInT++;
				basesInT+=r2.length();
			}
			
			if(mode==insertMode){
				int x=BBMerge.findOverlapStrict(r1, r2, false);
				if(x<1){
					x=findInsertSize(r1, r2, rightCounts);
				}
				insertSizes.increment(Tools.max(x, 0));
				return;
			}
			
			if(ecco && r1!=null && r2!=null && !r1.discarded() && !r2.discarded()){BBMerge.findOverlapStrict(r1, r2, true);}

			if(r1!=null){
				if(r1.discarded()){
					lowqBasesT+=r1.length();
					lowqReadsT++;
				}else{
					byte[] contig=makeContig(r1.bases, builderT, r1.numericID);
					if(contig!=null){
						if(verbose){System.err.println("Added "+contig.length);}
						final long num=contigNum.incrementAndGet();
						Read temp=new Read(contig, -1, -1, -1, "contig_"+num+"_length_"+contig.length, null, num, 0);
						contigs.add(temp);
					}
				}
			}
			if(r2!=null){
				if(r2.discarded()){
					lowqBasesT+=r2.length();
					lowqReadsT++;
				}else{
					byte[] contig=makeContig(r2.bases, builderT, r1.numericID);
					if(contig!=null){
						if(verbose){System.err.println("Added "+contig.length);}
						final long num=contigNum.incrementAndGet();
						Read temp=new Read(contig, -1, -1, -1, "contig_"+num+"_length_"+contig.length, null, num, 0);
						contigs.add(temp);
					}
				}
			}
		}
		
		/** From kmers */
		private byte[] makeContig(final long key, final ByteBuilder bb, boolean alreadyClaimed){
			builderT.setLength(0);
			builderT.appendKmer(key, k);
			if(verbose){outstream.println("Filled builder: "+builderT);}
			
			final int initialLength=bb.length();
			assert(initialLength==k);
			if(initialLength<k){return null;}
//			System.err.print("A");
			
			boolean success=(alreadyClaimed || !useOwnership ? true : claim(key, id));
			if(verbose){System.err.println("Thread "+id+" checking owner after setting: "+findOwner(bb, id));}
			if(!success){
				assert(bb.length()==k);
//				release(bb, id); //no need to release
				return null;
			}
//			System.err.print("B");
			if(verbose  /*|| true*/){System.err.println("Thread "+id+" building contig; initial length "+bb.length());}
			if(verbose){System.err.println("Extending to right.");}
			{
				final int status=extendToRight(bb, leftCounts, rightCounts, id);
				
				if(status==DEAD_END){
					//do nothing
				}else if(status==LOOP){//TODO
					//special case - handle specially, for a loop with no obvious junction, e.g. long tandem repeat.
					//Perhaps, the last kmer should be reclassified as a junction and removed.
				}else if(status==BAD_SEED){
					assert(bb.length()==k);
					release(key, id);
//					System.err.print("B1");
					return null;
				}else{
					if(bb.length()==k){
						if(status==BAD_OWNER){
							if(IGNORE_BAD_OWNER){
								//do nothing
							}else{
								release(key, id);
//								System.err.print("B2");
								return null;
							}
						}else if(status==BRANCH){
							release(key, id);
//							System.err.print("B3");
							return null;
						}else{
							throw new RuntimeException("Bad return value: "+status);
						}
					}else{
						if(status==BAD_OWNER){
							if(IGNORE_BAD_OWNER){
								bb.length--;
							}else{
								release(bb, id);
//								System.err.print("B4");
								return null;
							}
						}else if(status==BRANCH){
							//do nothing
						}else{
							throw new RuntimeException("Bad return value: "+status);
						}
					}
				}
			}
//			System.err.print("C");
			bb.reverseComplementInPlace();
			if(verbose  /*|| true*/){System.err.println("Extending rcomp to right; current length "+bb.length());}
			{
				final int status=extendToRight(bb, leftCounts, rightCounts, id);
				
				if(status==DEAD_END){
					//do nothing
				}else if(status==LOOP){//TODO
					//special case - handle specially, for a loop with no obvious junction, e.g. long tandem repeat.
					//Perhaps, the last kmer should be reclassified as a junction and removed.
				}else if(status==BAD_SEED){
					assert(false) : bb;//This should never happen.
					assert(bb.length()==k);
					release(key, id);
					return null;
				}else{
					if(status==BAD_OWNER){
						if(IGNORE_BAD_OWNER){
							if(bb.length()>k){bb.length--;}
						}else{
							release(bb, id);
//							System.err.print("C1");
							return null;
						}
					}else if(status==BRANCH){
						//do nothing
					}else{
						throw new RuntimeException("Bad return value: "+status);
					}
				}
			}
//			System.err.print("D");

			if(verbose  /*|| true*/){System.err.println("A: Final length for thread "+id+": "+bb.length());}
			
			//				if(useOwnership && THREADS==1){assert(claim(bases, bases.length, id, rid));}
			success=(useOwnership ? doubleClaim(bb, id) : true);
			if(verbose  /*|| true*/){System.err.println("Success for thread "+id+": "+success);}
			
			if(trimEnds>0){bb.trimByAmount(trimEnds, trimEnds);}
			if(bb.length()>=initialLength+minExtension && bb.length()>=minContigLen){
				if(success){
					bb.reverseComplementInPlace();
					return bb.toBytes();
				}else{
//					System.err.print("E");
					//					assert(false) : bb.length()+", "+id;
					release(bb, id);
					return null;
				}
			}
			if(verbose  /*|| true*/){System.err.println("A: Contig was too short for "+id+": "+bb.length());}
//			assert(false) : bb.length()+", "+initialLength+", "+minExtension+", "+minContigLen;
//			System.err.print("F");
			return null;
		}
		
		/** From a seed */
		private byte[] makeContig(final byte[] bases, final ByteBuilder bb, long rid){
			if(bases==null || bases.length<k){return null;}
//			if(verbose  /*|| true*/){System.err.println("Thread "+id+" checking owner: "+findOwner(bases, bases.length, id));}
			int owner=useOwnership ? findOwner(bases, bases.length, id) : -1;
			if(owner>=id){return null;}
			boolean success=(useOwnership ? claim(bases, bases.length, id, true) : true);
			if(verbose  /*|| true*/){System.err.println("Thread "+id+" checking owner after setting: "+findOwner(bases, bases.length, id));}
			if(!success){
				release(bases, bases.length, id);
				return null;
			}
			if(verbose  /*|| true*/){System.err.println("Thread "+id+" building contig; initial length "+bases.length);}
			bb.setLength(0);
			bb.append(bases);
			if(verbose){System.err.println("Extending to right.");}
			{
				final int status=extendToRight(bb, leftCounts, rightCounts, id);
				
				if(status==DEAD_END){
					//do nothing
				}else if(status==LOOP){//TODO
					//special case - handle specially, for a loop with no obvious junction, e.g. long tandem repeat.
					//Perhaps, the last kmer should be reclassified as a junction and removed.
				}else if(status==BAD_SEED){
					//do nothing
				}else{
					if(status==BAD_OWNER){
						release(bb, id);
						return null;
					}else if(status==BRANCH){
						//do nothing
					}else{
						throw new RuntimeException("Bad return value: "+status);
					}
				}
			}
			bb.reverseComplementInPlace();
			if(verbose  /*|| true*/){System.err.println("Extending rcomp to right; current length "+bb.length());}
			{
				final int status=extendToRight(bb, leftCounts, rightCounts, id);
				
				if(status==DEAD_END){
					//do nothing
				}else if(status==LOOP){//TODO
					//special case - handle specially, for a loop with no obvious junction, e.g. long tandem repeat.
					//Perhaps, the last kmer should be reclassified as a junction and removed.
				}else if(status==BAD_SEED){
					//do nothing
				}else{
					if(status==BAD_OWNER){
						release(bb, id);
						return null;
					}else if(status==BRANCH){
						//do nothing
					}else{
						throw new RuntimeException("Bad return value: "+status);
					}
				}
			}
			if(verbose  /*|| true*/){System.err.println("B: Final length for thread "+id+": "+bb.length());}
			
			//				if(useOwnership && THREADS==1){assert(claim(bases, bases.length, id, rid));}
			success=(useOwnership ? doubleClaim(bb, id) : true);
			if(verbose  /*|| true*/){System.err.println("Success for thread "+id+": "+success);}
			if(bb.length()>=bases.length+minExtension && bb.length()>=minContigLen){
				if(success){
					bb.reverseComplementInPlace();
					return bb.toBytes();
				}else{
					//					assert(false) : bb.length()+", "+id;
					release(bb.array, bb.length(), id);
					return null;
				}
			}
			
			if(verbose  /*|| true*/){System.err.println("B: Contig was too short for "+id+": "+bb.length());}
			return null;
		}
		
	}
	
	
	/*--------------------------------------------------------------*/
	/*----------------       Extension Methods      ----------------*/
	/*--------------------------------------------------------------*/


	public int findInsertSize(Read r1, Read r2, int[] rightCounts){
		final long kmer1=tables.rightmostKmer(r1.bases, r1.length());
		final long kmer2=tables.rightmostKmer(r2.bases, r2.length());
		if(kmer1<0 || kmer2<0){return -1;}
		final long rkmer1=rcomp(kmer1);
		final long rkmer2=rcomp(kmer2);
		final int x=measureInsert(kmer1, rkmer1, kmer2, rkmer2, 24000, rightCounts);
		if(x<0){return -1;}
		return r1.length()+r2.length()+x-k;//TODO: May be off by 1.
	}
	
	@Override
	public int extendRead(Read r, ByteBuilder bb, int[] leftCounts, int[] rightCounts, int distance, final Kmer kmer){
		return extendRead(r, bb, leftCounts, rightCounts, distance);
	}

	@Override
	public int extendRead(Read r, ByteBuilder bb, int[] leftCounts, int[] rightCounts, int distance){
		final int initialLen=r.length();
		if(initialLen<k){return 0;}
		bb.setLength(0);
		bb.append(r.bases);
		final int extension=extendToRight2(bb, leftCounts, rightCounts, distance, true);
		if(extension>0){
			r.bases=bb.toBytes();
			if(r.quality!=null){
				final byte q=Shared.FAKE_QUAL;
				r.quality=Arrays.copyOf(r.quality, r.bases.length);
				for(int i=initialLen; i<r.quality.length; i++){
					r.quality[i]=q;
				}
			}
		}
		assert(extension==r.length()-initialLen);
		return extension;
	}
	
	/** Returns distance between the two kmers, or -1 */
	public int measureInsert(final long kmer1, final long rkmer1, final long kmer2, final long rkmer2, final int maxlen, final int[] rightCounts){
		final int shift=2*k;
		final int shift2=shift-2;
		final long mask=~((-1L)<<shift);
		final long key2=toValue(kmer2, rkmer2);
		long kmer=kmer1;
		long rkmer=rkmer1;
		int len=0;
		
		{
			int count=tables.getCount(key2);
			if(count<minCountSeed){return -1;}
		}
		
		long key=toValue(kmer, rkmer);
		int count=tables.getCount(key);
		if(count<minCountSeed){return -1;}
		if(count<minCountSeed){
			if(verbose){outstream.println("Returning because count was too low: "+count);}
			return -1;
		}
		
		int rightMaxPos=fillRightCounts(kmer, rkmer, rightCounts, mask, shift2);
		int rightMax=rightCounts[rightMaxPos];
//		int rightSecondPos=Tools.secondHighestPosition(rightCounts);
//		int rightSecond=rightCounts[rightSecondPos];
		
		if(rightMax<minCountExtend){return -1;}
//		if(isJunction(rightMax, rightSecond)){return -1;}
		
		while(key!=key2 && len<maxlen){
			
			//Generate the new kmer
//			final byte b=AminoAcid.numberToBase[rightMaxPos];
			final long x=rightMaxPos;
			final long x2=AminoAcid.numberToComplement[(int)x];
			
			//Now consider the next kmer
			kmer=((kmer<<2)|(long)x)&mask;
			rkmer=(rkmer>>>2)|(x2<<shift2);
			
			assert(tables.getCount(kmer, rkmer)==rightMax);
			count=rightMax;
			
			assert(count>=minCountExtend) : count;
			
			rightMaxPos=fillRightCounts(kmer, rkmer, rightCounts, mask, shift2);
			rightMax=rightCounts[rightMaxPos];
//			rightSecondPos=Tools.secondHighestPosition(rightCounts);
//			rightSecond=rightCounts[rightSecondPos];
			
			if(verbose){
				outstream.println("kmer: "+toText(kmer)+", "+toText(rkmer));
				outstream.println("Counts: "+count+", "+Arrays.toString(rightCounts));
				outstream.println("rightMaxPos="+rightMaxPos);
				outstream.println("rightMax="+rightMax);
//				outstream.println("rightSecondPos="+rightSecondPos);
//				outstream.println("rightSecond="+rightSecond);
			}
			
			if(rightMax<minCountExtend){
				if(verbose){outstream.println("A: Breaking because highest right was too low:"+rightMax);}
				break;
			}

//			if(isJunction(rightMax, rightSecond)){return -1;}
			
			len++;
		}
		return len>=maxlen ? -1 : len;
	}
	

	
	/**
	 * Extend these bases into a contig.
	 * Stops at both left and right junctions.
	 * Claims ownership.
	 */
	public int extendToRight(final ByteBuilder bb, final int[] leftCounts, final int[] rightCounts, final int id){
		if(bb.length()<k){return BAD_SEED;}
		final int shift=2*k;
		final int shift2=shift-2;
		final long mask=~((-1L)<<shift);
		long kmer=0;
		long rkmer=0;
		int len=0;
		
		/* Loop through the bases, maintaining a forward and reverse kmer via bitshifts, to get the rightmost kmer */
		{
			final int bblen=bb.length();
			final byte[] bases=bb.array;
			for(int i=bblen-k; i<bblen; i++){
				final byte b=bases[i];
				final long x=AminoAcid.baseToNumber[b];
				final long x2=AminoAcid.baseToComplementNumber[b];
				kmer=((kmer<<2)|x)&mask;
				rkmer=(rkmer>>>2)|(x2<<shift2);
				if(x<0){
					len=0;
					kmer=rkmer=0;
				}else{len++;}
				if(verbose){outstream.println("A: Scanning i="+i+", len="+len+", kmer="+kmer+", rkmer="+rkmer+"\t"+new String(bases, Tools.max(0, i-k2), Tools.min(i+1, k)));}
			}
		}
		
		if(len<k){return BAD_SEED;}
		else{assert(len==k);}
		
		/* Now the trailing kmer has been initialized. */
		
		long key=toValue(kmer, rkmer);
		HashArray1D table=tables.getTableForKey(key);
		int count=table.getValue(key);
		if(count<minCountSeed){
			if(verbose){outstream.println("Returning because count was too low: "+count);}
			return BAD_SEED;
		}
		
		int owner=(useOwnership ? table.getOwner(key) : id);
		if(verbose){outstream.println("Owner: "+owner);}
		if(owner>id){return BAD_OWNER;}
		
		int leftMaxPos=0;
		int leftMax=minCountExtend;
		int leftSecondPos=1;
		int leftSecond=0;
		
		if(leftCounts!=null){
			leftMaxPos=fillLeftCounts(kmer, rkmer, leftCounts, mask, shift2);
			leftMax=leftCounts[leftMaxPos];
			leftSecondPos=Tools.secondHighestPosition(leftCounts);
			leftSecond=leftCounts[leftSecondPos];
		}
		
		int rightMaxPos=fillRightCounts(kmer, rkmer, rightCounts, mask, shift2);
		int rightMax=rightCounts[rightMaxPos];
		int rightSecondPos=Tools.secondHighestPosition(rightCounts);
		int rightSecond=rightCounts[rightSecondPos];
		
		if(verbose){
			outstream.println("kmer: "+toText(kmer)+", "+toText(rkmer));
			outstream.println("Counts: "+count+", "+(leftCounts==null ? "null" : Arrays.toString(leftCounts))+", "+Arrays.toString(rightCounts));
			outstream.println("leftMaxPos="+leftMaxPos);
			outstream.println("leftMax="+leftMax);
			outstream.println("leftSecondPos="+leftSecondPos);
			outstream.println("leftSecond="+leftSecond);
			outstream.println("rightMaxPos="+rightMaxPos);
			outstream.println("rightMax="+rightMax);
			outstream.println("rightSecondPos="+rightSecondPos);
			outstream.println("rightSecond="+rightSecond);
		}
		
		if(rightMax<minCountExtend){return DEAD_END;}
		if(isJunction(rightMax, rightSecond, leftMax, leftSecond)){
			return BRANCH;
		}
		
		if(useOwnership){
			owner=table.setOwner(key, id);
			if(verbose){outstream.println("A. Owner is now "+id+" for key "+key);}
			if(owner!=id){
				if(verbose){outstream.println("Returning early because owner was "+owner+" for thread "+id+".");}
				return BAD_OWNER;
			}
		}
		
		final int maxLen=Tools.min((extendRight<0 ? maxContigLen : bb.length()+extendRight), maxContigLen);
		
		while(owner==id && bb.length()<maxLen){
			
			//Generate the new kmer
			final byte b=AminoAcid.numberToBase[rightMaxPos];
			final long x=rightMaxPos;
			final long x2=AminoAcid.numberToComplement[(int)x];
			
			final long evicted=(kmer>>>shift2); //Binary value that falls off the end.
			
			//Now consider the next kmer
			kmer=((kmer<<2)|(long)x)&mask;
			rkmer=(rkmer>>>2)|(x2<<shift2);
			
			key=toValue(kmer, rkmer);
			table=tables.getTableForKey(key);
			
			assert(table.getValue(key)==rightMax);
			count=rightMax;
			
			assert(count>=minCountExtend) : count;

			if(leftCounts!=null){
				leftMaxPos=fillLeftCounts(kmer, rkmer, leftCounts, mask, shift2);
				leftMax=leftCounts[leftMaxPos];
				leftSecondPos=Tools.secondHighestPosition(leftCounts);
				leftSecond=leftCounts[leftSecondPos];
			}
			
			rightMaxPos=fillRightCounts(kmer, rkmer, rightCounts, mask, shift2);
			rightMax=rightCounts[rightMaxPos];
			rightSecondPos=Tools.secondHighestPosition(rightCounts);
			rightSecond=rightCounts[rightSecondPos];
			
			if(verbose){
				outstream.println("kmer: "+toText(kmer)+", "+toText(rkmer));
				outstream.println("Counts: "+count+", "+(leftCounts==null ? "null" : Arrays.toString(leftCounts))+", "+Arrays.toString(rightCounts));
				outstream.println("leftMaxPos="+leftMaxPos);
				outstream.println("leftMax="+leftMax);
				outstream.println("leftSecondPos="+leftSecondPos);
				outstream.println("leftSecond="+leftSecond);
				outstream.println("rightMaxPos="+rightMaxPos);
				outstream.println("rightMax="+rightMax);
				outstream.println("rightSecondPos="+rightSecondPos);
				outstream.println("rightSecond="+rightSecond);
			}
			
			if(isJunction(rightMax, rightSecond, leftMax, leftSecond)){
				if(verbose){outstream.println("B: Breaking because isJunction("+rightMax+", "+rightSecond+", "+leftMax+", "+leftSecond+")");}
				return BRANCH;
			}
			
			if(leftCounts!=null && leftMaxPos!=evicted && branchMult1>0){
				if(verbose){outstream.println("B: Breaking because of hidden branch: leftMaxPos!=evicted ("+leftMaxPos+"!="+evicted+")" +
						"\nleftMaxPos="+leftMaxPos+", leftMax="+leftMax+", leftSecondPos="+leftSecondPos+", leftSecond="+leftSecond);}
				return BRANCH;
			}
			
			bb.append(b);
			if(verbose){outstream.println("Added base "+(char)b);}
			
			if(useOwnership){
				owner=table.getOwner(key);
				if(verbose){outstream.println("Owner is initially "+id+" for key "+key);}
				if(owner==id){//loop detection
					if(verbose  /*|| true*/){
//						outstream.println(new String(bb.array, bb.length()-31, 31));
						outstream.println(bb);
						outstream.println(toText(kmer));
						outstream.println(toText(rkmer));
						outstream.println("Breaking because owner was "+owner+" for thread "+id+".");
					}
					return LOOP;
				}
				owner=table.setOwner(key, id);
				if(verbose){outstream.println("B. Owner is now "+id+" for key "+key);}
			}
			
			if(rightMax<minCountExtend){
				if(verbose){outstream.println("B: Breaking because highest right was too low:"+rightMax);}
				return DEAD_END;
			}
		}
		assert(owner!=id);
		if(verbose  /*|| true*/){
			outstream.println("Current contig: "+bb+"\nReturning because owner was "+owner+" for thread "+id+".");
		}
		return BAD_OWNER;
	}
	
	@Override
	public int extendToRight2(final ByteBuilder bb, final int[] leftCounts, final int[] rightCounts, final int distance, boolean includeJunctionBase){
		if(verbose || verbose2){outstream.println("Entering extendToRight2 (no kmers).");}
		final int initialLength=bb.length();
		if(initialLength<k){return 0;}
		final int shift=2*k;
		final int shift2=shift-2;
		final long mask=~((-1L)<<shift);
		long kmer=0;
		long rkmer=0;
		
		/* Loop through the bases, maintaining a forward and reverse kmer via bitshifts, to get the rightmost kmer */
		{
			int len=0;
			final byte[] bases=bb.array;
			for(int i=initialLength-k; i<initialLength; i++){
				final byte b=bases[i];
				final long x=AminoAcid.baseToNumber[b];
				final long x2=AminoAcid.baseToComplementNumber[b];
				kmer=((kmer<<2)|x)&mask;
				rkmer=(rkmer>>>2)|(x2<<shift2);
				if(x<0){
					len=0;
					kmer=rkmer=0;
				}else{len++;}
				if(verbose){outstream.println("B: Scanning i="+i+", len="+len+", kmer="+kmer+", rkmer="+rkmer+"\t"+new String(bases, Tools.max(0, i-k2), Tools.min(i+1, k)));}
			}
			if(len<k){
				if(verbose || verbose2){outstream.println("Returning because len<k: "+len+"<"+k);}
				return 0;
			}
			else{assert(len==k);}
		}
		return extendToRight2(bb, leftCounts, rightCounts, distance, includeJunctionBase, kmer, rkmer);
	}
	
	/**
	 * Extend these bases to the right by at most 'distance'.
	 * Stops at right junctions only.
	 * Does not claim ownership.
	 */
	public int extendToRight2(final ByteBuilder bb, final int[] leftCounts, final int[] rightCounts, final int distance, boolean includeJunctionBase,
			long kmer, long rkmer){
		if(verbose || verbose2){outstream.println("Entering extendToRight2 (with kmers).");}
		final int initialLength=bb.length();
		final int shift=2*k;
		final int shift2=shift-2;
		final long mask=~((-1L)<<shift);
		
		/* Now the trailing kmer has been initialized. */
		
		long key=toValue(kmer, rkmer);
		HashArray1D table=tables.getTableForKey(key);
		int count=table.getValue(key);
		if(count<minCountSeed){
			if(verbose || verbose2){outstream.println("Returning because count was too low: "+count+"<"+minCountSeed);}
			return 0;
		}
		
		int leftMaxPos=0;
		int leftMax=minCountExtend;
		int leftSecondPos=1;
		int leftSecond=0;
		
		if(leftCounts!=null){
			leftMaxPos=fillLeftCounts(kmer, rkmer, leftCounts, mask, shift2);
			leftMax=leftCounts[leftMaxPos];
			leftSecondPos=Tools.secondHighestPosition(leftCounts);
			leftSecond=leftCounts[leftSecondPos];
		}
		
		int rightMaxPos=fillRightCounts(kmer, rkmer, rightCounts, mask, shift2);
		int rightMax=rightCounts[rightMaxPos];
		int rightSecondPos=Tools.secondHighestPosition(rightCounts);
		int rightSecond=rightCounts[rightSecondPos];
		
		if(verbose){
			outstream.println("kmer: "+toText(kmer)+", "+toText(rkmer));
			outstream.println("Counts: "+count+", "+Arrays.toString(rightCounts));
			outstream.println("rightMaxPos="+rightMaxPos);
			outstream.println("rightMax="+rightMax);
			outstream.println("rightSecondPos="+rightSecondPos);
			outstream.println("rightSecond="+rightSecond);
		}
		
		if(rightMax<minCountExtend){
			if(verbose || verbose2){outstream.println("Returning because rightMax was too low: "+rightMax+"<"+minCountExtend+"\n"+count+", "+Arrays.toString(rightCounts));}
			return 0;
		}
		if(isJunction(rightMax, rightSecond, leftMax, leftSecond)){
			if(verbose || verbose2){outstream.println("Returning because isJunction: "+rightMax+", "+rightSecond+"; "+leftMax+", "+leftSecond);}
			return 0;
		}
		
		final int maxLen=Tools.min(bb.length()+distance, maxContigLen);
		
		while(bb.length()<maxLen){
			
			//Generate the new kmer
			final byte b=AminoAcid.numberToBase[rightMaxPos];
			final long x=rightMaxPos;
			final long x2=AminoAcid.numberToComplement[(int)x];
			
			final long evicted=(kmer>>>shift2); //Binary value that falls off the end.
			
			//Now consider the next kmer
			kmer=((kmer<<2)|(long)x)&mask;
			rkmer=(rkmer>>>2)|(x2<<shift2);
			
			key=toValue(kmer, rkmer);
			table=tables.getTableForKey(key);
			
			assert(table.getValue(key)==rightMax);
			count=rightMax;
			
			assert(count>=minCountExtend) : count;
			
			if(leftCounts!=null){
				leftMaxPos=fillLeftCounts(kmer, rkmer, leftCounts, mask, shift2);
				leftMax=leftCounts[leftMaxPos];
				leftSecondPos=Tools.secondHighestPosition(leftCounts);
				leftSecond=leftCounts[leftSecondPos];
			}
			
			rightMaxPos=fillRightCounts(kmer, rkmer, rightCounts, mask, shift2);
			rightMax=rightCounts[rightMaxPos];
			rightSecondPos=Tools.secondHighestPosition(rightCounts);
			rightSecond=rightCounts[rightSecondPos];
			
			if(verbose){
				outstream.println("kmer: "+toText(kmer)+", "+toText(rkmer));
				outstream.println("Counts: "+count+", "+Arrays.toString(rightCounts));
				outstream.println("rightMaxPos="+rightMaxPos);
				outstream.println("rightMax="+rightMax);
				outstream.println("rightSecondPos="+rightSecondPos);
				outstream.println("rightSecond="+rightSecond);
			}

			if(isJunction(rightMax, rightSecond, leftMax, leftSecond)){
				if(verbose){outstream.println("B: Breaking because isJunction("+rightMax+", "+rightSecond+", "+leftMax+", "+leftSecond+")");}
				if(includeJunctionBase && kmer>rkmer){
					bb.append(b);
					if(verbose){outstream.println("Added base "+(char)b);}
				}
				break;
			}
			
			if(leftCounts!=null && leftMaxPos!=evicted){
				if(verbose){outstream.println("B: Breaking because of hidden branch: leftMaxPos!=evicted ("+leftMaxPos+"!="+evicted+")" +
						"\nleftMaxPos="+leftMaxPos+", leftMax="+leftMax+", leftSecondPos="+leftSecondPos+", leftSecond="+leftSecond);}
				if(includeJunctionBase && kmer>rkmer){
					bb.append(b);
					if(verbose){outstream.println("Added base "+(char)b);}
				}
				break;
			}
			
			bb.append(b);
			if(verbose){outstream.println("Added base "+(char)b);}
			
			if(rightMax<minCountExtend){
				if(verbose || verbose2){outstream.println("C: Breaking because highest right was too low: "+rightMax+"<"+minCountExtend);}
				break;
			}
		}
		if(verbose || verbose2){System.err.println("Extended by "+(bb.length()-initialLength));}
		return bb.length()-initialLength;
	}
	
	
	/*--------------------------------------------------------------*/
	/*----------------       Error Correction       ----------------*/
	/*--------------------------------------------------------------*/
	
	@Override
	public int errorCorrect(Read r){
		initializeThreadLocals();
		int corrected=errorCorrect(r, localLeftCounts.get(), localRightCounts.get(), localLongList.get(), localIntList.get(), localByteBuilder.get(), null, localBitSet.get());
		return corrected;
	}
	
	@Override
	public int errorCorrect(Read r, final int[] leftCounts, final int[] rightCounts, LongList kmers, IntList counts, 
			final ByteBuilder bb, final int[] detectedArray, final BitSet bs, Kmer kmer){
		return errorCorrect(r, leftCounts, rightCounts, kmers, counts, bb, detectedArray, bs);
	}
	
	public int errorCorrect(Read r, final int[] leftCounts, final int[] rightCounts, LongList kmers, IntList counts, 
			final ByteBuilder bb, final int[] detectedArray, final BitSet bs){
		final byte[] bases=r.bases;
		final byte[] quals=r.quality;
		if(detectedArray!=null){
			detectedArray[0]=0;
			detectedArray[1]=0;
			detectedArray[2]=0;
			detectedArray[3]=0;
		}
		int valid=tables.fillKmers(bases, kmers);
		if(valid<2){return 0;}
		tables.fillCounts(kmers, counts);
		int correctedPincer=0;
		int correctedTail=0;
		
		if(ECC_PINCER){
			correctedPincer+=errorCorrectPincer(bases, quals, leftCounts, rightCounts, kmers, counts, bb, detectedArray, errorExtensionPincer);
		}
		
		if(ECC_TAIL || ECC_ALL){
			int start=(ECC_ALL ? 0 : counts.size-k-1);
//			if(ECC_PINCER && detectedArray!=null && detectedArray[0]>correctedPincer){start=start-k;}
			correctedTail+=errorCorrectTail(bases, quals, leftCounts, rightCounts, kmers, counts, bb, detectedArray, start, errorExtensionTail);
			r.reverseComplement();
			valid=tables.fillKmers(bases, kmers);
			counts.reverse();
			correctedTail+=errorCorrectTail(bases, quals, leftCounts, rightCounts, kmers, counts, bb, detectedArray, start, errorExtensionTail);
			r.reverseComplement();
			counts.reverse();
		}
		
		if(MARK_BAD_BASES>0){
			int marked=markBadBases(bases, quals, counts, bs, MARK_BAD_BASES, MARK_DELTA_ONLY);
			detectedArray[3]=marked;
		}
		assert(detectedArray==null || (correctedPincer==detectedArray[1] && correctedTail==detectedArray[2])) : correctedPincer+", "+correctedTail+", "+Arrays.toString(detectedArray);
//		if(ECC_PINCER && correctedTail>0){
//			valid=fillKmers(bases, kmers);
//			counts.reverse();
//			correctedPincer+=errorCorrectPincer(bases, quals, leftCounts, rightCounts, kmers, counts, bb, detectedArray, errorExtensionPincer);
//		}
		
		return correctedPincer+correctedTail;
	}

	public int errorCorrectPincer(final byte[] bases, final byte[] quals, final int[] leftBuffer, final int[] rightBuffer, 
			final LongList kmers, final IntList counts, final ByteBuilder bb, final int[] detectedArray, final int errorExtension){
		
		int detected=0;
		int corrected=0;
		
		//a is the index of the left kmer
		//b is a+1
		//c is d-1
		//d is the index of the right kmer
		//the base between the kmers is at a+k
		for(int a=0, d=k+1; d<counts.size; a++, d++){
			final int aCount=counts.get(a);
			final int bCount=counts.get(a+1);
			final int cCount=counts.get(d-1);
			final int dCount=counts.get(d);
			if(isError(aCount, bCount) && isError(dCount, cCount) && isSimilar(aCount, dCount)){
				if(verbose){
					System.err.println("Found error: "+aCount+", "+bCount+", "+cCount+", "+dCount);
				}
				//Looks like a 1bp substitution; attempt to correct.
				detected++;
				int ret=correctSingleBasePincer(a, d, bases, quals, leftBuffer, rightBuffer, kmers, counts, bb, errorExtension);
				corrected+=ret;
				if(verbose){
					System.err.println("Corrected error.");
				}
			}else{
				if(verbose){
					System.err.println("Not an error: "+aCount+", "+bCount+", "+cCount+", "+dCount+
							";  "+isError(aCount, bCount)+", "+isError(dCount, cCount)+", "+isSimilar(aCount, dCount));
				}
			}
		}
		
//		if(detected==0 && counts.get(0)>2 && counts.get(counts.size-1)>2){
//			assert(!verbose);
//			verbose=true;
//			System.err.println("\n"+counts);
//			errorCorrectPincer(bases, quals, leftBuffer, rightBuffer, kmers, counts, bb, detectedArray);
//			assert(false);
//		}
		
		if(detectedArray!=null){
			detectedArray[0]+=detected;
			detectedArray[1]+=corrected;
		}
		
		return corrected;
	}

	public int errorCorrectTail(final byte[] bases, final byte[] quals, final int[] leftBuffer, final int[] rightBuffer, 
			final LongList kmers, final IntList counts, final ByteBuilder bb, final int[] detectedArray, final int startPos, final int errorExtension){
		if(bases.length<k+2*(1+errorExtension)){return 0;}
		int detected=0;
		int corrected=0;
		
		//a is the index of the left kmer
		//b is a+1
		//the base between the kmers is at a+k
		for(int a=Tools.max(startPos, errorExtension), lim=counts.size-Tools.min(errorExtension, (errorExtension+3)/2); a<lim; a++){//errorExtension-1
			final int aCount=counts.get(a);
			final int bCount=counts.get(a+1);
			if(isError(aCount, bCount) && isSimilar(aCount, a-errorExtension, a-1, counts) && isError(aCount, a+2, a+k, counts)){
				if(verbose){
					System.err.println("Found error: "+aCount+", "+bCount);
				}
				//Assume like a 1bp substitution; attempt to correct.
				detected++;
				int ret=correctSingleBaseRight(a, bases, quals, leftBuffer, rightBuffer, kmers, counts, bb, errorExtension);
				corrected+=ret;
				if(verbose){
					System.err.println("Corrected error.");
				}
			}else{
				if(verbose){
					System.err.println("Not an error: "+aCount+", "+bCount+
							";  "+isError(aCount, bCount)+", "+isSimilar(aCount, a-errorExtension, a-1, counts)+", "+isError(aCount, a+2, a+k, counts));
				}
			}
		}
		
//		if(detected==0 && counts.get(0)>2 && counts.get(counts.size-1)>2){
//			assert(!verbose);
//			verbose=true;
//			System.err.println("\n"+counts);
//			errorCorrectPincer(bases, quals, leftBuffer, rightBuffer, kmers, counts, bb, detectedArray);
//			assert(false);
//		}
		
		if(detectedArray!=null){
			detectedArray[0]+=detected;
			detectedArray[2]+=corrected;
		}
		
		return corrected;
	}
	
	private int correctSingleBasePincer(final int a, final int d, final byte[] bases, final byte[] quals, final int[] leftBuffer, final int[] rightBuffer, 
			final LongList kmers, final IntList counts, final ByteBuilder bb, final int errorExtension){
		final byte leftReplacement, rightReplacement;
		final int loc=a+k;
		{
			bb.clear();
			final long kmer=kmers.get(a);
			final long rkmer=rcomp(kmer);
			int extension=extendToRight2(bb, null, rightBuffer, errorExtension, true, kmer, rkmer);
			if(extension<errorExtension){return 0;}
			for(int i=1; i<extension; i++){
				if(bb.get(i)!=bases[loc+i]){
					return 0;
				}
			}
			leftReplacement=bb.get(0);
		}
		{
			bb.clear();
			final long rkmer=kmers.get(d);
			final long kmer=rcomp(rkmer);
			int extension=extendToRight2(bb, null, rightBuffer, errorExtension, true, kmer, rkmer);
			if(extension<errorExtension){return 0;}
			bb.reverseComplementInPlace();
			for(int i=0; i<extension-1; i++){
				if(bb.get(i)!=bases[loc+i+1-extension]){
					return 0;
				}
			}
			rightReplacement=bb.get(extension-1);
		}
		if(leftReplacement!=rightReplacement){return 0;}
		if(bases[loc]==leftReplacement){return 0;}
		if(!isSimilar(a, leftReplacement, kmers, counts)){return 0;}
		
		bases[loc]=leftReplacement;
		assert(d==a+k+1);
		tables.regenerateKmers(bases, kmers, counts, a);
		return 1;
	}
	
	private int correctSingleBaseRight(final int a, final byte[] bases, final byte[] quals, final int[] leftBuffer, final int[] rightBuffer, 
			final LongList kmers, final IntList counts, final ByteBuilder bb, final int errorExtension0){
		final byte leftReplacement;
		final int loc=a+k;
		final int errorExtension=Tools.min(errorExtension0, bases.length-loc);
		{
			bb.clear();
			final long kmer=kmers.get(a);
			final long rkmer=rcomp(kmer);
			int extension=extendToRight2(bb, null, rightBuffer, errorExtension, true, kmer, rkmer);
			if(extension<errorExtension){return 0;}
			for(int i=1; i<extension; i++){
				if(bb.get(i)!=bases[loc+i]){
					return 0;
				}
			}
			leftReplacement=bb.get(0);
		}
		
		if(bases[loc]==leftReplacement){return 0;}
		if(!isSimilar(a, leftReplacement, kmers, counts)){return 0;}
		
		bases[loc]=leftReplacement;
		tables.regenerateKmers(bases, kmers, counts, a);
		return 1;
	}
	
	private boolean isSimilar(int a, byte newBase, LongList kmers, IntList counts){
		final int shift=2*k;
		final long mask=~((-1L)<<shift);
		long kmer=kmers.get(a);
				
		final long x=AminoAcid.baseToNumber[newBase];
		kmer=((kmer<<2)|x)&mask;
		long rkmer=rcomp(kmer);
		int count=getCount(kmer, rkmer);
		int aCount=counts.get(a);
		boolean similar=isSimilar(aCount, count);
		return similar;
	}
	
	/*--------------------------------------------------------------*/
	/*----------------  Inherited Abstract Methods  ----------------*/
	/*--------------------------------------------------------------*/
	
	final void makeKhist(){
		tables.makeKhist(outHist, histColumns, histMax, histHeader, histZeros, true, smoothHist, 1);
	}
	final void dumpKmersAsText(){
		tables.dumpKmersAsBytes_MT(outKmers, minToDump, true);
	}
	
	/*--------------------------------------------------------------*/
	/*----------------            Fields            ----------------*/
	/*--------------------------------------------------------------*/

	final KmerTableSet tables(){return tables;}
	public final KmerTableSet tables;
	
	/** Normal kmer length */
	private final int k;
	/** k-1; used in some expressions */
	private final int k2;
	
}
