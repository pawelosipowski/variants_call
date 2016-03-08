author: Pawel Osipowski 8.3.2016

Script for calling and filtering small variants (SV) from short read 
data.

literature sources for pipeline:
  https://bcbio.wordpress.com/2014/05/12/wgs-trio-variant-evaluation
  http://bioinformatics.oxfordjournals.org/content/early/2014/07/03/bioinformatics.btu356

Pipeline: 
1. Read correction and alignment:
  ./read_alignment.sh

2. Variant calling and filtering:
  ./variants_call.sh

3. Final max MD filtering of variants:
  ./maxmd_filter.sh

For details see shell scripts themselves
