#Pawel Osipowski 16.11.2016
#SCRIPT for read alignment
#read files should be trimmed of adapters
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

#READ PREPARATION
#read adapter trimming, quality trimming - choose proper adapter fasta file in ./soft/Trimmomatic/adapters:
#trimmomatic: http://www.usadellab.org/cms/index.php?page=trimmomatic
echo 'Trimmomatic'
echo ''
java -jar ./soft/Trimmomatic-0.35/trimmomatic-0.35.jar PE ./"$2"_fw.fq ./"$2"_rv.fq ./"$2"/tt_"$2"_fw.fq ./"$2"/tt_"$2"_fw_unpaired.fq ./"$2"/tt_"$2"_rv.fq ./"$2"/tt_"$2"_rv_unpaired.fq ILLUMINACLIP:./soft/Trimmomatic-0.35/adapters/TruSeq3-PE.fa:2:30:15 TRAILING:30 MINLEN:50
echo''

#read normalization, kmer error correction and kmer distribution histograms
#bbmap
echo 'bbmap'
echo ''
./soft/bbmap/ecc.sh in=./"$2"/tt_"$2"_fw.fq in2=./"$2"/tt_"$2"_rv.fq out1=./"$2"/ecc_"$2"_fw.fq out2=./"$2"/ecc_"$2"_rv.fq hist=./"$2"/bbmap_kmer_"$2"_in_hist histout=./"$2"/bbmap_kmer_"$2"_out_hist
#./soft/bbmap/bbnorm.sh in=./"$2"/tt_"$2"_fw_unpaired.fq  out=./"$2"/ecc_"$2"_fw_unpaired.fq target=40 mindepth=3 ecc cec=t hist=./"$2"/kmer_"$2"_up_fw_in_hist histout=./"$2"/kmer_"$2"_up_fw_out_hist
#./soft/bbmap/bbnorm.sh in=./"$1"/tt_"$2"_rv_unpaired.fq  out=./"$2"/ecc_"$2"_rv_unpaired.fq target=40 mindepth=3 ecc cec=t hist=./"$2"/kmer_"$1"_up_rv_in_hist histout=./"$2"/kmer_"$2"_up_rv_out_hist
echo ''

# 1. ALIGNMENT with MOSAIK
echo 'MOSAIK'
echo ''
#reference conversion
./soft/MOSAIK/build/MosaikBuild -fr ./"$1" -oa mosaik_"$1".dat
#read conversion
./soft/MOSAIK/build/MosaikBuild -quiet -mfl 300 -st illumina -q ./"$2"/ecc_"$2"_fw.fq -q2 ./"$2"/ecc_"$2"_rv.fq -out ./"$2"/"$2".dat
#build data
./soft/MOSAIK/build/MosaikJump -ia mosaik_"$1".dat -out mosaik_"$1" -hs 15 -mem 250
#alignment
./soft/MOSAIK/build/MosaikAligner -p 60 -quiet -annse ./soft/mosaik_2.2.3/networkFile/2.1.26.se.100.005.ann -annpe ./soft/mosaik_2.2.3/networkFile/2.1.26.pe.100.0065.ann -ia mosaik_"$1".dat -in ./"$2"/"$2".dat -out ./"$2"/"$2"
#export and duplicate removal
#./soft/MOSAIK/build/MosaikText -in ./"$2"/"$2".bam -bam ./"$2"/"$2"_unique.bam
echo ''

#additional ALIGNMENTS
#INDEXING genome for alignment with bwa
#./soft/bwa-0.7.7/bwa index "$1"
# ALIGNMENT with bwa
#./soft/bwa-0.7.12/bwa mem -t 10 "$1" "$2"_fw.fq "$2"_rv.fq > ./"$2"/"$2".sam
#secondary ALIGNMENT with bbmap (more sensitive but some errors in .bam files)
#./soft/bbmap/bbmap.sh ref="$1" in="$2"_fw.fq in2="$2"_rv.fq out=./"$2"/"$2".sam

#converting bam to sam
./soft/samtools-1.3/samtools view -h -o ./"$2"/"$2".sam ./"$2"/"$2".bam

# 2. DUPLICATE REMOVAL with samblaster version 0.1.22
./soft/samblaster/samblaster -i ./"$2"/"$2".sam -o ./"$2"/"$2"_marked.sam

# 3. CONVERTING TO BAM with samtools 1.2 using htslib 1.2.1

./soft/samtools-1.3/samtools view -Sb -o ./"$2"/"$2".bam ./"$2"/"$2"_marked.sam
./soft/samtools-1.3/samtools sort -o ./"$2"/"$2"_sorted.bam -@ 20 ./"$2"/"$2"_uniwue.bam
./soft/samtools-1.3/samtools index ./"$2"/"$2"_sorted.bam

