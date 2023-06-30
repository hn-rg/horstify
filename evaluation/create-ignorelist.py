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


# Options
# -i : path to input directory
# -o : path to output file

# TODO: later we want to add some more options here (e.g. selecting only files that terminated)
def main(argv):
    input_dir = ''
    output = ''
    global TIMEOUT
    ignore_executed = False
    allow_timedout = False

    try:
        opts, args = getopt.getopt(argv, "hi:o:et", ["input-dir=", "output="])
    except getopt.GetoptError:
        print(f'execute-securify.py -i <input-dir> -o <output> -e -t')
        sys.exit(2)
    for opt, arg in opts:
        if opt == '-h':
            print(f'execute-securify.py -i <input-dir> -o <output>')
            sys.exit()
        elif opt in ("-i", "--input-dir"):
            input_dir = arg
        elif opt in ("-o", "--output-dir"):
            output = arg
        elif opt in ("-e", "--ignore-executed"):
            ignore_executed = True
        elif opt in ("-t", "--allow-timedout"):
            allow_timedout = True
            print(f'Create Allow list!')

    # Iterates through all files in the contract directory
    pathlist = Path(input_dir).glob('**/*.json')

    with open(f'{output}', "w") as write_file:
        write_file.write('')  # initially create an empty file

    for path in pathlist:
        # because path is object not string
        path_in_str = str(path)
        # extract file name from path
        file = str(os.path.basename(path))
        contract = os.path.splitext(os.path.splitext(os.path.splitext(file)[0])[0])[0]

        if ignore_executed:
            with open(f'{output}', "a") as write_file:
                write_file.write(f'{contract}\n')
        elif allow_timedout:
            print(f'We decide whether to add the item now')
            with open(path_in_str) as f:
                result = json.load(f)
            if result['timed_out'] == True:
                with open(f'{output}', "a") as write_file:
                    write_file.write(f'{contract}\n')


if __name__ == "__main__":
    main(sys.argv[1:])
