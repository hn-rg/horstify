#!/usr/bin/python3
from wsgiref import headers
import pandas as pd
import numpy as np
from bdb import effective
from nis import match
import re
import sys, os
import subprocess
from subprocess import Popen, PIPE, TimeoutExpired
from time import time
from contextlib import contextmanager
import datetime
import io
import signal
import json
import getopt
from pathlib import Path

from disassembler import *


def isRelevantOpcode(opcode):
    return (opcode in ['CALL', 'CALLCODE', 'STATICCALL', 'DELEGATECALL', 'CREATE', 'CREATE2'])


def main(argv):
    contracts_dir = ''
    out = ''
    sources_file = ''

    try:
        opts, args = getopt.getopt(argv, "hf:i:o:", ["data=", "out="])
    except getopt.GetoptError:
        print(f'dataset-stats.py -f <sources-file> -i <contracts-dir> -o <output-file>')
        sys.exit(2)
    for opt, arg in opts:
        if opt == '-h':
            print(f'dataset-stats.py -f <sources-file> -i <contracts-dir> -o <output-file>')
            sys.exit()
        elif opt in ("-f",
                     "--sources-file"):  # if only one file is given, individual files are given, otherwise the files are compared
            sources_file = arg
        elif opt in ("-i",
                     "--contracts-dir"):  # if only one file is given, individual files are given, otherwise the files are compared
            contracts_dir = arg
        elif opt in ("-o", "--out"):
            out = arg

    df_sources = pd.read_csv(sources_file)

    stats = []
    for address in df_sources['contract']:
        contract_code = contracts_dir + address + '.bin.hex'
        with open(contract_code) as f:  # read hexcode from file
            hexcode = f.read()
        contract = decode(address, hexcode)  # returns json object of disassembled contract
        callcount = 0
        callpcs = []
        hasTimestamp = False
        hasSource = df_sources[df_sources['contract'] == address]['hassource'].bool()
        for opcode in contract['opcodes']:
            if isRelevantOpcode(opcode['opcode']):
                callpcs.append(opcode['pc'])
                callcount += 1
            if opcode['opcode'] == 'TIMESTAMP':
                hasTimestamp = True
        stats.append([address, callpcs, callcount, hasTimestamp, hasSource])
    df_dataset_info = pd.DataFrame(stats, columns=['contract', 'call_pcs', 'call_count', 'has_timestamp', 'has_source'])

    print(df_dataset_info)
    df_dataset_info.to_csv(out, sep=',', index=False, header=True)

    # dataset stats number of contracts with timestamp, number of contracts with code

    df_dataset_timestamps = df_dataset_info[df_dataset_info['has_timestamp'] == True]
    print(df_dataset_timestamps)
    df_dataset_sources = df_dataset_info[df_dataset_info['has_source'] == True]
    print(df_dataset_sources)


# compute stats over the whole data set
# receives as input the table with the source codes and the folder with the bytecodes
# computes the pcs of relevant (call opcodes), their number, the existence of source code, the existence of timestamp opcode, the pcs and number of store opcodes
# outputs the resulting table to the output file

if __name__ == "__main__":
    main(sys.argv[1:])
