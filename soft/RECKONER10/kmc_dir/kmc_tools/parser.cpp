/*
  This file is a part of KMC software distributed under GNU GPL 3 licence.
  The homepage of the KMC project is http://sun.aei.polsl.pl/kmc
  
  Authors: Marek Kokot
  
  Version: 2.3.0
  Date   : 2015-08-21
*/

#include "stdafx.h"
#include "parser.h"
#include "tokenizer.h"
#include "output_parser.h"
#include "config.h"

/*****************************************************************************************************************************/
/******************************************************** CONSTRUCTOR ********************************************************/
/*****************************************************************************************************************************/

CParser::CParser(const std::string& src):
	config(CConfig::GetInstance())
{
	line_no = 0;
	file.open(src);
	if (!file.is_open())
	{
		std::cout << "Cannot open file: " << src << "\n";
		exit(1);
	}
	//input_line_pattern = "\\s*(\\w*)\\s*=\\s*(.*)$";
	input_line_pattern = "^\\s*([\\w-+]*)\\s*=\\s*(.*)$"; //TODO: consider valid file name	
	empty_line_pattern = "^\\s*$";
}

/*****************************************************************************************************************************/
/********************************************************** PUBLIC ***********************************************************/
/*****************************************************************************************************************************/

void CParser::ParseInputs()
{
	std::string line;
	while (true)
	{
		if (!nextLine(line))
		{
			std::cout << "Error: 'INPUT:' missing\n";
			exit(1);
		}
		if (line.find("INPUT:") != std::string::npos)
			break;
	}

	if (!nextLine(line) || line.find("OUTPUT:") != std::string::npos)
	{
		std::cout << "Error: None input was defined\n";
		exit(1);
	}

	while (true)
	{
		parseInputLine(line);
		if (!nextLine(line))
		{
			std::cout << "Error: 'OUTPUT:' missing\n";
			exit(1);
		}
		if (line.find("OUTPUT:") != std::string::npos)
			break;
	}
}


/*****************************************************************************************************************************/
/********************************************************** PRIVATE **********************************************************/
/*****************************************************************************************************************************/

/*****************************************************************************************************************************/
void CParser::parseInputLine(const std::string& line)
{
	std::smatch match;
	if (std::regex_search(line, match, input_line_pattern))
	{
#ifdef ENABLE_DEBUG
		std::cout << "\ninput name: " << match[1];
		std::cout << "\nafter = " << match[2];
#endif
		if (input.find(match[1]) != input.end())
		{
			std::cout << "Error: Name redefinition(" << match[1] << ")" << " line: " << line_no << "\n";
			exit(1);
		}
		else
		{
			std::string file_name;
			std::istringstream stream(match[2]);
			
			CInputDesc desc;

			if (!(stream >> desc.file_src))
			{
				std::cout << "Error: file name for " << match[1] << " was not specified, line: "<< line_no <<"\n";
				exit(1);
			}
			std::string tmp;
			while (stream >> tmp)
			{
				if (strncmp(tmp.c_str(), "-ci", 3) == 0)
				{
					desc.cutoff_min = atoi(tmp.c_str() + 3);
					continue;
				}
				else if (strncmp(tmp.c_str(), "-cx", 3) == 0)
				{
					desc.cutoff_max = atoi(tmp.c_str() + 3);
					continue;
				}
				std::cout << "Error: Unknow parameter " << tmp << " for variable " << match[1] << ", line: "<< line_no <<"\n";
				exit(1);
			}

			config.input_desc.push_back(desc);
			input[match[1]] = (uint32)(config.input_desc.size() - 1);				
		}
	}
	else
	{
		std::cout << "Error: wrong line format, line: " << line_no << "\n";
		exit(1);
	}
}

/*****************************************************************************************************************************/
void CParser::parseOtuputParamsLine()
{
	std::string line;

	if (!nextLine(line))
	{
		std::cout << "Warning: OUTPUT_PARAMS exists, but no parameters are defined\n";
	}
	else
	{
		std::istringstream stream(line);
		std::string tmp;
		while (stream >> tmp)
		{
			if (strncmp(tmp.c_str(), "-ci", 3) == 0)
			{				
				config.output_desc.cutoff_min = atoi(tmp.c_str() + 3);
				continue;
			}
			else if (strncmp(tmp.c_str(), "-cx", 3) == 0)
			{			
				config.output_desc.cutoff_max = atoi(tmp.c_str() + 3);
				continue;
			}
			else if ((strncmp(tmp.c_str(), "-cs", 3) == 0))
			{
				config.output_desc.counter_max = atoi(tmp.c_str() + 3);
				continue;
			}
			std::cout << "Error: Unknow parameter " << tmp << " for variable " << tmp << ", line: " << line_no << "\n";
			exit(1);
		}
	}
}

/*****************************************************************************************************************************/
bool CParser::nextLine(std::string& line)
{
	while (true)
	{
		if (file.eof())
			return false;
		std::getline(file, line);
		++line_no;
		std::smatch match;
		if (!std::regex_search(line, match, empty_line_pattern))
			return true;
	}
}

// ***** EOF