#Pawel Osipowski 17.02.2016
#SCRIPT for variant calling from more than one .bam file


#STEPS:
#PROCESSING after: samblaster, samtools


# SYNOPIS:
# sh variant_call.sh <genome_name.fasta> reads_name

# LCR (low complexity regions) specification on genome with mdust
#./soft/mdust/mdust "$1" -w 6 -v 20 -c > ./"$2"/"$2"_lcr.bed

# 5. VARIANT CALLING with freebayes v0.9.21-19-gc003c1e
# allele balance filter - 50% to call
#./soft/freebayes/bin/freebayes -f "$1" -L ./"$2"/"$2"_bam.list > ./"$2"/"$2".vcf

./soft/tabix-0.2.6/bgzip ./"$2"/"$2".vcf
./soft/tabix-0.2.6/tabix ./"$2"/"$2".vcf.gz
./soft/bcftools-1.3/bcftools stats ./"$2"/"$2".vcf.gz > ./"$2"/1_"$2".stats
./soft/tabix-0.2.6/bgzip -d ./"$2"/"$2".vcf.gz

# 6. VARIANT FILTERING vcflib
#variant quality filtering with vcffilter
./soft/vcflib/bin/vcffilter -f "QUAL > 30" ./"$2"/"$2".vcf > ./"$2"/"$2"_q30.vcf

./soft/tabix-0.2.6/bgzip ./"$2"/"$2"_q30.vcf
./soft/tabix-0.2.6/tabix ./"$2"/"$2"_q30.vcf.gz
./soft/bcftools-1.3/bcftools stats ./"$2"/"$2"_q30.vcf.gz > ./"$2"/2_"$2"_q30.stats
./soft/tabix-0.2.6/bgzip -d ./"$2"/"$2"_q30.vcf.gz

# lcr read filtering with lcr_filter.py
python ./soft/lcr_filter.py ./"$1"_lcr.bed ./"$2"/"$2"_q30.vcf ./"$2"/"$2"_LCRfree.vcf ./"$2"/"$2"_inlcr_var.txt

./soft/tabix-0.2.6/bgzip ./"$2"/"$2"_LCRfree.vcf
./soft/tabix-0.2.6/tabix ./"$2"/"$2"_LCRfree.vcf.gz
./soft/bcftools-1.3/bcftools stats ./"$2"/"$2"_LCRfree.vcf.gz > ./"$2"/3_"$2"_LCRfree.stats
./soft/tabix-0.2.6/bgzip -d ./"$2"/"$2"_LCRfree.vcf.gz

#variant read depth filtering(MD); if either the number of non-reference reads on the forward strand or on the reverse strand is
#below treshold (SAF & SAR)

#count depth score for max MD filtering (Li H, 2014)
#samtools (this is valid - takes only covered bases to count average)
#avg_depth=$(./soft/samtools-1.3/samtools depth ./bam/"$2"_sorted.bam  |  awk '{sum+=$3} END { print sum/NR}'| bc -l)
#depth_score=$(echo "$avg_depth + sqrt($avg_depth) * 3" | bc -l)
#echo 'average loci base coverage is: ' $avg_depth ', and depth score is: ' $depth_score

#bedtools (this is not valid - 0 coverage bases are also taken to count average depth)
#keeping it for future
#./soft/bedtools2/bin/bedtools genomecov -it -ibam ./"$2"/"$2"_sorted.bam > ./"$2"/"$2"_coverage_hist.txt
#avg_depth=$(awk '{ total += $4; count++ } END { print total/count }' ./"$2"/"$2"_coverage.txt)
#depth_score=$(echo "$avg_depth + sqrt($avg_depth) * 3" | bc -l)
#echo 'average loci base coverage is: ' $avg_depth ', and depth score is: ' $depth_score

#min MD (treshold=10)
./soft/vcflib/bin/vcffilter -f "DP > 10" ./"$2"/"$2"_LCRfree.vcf > ./"$2"/"$2"_minDP.vcf

./soft/tabix-0.2.6/bgzip ./"$2"/"$2"_minDP.vcf
./soft/tabix-0.2.6/tabix ./"$2"/"$2"_minDP.vcf.gz
./soft/bcftools-1.3/bcftools stats ./"$2"/"$2"_minDP.vcf.gz > ./"$2"/4_"$2"_minDP.stats
./soft/tabix-0.2.6/bgzip -d ./"$2"/"$2"_minDP.vcf.gz

#SAF&SAR (treshold=5)
./soft/vcflib/bin/vcffilter -f "SAF > 5" ./"$2"/"$2"_minDP.vcf > ./"$2"/"$2"_SAF.vcf
./soft/vcflib/bin/vcffilter -f "SAR > 5" ./"$2"/"$2"_SAF.vcf > ./"$2"/"$2"_SA.vcf

# 7. POST DATA preparation with bcftools
./soft/tabix-0.2.6/bgzip ./"$2"/"$2"_SA.vcf
./soft/tabix-0.2.6/tabix ./"$2"/"$2"_SA.vcf.gz
./soft/bcftools-1.3/bcftools stats ./"$2"/"$2"_SA.vcf.gz > ./"$2"/5_"$2"_SA.stats
./soft/tabix-0.2.6/bgzip -d ./"$2"/"$2"_SA.vcf.gz
