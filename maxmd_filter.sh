#Pawel Osipowski 29.02.2016
#SCRIPT for MD MAX filtering (Li H, 2014)
#to extract mean DP use command: cut -f 8 file.vcf | awk -F \; '{print $8}' > dp.txt
#and process it to count avg_depth

#SYNOPSIS:
#sh maxmd_filter.sh <avg_depth> read_name

#count depth score for max MD filtering (Li H, 2014)
#samtools (this is valid - takes only covered bases to count average)
avg_depth="$1"
depth_score=$(echo "$avg_depth + sqrt($avg_depth) * 3" | bc -l)
echo 'average loci base coverage is: ' $avg_depth ', and depth score is: ' $depth_score


#max MD (treshold=depth score from Li H., 2014)
./soft/vcflib/bin/vcffilter -f "DP < $depth_score" ./"$2"/"$2"_SA.vcf > ./"$2"/"$2"_maxDP.vcf

./soft/tabix-0.2.6/bgzip ./"$2"/"$2"_maxDP.vcf
./soft/tabix-0.2.6/tabix ./"$2"/"$2"_maxDP.vcf.gz
./soft/bcftools-1.3/bcftools stats ./"$2"/"$2"_maxDP.vcf.gz > ./"$2"/6_"$2"_maxDP.stats
./soft/tabix-0.2.6/bgzip -d ./"$2"/"$2"_maxDP.vcf.gz
