/*
  This file is a part of KMC software distributed under GNU GPL 3 licence.
  The homepage of the KMC project is http://sun.aei.polsl.pl/kmc
  
  Authors: Marek Kokot
  
  Version: 2.3.0
  Date   : 2015-08-21
*/

#ifndef _PARAMETERS_PARSER_H
#define _PARAMETERS_PARSER_H

#include "defs.h"
#include "parser.h"
#include <memory>
class CParametersParser
{	
	std::unique_ptr<CParser> complex_parser;
	int argc;
	char** argv;
	int pos;
	CConfig& config;

	uint32 replace_zero(uint32 val, const char* param_name, uint32 value_to_set_if_zero);
	void parse_int_or_float(bool& force_float, bool& force_int, float& float_value, uint32& int_val, const char* param_name);
	void parse_global_params();
	void read_input_fastq_desc();
	void read_output_fastq_desc();
	void read_input_desc();
	void read_dump_params();
	void read_output_desc();
public:
	
	CParametersParser(int argc, char** argv);
	void Usage();
	
	template<unsigned SIZE>
	CExpressionNode<SIZE>* GetExpressionRoot();

	void Parse();
	bool validate_input_dbs();
	void SetThreads();
	
};

template<unsigned SIZE>
CExpressionNode<SIZE>* CParametersParser::GetExpressionRoot()
{
	if (config.mode == CConfig::Mode::INTERSECTION || config.mode == CConfig::Mode::KMERS_SUBTRACT || config.mode == CConfig::Mode::UNION || config.mode == CConfig::Mode::COUNTERS_SUBTRACT)
	{
		CExpressionNode<SIZE>* left = new CInputNode<SIZE>(0);
		CExpressionNode<SIZE>* right = new CInputNode<SIZE>(1);
		CExpressionNode<SIZE>* expression_root = nullptr;
		switch (config.mode)
		{
		case CConfig::Mode::INTERSECTION:
			expression_root = new CIntersectionNode<SIZE>;
			break;
		case CConfig::Mode::KMERS_SUBTRACT:
			expression_root = new CKmersSubtractionNode<SIZE>;
			break;
		case CConfig::Mode::UNION:
			expression_root = new CUnionNode<SIZE>;
			break;
		case CConfig::Mode::COUNTERS_SUBTRACT:
			expression_root = new CCountersSubtractionNode<SIZE>;
			break;
		default:
			std::cout << "Error: unknow operation\n";
			exit(1);
		}
		expression_root->AddLeftChild(left);
		expression_root->AddRightChild(right);
		return expression_root;
	}
	else if (config.mode == CConfig::Mode::COMPLEX)
	{
		auto result = complex_parser->ParseOutput<SIZE>();
		return result;
	}
	else if (config.mode == CConfig::Mode::SORT)
	{
		if (!config.headers.front().IsKMC2())
		{
			std::cout << "This database contains sorted k-mers already!";
			exit(1);
		}
		return new CInputNode<SIZE>(0);
	}
	else if (config.mode == CConfig::Mode::REDUCE)
	{
		return new CInputNode<SIZE>(0);
	}
	else if (config.mode == CConfig::Mode::COMPACT)
	{
		return new CInputNode<SIZE>(0);
	}
	else //should never be here
	{
		std::cout << "Error: unknow operation\n";
#ifdef ENABLE_DEBUG
		std::cout << __FUNCTION__ << " line: " << __LINE__ << "\n";
#endif
		exit(1);
	}
	}
#endif


// ***** EOF