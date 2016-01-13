#Pawel Osipowski 16.11.2016
#SCRIPT for read alignment and variant calling
#read files should be deduplicated and trimmed of adapter 
#sequences. trimmomatic can do this for instance.

#SOFTWARE:
#ALIGNMENT: bbmap(more sensitive) or bwa
#PROCESSING after: samblaster, samtools
#VARIANT_CALLASSEMBLY with SOAPdenovo2, version 2.04-r240


# SYNOPIS:
# sh variant_call.sh <genome_name.fasta> reads_name

# all paired read files must be in a form: <read_name>_fw.fq <read_name>_rv.fq

#When using bwa for alignment separate genome
#indexing is needed (hashed command in the script below)

mkdir ./"$2"

#INDEXING genome for alignment with bwa

./soft/bwa-0.7.7/bwa index "$1"
# 1. ALIGNMENT with bwa
~/biosoft/read_alignment/bwa-0.7.7/bwa mem -t 10 "$1" "$2"_fw.fq "$2"_rv.fq > ./"$2"/"$2".sam

# 1. ALIGNMENT with bbmap (more sensitive but .bam generates some errors along this pipeline)
#./soft/bbmap/bbmap.sh ref="$1" in="$2"_fw.fq in2="$2"_rv.fq out=./"$2"/"$2".sam

# 2. DUPLICATE REMOVAL with samblaster version 0.1.22
./soft/samblaster/samblaster -i ./"$2"/"$2".sam -o ./"$2"/"$2"_marked.sam 

# 3. CONVERTING TO BAM with samtools 1.2 using htslib 1.2.1
./soft/samtools-1.2/samtools view -Sb -o ./"$2"/"$2".bam ./"$2"/"$2"_marked.sam
./soft/samtools-1.2/samtools sort ./"$2"/"$2".bam ./"$2"/"$2"_sorted
./soft/samtools-1.2/samtools index ./"$2"/"$2"_sorted.bam

# 4. LCR (low complexity regions) specification on genome with mdust
./soft/mdust/mdust "$1" -w 6 -v 20 -c > ./"$2"/"$2"_lcr.bed

# 5. VARIANT CALLING with freebayes v0.9.21-19-gc003c1e
# allele balance filter - 50% to call
./soft/freebayes/bin/freebayes -p 1 --min-alternate-fraction 0.5 -F 0.4 -f "$1" ./"$2"/"$2"_sorted.bam > ./"$2"/"$2".vcf

# 6. VARIANT FILTERING vcflib
#variant quality filtering with vcffilter
./soft/vcflib/bin/vcffilter -f "QUAL > 30" ./"$2"/"$2".vcf > ./"$2"/"$2"_q30.vcf

# lcr read filtering with lcr_filter.py
python ./soft/lcr_filter.py ./"$2"/"$2"_lcr.bed ./"$2"/"$2"_q30.vcf ./"$2"/"$2"_lcrfree.vcf ./"$2"/"$2"_inlcr_var.txt

#variant read depth filtering(MD); if either the number of non-reference reads on the forward strand or on the reverse strand is
#below treshold (SAF & SAR)

#count depth score for max MD filtering (Li H, 2014)
#samtools (this is valid - takes only covered bases to count average)
avg_depth=$(./soft/samtools-1.3/samtools depth ./0/0_sorted.bam  |  awk '{sum+=$3} END { print sum/NR}'| bc -l)
depth_score=$(echo "$avg_depth + sqrt($avg_depth) * 3" | bc -l)
echo 'average loci base coverage is: ' $avg_depth ', and depth score is: ' $depth_score
#bedtools (this is not valid - 0 coverage bases are also taken to count average depth)
#keeping it for future
#./soft/bedtools2/bin/bedtools genomecov -it -ibam ./"$2"/"$2"_sorted.bam > ./"$2"/"$2"_coverage_hist.txt
#avg_depth=$(awk '{ total += $4; count++ } END { print total/count }' ./"$2"/"$2"_coverage.txt)
#depth_score=$(echo "$avg_depth + sqrt($avg_depth) * 3" | bc -l)
#echo 'average loci base coverage is: ' $avg_depth ', and depth score is: ' $depth_score

#max MD (treshold=depth score from Li H., 2014)
./soft/vcflib/bin/vcffilter -f "DP < $depth_score" ./"$2"/"$2"_lcrfree.vcf > ./"$2"/"$2"_DP.vcf
#min MD (treshold=10)
./soft/vcflib/bin/vcffilter -f "DP > 10" ./"$2"/"$2"_DP.vcf > ./"$2"/"$2"_DP2.vcf
#SAF&SAR (treshold=5)
./soft/vcflib/bin/vcffilter -f "SAF > 5" ./"$2"/"$2"_DP2.vcf > ./"$2"/"$2"_SAF.vcf
./soft/vcflib/bin/vcffilter -f "SAR > 5" ./"$2"/"$2"_SAF.vcf > ./"$2"/"$2"_filt.vcf

# 7. POST DATA preparation with bcftools
bgzip ./"$2"/"$2"_filt.vcf
tabix ./"$2"/"$2"_filt.vcf.gz
./soft/bcftools-1.2/bcftools stats ./"$2"/"$2"_filt.vcf.gz > ./"$2"/"$2".stats


