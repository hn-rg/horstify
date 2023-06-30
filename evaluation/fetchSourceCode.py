#!/usr/bin/python3
from wsgiref import headers
import pandas as pd
import numpy as np
from bdb import effective
from nis import match
import re
import sys
import os
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
from etherscan import Etherscan
from disassembler import *


eth = Etherscan("5S4RIQJ4QQCED9M3BIZX2CSYWR8HZG17QS")



def main(argv):
    input_dir = ''
    out = ''

    try:
        opts, args = getopt.getopt(argv, "hi:o:", ["data=", "out="])
    except getopt.GetoptError:
        print(f'fetchSourceCode.py -i <input-dir> -o <output-file>')
        sys.exit(2)
    for opt, arg in opts:
        if opt == '-h':
            print(f'fetchSourceCode.py -i <input-dir> -o <output-file>')
            sys.exit()
        elif opt in (
                "-i",
                "--input-dir"):  # if only one file is given, individual files are given, otherwise the files are compared
            input_dir = arg
        elif opt in ("-o", "--out"):
            out = arg

    # check for source code of contracts (create table that indicates existence of source code)
    sourcecodes = []
    # Iterates through all files in the contract directory
    pathlist = Path(input_dir).glob('**/*.json')

    for path in pathlist:
        # because path is object not string
        path_in_str = str(path)
        # extract file name from path
        file = str(os.path.basename(path))
        contract = os.path.splitext(os.path.splitext(os.path.splitext(file)[0])[0])[0]
        source = eth.get_contract_source_code(contract)
        # source = json.loads(source_str)
        code = source[0]['SourceCode']
        print(code)
        if not code:
            sourcecodes.append([contract, False, code])
        else:
            sourcecodes.append([contract, True, code])

    df_sources = pd.DataFrame(sourcecodes, columns=['contract', 'hassource', 'sourcecode'])
    print(df_sources)
    df_sources.to_csv(out, sep=',', index=False, header=True)


if __name__ == "__main__":
    main(sys.argv[1:])
