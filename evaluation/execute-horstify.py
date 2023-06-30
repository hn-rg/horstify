#!/usr/bin/python3
# script to execute horstify and to write the results into a json format

# from asyncio import subprocess
from bdb import effective
from nis import match
import re
import sys, os, shutil
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

RULES = f'executables/rules'
HORSTIFY_DIR = f'/tmp/horstify/'   # default directory used during contract
HORSTIFY_RESULTS = f'/tmp/horstify/horstify-out/'
SCRIPT_RESULTS = f'/tmp/horstify/script-out/'

PROG_FLAG = "prog"
EXEC_FLAG = "exec"
FACTS_FLAG = "facts"

TIMEOUT = 60


def horstify(contract, dir, results, contract_path):
    # dummy object to return in case of time-out
    res = {
        "tool": 'horstify',
        "version": "full",
        "contract": contract,
        "timeout": TIMEOUT,
        "timed_out": True,
        "error_occurred": False,
        "results": [],
        "errors": []
    }

    start_time = datetime.datetime.now()
    print(f"./horstify {EXEC_FLAG} {dir} {results} {contract_path} ")
    proc = subprocess.Popen(["./horstify", f"{EXEC_FLAG}",f"{dir}", f"{contract_path}", f"{results}"], stdout=PIPE, stderr=PIPE,
                            preexec_fn=os.setsid)

    try:
        stream, errs = proc.communicate(timeout=TIMEOUT)
    except TimeoutExpired:
        os.killpg(os.getpgid(proc.pid), signal.SIGTERM)
        return res
    end_time = datetime.datetime.now()

    # If the execution made it here then there is no timeout
    res['timed_out'] = False

    # Check whether there were errors produced during the execution
    if errs != b'':
        res['errors'].append(errs.decode('utf-8'))
        res['error_occurred'] = True
        return res

    # parses horstify result into a json object
    result_file = f'{HORSTIFY_RESULTS}{contract}.json'

    try:
        with open(result_file) as f:
            horstify_result = json.load(f)
    # if the result could not be parsed into a json write the corresponding error message to the object
    except BaseException as err:
        res['errors'].append(format(err))
        res['error_occurred'] = True
        return res

    exec_time = int((end_time - start_time).total_seconds() * 1000)

    # Generate type from Pattern name
    for res in horstify_result['results']:
        type = res['type']
        str = res['pattern']
        if type == 'Violation':
            res['property'] = re.findall(r'(^\w+)Violation', str)[0]
        if type == 'Safety':
            res['property'] = re.findall(r'(^\w+)Safety', str)[0]

    # add the required data to the result file
    horstify_result['tool'] = 'horstify'
    horstify_result['version'] = "full"
    horstify_result['total_time'] = exec_time
    horstify_result['timeout'] = TIMEOUT
    horstify_result['timed_out'] = False
    horstify_result['error_occurred'] = False
    horstify_result['errors'] = []

    return horstify_result

# Options
# -p : path to the program
# -c : path to the contract directory 
# -t : timeout (in s)
# -r : path to result directory

def main(argv):
    program = 'executables/rules'
    contract_dir = 'experiments/data/ethor-contracts-bytecode'
    result_dir = f'{SCRIPT_RESULTS}'
    ignore_list = ''
    allow_list = ''
    global TIMEOUT

    try:
        opts, args = getopt.getopt(argv, "hp:c:t:r:i:a:",
                                   ["program=", "contracts=", "timeout=", "results=", "ignore=", "allow="])
        #if len(opts) == 0:
        #    raise getopt.GetoptError("No args given")
    except getopt.GetoptError:
        print(
            f'execute-horstify.py -p <program-file> -c <contract-dir> -t <timeout> -r <result-dir> -i <ignore-list> -a <allow-list>')
        sys.exit(2)
    for opt, arg in opts:
        if opt == '-h':
            print(
                f'execute-horstify.py -p <program-file> -c <contract-dir> -t <timeout> -r <result-dir> -i <ignore-list> -a <allow-list>')
            sys.exit()
        elif opt in ("-p", "--program"):
            program = arg
        elif opt in ("-c", "--contracts"):
            contract_dir = arg
        elif opt in ("-t", "--timeout"):
            TIMEOUT = int(arg)
        elif opt in ("-r", "--results"):
            result_dir = arg
        elif opt in ("-i", "--ignore"):
            ignore_list = arg
        elif opt in ("-a", "--allow"):
            allow_list = arg

    if ignore_list != '':
        f_ignore = open(ignore_list)
        content = f_ignore.read()
        ignore = content.split("\n")
    else:
        ignore = []

    # read contract addresses from file
    if allow_list != '':
        f_allow = open(allow_list)
        content = f_allow.read()
        allow = content.split("\n")
        allow_exists = True
    else:
        allow = []
        allow_exists = False

    if not os.path.exists(HORSTIFY_DIR):
        os.makedirs(HORSTIFY_DIR)

    if not os.path.exists(HORSTIFY_RESULTS):
        os.makedirs(HORSTIFY_RESULTS)

    if not os.path.exists(result_dir):
        os.makedirs(result_dir)

    shutil.copy2(program, f"{HORSTIFY_DIR}rules")

    # Iterates through all files in the contract directory
    pathlist = Path(contract_dir).glob('**/*.bin.hex')
    for path in pathlist:
        # because path is object not string
        path_in_str = str(path)
        # extract file name from path
        file = str(os.path.basename(path))
        file_stripped = os.path.splitext(os.path.splitext(file)[0])[0]

        if file_stripped in ignore:
            print(f'Ignore {file_stripped}')
            continue

        if allow_exists and (not file_stripped in allow):
            print(f'{file_stripped} not allowed')
            continue

        with open(f'{HORSTIFY_RESULTS}{file_stripped}.json', 'a'):
            os.utime(f'{HORSTIFY_RESULTS}{file_stripped}.json')
        # execute horstify
        # TODO JSON Out dir flag is currently not accepted due to Pyhton Popen stuff
        horstify_result = horstify(file_stripped,
                                   f'{HORSTIFY_DIR}',
                                   f'-j{HORSTIFY_RESULTS}',
                                   f'{path_in_str}')

        # write resulting object into a .json file in the result directory
        with open(f'{result_dir}/{file_stripped}.json', "w") as write_file:
            json.dump(horstify_result, write_file)


if __name__ == "__main__":
    main(sys.argv[1:])
