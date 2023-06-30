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


# code to compute the real binary timestamp mismatches

# disassemble the bytecode of contracts in question
# compute the number of maximal possible matches per contract
# filter mismatches for those results where the tool with more matches is maximal


# Input: directory with plain contracts
# Input: Table with mismatches
# Output: Table with real mismatches
def main(argv):
    contracts_dir = ''
    mismatches = ''
    dataset_stats = ''
    out_dir = ''

    try:
        opts, args = getopt.getopt(argv, "hd:f:o:", ["data=", "out="])
    except getopt.GetoptError:
        print(f'compute-stats.py -d <data-dir1> : [<data-dir2>]')
        sys.exit(2)
    for opt, arg in opts:
        if opt == '-h':
            print(f'binary-timestamp-mismatches.py -d <dataset-stats> -f <mismatch-table> -o <out-dir>')
            sys.exit()
        elif opt in (
        "-d", "--data"):  # if only one file is given, individual files are given, otherwise the files are compared
            dataset_stats = arg
        elif opt in ("-f", "--mismatch-table"):
            mismatches = arg
        elif opt in ("-o", "--out-dir"):
            out_dir = arg

    if not os.path.exists(out_dir):
        os.makedirs(out_dir)

    # read .csv into pandas
    df_mismatches = pd.read_csv(mismatches)
    df_dataset_stats = pd.read_csv(dataset_stats)

    # add the number of calls to table
    df_mismatches_with_callcounts = pd.merge(df_mismatches, df_dataset_stats, on='contract')

    print(df_mismatches_with_callcounts)

    # filter table for binary mismatches: We are only interested in those mismatches where the higher count (count_1) agrees with the number counted calls
    df_binary_mismatches = df_mismatches_with_callcounts[
        (df_mismatches_with_callcounts['property'] != 'TimestampDepend') | (
                    (df_mismatches_with_callcounts['count_1'] == df_mismatches_with_callcounts['call_count']) & (
                        df_mismatches_with_callcounts['has_timestamp'] == True))]
    print(df_binary_mismatches)

    df_binary_mismatches_test = df_mismatches_with_callcounts[
        (df_mismatches_with_callcounts['property'] == 'TimestampDepend') & (
                    df_mismatches_with_callcounts['count_1'] == df_mismatches_with_callcounts['call_count']) & (
                    df_mismatches_with_callcounts['has_timestamp'] == True)]
    print(df_binary_mismatches_test)

    # filter table for binary mismatchs which have a source code
    df_binary_mismatches_with_source = df_mismatches_with_callcounts[
        df_mismatches_with_callcounts['has_source'] == True]

    print(df_binary_mismatches_with_source)

    # write final table to output directory
    df_binary_mismatches.to_csv(f'{out_dir}mismatches_good_binary_correct.csv', sep=',', index=False, header=True)


if __name__ == "__main__":
    main(sys.argv[1:])
