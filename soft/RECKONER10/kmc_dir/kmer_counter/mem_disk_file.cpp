#include "stdafx.h"
/*
  This file is a part of KMC software distributed under GNU GPL 3 licence.
  The homepage of the KMC project is http://sun.aei.polsl.pl/kmc
  
  Authors: Sebastian Deorowicz, Agnieszka Debudaj-Grabysz, Marek Kokot
  
  Version: 2.3.0
  Date   : 2015-08-21
*/

#include "mem_disk_file.h"
#include "asmlib_wrapper.h"
#include <iostream>
using namespace std;

//----------------------------------------------------------------------------------
// Constructor 
CMemDiskFile::CMemDiskFile(bool _memory_mode)
{
	memory_mode = _memory_mode;
	file = NULL;
}

//----------------------------------------------------------------------------------
void CMemDiskFile::Open(const string& f_name)
{	
	if(memory_mode)
	{

	}
	else
	{
		file = fopen(f_name.c_str(), "wb+");

		if (!file)
		{
			cout << "Error: Cannot open temporary file " << f_name << "\n";
			exit(1);
		}
		setbuf(file, nullptr);
	}
	name = f_name;
}

//----------------------------------------------------------------------------------
void CMemDiskFile::Rewind()
{
	if(memory_mode)
	{

	}
	else
	{
		rewind(file);
	}
}

//----------------------------------------------------------------------------------
int CMemDiskFile::Close()
{
	if(memory_mode)
	{
		for(auto& p : container)
		{
			delete[] p.first;
		}
		container.clear();
		return 0;
	}
	else
	{
		return fclose(file);
	}
}
//----------------------------------------------------------------------------------
void CMemDiskFile::Remove()
{
	if (!memory_mode)
		remove(name.c_str());
}
//----------------------------------------------------------------------------------
size_t CMemDiskFile::Read(uchar * ptr, size_t size, size_t count)
{
	if(memory_mode)
	{
		uint64 pos = 0;
		for(auto& p : container)
		{
			A_memcpy(ptr + pos, p.first, p.second);
			pos += p.second;
			delete[] p.first;
		}
		container.clear();
		return pos;
	}
	else
	{
		return fread(ptr, size, count, file);
	}
}

//----------------------------------------------------------------------------------
size_t CMemDiskFile::Write(const uchar * ptr, size_t size, size_t count)
{
	if(memory_mode)
	{
		uchar *buf = new uchar[size * count];
		A_memcpy(buf, ptr, size * count);
		container.push_back(make_pair(buf, size * count));
		return size * count;
	}
	else
	{
		return fwrite(ptr, size, count, file);
	}
}
