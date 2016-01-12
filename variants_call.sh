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
#./soft/bwa-0.7.7/bwa index "$1"
#ALIGNMENT with bwa
#~/biosoft/read_alignment/bwa-0.7.7/bwa mem -t 10 "$1" "$2"_fw.fq "$2"_rv.fq > ./"$2"/"$2".sam

#ALIGNMENT with bbmap
./soft/bbmap/bbmap.sh ref="$1" in="$2"_fw.fq in2="$2"_rv.fq out=./"$2"/"$2".sam

# samblaster version 0.1.22
./soft/samblaster/samblaster -i ./"$2"/"$2".sam -o ./"$2"/"$2"_marked.sam 

# samtools 1.2 using htslib 1.2.1
./soft/samtools-1.2/samtools view -Sb -o ./"$2"/"$2".bam ./"$2"/"$2"_marked.sam
./soft/samtools-1.2/samtools sort ./"$2"/"$2".bam ./"$2"/"$2"_sorted
./soft/samtools-1.2/samtools index ./"$2"/"$2"_sorted.bam

# low complexity regions specification on genome with mdust
./soft/mdust/mdust "$1" -w 6 -v 20 -c > ./"$2"/"$2"_lcr.bed

# freebayes v0.9.21-19-gc003c1e
# allele balance filter - 50% to call
./soft/freebayes/bin/freebayes -p 1 --min-alternate-fraction 0.5 -F 0.4 -f "$1" ./"$2"/"$2"_sorted.bam > ./"$2"/"$2".vcf

#vcflib
#variant quality filtering
./soft/vcflib/bin/vcffilter -f "QUAL > 30" ./"$2"/"$2".vcf > ./"$2"/"$2"_q30.vcf

# lcr read filtering with lcr_filter.py
python ./soft/lcr_filter.py ./"$2"/"$2"_lcr.bed ./"$2"/"$2"_q30.vcf ./"$2"/"$2"_lcrfree.vcf ./"$2"/"$2"_inlcr_var.txt

#count depth score for filtering (Li H, 2014)
avg_depth=$(./soft/samtools-1.3/samtools depth ./0/0_sorted.bam  |  awk '{sum+=$3} END { print sum/NR}'| bc -l)
depth_score=$(echo "$avg_depth + sqrt($avg_depth) * 3" | bc -l)
echo $depth_score


#variant max min read depth filtering(DP); if either the number of non-reference reads on the forward strand or on the reverse strand is
#below treshold=3(SAF & SAR)
./soft/vcflib/bin/vcffilter -f "DP < 50" ./"$2"/"$2"_lcrfree.vcf > ./"$2"/"$2"_DP.vcf
./soft/vcflib/bin/vcffilter -f "DP > 5" ./"$2"/"$2"_DP.vcf > ./"$2"/"$2"_DP2.vcf
./soft/vcflib/bin/vcffilter -f "SAF > 3" ./"$2"/"$2"_DP2.vcf > ./"$2"/"$2"_SAF.vcf
./soft/vcflib/bin/vcffilter -f "SAR > 3" ./"$2"/"$2"_SAF.vcf > ./"$2"/"$2"_filt.vcf

# bcftools
bgzip ./"$2"/"$2"_filt.vcf
tabix ./"$2"/"$2"_filt.vcf.gz
./soft/bcftools-1.2/bcftools stats ./"$2"/"$2"_filt.vcf.gz > ./"$2"/"$2".stats


