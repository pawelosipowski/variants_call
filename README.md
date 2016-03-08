author: Pawel Osipowski
8.3.2016

Script for calling and filtering small variants (SV) from short read data.

Pipeline:
1. Read correction and alignment:
./read_alignment.sh
2. Variant calling and filtering:
./variants_call.sh
3. Final max MD filtering of variants:
./maxmd_filter.sh

For detail see shell scripts themselves
