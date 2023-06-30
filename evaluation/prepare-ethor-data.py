#!/usr/bin/python3
# script to prepare the ethor data (just strip of the execution code)

# from asyncio import subprocess
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


# -i input folder
# - o output folder

def main(argv):
    input_dir = ''
    output_dir = ''

    try:
        opts, args = getopt.getopt(argv, "hi:o:", ["input_dir=", "output_dirs="])
    except getopt.GetoptError:
        print(f'execute-horstify.py -s <semantics-file> -c <contract-dir> -t <timeout> -r <result-dir> i <ignore-list>')
        sys.exit(2)
    for opt, arg in opts:
        if opt == '-h':
            print(
                f'execute-horstify.py -s <semantics-file> -c <contract-dir> -t <timeout> -r <result-dir> i <ignore-list>')
            sys.exit()
        elif opt in ("-i", "--input_dir"):
            input_dir = arg
        elif opt in ("-o", "--output_dir"):
            output_dir = arg

    # TODO create output_dir if it does not exist
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)

    # Iterates through all files in the contract directory
    pathlist = Path(input_dir).glob('**/*.json')
    for path in pathlist:
        # because path is object not string
        path_in_str = str(path)

        # extract file name from path
        file = str(os.path.basename(path))
        # extract contract name
        contract = os.path.splitext(file)[0]

        with open(path_in_str) as f:
            contract_json = json.load(f)
            bytecode = contract_json['bytecode']
            # strip of 0x prefix
            bytecode = bytecode[len('0x'):] if bytecode.startswith('0x') else bytecode

        with open(f'{output_dir}/{contract}.bin.hex', "w") as write_file:
            write_file.write(bytecode)


if __name__ == "__main__":
    main(sys.argv[1:])
