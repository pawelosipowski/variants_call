# -*- coding: utf-8 -*-

#proper command: python lcr_filter.py <lcr_file> <in.vcf> <out.vcf> <deleted.txt>

import time
import sys

lcr_filter = sys.argv[1]
var_in = sys.argv[2]
var_out = sys.argv[3]
deleted_out = sys.argv[4]

with open(lcr_filter,'r') as p1:
    with open(var_in,'r') as p2:
        with open(var_out,'w') as pout:
            with open(deleted_out,'w') as wyrzut:
                # idea - najpierw wylapujemy przedzialy z pliku p1 i zapisujemy jako slownik,
                # ktorego wartosciamy jest zbior przedzialow.
                # potem dla wartosci w p2 spr, czy jest ona w ktorym z przedzialow

                p1dict = {}

                #czas
                time_start = time.time()

                while(True):
                    p1line = p1.readline()
                    if p1line == '' :
                        break
                    p1split = p1line.split('\t')
                    klucz = p1split[0]
                    od = int(p1split[2])
                    do = int(p1split[3].split('\n')[0])
                    if klucz not in p1dict:
                        p1dict[klucz] = [ [od,do] ]
                    else :
                        p1dict[klucz] += [ [ od, do] ]

                cnt_linii = 0
                nie_ma_klucza_cnt = 0
                sa_w_przedziale_cnt = 0
                nie_ma_w_przedziale_cnt = 0
                # przed '#CHROME' wlacznie wszystko wpisujemy
                while(True):
                    cnt_linii += 1
                    this_line = p2.readline()
                    pout.write(this_line)
                    if this_line[:6] == '#CHROM':
                        break

                while(True):
                    #wydlubujemy wartosci
                    p2line = p2.readline()
                    cnt_linii += 1
                    #jesli pusta linia, to koniec pliku
                    if p2line == '' :
                        break
                    p2split = p2line.split('\t')
                    klucz = p2split[0]
                    val = int(p2split[1])
                    if klucz not in p1dict:
                        nie_ma_klucza_cnt += 1
                        pout.write(p2line)
                    else:
                        found = False
                        for i in range( len(p1dict[klucz]) ):
                            if p1dict[klucz][i][0] < val < p1dict[klucz][i][1]:
                                # znalezione
                                wyrzut.write("linia=" + str(cnt_linii) +
                                        " , klucz=" + str(p1dict[klucz]) +
                                        " , od=" + str(p1dict[klucz][i][0]) +
                                        " , val=" + str(val) +
                                        " , do=" + str(p1dict[klucz][i][1]) +
                                        "\n")
                                found = True
                                sa_w_przedziale_cnt += 1
                                break
                        if not found:
                            nie_ma_w_przedziale_cnt += 1
                            pout.write(p2line)
                    if cnt_linii%1000 == 0:
                        time_now = time.time()
                        print '-- linia  ' + str(cnt_linii) + '  -----  uplynelo ' + str( time_now - time_start ) + ' sekund'

                time_end = time.time()
                print '-----  end  -----   '
                print 'czas = ' + str( time_end - time_start ) + ' sekund'
                print ' ilosc linii        ' + str( cnt_linii )
                print 'nie_ma_klucza_cnt = ' + str(nie_ma_klucza_cnt)
                print 'sa_w_przedzialach_cnt = ' + str(sa_w_przedziale_cnt)
                print 'nie_ma_w_przedzialach_cnt = ' + str(nie_ma_w_przedziale_cnt)


