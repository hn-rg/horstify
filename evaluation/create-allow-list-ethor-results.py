#!/usr/bin/python3
# script to execute horstify and to write the results into a json format

# from asyncio import subprocess
# from ast import pattern
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
import pandas as pd


# Options
# -i : path to input directory
# -o : path to output file

# TODO: later we want to add some more options here (e.g. selecting only files that terminated)
def main(argv):
    input_file = ''
    output = ''
    global TIMEOUT

    try:
        opts, args = getopt.getopt(argv, "hi:o:", ["input-dir=", "output="])
    except getopt.GetoptError:
        print(f'execute-securify.py -i <input-dir> -o <output>')
        sys.exit(2)
    for opt, arg in opts:
        if opt == '-h':
            print(f'execute-securify.py -i <input-file> -o <output>')
            sys.exit()
        elif opt in ("-i", "--input-file"):
            input_file = arg
        elif opt in ("-o", "--output-dir"):
            output = arg

    # Iterates through all files in the contract directory
    path_in_str = input_file

    print(path_in_str)

    df = pd.read_csv(path_in_str, sep=';')

    df_secure_labeled_contracts = df[df['ethor-result'] == 'secure']['contract-id']

    df_secure_labeled_contracts.to_csv(output, sep=',', index=False, header=False)


if __name__ == "__main__":
    main(sys.argv[1:])
